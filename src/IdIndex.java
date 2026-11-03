import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Файловый хеш-индекс: ID участника -> номер слота.
 * Коллизии разрешаются цепочками узлов на диске.
 * При заполнении более 75% индекс расширяется; старый формат поддерживается.
 */
public final class IdIndex implements Closeable {
    private static final int MAGIC = 0x49444958;
    private static final int VERSION = 1;
    private static final int INITIAL_BUCKETS = 4099;

    private static final long HEADER_SIZE = 12;
    
    private static final int NODE_SIZE = 20;

    private RandomAccessFile file;
    private final Path path;
    private int buckets;
    private long dataStart;
    private long entries;

    public IdIndex(Path path) throws IOException {
        this.path = path.toAbsolutePath();
        file = new RandomAccessFile(this.path.toFile(), "rw");
        try {
            if (file.length() == 0) {
                initialize(INITIAL_BUCKETS);
            }
            file.seek(0);
            if (file.length() < HEADER_SIZE
                    || file.readInt() != MAGIC || file.readInt() != VERSION) {
                throw new IOException("Некорректный файл индекса");
            }
            buckets = file.readInt();
            dataStart = HEADER_SIZE + buckets * 8L;
            if (buckets <= 0 || file.length() < dataStart
                    || (file.length() - dataStart) % NODE_SIZE != 0) {
                throw new IOException("Некорректный размер индекса");
            }
            entries = countEntries();
        } catch (IOException | RuntimeException exception) {
            file.close();
            throw exception;
        }
    }

    private void initialize(int count) throws IOException {
        buckets = count;
        dataStart = HEADER_SIZE + buckets * 8L;
        file.setLength(dataStart);
        file.seek(0);
        file.writeInt(MAGIC);
        file.writeInt(VERSION);
        file.writeInt(buckets);
        for (int i = 0; i < buckets; i++) {
            file.writeLong(-1);
        }
        entries = 0;
    }

    private void checkNode(long node) throws IOException {
        if (node < dataStart || node > file.length() - NODE_SIZE
                || (node - dataStart) % NODE_SIZE != 0) {
            throw new IOException("Повреждён указатель индекса");
        }
    }

    private long countEntries() throws IOException {
        long count = 0;
        long totalNodes = (file.length() - dataStart) / NODE_SIZE;
        for (int bucket = 0; bucket < buckets; bucket++) {
            long node = readPointer(HEADER_SIZE + bucket * 8L);
            long remaining = totalNodes;
            while (node != -1) {
                checkNode(node);
                if (remaining-- <= 0) {
                    throw new IOException("Цикл в цепочке индекса");
                }
                file.seek(node);
                int id = file.readInt();
                long slot = file.readLong();
                if (id <= 0 || slot < 0 || Math.floorMod(id, buckets) != bucket) {
                    throw new IOException("Повреждён узел индекса");
                }
                node = file.readLong();
                count++;
            }
        }
        return count;
    }

