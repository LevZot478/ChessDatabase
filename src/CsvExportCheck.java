import java.nio.file.*;
import java.time.LocalDate;
import java.util.Comparator;

public final class CsvExportCheck {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("csv-check-");
        try {
            try (PlayerDatabase db = new PlayerDatabase(root.resolve("db"))) {
                Path empty = root.resolve("empty.csv");
                require(DatabaseCsvExport.write(db, empty) == 0, "Пустая база");
                require(Files.readString(empty).equals("\uFEFFID;Имя;Рейтинг;Взнос;Дата;Активен\r\n"), "Заголовок/BOM");
                db.add(new Player(1, "Лев; \"тест\"\nстрока", 2100, 500.5, LocalDate.of(2026, 11, 3), true));
                db.add(new Player(2, "Удалённый", 0, 0, LocalDate.of(2026, 11, 3), false));
                db.add(new Player(3, "=1+1", 1000, 0, LocalDate.of(2026, 11, 3), false));
                db.deleteById(2);
                Path csv = root.resolve("players.csv");
                require(DatabaseCsvExport.write(db, csv) == 2, "Количество живых записей");
                String expected = "\uFEFFID;Имя;Рейтинг;Взнос;Дата;Активен\r\n"
                        + "1;\"Лев; \"\"тест\"\"\nстрока\";2100;500,5;2026-11-03;true\r\n"
                        + "3;\"'=1+1\";1000;0,0;2026-11-03;false\r\n";
                require(Files.readString(csv).equals(expected), "Экранирование/кириллица/числа");
                boolean rejected = false;
                try { DatabaseCsvExport.write(db, csv); }
                catch (FileAlreadyExistsException ex) { rejected = true; }
                require(rejected && Files.readString(csv).equals(expected), "Защита от перезаписи");
                byte[] dataBefore = Files.readAllBytes(root.resolve("db/players.dat"));
                try { DatabaseCsvExport.write(db, root.resolve("db/players.dat")); }
                catch (FileAlreadyExistsException ex) { }
                require(java.util.Arrays.equals(dataBefore, Files.readAllBytes(root.resolve("db/players.dat"))), "Файл БД сохранён");
                require(db.validate() == 2 && db.findById(1) != null, "База не изменилась");
                boolean missing = false;
                try { DatabaseCsvExport.write(db, root.resolve("missing/file.csv")); }
                catch (java.io.IOException ex) { missing = true; }
                require(missing, "Ошибка пути");
            }
            System.out.println("OK: CSV — пустая база, кириллица, спецсимволы, удаление, защита файлов, ошибка пути");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
