import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Создание резервной копии и восстановление в новую папку.
 */
public final class DatabaseBackup {
    private static final String DATA = "players.dat";
    private static final String INDEX = "players-id.idx";

    private DatabaseBackup() {
    }

    public static Path create(Path databaseDirectory, Path backupParent)
            throws IOException {

        Path archive = Files.createTempFile(
                backupParent, "chess-backup-", ".zip"
        );

        try {
            try (ZipOutputStream output = new ZipOutputStream(
                    Files.newOutputStream(archive))) {

                writeEntry(output, databaseDirectory.resolve(DATA), DATA);
                writeEntry(output, databaseDirectory.resolve(INDEX), INDEX);
            }

            return archive;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(archive);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    private static void writeEntry(
            ZipOutputStream output, Path source, String name
    ) throws IOException {

        output.putNextEntry(new ZipEntry(name));
        Files.copy(source, output);
        output.closeEntry();
    }

    public static Path restore(Path archive, Path destinationParent)
            throws IOException {

        // Принимаем архив только с двумя ожидаемыми файлами.
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry data = zip.getEntry(DATA);
            ZipEntry index = zip.getEntry(INDEX);

            if (zip.size() != 2
                    || data == null || data.isDirectory()
                    || index == null || index.isDirectory()) {
                throw new IOException(
                        "В архиве должны быть только "
                                + DATA + " и " + INDEX
                );
            }

            Path target = Files.createTempDirectory(
                    destinationParent, "restored-db-"
            );

            try {
                extract(zip, data, target.resolve(DATA));
                extract(zip, index, target.resolve(INDEX));

                // Проверяем заголовки и читаем записи восстановленной базы.
                try (PlayerDatabase database = new PlayerDatabase(target)) {
                    database.validate();
                }

                return target;
            } catch (IOException | RuntimeException exception) {
                try {
                    Files.deleteIfExists(target.resolve(DATA));
                    Files.deleteIfExists(target.resolve(INDEX));
                    Files.deleteIfExists(target);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }

                throw exception;
            }
        }
    }

    private static void extract(
            ZipFile zip, ZipEntry entry, Path destination
    ) throws IOException {

        try (InputStream input = zip.getInputStream(entry)) {
            Files.copy(input, destination);
        }
    }
}