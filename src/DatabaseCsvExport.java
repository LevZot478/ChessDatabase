import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Потоковая выгрузка всех живых записей, независимо от фильтра в GUI. */
public final class DatabaseCsvExport {
    private DatabaseCsvExport() { }

    public static long write(PlayerDatabase database, Path target) throws IOException {
        // CREATE_NEW защищает существующие файлы, включая файлы самой БД.
        BufferedWriter opened = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        long[] count = {0};
        try (BufferedWriter writer = opened) {
            writer.write('\uFEFF'); // BOM помогает Excel распознать кириллицу.
            writer.write("ID;Имя;Рейтинг;Взнос;Дата;Активен\r\n");
            database.forEachPlayer(player -> {
                try {
                    writer.write(player.getId() + ";" + quote(safeName(player.getName()))
                            + ";" + player.getRating() + ";"
                            + Double.toString(player.getFee()).replace('.', ',') + ";"
                            + player.getRegisteredOn() + ";" + player.isActive() + "\r\n");
                    count[0]++;
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException | RuntimeException exception) {
            IOException failure = exception instanceof UncheckedIOException
                    ? ((UncheckedIOException) exception).getCause()
                    : exception instanceof IOException ? (IOException) exception
                    : new IOException("Не удалось выгрузить БД", exception);
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        return count[0];
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String safeName(String value) {
        // Текст, похожий на формулу, остаётся текстом в табличном редакторе.
        return "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
