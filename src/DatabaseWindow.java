import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

public final class DatabaseWindow extends JFrame {
    private final PlayerDatabase database;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Имя", "Рейтинг", "Взнос", "Дата", "Активен"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");

    public DatabaseWindow(Path directory) throws IOException {
        super("Шахматный клуб — " + directory.toAbsolutePath());

        database = new PlayerDatabase(directory);

        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);

        JPanel buttons = new JPanel(new GridLayout(0, 4, 6, 6));

        addButton(buttons, "Добавить", this::addPlayer);
        addButton(buttons, "Найти по ID", this::findPlayer);
        addButton(buttons, "Показать всех", this::refresh);
        addButton(buttons, "Поиск по полю", () -> fieldOperation(false));
        addButton(buttons, "Удалить по полю", () -> fieldOperation(true));
        addButton(buttons, "Изменить", this::editPlayer);
        addButton(buttons, "Удалить", this::deletePlayer);
        addButton(buttons, "Очистить БД", this::clearDatabase);
        addButton(buttons, "Сохранить", () -> {
            database.save();
            status.setText("Изменения сохранены");
        });

        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                runAction(() -> {
                    database.save();
                    database.close();
                    dispose();
                });
            }
        });

        try {
            refresh();
        } catch (IOException | RuntimeException exception) {
            try {
                database.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }

            dispose();
            throw exception;
        }
    }

    private void addButton(JPanel panel, String text, UiAction action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> runAction(action));
        panel.add(button);
    }

    private void runAction(UiAction action) {
        try {
            action.run();
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void addRow(Player player) {
        model.addRow(new Object[]{
                player.getId(),
                player.getName(),
                player.getRating(),
                player.getFee(),
                player.getRegisteredOn(),
                player.isActive()
        });
    }

    private void refresh() throws IOException {
        model.setRowCount(0);
        database.forEachPlayer(this::addRow);
        status.setText("Записей показано: " + model.getRowCount());
    }
    private void fieldOperation(boolean delete) throws IOException {
        JComboBox<PlayerField> fieldBox =
                new JComboBox<>(PlayerField.values());

        JTextField valueInput = new JTextField();

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("Поле:"));
        form.add(fieldBox);
        form.add(new JLabel("Точное значение:"));
        form.add(valueInput);

        int answer = JOptionPane.showConfirmDialog(
                this,
                form,
                delete ? "Удаление по полю" : "Поиск по полю",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        PlayerField field = (PlayerField) fieldBox.getSelectedItem();
        String value = valueInput.getText();

        // Проверяем формат значения до изменения таблицы или файла.
        field.condition(value);

        if (delete) {
            int confirmation = JOptionPane.showConfirmDialog(
                    this,
                    "Удалить ВСЕ записи, где "
                            + field + " = " + value.trim() + "?",
                    "Подтверждение",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirmation != JOptionPane.YES_OPTION) {
                return;
            }

            long deleted = database.deleteByField(field, value);
            database.save();
            refresh();

            status.setText("Удалено записей: " + deleted);
        } else {
            model.setRowCount(0);
            database.search(field, value, this::addRow);

            status.setText("Найдено записей: " + model.getRowCount());
        }
    }
    private void clearDatabase() throws IOException {
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Удалить ВСЕ записи из текущей базы?\n"
                        + "Это действие нельзя отменить.",
                "Очистка базы",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        database.clear();
        refresh();
        status.setText("База очищена");
    }
    private void addPlayer() throws IOException {
        Player player = showPlayerForm(null);

        if (player == null) {
            return;
        }

        database.add(player);
        database.save();
        refresh();
    }

    private void findPlayer() throws IOException {
        String input = JOptionPane.showInputDialog(this, "ID участника:");

        if (input == null) {
            return;
        }

        Player player = database.findById(Integer.parseInt(input.trim()));

        if (player == null) {
            JOptionPane.showMessageDialog(this, "Участник не найден");
            return;
        }

        model.setRowCount(0);
        addRow(player);
        status.setText("Результат поиска по ID: " + player.getId());
    }

    private int selectedId() {
        int viewRow = table.getSelectedRow();

        if (viewRow == -1) {
            throw new IllegalArgumentException(
                    "Сначала выбери участника в таблице"
            );
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        return ((Number) model.getValueAt(modelRow, 0)).intValue();
    }

    private void editPlayer() throws IOException {
        int oldId = selectedId();

        // Актуальные данные читаем из файла.
        Player original = database.findById(oldId);

        if (original == null) {
            throw new IllegalArgumentException("Участник не найден");
        }

        Player replacement = showPlayerForm(original);

        if (replacement == null) {
            return;
        }

        database.update(oldId, replacement);
        database.save();
        refresh();
    }

    private void deletePlayer() throws IOException {
        int id = selectedId();

        int answer = JOptionPane.showConfirmDialog(
                this,
                "Удалить участника с ID " + id + "?",
                "Удаление",
                JOptionPane.YES_NO_OPTION
        );

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        database.deleteById(id);
        database.save();
        refresh();
    }

    private Player showPlayerForm(Player original) {
        JTextField id = new JTextField(
                original == null ? "" : String.valueOf(original.getId())
        );
        JTextField name = new JTextField(
                original == null ? "" : original.getName()
        );
        JTextField rating = new JTextField(
                original == null ? "0" : String.valueOf(original.getRating())
        );
        JTextField fee = new JTextField(
                original == null ? "0.0" : String.valueOf(original.getFee())
        );
        JTextField date = new JTextField(
                original == null
                        ? LocalDate.now().toString()
                        : original.getRegisteredOn().toString()
        );

        JCheckBox active = new JCheckBox();
        active.setSelected(original == null || original.isActive());

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));

        form.add(new JLabel("ID:"));
        form.add(id);
        form.add(new JLabel("Имя:"));
        form.add(name);
        form.add(new JLabel("Рейтинг:"));
        form.add(rating);
        form.add(new JLabel("Взнос:"));
        form.add(fee);
        form.add(new JLabel("Дата (ГГГГ-ММ-ДД):"));
        form.add(date);
        form.add(new JLabel("Активен:"));
        form.add(active);

        while (true) {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    form,
                    original == null ? "Новый участник" : "Изменение участника",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }

            try {
                return new Player(
                        Integer.parseInt(id.getText().trim()),
                        name.getText(),
                        Integer.parseInt(rating.getText().trim()),
                        Double.parseDouble(
                                fee.getText().trim().replace(',', '.')
                        ),
                        LocalDate.parse(date.getText().trim()),
                        active.isSelected()
                );
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        "Проверь поля:\n" + exception.getMessage(),
                        "Некорректные данные",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    @FunctionalInterface
    private interface UiAction {
        void run() throws Exception;
    }
}