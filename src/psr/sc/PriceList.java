package psr.sc;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
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

    /** Показать прайс-лист в диалоге из формы заявки. */
    public static void show(Component parent) {
        PriceList panel = new PriceList();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new java.awt.Dimension(900, 600));
        JOptionPane.showMessageDialog(parent, scroll,
            "Прайс-лист", JOptionPane.PLAIN_MESSAGE);
    }
}
