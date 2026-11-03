import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;

public class PerformanceCheck {
    private static final int OPERATIONS = 20;

    public static void main(String[] args) throws Exception {
        int[] sizes = {100, 1000, 5000};

        System.out.println("Среднее время одной операции, мкс");
        System.out.println(
                "Записей | Добавление | Поиск: индекс | Поиск: перебор | Удаление"
        );

        for (int size : sizes) {
            measure(size);
        }

        System.out.println("Замеры завершены");
    }

    private static void measure(int size) throws Exception {
        Path directory = Files.createTempDirectory(
                Paths.get("."), "database-check-performance-"
        );

        // Подготовка исходных данных не входит в замеры.
        try (PlayerDatabase database = new PlayerDatabase(directory)) {
            for (int id = 1; id <= size; id++) {
                database.add(player(id));
            }

            database.save();
        }

        double indexedSearch;
        double addition;
        double deletion;

        try (PlayerDatabase database = new PlayerDatabase(directory)) {
            // Несколько предварительных вызовов перед измерением.
            for (int i = 0; i < 10; i++) {
                database.findById(size);
            }

            long started = System.nanoTime();

            for (int i = 0; i < OPERATIONS; i++) {
                int id = queryId(i, size);
                Player found = database.findById(id);

                if (found == null || found.getId() != id) {
                    throw new IllegalStateException("Ошибка поиска по индексу");
                }
            }

            indexedSearch = averageMicros(started);

            // Объекты создаём до измерения вставки.
            Player[] additions = new Player[OPERATIONS];

            for (int i = 0; i < OPERATIONS; i++) {
                additions[i] = player(size + i + 1);
            }

            started = System.nanoTime();

            for (Player player : additions) {
                database.add(player);
            }

            addition = averageMicros(started);

            started = System.nanoTime();

            for (Player player : additions) {
                if (!database.deleteById(player.getId())) {
                    throw new IllegalStateException("Ошибка удаления");
                }
            }

            deletion = averageMicros(started);

            database.save();

            if (database.validate() != size) {
                throw new IllegalStateException(
                        "После замеров изменилось число участников"
                );
            }
        }

        double linearSearch;

        try (PlayerStorage storage = new PlayerStorage(
                directory.resolve("players.dat"))) {

            for (int i = 0; i < 3; i++) {
                findLinear(storage, size);
            }

            long started = System.nanoTime();

            for (int i = 0; i < OPERATIONS; i++) {
                int id = queryId(i, size);
                Player found = findLinear(storage, id);

                if (found == null || found.getId() != id) {
                    throw new IllegalStateException("Ошибка перебора");
                }
            }

            linearSearch = averageMicros(started);
        }

        System.out.printf(
                Locale.US,
                "%7d | %10.2f | %13.2f | %14.2f | %8.2f%n",
                size, addition, indexedSearch, linearSearch, deletion
        );
    }

    private static Player findLinear(PlayerStorage storage, int id)
            throws Exception {

        long count = storage.slotCount();

        for (long slot = 0; slot < count; slot++) {
            Player player = storage.read(slot);

            if (player != null && player.getId() == id) {
                return player;
            }
        }

        return null;
    }

    private static int queryId(int operation, int size) {
        // Одинаковые запросы для поиска с индексом и перебором.
        return 1 + (int) ((long) operation * (size - 1)
                / (OPERATIONS - 1));
    }

    private static Player player(int id) {
        return new Player(
                id,
                "Участник " + id,
                1000 + id % 1500,
                500.0,
                LocalDate.of(2026, 11, 1),
                true
        );
    }

    private static double averageMicros(long started) {
        long elapsed = System.nanoTime() - started;

        return elapsed / 1000.0 / OPERATIONS;
    }
}