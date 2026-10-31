import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Файловый хеш-индекс: ID участника -> номер слота.
 * Коллизии разрешаются цепочками узлов на диске.
 */
public final class IdIndex implements Closeable {
    private static final int MAGIC = 0x49444958;
    private static final int VERSION = 1;
    private static final int BUCKETS = 4099;

    private static final long HEADER_SIZE = 12;
    private static final long DATA_START = HEADER_SIZE + BUCKETS * 8L;
    private static final int NODE_SIZE = 20;

    private final RandomAccessFile file;

    public IdIndex(Path path) throws IOException {
        file = new RandomAccessFile(path.toFile(), "rw");

        try {
            if (file.length() == 0) {
                file.writeInt(MAGIC);
                file.writeInt(VERSION);
                file.writeInt(BUCKETS);

                for (int i = 0; i < BUCKETS; i++) {
                    file.writeLong(-1);
                }
            }

            file.seek(0);

            if (file.length() < DATA_START
                    || file.readInt() != MAGIC
                    || file.readInt() != VERSION
                    || file.readInt() != BUCKETS
                    || (file.length() - DATA_START) % NODE_SIZE != 0) {
                throw new IOException("Некорректный файл индекса");
            }
        } catch (IOException | RuntimeException exception) {
            file.close();
            throw exception;
        }
    }

    private long bucketPosition(int id) {
        return HEADER_SIZE + Math.floorMod(id, BUCKETS) * 8L;
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
        long remaining = (file.length() - DATA_START) / NODE_SIZE;

        while (true) {
            long node = readPointer(link);

            if (node == -1) {
                return link;
            }

            if (remaining-- <= 0
                    || node < DATA_START
                    || node > file.length() - NODE_SIZE
                    || (node - DATA_START) % NODE_SIZE != 0) {
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

        long node = file.length();

        file.seek(node);
        file.writeInt(id);
        file.writeLong(slot);
        file.writeLong(-1);

        writePointer(link, node);
    }

    public boolean delete(int id) throws IOException {
        long link = findLink(id);
        long node = readPointer(link);

        if (node == -1) {
            return false;
        }

        long next = readPointer(node + 12);
        writePointer(link, next);

        return true;
    }

    public void save() throws IOException {
        file.getFD().sync();
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}