    /** Переносит доступные узлы в новый файл, по одному, без загрузки базы в память. */
    private void grow() throws IOException {
        if (buckets > (Integer.MAX_VALUE - 1) / 2) {
            throw new IOException("Достигнут максимальный размер индекса");
        }
        int newBuckets = buckets * 2 + 1;
        Path temporary = Files.createTempFile(path.getParent(), "index-grow-", ".tmp");
        try {
            try (IdIndex target = new IdIndex(temporary)) {
                target.initialize(newBuckets);
                long copied = 0;
                long totalNodes = (file.length() - dataStart) / NODE_SIZE;
                for (int bucket = 0; bucket < buckets; bucket++) {
                    long node = readPointer(HEADER_SIZE + bucket * 8L);
                    long remaining = totalNodes;
                    while (node != -1) {
                        checkNode(node);
                        if (remaining-- <= 0) {
                            throw new IOException("Цикл в цепочке индекса");
                        }
                        file.seek(node);
                        int id = file.readInt();
                        long slot = file.readLong();
                        long next = file.readLong();
                        target.prepend(id, slot);
                        copied++;
                        node = next;
                    }
                }
                if (copied != entries) {
                    throw new IOException("Число узлов изменилось при расширении");
                }
                target.save();
            }
            // На Windows открытый файл нельзя заменять, поэтому закрываем его.
            // Если файловая система не поддерживает атомарную замену, исходник сохранится.
            file.close();
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                buckets = newBuckets;
                dataStart = HEADER_SIZE + buckets * 8L;
            } finally {
                file = new RandomAccessFile(path.toFile(), "rw");
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void prepend(int id, long slot) throws IOException {
        long link = bucketPosition(id);
        long previous = readPointer(link);
        long node = file.length();
        file.seek(node);
        file.writeInt(id);
        file.writeLong(slot);
        file.writeLong(previous);
        writePointer(link, node);
    }

    public int bucketCount() {
        return buckets;
    }

    public long entryCount() {
        return entries;
    }

    private long bucketPosition(int id) {
        return HEADER_SIZE + Math.floorMod(id, buckets) * 8L;
    }

    private long readPointer(long position) throws IOException {
        file.seek(position);
        return file.readLong();
    }

    private void writePointer(long position, long value) throws IOException {
        file.seek(position);
        file.writeLong(value);
    }

    /**
     * Возвращает адрес указателя на узел с данным ID.
     * Если ID отсутствует — адрес последнего указателя со значением -1.
     */
    private long findLink(int id) throws IOException {
        long link = bucketPosition(id);
        long remaining = (file.length() - dataStart) / NODE_SIZE;

        while (true) {
            long node = readPointer(link);

            if (node == -1) {
                return link;
            }

            if (remaining-- <= 0
                    || node < dataStart
                    || node > file.length() - NODE_SIZE
                    || (node - dataStart) % NODE_SIZE != 0) {
                throw new IOException("Повреждена цепочка индекса");
            }

            file.seek(node);

            if (file.readInt() == id) {
                return link;
            }

            link = node + 12;
        }
    }

    /**
     * Возвращает номер слота или -1, если ID не найден.
     */
    public long find(int id) throws IOException {
        long node = readPointer(findLink(id));

        if (node == -1) {
            return -1;
        }

        file.seek(node + 4);
        return file.readLong();
    }

    public void insert(int id, long slot) throws IOException {
        if (id <= 0 || slot < 0) {
            throw new IllegalArgumentException("Некорректные ID или слот");
        }

        long link = findLink(id);

        if (readPointer(link) != -1) {
            throw new IllegalArgumentException("ID уже существует: " + id);
        }

        if (entries + 1 > buckets * 3L / 4) {
            grow();
        }
        prepend(id, slot);
        entries++;
    }

    public boolean delete(int id) throws IOException {
        long link = findLink(id);
        long node = readPointer(link);

        if (node == -1) {
            return false;
        }

        long next = readPointer(node + 12);
        writePointer(link, next);
        entries--;

        return true;
    }
    /**
     * Удаляет узлы индекса и очищает все корзины.
     */
    public void clear() throws IOException {
        initialize(INITIAL_BUCKETS);
    }
    /**
     * Проверяет, что каждый доступный узел индекса
     * указывает на существующего участника с соответствующим ID.
     *
     * Возвращает количество доступных узлов.
     */
    public long validateEntries(PlayerStorage storage) throws IOException {
        long entries = 0;
        long totalNodes = (file.length() - dataStart) / NODE_SIZE;

        for (int bucket = 0; bucket < buckets; bucket++) {
            long node = readPointer(HEADER_SIZE + bucket * 8L);
            long remaining = totalNodes;

            while (node != -1) {
                if (remaining-- <= 0
                        || node < dataStart
                        || node > file.length() - NODE_SIZE
                        || (node - dataStart) % NODE_SIZE != 0) {
                    throw new IOException(
                            "Повреждена цепочка индекса в корзине " + bucket
                    );
                }

                file.seek(node);

                int id = file.readInt();
                long slot = file.readLong();
                long next = file.readLong();

                if (id <= 0 || Math.floorMod(id, buckets) != bucket) {
                    throw new IOException(
                            "ID находится в неправильной корзине: " + id
                    );
                }

                if (slot < 0 || slot >= storage.slotCount()) {
                    throw new IOException(
                            "Индекс указывает на несуществующий слот: " + slot
                    );
                }

                Player player = storage.read(slot);

                if (player == null || player.getId() != id) {
                    throw new IOException(
                            "Индекс не соответствует записи с ID " + id
                    );
                }

                entries++;
                node = next;
            }
        }

        return entries;
    }
    public void save() throws IOException {
        file.getFD().sync();
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
