package psr.sc;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Прайс-лист: стоимость работ и наборов запчастей. */
public class PriceList extends JPanel {

    private final JTable table = new JTable();
    private final JComboBox<String> categoryFilter = new JComboBox<>(new String[] {
        "Все", "Работы", "Наборы запчастей", "Диагностика", "Профилактика", "Ремонт"
    });

    public PriceList() {
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(categoryFilter);
        tools.add(Theme.on("Фильтр", Theme.BLUE, e -> load()));
        if (Session.edits(Roles.PRICE)) {
            tools.add(Theme.on("Добавить", Theme.YELLOW, e -> addItem()));
            tools.add(Theme.on("Изменить", Theme.BLUE, e -> editItem()));
            tools.add(Theme.on("Удалить", Theme.GRAY_PANEL, e -> deleteItem()));
        }
        tools.add(Theme.on("Импорт из файла", Theme.YELLOW, e -> importFromFile()));
        tools.add(Theme.on("Экспорт в файл", Theme.MINT_TOP, e -> exportToFile()));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> load()));

        add(tools, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        Theme.styleTable(table);
        load();
    }

    void load() {
        String cat = (String) categoryFilter.getSelectedItem();
        String where = "Все".equals(cat) ? "" : " WHERE category='" + Db.esc(cat) + "'";
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, category, name, price, unit, note FROM price_list"
                 + where + " ORDER BY category, name")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) {
                rows.add(new Vector<>(java.util.Arrays.asList(
                    rs.getLong(1),
                    RepairsPanel.nz(rs.getString(2)),
                    RepairsPanel.nz(rs.getString(3)),
                    WarehousePanel.fmt(rs.getDouble(4)),
                    RepairsPanel.nz(rs.getString(5)),
                    RepairsPanel.nz(rs.getString(6))
                )));
            }
            table.setModel(new DefaultTableModel(rows, new Vector<String>(java.util.Arrays.asList(
                "№", "Категория", "Наименование", "Цена", "Ед.", "Примечание"))) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void importFromFile() {
        if (!Session.edits(Roles.PRICE)) {
            Warn.show("Прайс-лист редактирует администратор, руководитель или бухгалтер");
            return;
        }
        PriceListImport.dialog(this);
        load();
    }

    private void exportToFile() {
        try {
            File out = Export.csv("prais-list",
                "SELECT category, name, price, unit, note FROM price_list ORDER BY category, name",
                new String[] { "Категория", "Наименование", "Цена", "Ед.", "Примечание" });
            JOptionPane.showMessageDialog(this,
                "Экспортировано в файл:\n" + out.getAbsolutePath(),
                "Экспорт прайс-листа", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            Warn.show("Ошибка экспорта: " + ex.getMessage());
        }
    }

    private void addItem() {
        String[] data = showEditDialog(this, "Добавить позицию", "", "", "0", "руб.", "");
        if (data == null) return;
        try {
            Db.exec("INSERT INTO price_list(category,name,price,unit,note) VALUES('"
                + Db.esc(data[0]) + "','" + Db.esc(data[1]) + "',"
                + RepairForm.parse(data[2]) + ",'" + Db.esc(data[3]) + "','" + Db.esc(data[4]) + "')");
            load();
        } catch (Exception ex) {
            Warn.show("Ошибка добавления: " + ex.getMessage());
        }
    }

    private void editItem() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Выберите позицию для редактирования");
            return;
        }
        long id = (Long) table.getValueAt(row, 0);
        String category = String.valueOf(table.getValueAt(row, 1));
        String name = String.valueOf(table.getValueAt(row, 2));
        String price = String.valueOf(table.getValueAt(row, 3));
        String unit = String.valueOf(table.getValueAt(row, 4));
        String note = String.valueOf(table.getValueAt(row, 5));

        String[] data = showEditDialog(this, "Изменить позицию", category, name, price, unit, note);
        if (data == null) return;
        try {
            Db.exec("UPDATE price_list SET category='" + Db.esc(data[0]) + "', name='" + Db.esc(data[1])
                + "', price=" + RepairForm.parse(data[2]) + ", unit='" + Db.esc(data[3])
                + "', note='" + Db.esc(data[4]) + "' WHERE id=" + id);
            load();
        } catch (Exception ex) {
            Warn.show("Ошибка изменения: " + ex.getMessage());
        }
    }

    private void deleteItem() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Выберите позицию для удаления");
            return;
        }
        long id = (Long) table.getValueAt(row, 0);
        String name = String.valueOf(table.getValueAt(row, 2));
        int confirm = JOptionPane.showConfirmDialog(this,
            "Удалить позицию «" + name + "»?", "Подтверждение удаления",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            Db.exec("DELETE FROM price_list WHERE id=" + id);
            load();
        } catch (Exception ex) {
            Warn.show("Ошибка удаления: " + ex.getMessage());
        }
    }

    private static String[] showEditDialog(Component parent, String title,
            String category, String name, String price, String unit, String note) {
        JDialog dlg = new JDialog((java.awt.Dialog) null, title, true);
        dlg.setLayout(new java.awt.GridBagLayout());
        dlg.getContentPane().setBackground(Theme.GRAY_BG);
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.insets = new java.awt.Insets(4, 6, 4, 6);
        g.anchor = java.awt.GridBagConstraints.WEST;
        g.fill = java.awt.GridBagConstraints.HORIZONTAL;

        javax.swing.JTextField fCategory = new javax.swing.JTextField(category, 20);
        javax.swing.JTextField fName = new javax.swing.JTextField(name, 30);
        javax.swing.JTextField fPrice = new javax.swing.JTextField(price, 10);
        javax.swing.JTextField fUnit = new javax.swing.JTextField(unit, 10);
        javax.swing.JTextField fNote = new javax.swing.JTextField(note, 30);

        fCategory.setFont(Theme.font(14));
        fName.setFont(Theme.font(14));
        fPrice.setFont(Theme.font(14));
        fUnit.setFont(Theme.font(14));
        fNote.setFont(Theme.font(14));

        int y = 0;
        g.gridx = 0; g.gridy = y; dlg.add(new JLabel("Категория:"), g);
        g.gridx = 1; dlg.add(fCategory, g);
        y++;
        g.gridx = 0; g.gridy = y; dlg.add(new JLabel("Наименование:"), g);
        g.gridx = 1; dlg.add(fName, g);
        y++;
        g.gridx = 0; g.gridy = y; dlg.add(new JLabel("Цена:"), g);
        g.gridx = 1; dlg.add(fPrice, g);
        y++;
        g.gridx = 0; g.gridy = y; dlg.add(new JLabel("Ед. измерения:"), g);
        g.gridx = 1; dlg.add(fUnit, g);
        y++;
        g.gridx = 0; g.gridy = y; dlg.add(new JLabel("Примечание:"), g);
        g.gridx = 1; dlg.add(fNote, g);
        y++;

        final String[][] result = { null };
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(Theme.on("Сохранить", Theme.YELLOW, e -> {
            result[0] = new String[] {
                fCategory.getText().trim(),
                fName.getText().trim(),
                fPrice.getText().trim(),
                fUnit.getText().trim(),
                fNote.getText().trim()
            };
            if (result[0][1].isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Укажите наименование");
                result[0] = null;
                return;
            }
            dlg.dispose();
        }));
        buttons.add(Theme.on("Отмена", Theme.GRAY_PANEL, e -> dlg.dispose()));
        g.gridx = 0; g.gridy = y; g.gridwidth = 2; g.fill = java.awt.GridBagConstraints.NONE;
        dlg.add(buttons, g);

        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
        return result[0];
    }

    /** Показать прайс-лист в диалоге из формы заявки. */
    public static void show(Component parent) {
        PriceList panel = new PriceList();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new java.awt.Dimension(900, 600));
        JOptionPane.showMessageDialog(parent, scroll,
            "Прайс-лист", JOptionPane.PLAIN_MESSAGE);
    }

    /** Выбрать одну позицию из прайс-листа; возвращает null при отмене. */
    public static double[] select(Component parent, String[] outName) {
        PriceList panel = new PriceList();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new java.awt.Dimension(900, 600));
        final double[][] result = { null };
        JOptionPane pane = new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION);
        JDialog dlg = pane.createDialog(parent, "Выбрать из прайс-листа");
        pane.addPropertyChangeListener("value", e -> {
            int val = pane.getValue() == null ? -1 : (Integer) pane.getValue();
            if (val != JOptionPane.OK_OPTION) { dlg.dispose(); return; }
            int row = panel.table.getSelectedRow();
            if (row < 0) { javax.swing.JOptionPane.showMessageDialog(dlg,
                "Выберите строку в прайс-листе"); pane.setValue(JOptionPane.UNINITIALIZED_VALUE); return; }
            outName[0] = String.valueOf(panel.table.getValueAt(row, 2));
            result[0] = new double[] {
                (Long) panel.table.getValueAt(row, 0),
                RepairForm.parse(String.valueOf(panel.table.getValueAt(row, 3)))
            };
            dlg.dispose();
        });
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
        return result[0];
    }
}
