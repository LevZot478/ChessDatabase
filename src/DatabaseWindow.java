import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.nio.file.Files;

public final class DatabaseWindow extends JFrame {
    private PlayerDatabase database;
    private Path currentDirectory;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Имя", "Рейтинг", "Взнос", "Дата", "Активен"}, 0
    ) {
        @Override
        public Class<?> getColumnClass(int column) {
            return new Class<?>[]{Integer.class, String.class, Integer.class,
                    Double.class, LocalDate.class, Boolean.class}[column];
        }
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
        currentDirectory = directory.toRealPath();

        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 600));
        setLayout(new BorderLayout(16, 16));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        getContentPane().setBackground(new Color(245, 247, 250));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(36);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(230, 234, 240));
        table.setSelectionBackground(new Color(218, 232, 252));
        table.setSelectionForeground(new Color(24, 44, 70));
        table.setIntercellSpacing(new Dimension(12, 1));
        status.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        table.setFillsViewportHeight(true);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Шахматный клуб");
        heading.setFont(new Font("SansSerif", Font.BOLD, 26));
        buttons.add(heading);
        buttons.add(Box.createVerticalStrut(12));
        JPanel files = buttonGroup(buttons, "База и резервные копии");
        JPanel players = buttonGroup(buttons, "Участники");
        JPanel maintenance = buttonGroup(buttons, "Обслуживание");
        addButton(files, "Создать БД", this::createDatabase);
        addButton(files, "Открыть БД", this::openDatabase);
        addButton(files, "Удалить БД", this::deleteDatabase);
        addButton(files, "Создать копию", this::createBackup);
        addButton(files, "Восстановить копию", this::restoreBackup);
        addButton(files, "Выгрузить CSV", this::exportCsv);

        addButton(players, "Добавить", this::addPlayer);
        addButton(players, "Найти по ID", this::findPlayer);
        addButton(players, "Показать всех", this::refresh);
        addButton(players, "Поиск по полю", () -> fieldOperation(false));
        addButton(players, "Удалить по полю", () -> fieldOperation(true));
        addButton(players, "Изменить", this::editPlayer);
        addButton(players, "Удалить", this::deletePlayer);
        addButton(maintenance, "Очистить БД", this::clearDatabase);
        addButton(maintenance, "Сохранить", () -> {
            database.save();
            status.setText("Изменения сохранены");
        });
        addButton(maintenance, "Проверить БД", () -> {
            long count = database.validate();

            status.setText("Проверка пройдена. Участников: " + count);

            JOptionPane.showMessageDialog(
                    this,
                    "Данные и индекс согласованы.\nУчастников: " + count
            );
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

    private static JPanel buttonGroup(JPanel parent, String title) {
        JPanel group = new JPanel(new GridLayout(0, 4, 8, 8));
        group.setOpaque(false);
        group.setBorder(BorderFactory.createTitledBorder(title));
        parent.add(group);
        return group;
    }

    private static int integerValue(String text, String label, int min, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + ": введи целое число от " + min + " до " + max);
        }
    }

    private static double feeValue(String text) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Взнос: введи неотрицательное число, например 500 или 500,50");
        }
    }

    private static void selectOnFocus(JTextField field) {
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                SwingUtilities.invokeLater(field::selectAll);
            }
        });
    }

    private void addButton(JPanel panel, String text, UiAction action) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.PLAIN, 13));
        button.setFocusPainted(false);
        button.setMargin(new Insets(9, 12, 9, 12));
        button.setBackground(Color.WHITE);
        if (text.startsWith("Удалить") || text.startsWith("Очистить")) {
            button.setForeground(new Color(165, 45, 45));
        }
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
    /**
     * Предлагает выбрать папку.
     * Возвращает null при отмене.
     */
    private Path chooseDirectory(String title) {
        Path parent = currentDirectory.getParent();

        JFileChooser chooser = new JFileChooser(
                (parent == null ? currentDirectory : parent).toFile()
        );

        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        return chooser.getSelectedFile().toPath();
    }

    private void createDatabase() throws IOException {
        Path parent = chooseDirectory(
                "Выбери папку, внутри которой создать новую базу"
        );

        if (parent == null) {
            return;
        }

        String name = JOptionPane.showInputDialog(
                this,
                "Название новой базы: латинские буквы, цифры, _ или -",
                "club-2"
        );

        if (name == null) {
            return;
        }

        name = name.trim();

        if (!name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Используй латинские буквы, цифры, _ или -"
            );
        }

        Path target = parent.resolve(name);

        // Создаём именно новую папку.
        // Если такая уже есть — получим ошибку без перезаписи данных.
        Files.createDirectory(target);

        switchDatabase(target);

        status.setText("Создана база: " + currentDirectory);
    }

    private void openDatabase() throws IOException {
        Path target = chooseDirectory(
                "Выбери папку существующей базы"
        );

        if (target == null) {
            return;
        }

        // Открытие не должно создавать файлы в произвольной папке.
        if (!Files.isRegularFile(target.resolve("players.dat"))
                || !Files.isRegularFile(target.resolve("players-id.idx"))) {
            throw new IllegalArgumentException(
                    "В папке должны быть players.dat и players-id.idx"
            );
        }

        switchDatabase(target);
    }

    private void switchDatabase(Path target) throws IOException {
        Path realTarget = target.toRealPath();

        if (realTarget.equals(currentDirectory)) {
            status.setText("Эта база уже открыта");
            return;
        }

        // Сохраняем текущую базу перед переключением.
        database.save();

        // Сначала проверяем, что новую базу удаётся открыть.
        PlayerDatabase next = new PlayerDatabase(realTarget);

        try {
            database.close();
        } catch (IOException exception) {
            try {
                next.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }

        database = next;
        currentDirectory = realTarget;

        setTitle("Шахматный клуб — " + currentDirectory);
        refresh();

        status.setText("Открыта база: " + currentDirectory);
    }
    private void exportCsv() throws IOException {
        JFileChooser chooser = new JFileChooser(currentDirectory.getParent() == null
                ? currentDirectory.toFile() : currentDirectory.getParent().toFile());
        chooser.setDialogTitle("Выгрузить всех участников в новый CSV-файл");
        chooser.setSelectedFile(new java.io.File("players.csv"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Таблица CSV (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            target = target.resolveSibling(target.getFileName() + ".csv");
        }
        if (Files.exists(target)) {
            throw new IOException("Файл уже существует. Выбери новое имя для выгрузки.");
        }
        long count = DatabaseCsvExport.write(database, target);
        status.setText("В CSV выгружено записей: " + count);
        JOptionPane.showMessageDialog(this, "Выгружены все участники: " + count
                + "\n" + target.toAbsolutePath()
                + "\nФормат: UTF-8, разделитель — точка с запятой.");
    }

    private void createBackup() throws IOException {
        Path parent = chooseDirectory(
                "Выбери папку для резервной копии"
        );

        if (parent == null) {
            return;
        }

        // Перед копированием сохраняем оба файла.
        database.save();

        Path archive = DatabaseBackup.create(currentDirectory, parent);

        status.setText("Backup создан: " + archive);

        JOptionPane.showMessageDialog(
                this,
                "Резервная копия создана:\n" + archive
        );
    }

    private void restoreBackup() throws IOException {
        JFileChooser chooser = new JFileChooser(
                currentDirectory.getParent().toFile()
        );

        chooser.setDialogTitle("Выбери ZIP-файл резервной копии");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(
                new javax.swing.filechooser.FileNameExtensionFilter(
                        "Резервная копия ZIP", "zip"
                )
        );

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path archive = chooser.getSelectedFile().toPath();

        Path parent = chooseDirectory(
                "Куда восстановить базу? Будет создана новая подпапка"
        );

        if (parent == null) {
            return;
        }

        Path restored = DatabaseBackup.restore(archive, parent);

        switchDatabase(restored);

        JOptionPane.showMessageDialog(
                this,
                "База восстановлена и открыта:\n" + restored
        );
    }
    private void deleteDatabase() throws IOException {
        Path selected = chooseDirectory(
                "Выбери папку базы, которую нужно удалить"
        );

        if (selected == null) {
            return;
        }

        Path target = selected.toRealPath();

        if (target.equals(currentDirectory)) {
            throw new IllegalArgumentException(
                    "Эта база сейчас открыта.\n"
                            + "Сначала открой другую базу, затем удали эту."
            );
        }

        Path dataPath = target.resolve("players.dat");
        Path indexPath = target.resolve("players-id.idx");

        if (!Files.isRegularFile(dataPath)
                || !Files.isRegularFile(indexPath)) {
            throw new IllegalArgumentException(
                    "В выбранной папке нет файлов нашей базы"
            );
        }

        // Проверяем содержимое папки.
        // Чужие файлы и вложенные базы не удаляем.
        try (java.nio.file.DirectoryStream<Path> entries =
                     Files.newDirectoryStream(target)) {

            for (Path entry : entries) {
                String name = entry.getFileName().toString();

                if (!name.equals("players.dat")
                        && !name.equals("players-id.idx")) {
                    throw new IllegalArgumentException(
                            "В папке есть посторонние файлы или подпапки.\n"
                                    + "Перемести их перед удалением базы."
                    );
                }
            }
        }

        // Проверяем формат и доступность файлов.
        try (PlayerDatabase checked = new PlayerDatabase(target)) {
            checked.forEachPlayer(player -> {
                // Читаем записи для проверки формата.
            });
            checked.save();
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "Удалить базу?\n" + target
                        + "\n\nПеред удалением будет создан ZIP-backup"
                        + " рядом с её папкой.",
                "Удаление базы данных",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        Path backup = DatabaseBackup.create(target, target.getParent());

        try {
            Files.delete(dataPath);
            Files.delete(indexPath);
            Files.delete(target);
        } catch (IOException exception) {
            throw new IOException(
                    "Не удалось полностью удалить базу.\n"
                            + "Резервная копия сохранена:\n" + backup,
                    exception
            );
        }

        status.setText("База удалена. Backup: " + backup);

        JOptionPane.showMessageDialog(
                this,
                "База удалена:\n" + target
                        + "\n\nРезервная копия:\n" + backup
        );
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

        Player player = database.findById(integerValue(input, "ID", 1, Integer.MAX_VALUE));

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
                original == null ? "" : String.valueOf(original.getRating())
        );
        JTextField fee = new JTextField(
                original == null ? "" : String.valueOf(original.getFee())
        );
        JTextField date = new JTextField(
                original == null
                        ? LocalDate.now().toString()
                        : original.getRegisteredOn().toString()
        );

        for (JTextField field : new JTextField[]{id, rating, fee, date}) {
            selectOnFocus(field);
            field.setColumns(20);
        }
        rating.setToolTipText("Целое число от 0 до 4000");
        fee.setToolTipText("Например: 500 или 500,50");
        JCheckBox active = new JCheckBox("Участник активен");
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
                        integerValue(id.getText(), "ID", 1, Integer.MAX_VALUE),
                        name.getText(),
                        integerValue(rating.getText(), "Рейтинг", 0, 4000),
                        feeValue(fee.getText()),
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
