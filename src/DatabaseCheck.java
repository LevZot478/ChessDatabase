import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class DatabaseCheck {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(
                Paths.get("."), "database-check-"
        );

        System.out.println("База: " + directory.toAbsolutePath().normalize());

        try (PlayerDatabase database = new PlayerDatabase(directory)) {
            database.add(player(1, "Лев", 1700));
            database.add(player(4100, "Анна", 1500));

            check(database.findById(1).getName().equals("Лев"),
                    "Поиск по ID");

            boolean duplicateRejected = false;

            try {
                database.add(player(1, "Иван", 1400));
            } catch (IllegalArgumentException exception) {
                duplicateRejected = true;
                System.out.println(exception.getMessage());
            }

            check(duplicateRejected, "Повторный ID отклонён");

            database.update(4100, player(4100, "Анна", 1600));

            check(database.findById(4100).getRating() == 1600,
                    "Рейтинг изменён");

            boolean occupiedIdRejected = false;

            try {
                database.update(4100, player(1, "Анна", 1600));
            } catch (IllegalArgumentException exception) {
                occupiedIdRejected = true;
            }

            check(occupiedIdRejected, "Нельзя изменить ID на занятый");

            database.update(4100, player(5, "Анна", 1600));

            check(database.findById(4100) == null
                            && database.findById(5) != null,
                    "Изменение ID обновляет индекс");

            check(database.deleteById(1), "Удаление по ID");
            check(!database.deleteById(1), "Повторное удаление");

            database.add(player(3, "Иван", 1400));

            check(database.findById(1) == null
                            && database.findById(3).getName().equals("Иван"),
                    "Повторное использование места не путает ID");

            database.save();
        }

        try (PlayerDatabase database = new PlayerDatabase(directory)) {
            check(database.findById(1) == null, "Удаление сохранилось");
            check(database.findById(3) != null, "Добавление сохранилось");
            check(database.findById(5).getRating() == 1600,
                    "Изменение сохранилось");
        }

        System.out.println("Все проверки базы пройдены");
    }

    private static Player player(int id, String name, int rating) {
        return new Player(
                id, name, rating, 0.0, LocalDate.now(), true
        );
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new IllegalStateException(description);
        }

        System.out.println("ОК: " + description);
    }
}