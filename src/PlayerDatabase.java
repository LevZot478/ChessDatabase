import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Управляет записями участников и индексом по ID.
 * Одна база хранится в отдельной папке.
 */
public final class PlayerDatabase implements Closeable {
    private final PlayerStorage storage;
    private final IdIndex index;

    public PlayerDatabase(Path directory) throws IOException {
        Files.createDirectories(directory);

        Path dataPath = directory.resolve("players.dat");
        Path indexPath = directory.resolve("players-id.idx");

        // Не создаём пустой индекс рядом с существующими данными.
        if (Files.exists(dataPath)
                && Files.size(dataPath) > 16
                && (!Files.exists(indexPath) || Files.size(indexPath) == 0)) {
            throw new IOException(
                    "Отсутствует индекс существующей базы"
            );
        }

        // Не подключаем старый индекс к новому файлу данных.
        if ((!Files.exists(dataPath) || Files.size(dataPath) == 0)
                && Files.exists(indexPath)
                && Files.size(indexPath) > 0) {
            throw new IOException(
                    "Есть индекс, но отсутствует файл данных"
            );
        }

        storage = new PlayerStorage(dataPath);

        try {
            index = new IdIndex(indexPath);
        } catch (IOException | RuntimeException exception) {
            storage.close();
            throw exception;
        }
    }

    public Player findById(int id) throws IOException {
        long slot = index.find(id);

        if (slot == -1) {
            return null;
        }

        Player player = storage.read(slot);

        if (player == null || player.getId() != id) {
            throw new IOException("Индекс не соответствует данным");
        }

        return player;
    }

    public void add(Player player) throws IOException {
        requirePlayer(player);

        if (index.find(player.getId()) != -1) {
            throw new IllegalArgumentException(
                    "Участник с ID " + player.getId() + " уже существует"
            );
        }

        long slot = storage.insert(player);
        index.insert(player.getId(), slot);
    }

    /**
     * Изменяет запись. Разрешает изменить ID,
     * если новый ID не занят другим участником.
     */
    public void update(int oldId, Player replacement) throws IOException {
        requirePlayer(replacement);

        if (findById(oldId) == null) {
            throw new IllegalArgumentException("Участник не найден");
        }

        long slot = index.find(oldId);
        int newId = replacement.getId();

        if (newId != oldId && index.find(newId) != -1) {
            throw new IllegalArgumentException(
                    "Новый ID уже занят: " + newId
            );
        }

        storage.update(slot, replacement);

        if (newId != oldId) {
            index.delete(oldId);
            index.insert(newId, slot);
        }
    }

    public boolean deleteById(int id) throws IOException {
        if (findById(id) == null) {
            return false;
        }

        long slot = index.find(id);
        storage.delete(slot);
        index.delete(id);

        return true;
    }

    public void save() throws IOException {
        storage.save();
        index.save();
    }
    /**
     * Читает существующие записи по одной для отображения.
     */
    public void forEachPlayer(java.util.function.Consumer<Player> action)
            throws IOException {

        long count = storage.slotCount();

        for (long slot = 0; slot < count; slot++) {
            Player player = storage.read(slot);

            if (player != null) {
                action.accept(player);
            }
        }
    }
    private void requirePlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Не указан участник");
        }
    }

    @Override
    public void close() throws IOException {
        try {
            storage.close();
        } finally {
            index.close();
        }
    }
}