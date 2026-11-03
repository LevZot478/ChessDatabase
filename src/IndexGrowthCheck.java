import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/** Самостоятельная проверка: создаёт только собственную временную базу. */
public class IndexGrowthCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("database-check-growth-");
        try {
            Path legacy = directory.resolve("legacy.idx");
            // Файл прежнего формата: одна запись ID=7 -> слот 42.
            try (RandomAccessFile file = new RandomAccessFile(legacy.toFile(), "rw")) {
                file.writeInt(0x49444958);
                file.writeInt(1);
                file.writeInt(4099);
                for (int i = 0; i < 4099; i++) file.writeLong(-1);
                long node = file.length();
                file.writeInt(7);
                file.writeLong(42);
                file.writeLong(-1);
                file.seek(12 + 7 * 8L);
                file.writeLong(node);
            }
            try (IdIndex index = new IdIndex(legacy)) {
                require(index.find(7) == 42, "Старый индекс не прочитан");
                require(index.entryCount() == 1, "Неверный счётчик");
            }
            System.out.println("OK: прежний формат читается");

            Path databasePath = directory.resolve("db");
            try (PlayerDatabase db = new PlayerDatabase(databasePath)) {
                for (int id = 1; id <= 7000; id++) {
                    db.add(new Player(id, "Игрок " + id, 1500, 500,
                            LocalDate.of(2026, 11, 1), true));
                }
                require(db.validate() == 7000, "Ошибка после расширения");
                for (int id = 1; id <= 7000; id++) {
                    require(db.findById(id) != null, "Потерян ID " + id);
                }
                for (int id = 1; id <= 7000; id += 3) db.deleteById(id);
                db.save();
                require(db.validate() == 4666, "Ошибка удаления");
            }
            try (IdIndex index = new IdIndex(databasePath.resolve("players-id.idx"))) {
                require(index.bucketCount() > 4099, "Индекс не расширился");
                require(index.entryCount() == 4666, "Неверное число узлов");
                System.out.println("OK: число корзин выросло до " + index.bucketCount());
                try {
                    index.insert(2, 1);
                    throw new IllegalStateException("Принят повторный ID");
                } catch (IllegalArgumentException expected) {
                    System.out.println("OK: повторный ID отклонён");
                }
            }
            try (PlayerDatabase db = new PlayerDatabase(databasePath)) {
                require(db.validate() == 4666, "Ошибка повторного открытия");
                require(db.findById(1) == null, "Удалённый ID найден");
                require(db.findById(2) != null, "Существующий ID потерян");
                db.clear();
                require(db.validate() == 0, "Ошибка очистки");
                db.add(new Player(1, "После очистки", 1500, 0,
                        LocalDate.of(2026, 11, 1), true));
                require(db.validate() == 1, "Ошибка после очистки");
            }
            System.out.println("Все проверки расширения индекса пройдены");
        } finally {
            // Удаляем только файлы созданного этим тестом каталога.
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
