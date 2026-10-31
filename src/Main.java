import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                DatabaseWindow window = new DatabaseWindow(
                        Paths.get("chess-club-db")
                );

                window.setVisible(true);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                        null,
                        "Не удалось открыть базу:\n" + exception.getMessage(),
                        "Ошибка запуска",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}