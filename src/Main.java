import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.put("Button.gradient", java.util.Arrays.asList(
                        0.0f, 0.0f, java.awt.Color.WHITE, java.awt.Color.WHITE, java.awt.Color.WHITE));
                javax.swing.UIManager.put("Panel.background", new java.awt.Color(245, 247, 250));
                javax.swing.UIManager.put("OptionPane.background", new java.awt.Color(245, 247, 250));
                javax.swing.UIManager.put("TextField.font", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14));
                javax.swing.UIManager.put("Label.font", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14));
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