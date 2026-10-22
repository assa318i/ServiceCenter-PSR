package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Лента заявки: реплики сотрудников, изменения полей и внутренние пометки одним хронологическим
 * списком. Реплика с @Имя попадает в общий чат, подписка «слежу за заявкой» даёт напоминание при запуске.
 */
public final class Stream {

    static final String POST = "POST", UPDATE = "UPDATE", NOTE = "NOTE";
    private static final String[] FILTERS = { "Всё", "Реплики", "Изменения", "Пометки" };

    /** Окно ленты одной заявки. */
    static void show(java.awt.Component owner, final long repairId) {
        JDialog dlg = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(owner),
            "Лента заявки №" + repairId, false);
        JTable table = new JTable();
        JComboBox<String> type = new JComboBox<>(FILTERS);
        type.setFont(Theme.font(15));
        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Показывать: "));
        tools.add(type);
        type.addActionListener(e -> load(table, repairId, (String) type.getSelectedItem()));
        final JCheckBox watch = new JCheckBox("Слежу за заявкой", watched(repairId));
        watch.setOpaque(false);
        watch.setFont(Theme.font(15));
        watch.addActionListener(e -> setWatch(repairId, watch.isSelected()));
        tools.add(watch);
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL,
            e -> load(table, repairId, (String) type.getSelectedItem())));

        JComboBox<String> kind = new JComboBox<>(new String[] { "Реплика", "Пометка" });
        kind.setFont(Theme.font(15));
        JTextField input = new JTextField(40);
        input.setFont(Theme.font(15));
        JPanel send = new JPanel(new BorderLayout());
        send.setOpaque(false);
        send.add(kind, BorderLayout.WEST);
        send.add(input, BorderLayout.CENTER);
        JButton sendBtn = Theme.on("Отправить", Theme.YELLOW, e -> send(repairId, input, kind,
            table, type, owner));
        input.addActionListener(ev -> sendBtn.doClick());
        send.add(sendBtn, BorderLayout.EAST);

        dlg.setLayout(new BorderLayout());
        dlg.setBackground(Theme.GRAY_BG);
        dlg.add(tools, BorderLayout.NORTH);
        Theme.styleTable(table);
        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.add(send, BorderLayout.SOUTH);
        dlg.setSize(980, 520);
        dlg.setLocationRelativeTo(owner);
        load(table, repairId, (String) type.getSelectedItem());
        dlg.setVisible(true);
    }

    /** Отправить запись ленты; возвращает true, если запись создана. */
    static boolean send(long repairId, JTextField input, JComboBox<String> kind,
                        JTable table, JComboBox<String> type, java.awt.Component owner) {
        String text = input.getText().trim();
        if (text.isEmpty()) return false;
        String k = "Пометка".equals(kind.getSelectedItem()) ? NOTE : POST;
        try {
            post(repairId, text, k);
            int mentioned = mention(repairId, text);
            input.setText("");
            load(table, repairId, (String) type.getSelectedItem());
            if (mentioned > 0)
                JOptionPane.showMessageDialog(owner, "Упомянуто в чате сотрудников: " + mentioned,
                    "Лента заявки", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); return false; }
        return true;
    }

    /** Служебная запись об изменении полей — всегда с подписью автора. */
    static void log(long repairId, String info) throws Exception {
        post(repairId, info, UPDATE);
    }

    static void post(long repairId, String text, String kind) throws Exception {
        Db.exec("INSERT INTO repair_history(repair_id,ts,info,who,kind) VALUES(" + repairId + ",'"
            + RepairsPanel.stamp() + "','" + Db.esc(text) + "','" + Db.esc(Session.name) + "','"
            + kind + "')");
    }

    /** Реплики вида «@Иван» кидают строку в общий чат; возвращает число найденных упоминаний. */
    static int mention(long repairId, String text) {
        int n = 0;
        Vector<String> seen = new Vector<>();
        Matcher m = Pattern.compile("@[^\\s@]+").matcher(text);
        while (m.find()) {
            String name = userNamed(m.group().substring(1));
            if (name == null || containsIgnoreCase(seen, name)) continue;
            seen.add(name);
            try {
                Db.exec("INSERT INTO chat(ts,author,role,text) VALUES('" + RepairsPanel.stamp()
                    + "','" + Db.esc(Session.name) + "','" + Session.role + "','!!! "
                    + Db.esc(name) + ", вас упомянули в заявке №" + repairId + ": "
                    + Db.esc(text) + "')");
                n++;
            } catch (Exception ignored) { }
        }
        return n;
    }

    /** Имя пользователя по токену @ — совпадение полное или по первому слову. */
    private static String userNamed(String token) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM users ORDER BY id")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name == null) continue;
                if (name.equalsIgnoreCase(token)) return name;
                int sp = name.indexOf(' ');
                if (sp > 0 && name.substring(0, sp).equalsIgnoreCase(token)) return name;
            }
        } catch (Exception ignored) { }
        return null;
    }

    static boolean watched(long repairId) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM watch WHERE repair_id=" + repairId
                 + " AND who='" + Db.esc(Session.name) + "'")) {
            return rs.next() && rs.getLong(1) > 0;
        } catch (Exception e) { return false; }
    }

    /** Подписка: с текущего хвоста ленты, чтобы стартовое напоминание не показывало прочитанное. */
    static void setWatch(long repairId, boolean on) {
        String who = Db.esc(Session.name);
        try {
            if (!on) { Db.exec("DELETE FROM watch WHERE repair_id=" + repairId + " AND who='" + who + "'"); return; }
            if (watched(repairId)) return;
            long seen = 0;
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT IFNULL(MAX(id),0) FROM repair_history"
                     + " WHERE repair_id=" + repairId)) {
                if (rs.next()) seen = rs.getLong(1);
            }
            Db.exec("INSERT INTO watch(repair_id,who,seen_id) VALUES(" + repairId + ",'"
                + who + "'," + seen + ")");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    /** Новые записи в подписках; отмечает их прочитанными. null — если ничего нового. */
    static String sinceText() {
        Vector<String> lines = new Vector<>();
        Vector<long[]> read = new Vector<>();
        String who = Db.esc(Session.name);
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT repair_id, seen_id FROM watch WHERE who='" + who + "'");
            Vector<long[]> subs = new Vector<>();
            while (rs.next()) subs.add(new long[] { rs.getLong(1), rs.getLong(2) });
            rs.close();
            for (long[] s : subs) {
                ResultSet q = st.executeQuery("SELECT COUNT(*), IFNULL(MAX(id),0) FROM repair_history"
                    + " WHERE repair_id=" + s[0] + " AND id>" + s[1]);
                q.next();
                long count = q.getLong(1), max = q.getLong(2);
                q.close();
                if (count == 0) continue;
                lines.add("Заявка №" + s[0] + " — новых записей: " + count);
                read.add(new long[] { s[0], max });
            }
            for (long[] r : read)
                st.executeUpdate("UPDATE watch SET seen_id=" + r[1] + " WHERE repair_id=" + r[0]
                    + " AND who='" + who + "'");
        } catch (Exception ignored) { }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /** Напоминание при запуске: по заявкам, за которыми следит текущий сотрудник. */
    static void notifyNew() {
        String s = sinceText();
        if (s == null) return;
        JOptionPane.showMessageDialog(null, s, "Слежу за заявками: появились записи",
            JOptionPane.PLAIN_MESSAGE);
    }

    static void load(JTable table, long repairId, String filter) {
        String where = null;
        if ("Реплики".equals(filter)) where = "kind='" + POST + "'";
        else if ("Пометки".equals(filter)) where = "kind='" + NOTE + "'";
        else if ("Изменения".equals(filter)) where = "kind='" + UPDATE + "'";
        Vector<Vector<Object>> rows = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, ts, who, kind, info FROM repair_history"
                 + " WHERE repair_id=" + repairId + (where == null ? "" : " AND " + where) + " ORDER BY id")) {
            while (rs.next())
                rows.add(new Vector<>(Arrays.asList(rs.getLong(1), nz(rs.getString(2)),
                    nz(rs.getString(3)), ru(nz(rs.getString(4))), nz(rs.getString(5)))));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        table.setModel(new DefaultTableModel(rows, new Vector<>(Arrays.asList(
            "№", "Дата", "Кто", "Тип", "Запись"))) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        });
        table.getColumn("№").setMinWidth(0);
        table.getColumn("№").setMaxWidth(0);
        table.getColumn("Дата").setPreferredWidth(120);
        table.getColumn("Кто").setPreferredWidth(140);
        table.getColumn("Тип").setPreferredWidth(90);
        table.getColumn("Запись").setPreferredWidth(560);
        table.getColumn("Запись").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                setFont("Реплика".equals(t.getValueAt(r, 3)) || "Пометка".equals(t.getValueAt(r, 3))
                    ? Theme.fontB(14) : Theme.font(14));
                setToolTipText(String.valueOf(v));
                return this;
            }
        });
        if (table.getRowCount() > 0)
            table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
    }

    private static boolean containsIgnoreCase(Vector<String> v, String s) {
        for (String x : v) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static String ru(String kind) {
        if (POST.equals(kind)) return "Реплика";
        if (NOTE.equals(kind)) return "Пометка";
        return "Изменение";
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private Stream() { }
}
