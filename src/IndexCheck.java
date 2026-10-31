import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IndexCheck {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile(
                Paths.get("."), "index-check-", ".idx"
        );

        System.out.println("Файл: " + path.toAbsolutePath().normalize());

        try (IdIndex index = new IdIndex(path)) {
            index.insert(1, 0);
            index.insert(4100, 7);
            index.insert(8199, 12);

            check(index.find(1) == 0, "Поиск ID 1");
            check(index.find(4100) == 7, "Поиск при коллизии");
            check(index.find(8199) == 12, "Конец цепочки");
            check(index.find(99) == -1, "Отсутствующий ID");

            boolean duplicateRejected = false;

            try {
                index.insert(1, 50);
            } catch (IllegalArgumentException exception) {
                duplicateRejected = true;
                System.out.println(exception.getMessage());
            }

            check(duplicateRejected, "Запрет повторного ID");

            check(index.delete(4100), "Удаление из середины цепочки");
            check(index.find(4100) == -1, "Удалённый ID отсутствует");
            check(index.find(8199) == 12, "Цепочка после удаления");

            check(index.delete(1), "Удаление начала цепочки");
            check(index.find(8199) == 12, "Оставшийся узел доступен");

            index.insert(4100, 20);
            index.save();
        }

        try (IdIndex index = new IdIndex(path)) {
            check(index.find(1) == -1, "Удаление сохранилось");
            check(index.find(4100) == 20, "Повторное добавление сохранилось");
            check(index.find(8199) == 12, "Данные после открытия");
        }

        System.out.println("Все проверки индекса пройдены");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new IllegalStateException(description);
        }

        System.out.println("ОК: " + description);
    }
}