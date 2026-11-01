import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class ConsistencyCheck {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(
                Paths.get("."), "database-check-consistency-"
        );

        try (PlayerDatabase database = new PlayerDatabase(directory)) {
            database.add(new Player(
                    1, "Лев", 1700, 0.0, LocalDate.now(), true
            ));

            database.add(new Player(
                    4100, "Анна", 1500, 0.0, LocalDate.now(), true
            ));

            if (database.validate() != 2) {
                throw new IllegalStateException(
                        "Неправильное количество участников"
                );
            }

            database.save();
            System.out.println("ОК: исправная база прошла проверку");
        }

        // Специально удаляем указатель из индекса.
        // Сама запись участника остаётся в файле данных.
        try (IdIndex index = new IdIndex(
                directory.resolve("players-id.idx"))) {
            index.delete(1);
            index.save();
        }

        boolean detected = false;

        try (PlayerDatabase database = new PlayerDatabase(directory)) {
            try {
                database.validate();
            } catch (IOException exception) {
                detected = true;
                System.out.println(
                        "ОК: неисправность обнаружена:\n"
                                + exception.getMessage()
                );
            }
        }

        if (!detected) {
            throw new IllegalStateException(
                    "Проверка не обнаружила потерянный указатель"
            );
        }

        System.out.println("Все проверки согласованности пройдены");
    }
}