import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class StorageCheck {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile(
                Paths.get("."), "storage-check-", ".dat"
        );

        System.out.println("Файл: " + path.toAbsolutePath().normalize());

        try (PlayerStorage storage = new PlayerStorage(path)) {
            long firstSlot = storage.insert(new Player(
                    1, "Лев", 1700, 500.0, LocalDate.now(), true
            ));

            long secondSlot = storage.insert(new Player(
                    2, "Анна", 1500, 300.0, LocalDate.now(), true
            ));

            System.out.println("Слот Льва: " + firstSlot);
            System.out.println("Слот Анны: " + secondSlot);

            storage.update(secondSlot, new Player(
                    2, "Анна", 1600, 300.0, LocalDate.now(), true
            ));

            System.out.println(
                    "Рейтинг Анны после изменения: "
                            + storage.read(secondSlot).getRating()
            );

            storage.delete(firstSlot);
            System.out.println("Удалённая запись: " + storage.read(firstSlot));

            long reusedSlot = storage.insert(new Player(
                    3, "Иван", 1400, 0.0, LocalDate.now(), false
            ));

            System.out.println("Слот Ивана: " + reusedSlot);
            System.out.println("Всего слотов: " + storage.slotCount());

            if (reusedSlot != firstSlot || storage.slotCount() != 2) {
                throw new IllegalStateException("Место не использовано повторно");
            }

            storage.save();
        }

        try (PlayerStorage storage = new PlayerStorage(path)) {
            System.out.println("После повторного открытия:");
            System.out.println(storage.read(0));
            System.out.println(storage.read(1));

            if (storage.read(0).getId() != 3
                    || storage.read(1).getRating() != 1600) {
                throw new IllegalStateException("Данные не сохранились");
            }
        }

        System.out.println("Все проверки пройдены");
    }
}