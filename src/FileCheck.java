import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class FileCheck {
    public static void main(String[] args) {
        try {
            // Каждый запуск создаёт отдельный проверочный файл.
            Path path = Files.createTempFile(
                    Paths.get("."),
                    "players-check-",
                    ".dat"
            );

            System.out.println("Файл: " + path.toAbsolutePath().normalize());

            // Открываем файл для чтения и записи.
            try (RandomAccessFile file =
                         new RandomAccessFile(path.toFile(), "rw")) {

                Player first = new Player(
                        1, "Лев", 1700, 500.0,
                        LocalDate.of(2026, 10, 31), true
                );

                Player second = new Player(
                        2, "Анна", 1500, 300.0,
                        LocalDate.of(2026, 10, 30), false
                );

                PlayerCodec.write(file, first);
                PlayerCodec.write(file, second);

                System.out.println(
                        "Размер одной записи: " + PlayerCodec.RECORD_SIZE
                );
                System.out.println("Размер файла: " + file.length());

                if (file.length() != 2L * PlayerCodec.RECORD_SIZE) {
                    throw new IOException("Неожиданный размер файла");
                }
            }

            System.out.println("Файл закрыт. Открываем заново.");

            // Повторно открываем тот же файл только для чтения.
            try (RandomAccessFile file =
                         new RandomAccessFile(path.toFile(), "r")) {

                // Сразу переходим ко второй записи.
                file.seek(PlayerCodec.RECORD_SIZE);
                Player second = PlayerCodec.read(file);
                System.out.println("Вторая запись: " + second);

                // Возвращаемся к первой записи.
                file.seek(0);
                Player first = PlayerCodec.read(file);
                System.out.println("Первая запись: " + first);
            }

        } catch (IOException exception) {
            System.out.println("Ошибка работы с файлом:");
            exception.printStackTrace();
        }
    }
}