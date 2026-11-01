import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Управляет файлом участников и индексом по ID.
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
                && (!Files.exists(indexPath)
                || Files.size(indexPath) == 0)) {

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

    /**
     * Находит участника через индекс.
     * Возвращает null, если ID отсутствует.
     */
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

    /**
     * Добавляет участника с проверкой уникальности ID.
     */
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
     * Изменяет запись. Новый ID должен быть свободен
     * либо совпадать с прежним ID участника.
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

    /**
     * Удаляет участника по ID.
     * Возвращает false, если участник отсутствует.
     */
    public boolean deleteById(int id) throws IOException {
        if (findById(id) == null) {
            return false;
        }

        long slot = index.find(id);

        storage.delete(slot);
        index.delete(id);

        return true;
    }

    /**
     * Читает существующие записи по одной для отображения.
     */
    public void forEachPlayer(Consumer<Player> action) throws IOException {
        long count = storage.slotCount();

        for (long slot = 0; slot < count; slot++) {
            Player player = storage.read(slot);

            if (player != null) {
                action.accept(player);
            }
        }
    }

    /**
     * Ищет все записи с точным совпадением значения поля.
     * Поиск по ID использует индекс.
     */
    public void search(
            PlayerField field,
            String value,
            Consumer<Player> action
    ) throws IOException {

        if (field == PlayerField.ID) {
            Player player = findById(Integer.parseInt(value.trim()));

            if (player != null) {
                action.accept(player);
            }

            return;
        }

        Predicate<Player> condition = field.condition(value);
        long count = storage.slotCount();

        for (long slot = 0; slot < count; slot++) {
            Player player = storage.read(slot);

            if (player != null && condition.test(player)) {
                action.accept(player);
            }
        }
    }

    /**
     * Удаляет все записи с точным совпадением значения поля.
     * Возвращает количество удалённых записей.
     */
    public long deleteByField(PlayerField field, String value)
            throws IOException {

        if (field == PlayerField.ID) {
            int id = Integer.parseInt(value.trim());
            return deleteById(id) ? 1 : 0;
        }

        // Проверяем формат значения до начала удаления.
        Predicate<Player> condition = field.condition(value);

        long deleted = 0;
        long count = storage.slotCount();

        for (long slot = 0; slot < count; slot++) {
            Player player = storage.read(slot);

            if (player != null && condition.test(player)) {
                if (deleteById(player.getId())) {
                    deleted++;
                }
            }
        }

        return deleted;
    }
    /**
     * Очищает данные и индекс.
     */
    public void clear() throws IOException {
        storage.clear();
        index.clear();
        save();
    }
    /**
     * Проверяет соответствие записей и индекса.
     */
    public long validate() throws IOException {
        long players = 0;
        long slots = storage.slotCount();

        for (long slot = 0; slot < slots; slot++) {
            Player player = storage.read(slot);

            if (player == null) {
                continue;
            }

            long indexedSlot = index.find(player.getId());

            if (indexedSlot != slot) {
                throw new IOException(
                        "Нарушен индекс для ID " + player.getId()
                                + ": запись находится в слоте " + slot
                                + ", индекс содержит " + indexedSlot
                );
            }

            players++;
        }

        long indexEntries = index.validateEntries(storage);

        if (indexEntries != players) {
            throw new IOException(
                    "Количество записей и элементов индекса различается: "
                            + players + " и " + indexEntries
            );
        }

        return players;
    }
    /**
     * Сбрасывает изменения обоих файлов на устройство хранения.
     */

    public void save() throws IOException {
        storage.save();
        index.save();
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