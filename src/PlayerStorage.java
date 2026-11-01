import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Хранит записи непосредственно в файле.
 * Работает с номерами слотов, а не с ID участников.
 */
public final class PlayerStorage implements Closeable {
    private static final int MAGIC = 0x43484442;
    private static final int VERSION = 1;

    private static final int HEADER_SIZE = 16;
    private static final int SLOT_SIZE = 1 + 8 + PlayerCodec.RECORD_SIZE;
    private static final long NO_SLOT = -1;

    private final RandomAccessFile file;
    /**
     * Удаляет все слоты и сбрасывает цепочку свободных мест.
     */
    public void clear() throws IOException {
        file.setLength(HEADER_SIZE);

        file.seek(0);
        file.writeInt(MAGIC);
        file.writeInt(VERSION);
        file.writeLong(NO_SLOT);
    }
    public PlayerStorage(Path path) throws IOException {
        file = new RandomAccessFile(path.toFile(), "rw");

        try {
            if (file.length() == 0) {
                file.writeInt(MAGIC);
                file.writeInt(VERSION);
                file.writeLong(NO_SLOT);
            }

            file.seek(0);

            if (file.length() < HEADER_SIZE
                    || file.readInt() != MAGIC
                    || file.readInt() != VERSION) {
                throw new IOException("Неизвестный формат базы данных");
            }

            if ((file.length() - HEADER_SIZE) % SLOT_SIZE != 0) {
                throw new IOException("Файл повреждён: неполная запись");
            }
        } catch (IOException | RuntimeException exception) {
            file.close();
            throw exception;
        }
    }

    public long slotCount() throws IOException {
        return (file.length() - HEADER_SIZE) / SLOT_SIZE;
    }

    private long offset(long slot) throws IOException {
        if (slot < 0 || slot >= slotCount()) {
            throw new IllegalArgumentException("Нет слота №" + slot);
        }

        return HEADER_SIZE + slot * SLOT_SIZE;
    }

    private long readFreeHead() throws IOException {
        file.seek(8);
        return file.readLong();
    }

    private void writeFreeHead(long slot) throws IOException {
        file.seek(8);
        file.writeLong(slot);
    }

    /**
     * Добавляет запись и возвращает номер занятого слота.
     * Уникальность ID проверяется уровнем базы данных.
     */
    public long insert(Player player) throws IOException {
        if (player == null) {
            throw new IllegalArgumentException("Не указан участник");
        }

        long slot = readFreeHead();
        long position;

        if (slot == NO_SLOT) {
            slot = slotCount();
            position = file.length();
        } else {
            position = offset(slot);
            file.seek(position);

            if (file.readBoolean()) {
                throw new IOException("Повреждена цепочка свободных слотов");
            }

            long nextFree = file.readLong();
            writeFreeHead(nextFree);
        }

        file.seek(position);
        file.writeBoolean(true);
        file.writeLong(NO_SLOT);
        PlayerCodec.write(file, player);

        return slot;
    }

    /**
     * Возвращает null, если запись в этом слоте удалена.
     */
    public Player read(long slot) throws IOException {
        file.seek(offset(slot));

        if (!file.readBoolean()) {
            return null;
        }

        file.readLong();
        return PlayerCodec.read(file);
    }

    public void update(long slot, Player player) throws IOException {
        if (player == null) {
            throw new IllegalArgumentException("Не указан участник");
        }

        long position = offset(slot);
        file.seek(position);

        if (!file.readBoolean()) {
            throw new IllegalArgumentException("Запись уже удалена");
        }

        file.seek(position + 1 + 8);
        PlayerCodec.write(file, player);
    }

    public boolean delete(long slot) throws IOException {
        long position = offset(slot);
        file.seek(position);

        if (!file.readBoolean()) {
            return false;
        }

        long previousFree = readFreeHead();

        file.seek(position);
        file.writeBoolean(false);
        file.writeLong(previousFree);

        writeFreeHead(slot);
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