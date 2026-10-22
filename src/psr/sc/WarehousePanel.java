package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Склад: окно остатков, заказы на склад, рекомендации по пополнению (нормы задаёт администратор). */
public class WarehousePanel extends JPanel {

    private final JTable items = new JTable();
    private final JTable orders = new JTable();
    private final JTable recs = new JTable();
    private final FilterBar bar = new FilterBar("Склад", this::loadAll);
    private final javax.swing.JCheckBox low;

    public WarehousePanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        low = bar.flag("ниже минимума");

        JPanel head = Settings.headerBar(12);
        add(head, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.fontB(15));
        tabs.setBackground(Theme.GRAY_BG);
        tabs.addTab("Склад (остатки)", itemsTab());
        tabs.addTab("Заказы на склад", ordersTab());
        tabs.addTab("Рекомендации по пополнению", recsTab());
        tabs.addTab("Картриджи", new CartridgesPanel());
        tabs.addTab("Наборы ЗЧ", new KitsPanel());
        add(tabs, BorderLayout.CENTER);
        loadAll();
    }

    private JPanel wrap(JTable t, JPanel tools) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        tools.setOpaque(false);
        p.add(tools, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        Theme.styleTable(t);
        return p;
    }

    private JPanel itemsTab() {
        JPanel tools = new JPanel();
        tools.add(Theme.on("Добавить позицию", Theme.YELLOW, e -> editItem()));
        tools.add(Theme.on("Изменить", Theme.BLUE, e -> editItem(selected(items))));
        tools.add(Theme.on("Удалить", Theme.BRIGHT_RED, e -> deleteItem()));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> loadAll()));
        JPanel rows = new JPanel(new BorderLayout());
        rows.setOpaque(false);
        rows.add(tools, BorderLayout.NORTH);
        rows.add(bar, BorderLayout.SOUTH);
        return wrap(items, rows);
    }

    private JPanel ordersTab() {
        JPanel tools = new JPanel();
        tools.add(Theme.on("Новый заказ на склад", Theme.YELLOW, e -> newOrder()));
        tools.add(Theme.on("Принято (увеличить остаток)", Theme.BLUE, e -> receiveOrder()));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> loadAll()));
        return wrap(orders, tools);
    }

    private JPanel recsTab() {
        JPanel tools = new JPanel();
        tools.add(Theme.on("Заказать всё по рекомендациям", Theme.BRIGHT_RED, e -> orderAllRecs()));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> loadAll()));
        JLabel hint = Theme.danger("Позиции с остатком ниже минимальной нормы — требуется пополнение");
        tools.add(hint);
        JPanel p = wrap(recs, tools);
        p.add(new JLabel("Минимальное количество задаётся администратором в окне «Склад (остатки)»."),
            BorderLayout.SOUTH);
        return p;
    }

    private Long selected(JTable t) {
        int r = t.getSelectedRow();
        return r < 0 ? null : (Long) t.getValueAt(r, 0);
    }

    private void editItem() { editItem(null); }

    private void editItem(Long id) {
        if (!Session.isAdmin()) { Warn.show("Нормы и позиции склада меняет только администратор"); return; }
        JTextField name = f("Наименование запчасти/компонента", id, "name");
        JTextField unit = f("Единица (шт, комп...)", id, "unit");
        JTextField qty = f("Остаток", id, "qty");
        JTextField min = f("МИНИМАЛЬНАЯ НОРМА (администратор задаёт)", id, "min_qty");
        JTextField price = f("Цена, руб.", id, "price");
        min.setForeground(Theme.BRIGHT_RED);
        int ok = JOptionPane.showConfirmDialog(this, new Object[] {
            "Наименование:", name, "Единица:", unit, "Остаток:", qty,
            Theme.danger("Минимальная норма пополнения:"), min, "Цена, руб.:", price },
            id == null ? "Новая позиция склада" : "Позиция склада",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        String sql = "name='" + Db.esc(name.getText()) + "', unit='" + Db.esc(unit.getText())
            + "', qty=" + RepairForm.parse(qty.getText()) + ", min_qty="
            + RepairForm.parse(min.getText()) + ", price=" + RepairForm.parse(price.getText());
        try {
            if (id == null)
                Db.exec("INSERT INTO items(name,unit,qty,min_qty,price) VALUES("
                    + buildValues(name, unit, qty, min, price) + ")");
            else Db.exec("UPDATE items SET " + sql + " WHERE id=" + id);
        } catch (Exception ex) { Warn.show("Проверьте числа в полях Остаток/Норма/Цена.\n" + ex.getMessage()); }
        loadAll();
    }

    private String buildValues(JTextField... fs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fs.length; i++) {
            if (i > 0) sb.append(',');
            String v = fs[i].getText();
            sb.append(i == 0 || i == 1 ? "'" + Db.esc(v) + "'" : String.valueOf(RepairForm.parse(v)));
        }
        return sb.toString();
    }

    private void deleteItem() {
        if (!Session.isAdmin()) { Warn.show("Позиции склада удаляет только администратор"); return; }
        Long id = selected(items);
        if (id == null) { Warn.show("Выберите позицию"); return; }
        if (JOptionPane.showConfirmDialog(this, "Удалить позицию склада?", "Склад",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try {
                Db.exec("DELETE FROM items WHERE id=" + id);
                Db.exec("DELETE FROM stock_orders WHERE item_id=" + id);
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
            loadAll();
        }
    }

    private void newOrder() {
        Vector<String> names = new Vector<>();
        Vector<Long> ids = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, qty FROM items ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString(2) + "  (остаток " + fmt(rs.getDouble(3)) + ")");
                ids.add(rs.getLong(1));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (names.isEmpty()) { Warn.show("Склад пуст — сначала добавьте позиции"); return; }
        JComboBox<String> pick = new JComboBox<>(names);
        pick.setFont(Theme.font(15));
        JTextField qty = new JTextField(10);
        qty.setFont(Theme.font(15));
        JTextField note = new JTextField(16);
        note.setFont(Theme.font(15));
        int ok = JOptionPane.showConfirmDialog(this, new Object[] {
            "Позиция:", pick, Theme.danger("Количество для заказа:"), qty, "Примечание:", note },
            "Заказ на склад", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            Db.exec("INSERT INTO stock_orders(item_id,qty,status,created,note) VALUES("
                + ids.get(pick.getSelectedIndex()) + "," + RepairForm.parse(qty.getText())
                + ",'NEW','" + RepairsPanel.stamp() + "','" + Db.esc(note.getText()) + "')");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        loadAll();
    }

    private void receiveOrder() {
        Long id = selected(orders);
        if (id == null) { Warn.show("Выберите заказ"); return; }
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT item_id, qty, status FROM stock_orders WHERE id=" + id);
            if (!rs.next()) return;
            if ("RECEIVED".equals(rs.getString(3))) { Warn.show("Заказ уже принят"); return; }
            long itemId = rs.getLong(1);
            double qty = rs.getDouble(2);
            rs.close();
            st.executeUpdate("UPDATE items SET qty = qty + " + qty + " WHERE id=" + itemId);
            st.executeUpdate("UPDATE stock_orders SET status='RECEIVED' WHERE id=" + id);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        loadAll();
    }

    private void orderAllRecs() {
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id, qty, min_qty FROM items WHERE qty < min_qty");
            int n = 0;
            while (rs.next()) {
                double need = Math.max(1, rs.getDouble(2) * 2 - rs.getDouble(3));
                st.executeUpdate("INSERT INTO stock_orders(item_id,qty,status,created,note)"
                    + " VALUES(" + rs.getLong(1) + "," + need + ",'NEW','" + RepairsPanel.stamp()
                    + "','из рекомендаций')");
                n++;
            }
            rs.close();
            JOptionPane.showMessageDialog(this, "Создано заказов: " + n, "Рекомендации",
                JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        loadAll();
    }

    private JTextField f(String tip, Long id, String col) {
        JTextField t = new JTextField(16);
        t.setFont(Theme.font(15));
        t.setToolTipText(tip);
        if (id != null) {
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT " + col + " FROM items WHERE id=" + id)) {
                if (rs.next()) t.setText(rs.getString(1));
            } catch (Exception ignored) { }
        }
        return t;
    }

    void loadAll() {
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id,name,unit,qty,min_qty,price FROM items ORDER BY name");
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) {
                if (!bar.match(rs.getString(2), rs.getString(3))) continue;
                if (bar.on(low) && rs.getDouble(4) > rs.getDouble(5)) continue;
                Vector<Object> r = new Vector<>(java.util.Arrays.asList(rs.getLong(1),
                    rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), rs.getDouble(6)));
                rows.add(r);
            }
            items.setModel(model(rows, new String[] {
                "№", "Наименование", "Ед.", "Остаток", "Мин. норма", "Цена, руб." }));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT o.id, i.name, o.qty,"
                 + " CASE o.status WHEN 'NEW' THEN 'Новый' WHEN 'RECEIVED' THEN 'Принято' ELSE o.status END,"
                 + " o.created, o.note FROM stock_orders o LEFT JOIN items i ON i.id=o.item_id"
                 + " ORDER BY o.id DESC")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) rows.add(new Vector<>(java.util.Arrays.asList(rs.getLong(1),
                rs.getString(2), rs.getDouble(3), rs.getString(4), rs.getString(5), rs.getString(6))));
            orders.setModel(model(rows, new String[] {
                "№", "Позиция", "Количество", "Статус", "Создан", "Примечание" }));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, qty, min_qty FROM items"
                 + " WHERE qty < min_qty ORDER BY (min_qty - qty) DESC")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) {
                double qty = rs.getDouble(3), min = rs.getDouble(4);
                rows.add(new Vector<>(java.util.Arrays.asList(rs.getLong(1), rs.getString(2),
                    fmt(qty), fmt(min), fmt(Math.max(1, min * 2 - qty)))));
            }
            recs.setModel(model(rows, new String[] {
                "№", "Позиция", "Остаток", "Мин. норма", "Рекомендуется заказать" }));
            recs.getColumnModel().getColumn(4).setCellRenderer(redCell());
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private DefaultTableModel model(Vector<Vector<Object>> rows, String[] cols) {
        return new DefaultTableModel(rows, new Vector<>(java.util.Arrays.asList(cols))) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private javax.swing.table.DefaultTableCellRenderer redCell() {
        return new javax.swing.table.DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(
                    JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                if (!s) { setForeground(Theme.BRIGHT_RED); setFont(Theme.fontB(14)); }
                return this;
            }
        };
    }

    static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.2f", d);
    }
}
