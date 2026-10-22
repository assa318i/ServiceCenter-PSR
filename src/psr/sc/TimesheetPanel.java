package psr.sc;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Табель рабочего времени: отметки «приход — уход» по каждому сотруднику за день.
 * Вносит их руководитель сервисных инженеров, бухгалтер или администратор; инженер
 * и заправщик видят свои отметки. Нормы и деньги здесь не считаются — только факт.
 */
public class TimesheetPanel extends JPanel {

    private final JComboBox<String> month = new JComboBox<>();
    private final JComboBox<String> who = new JComboBox<>();
    private final JTable table = new JTable();
    private final JLabel totals = new JLabel(" ");

    public TimesheetPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        for (int i = 0; i < 30; i++) month.addItem(monthName(i));
        month.setFont(Theme.font(14));
        who.setFont(Theme.font(14));
        who.addItem("все сотрудники");

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Месяц: "));
        tools.add(month);
        tools.add(new JLabel("  Сотрудник: "));
        tools.add(who);
        if (Session.edits(Roles.TIMES)) {
            tools.add(Theme.on("Добавить отметку", Theme.YELLOW, e -> form(0)));
            tools.add(Theme.on("Изменить", Theme.BLUE, e -> {
                long id = selectedId();
                if (id > 0) form(id);
            }));
            tools.add(Theme.on("Удалить", Theme.BRIGHT_RED, e -> remove()));
        }
        tools.add(Theme.on("Обновить", Theme.BLUE, e -> { fillPeople(); load(); }));
        tools.add(Theme.on("Календарь РФ", Theme.GRAY_PANEL,
            e -> ProdCalendar.open(javax.swing.SwingUtilities.getWindowAncestor(this))));
        // Шапка — FlowLayout: строку фильтров кладём отдельным рядом, иначе при переносе
        // она обрезается (вторую строку FlowLayout в preferredSize не учитывает).
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Settings.headerBar(12), BorderLayout.NORTH);
        head.add(tools, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        totals.setFont(Theme.fontB(15));
        totals.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(totals, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        Theme.styleTable(table);
        month.addActionListener(e -> load());
        who.addActionListener(e -> load());
        fillPeople();
        load();
    }

    /** Отметки за выбранный месяц; сотрудник — «все сотрудники» или конкретная фамилия. */
    private void load() {
        String sql = "SELECT id, day, who, t_in, t_out, note FROM timesheet"
            + " WHERE substr(day,4,2)||'.'||substr(day,7,4)='" + month.getSelectedItem() + "'";
        if (who.getSelectedIndex() > 0)
            sql += " AND who='" + Db.esc((String) who.getSelectedItem()) + "'";
        sql += " ORDER BY day, who, id";
        Map<String, Double> per = new LinkedHashMap<>();
        double sum = 0;
        int rows = 0;
        try (Statement st = Db.get().createStatement()) {
            Vector<Vector<Object>> data = new Vector<>();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                double h = hours(rs.getString(4), rs.getString(5));
                if (h > 0) {
                    per.merge(RepairsPanel.nz(rs.getString(3)), h, Double::sum);
                    sum += h;
                }
                rows++;
                data.add(new Vector<Object>(java.util.Arrays.asList(rs.getLong(1), rs.getString(2),
                    RepairsPanel.nz(rs.getString(3)), RepairsPanel.nz(rs.getString(4)),
                    RepairsPanel.nz(rs.getString(5)), h > 0 ? WarehousePanel.fmt(h) : "",
                    RepairsPanel.nz(rs.getString(6)))));
            }
            rs.close();
            table.setModel(new DefaultTableModel(data, new Vector<String>(java.util.Arrays.asList(
                "№", "Дата", "Сотрудник", "Приход", "Уход", "Часов", "Примечание"))) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        StringBuilder sb = new StringBuilder("Отметок: " + rows + " · часов: " + WarehousePanel.fmt(sum));
        for (Map.Entry<String, Double> e : per.entrySet())
            sb.append("  ·  ").append(e.getKey()).append(' ').append(WarehousePanel.fmt(e.getValue()));
        totals.setText(sb.toString());
    }

    /** Список сотрудников для фильтра: кто работает сейчас плюс кто уже есть в табеле. */
    private void fillPeople() {
        String keep = (String) who.getSelectedItem();
        who.removeAllItems();
        who.addItem("все сотрудники");
        Set<String> names = new LinkedHashSet<>();
        try {
            for (Users.Row r : Users.all()) if (!r.name.trim().isEmpty()) names.add(r.name.trim());
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT DISTINCT who FROM timesheet"
                     + " WHERE TRIM(who)<>'' ORDER BY who")) {
                while (rs.next()) names.add(rs.getString(1).trim());
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        for (String n : names) who.addItem(n);
        if (keep != null) who.setSelectedItem(keep);
    }

    /** Форма отметки: id = 0 — новая, иначе правка существующей. */
    private void form(long id) {
        String[] old = id == 0 ? new String[] { Tasks.dateText(), "", "", "", "" } : rowOf(id);
        if (old == null) return;
        final JDialog d = new JDialog((java.awt.Frame) getTopLevelAncestor(),
            id == 0 ? "Отметка рабочего времени" : "Правка отметки", true);
        JPanel f = new JPanel(new GridLayout(0, 2, 6, 6));
        f.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        f.setBackground(Theme.GRAY_BG);

        final JComboBox<String> person = new JComboBox<>();
        for (int i = 1; i < who.getItemCount(); i++) person.addItem(who.getItemAt(i));
        person.setEditable(true);
        person.setSelectedItem(old[0]);
        final JTextField day = new JTextField(old[1], 12);
        final JTextField tIn = new JTextField(old[2], 6);
        final JTextField tOut = new JTextField(old[3], 6);
        final JTextField note = new JTextField(old[4], 18);
        person.setFont(Theme.font(14));
        for (JTextField tf : new JTextField[] { day, tIn, tOut, note }) tf.setFont(Theme.font(14));
        f.add(lbl("Сотрудник")); f.add(person);
        f.add(lbl("Дата (дд.мм.гггг)")); f.add(day);
        f.add(lbl("Приход (чч:мм)")); f.add(tIn);
        f.add(lbl("Уход (чч:мм)")); f.add(tOut);
        f.add(lbl("Примечание")); f.add(note);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        final long target = id;
        buttons.add(Theme.on("Сохранить", Theme.YELLOW, e -> save(target, d, person, day, tIn, tOut, note)));
        buttons.add(Theme.on("Отмена", Theme.GRAY_PANEL, e -> d.dispose()));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.GRAY_BG);
        root.add(f, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(440, 280);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void save(long id, JDialog d, JComboBox<String> person, JTextField day,
                      JTextField tIn, JTextField tOut, JTextField note) {
        String whoName = String.valueOf(person.getSelectedItem()).trim();
        String date = day.getText().trim();
        String in = tIn.getText().trim(), out = tOut.getText().trim();
        if (whoName.isEmpty()) { Warn.show("Нужно указать сотрудника"); return; }
        try { Tasks.day(date); } catch (Exception bad) {
            Warn.show("Дата в формате дд.мм.гггг, например " + Tasks.dateText()); return;
        }
        if (!in.isEmpty() && !out.isEmpty()) {
            double h = hours(in, out);
            if (h <= 0) { Warn.show("Уход раньше прихода — проверьте время"); return; }
        }
        if (ProdCalendar.isOff(date) && JOptionPane.showConfirmDialog(this,
            date + " — выходной или праздник по производственному календарю."
                + " Отметить в этот день рабочее время?", "Работа в выходной",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            if (id == 0) Db.exec("INSERT INTO timesheet(who,day,t_in,t_out,note,created) VALUES('"
                + Db.esc(whoName) + "','" + Db.esc(date) + "','" + Db.esc(in) + "','" + Db.esc(out)
                + "','" + Db.esc(note.getText().trim()) + "','" + RepairsPanel.stamp() + "')");
            else Db.exec("UPDATE timesheet SET who='" + Db.esc(whoName) + "', day='" + Db.esc(date)
                + "', t_in='" + Db.esc(in) + "', t_out='" + Db.esc(out) + "', note='"
                + Db.esc(note.getText().trim()) + "' WHERE id=" + id);
            d.dispose();
            fillPeople();
            load();
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void remove() {
        long id = selectedId();
        if (id <= 0) return;
        if (JOptionPane.showConfirmDialog(this, "Удалить эту отметку?", "Учёт рабочего времени",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            Db.exec("DELETE FROM timesheet WHERE id=" + id);
            load();
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private long selectedId() {
        int r = table.getSelectedRow();
        if (r < 0) { Warn.show("Выберите отметку в списке"); return 0; }
        return (Long) table.getValueAt(r, 0);
    }

    /** Данные отметки для правки: {сотрудник, дата, приход, уход, примечание}. */
    private String[] rowOf(long id) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT who, day, t_in, t_out, note FROM timesheet"
                 + " WHERE id=" + id)) {
            if (!rs.next()) return null;
            return new String[] { RepairsPanel.nz(rs.getString(1)), RepairsPanel.nz(rs.getString(2)),
                RepairsPanel.nz(rs.getString(3)), RepairsPanel.nz(rs.getString(4)),
                RepairsPanel.nz(rs.getString(5)) };
        } catch (Exception ex) { Warn.show(ex.getMessage()); return null; }
    }

    /** Часы между приходом и уходом; 0 — если время не указано или ушло назад. */
    static double hours(String in, String out) {
        int a = minutes(in), b = minutes(out);
        if (a < 0 || b < 0 || b <= a) return 0;
        return Math.round((b - a) / 6.0) / 10.0;
    }

    private static int minutes(String hhmm) {
        String s = hhmm == null ? "" : hhmm.trim();
        if (!s.matches("\\d{1,2}:\\d{2}")) return -1;
        int h = Integer.parseInt(s.substring(0, s.indexOf(':'))), m = Integer.parseInt(s.substring(s.indexOf(':') + 1));
        return h > 23 || m > 59 ? -1 : h * 60 + m;
    }

    /** Название месяца отступом на monthsBack месяцев назад в формате ММ.ГГГГ. */
    private static String monthName(int monthsBack) {
        Calendar c = new GregorianCalendar();
        c.add(Calendar.MONTH, -monthsBack);
        return String.format("%1$02d.%2$04d", c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    private static JLabel lbl(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.font(14));
        return l;
    }
}
