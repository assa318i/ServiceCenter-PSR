package psr.sc;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Бухгалтерия: приходы и расходы (payments) с итогами за период. */
public class FinancePanel extends JPanel {

    private static final String[] PERIODS = { "Неделя", "Месяц", "Квартал", "Год", "Всё время" };
    private static final String[] METHODS = { "Наличные", "Безналичный", "Карта", "Перевод" };

    private final JComboBox<String> period = new JComboBox<>(PERIODS);
    private final JTable table = new JTable();
    private final JLabel totals = new JLabel(" ");

    public FinancePanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel head = Settings.headerBar(12);
        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Период: "));
        period.setFont(Theme.font(14));
        period.addActionListener(e -> load());
        tools.add(period);
        tools.add(Box.createHorizontalStrut(12));
        tools.add(Theme.on("Добавить", Theme.YELLOW, e -> addPayment()));
        tools.add(Theme.on("Удалить", Theme.BRIGHT_RED, e -> deletePayment()));
        tools.add(Theme.on("Обновить", Theme.BLUE, e -> load()));
        head.add(tools, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        totals.setFont(Theme.fontB(16));
        totals.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(totals, BorderLayout.SOUTH);
        Theme.styleTable(table);
        load();
    }

    private void load() {
        String where = "";
        if (period.getSelectedIndex() < 4) {
            Date[] r = range();
            where = " WHERE substr(p.ts,7,4)||substr(p.ts,4,2)||substr(p.ts,1,2) BETWEEN '"
                + ReportsPanel.iso(r[0]) + "' AND '" + ReportsPanel.iso(r[1]) + "'";
        }
        double in = 0, out = 0;
        try (Statement st = Db.get().createStatement()) {
            Vector<Vector<Object>> rows = new Vector<>();
            ResultSet rs = st.executeQuery("SELECT p.id, p.ts,"
                + " CASE p.dir WHEN 'IN' THEN 'Приход' ELSE 'Расход' END,"
                + " TRIM(COALESCE(c.org,'') || ' ' || COALESCE(c.fio,'')),"
                + " p.amount, COALESCE(p.method,''), CASE p.repair_id WHEN 0 THEN ''"
                + " ELSE '№' || p.repair_id END, COALESCE(p.note,'')"
                + " FROM payments p LEFT JOIN customers c ON c.id=p.customer_id"
                + where + " ORDER BY p.id DESC");
            while (rs.next()) {
                double amount = rs.getDouble(5);
                if ("Приход".equals(rs.getString(3))) in += amount; else out += amount;
                rows.add(new Vector<Object>(java.util.Arrays.asList(rs.getLong(1), rs.getString(2),
                    rs.getString(3), RepairsPanel.nz(rs.getString(4)),
                    WarehousePanel.fmt(amount), RepairsPanel.nz(rs.getString(6)),
                    RepairsPanel.nz(rs.getString(7)), RepairsPanel.nz(rs.getString(8)))));
            }
            rs.close();
            table.setModel(new DefaultTableModel(rows, new Vector<String>(java.util.Arrays.asList(
                "№", "Дата", "Направление", "Заказчик", "Сумма, руб.", "Способ", "Заявка", "Примечание"))) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        totals.setText(String.format("Приход: %,.2f  ·  Расход: %,.2f  ·  Итог: %,.2f руб.",
            in, out, in - out));
        totals.setForeground(in - out < 0 ? Theme.BRIGHT_RED : Theme.BLACK);
    }

    private Date[] range() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        Date now = c.getTime();
        switch (period.getSelectedIndex()) {
            case 0: c.add(java.util.Calendar.DAY_OF_YEAR, -7); break;
            case 1: c.add(java.util.Calendar.MONTH, -1); break;
            case 2: c.add(java.util.Calendar.MONTH, -3); break;
            default: c.add(java.util.Calendar.YEAR, -1); break;
        }
        return new Date[] { c.getTime(), now };
    }

    private void addPayment() {
        final JDialog d = new JDialog((java.awt.Frame) getTopLevelAncestor(), "Новая операция", true);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.setBackground(Theme.GRAY_BG);

        final JComboBox<String> dir = new JComboBox<>(new String[] { "Приход", "Расход" });
        final JTextField customer = new JTextField();
        final JTextField amount = new JTextField();
        final JComboBox<String> method = new JComboBox<>(METHODS);
        final JTextField repairId = new JTextField();
        final JTextField note = new JTextField();
        final JTextField date = new JTextField(new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date()));
        for (JComboBox<String> cb : new JComboBox[] { dir, method }) cb.setFont(Theme.font(14));
        for (JTextField tf : new JTextField[] { customer, amount, repairId, note, date }) tf.setFont(Theme.font(14));
        form.add(lbl("Направление")); form.add(dir);
        form.add(lbl("Заказчик (код или название)")); form.add(customer);
        form.add(lbl("Сумма, руб.")); form.add(amount);
        form.add(lbl("Способ оплаты")); form.add(method);
        form.add(lbl("№ заявки (можно 0)")); form.add(repairId);
        form.add(lbl("Дата")); form.add(date);
        form.add(lbl("Примечание")); form.add(note);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(Theme.on("Сохранить", Theme.YELLOW, e -> {
            double sum;
            try { sum = Double.parseDouble(amount.getText().replace(",", ".")); }
            catch (Exception ex) { Warn.show("Сумма не число: " + amount.getText()); return; }
            long cust = 0;
            String ct = customer.getText().trim();
            if (!ct.isEmpty()) cust = findCustomer(ct);
            try {
                Db.exec("INSERT INTO payments(ts,customer_id,dir,amount,method,note,repair_id) VALUES('"
                    + Db.esc(date.getText().trim()) + "'," + cust + ",'"
                    + ("Расход".equals(dir.getSelectedItem()) ? "OUT" : "IN") + "',"
                    + (sum + "").replace(",", ".") + ",'" + Db.esc((String) method.getSelectedItem())
                    + "','" + Db.esc(note.getText()) + "'," + parseLong(repairId.getText()) + ")");
                d.dispose();
                load();
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        }));
        buttons.add(Theme.on("Отмена", Theme.GRAY_PANEL, e -> d.dispose()));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.GRAY_BG);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(460, 340);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private long findCustomer(String t) {
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs;
            long id = parseLong(t);
            if (id > 0) rs = st.executeQuery("SELECT id FROM customers WHERE id=" + id);
            else rs = st.executeQuery("SELECT id FROM customers WHERE org LIKE '%" + Db.esc(t)
                + "%' OR fio LIKE '%" + Db.esc(t) + "%' ORDER BY id LIMIT 1");
            if (rs.next()) { long v = rs.getLong(1); rs.close(); return v; }
            rs.close();
        } catch (Exception ignored) { }
        return 0;
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private void deletePayment() {
        int r = table.getSelectedRow();
        if (r < 0) { Warn.show("Выберите операцию"); return; }
        if (JOptionPane.showConfirmDialog(this, "Удалить эту операцию?", "Бухгалтерия",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Db.exec("DELETE FROM payments WHERE id=" + table.getValueAt(r, 0));
            load();
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private static JLabel lbl(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.font(14));
        return l;
    }
}
