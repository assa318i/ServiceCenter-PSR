package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Дела по заявке в духе EspoCRM: задача, звонок, встреча — со сроком, ответственным,
 * повтором и напоминанием при запуске программы.
 */
public final class Tasks {

    static final String[] KINDS = { "Задача", "Звонок", "Встреча" };

    /** Окно дел: все дела или дела одной заявки (repairId != null). */
    static void show(java.awt.Component owner, final Long repairId) {
        final JDialog dlg = new JDialog((java.awt.Frame) javax.swing.SwingUtilities
            .getWindowAncestor(owner), repairId == null
            ? "Дела и напоминания" : "Дела по заявке №" + repairId, false);
        final JTable table = new JTable();
        // фильтр создаётся раньше таблицы: его перечитывание навешиваем после сборки всех виджетов
        final Runnable[] reload = new Runnable[1];
        final FilterBar bar = new FilterBar("Дела", () -> reload[0].run());
        final JCheckBox onlyMine = bar.flag("только мои");
        reload[0] = () -> load(table, bar, onlyMine, repairId);

        dlg.setLayout(new BorderLayout());
        dlg.setBackground(Theme.GRAY_BG);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(Theme.on("Новое дело", Theme.YELLOW, e -> {
            if (form(repairId)) reload[0].run();
        }));
        tools.add(Theme.on("Выполнено", Theme.BLUE, e -> {
            if (done(selected(table))) reload[0].run();
        }));
        tools.add(Theme.on("Удалить", Theme.BRIGHT_RED, e -> {
            if (remove(selected(table))) reload[0].run();
        }));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> reload[0].run()));
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(tools, BorderLayout.NORTH);
        north.add(bar, BorderLayout.SOUTH);
        dlg.add(north, BorderLayout.NORTH);
        Theme.styleTable(table);
        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.setSize(1000, 520);
        dlg.setLocationRelativeTo(owner);
        reload[0].run();
        dlg.setVisible(true);
    }

    static Long selected(JTable t) {
        int r = t.getSelectedRow();
        return r < 0 ? null : (Long) t.getValueAt(r, 0);
    }

    /** Форма нового дела. Возвращает true, если дело создано. */
    static boolean form(Long repairId) {
        JComboBox<String> kind = new JComboBox<>(KINDS);
        JTextField title = new JTextField(24);
        JTextField due = new JTextField(dateText(), 10);
        JTextField who = new JTextField(Session.name, 12);
        JTextField repeat = new JTextField("0", 4);
        JTextField rep = new JTextField(repairId == null ? "" : String.valueOf(repairId), 5);
        kind.setFont(Theme.font(15));
        for (JTextField f : new JTextField[] { title, due, who, repeat, rep })
            f.setFont(Theme.font(15));
        due.setToolTipText("дд.мм.гггг");
        repeat.setToolTipText("Через сколько дней создавать следующее такое же дело (0 — без повтора)");
        int ok = JOptionPane.showConfirmDialog(null, new Object[] {
            "Тип дела:", kind, "Что сделать:", title, "Срок (дд.мм.гггг):", due,
            "Ответственный:", who, "Номер заявки (можно пусто):", rep, "Повтор, дней (0 — нет):", repeat },
            "Новое дело", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return false;
        if (title.getText().trim().isEmpty()) { Warn.show("Напишите, что нужно сделать"); return false; }
        try {
            day(due.getText());
            Db.exec("INSERT INTO tasks(repair_id,kind,title,due,done,who,created,repeat_days)"
                + " VALUES(" + num(rep.getText()) + ",'" + kind.getSelectedItem() + "','"
                + Db.esc(title.getText().trim()) + "','" + due.getText().trim() + "',0,'"
                + Db.esc(who.getText().trim()) + "','" + RepairsPanel.stamp() + "',"
                + num(repeat.getText()) + ")");
        } catch (Exception ex) {
            Warn.show("Срок нужно писать дд.мм.гггг\n" + ex.getMessage());
            return false;
        }
        return true;
    }

    /** Отметить выполненным; для повторяющегося дела сразу ставится следующее по сроку. */
    static boolean done(Long id) {
        if (id == null) { Warn.show("Выберите дело"); return false; }
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT kind,title,due,who,repair_id,repeat_days"
                + " FROM tasks WHERE id=" + id);
            if (!rs.next()) return false;
            String kind = rs.getString(1), title = rs.getString(2), who = rs.getString(4);
            long rep = rs.getLong(5);
            int repeat = rs.getInt(6);
            rs.close();
            st.executeUpdate("UPDATE tasks SET done=1 WHERE id=" + id);
            if (rep > 0) Stream.log(rep, "Выполнено дело: " + kind + " — " + title);
            if (repeat > 0) {
                String next = new SimpleDateFormat("dd.MM.yyyy")
                    .format(new Date(System.currentTimeMillis() + repeat * 86_400_000L));
                st.executeUpdate("INSERT INTO tasks(repair_id,kind,title,due,done,who,created,repeat_days)"
                    + " VALUES(" + rep + ",'" + Db.esc(kind) + "','" + Db.esc(title) + "','" + next
                    + "',0,'" + Db.esc(who) + "','" + RepairsPanel.stamp() + "'," + repeat + ")");
                JOptionPane.showMessageDialog(null, "Дело закрыто. Следующее по повтору: " + next,
                    "Дела", JOptionPane.PLAIN_MESSAGE);
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); return false; }
        return true;
    }

    static boolean remove(Long id) {
        if (id == null) { Warn.show("Выберите дело"); return false; }
        if (JOptionPane.showConfirmDialog(null, "Удалить дело?", "Дела",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        try { Db.exec("DELETE FROM tasks WHERE id=" + id); }
        catch (Exception ex) { Warn.show(ex.getMessage()); return false; }
        return true;
    }

    static void load(JTable table, FilterBar bar, JCheckBox onlyMine, Long repairId) {
        Vector<Vector<Object>> rows = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, repair_id, kind, title, due, who,"
                 + " repeat_days, done FROM tasks" + (repairId == null ? "" : " WHERE repair_id=" + repairId)
                 + " ORDER BY done, due, id")) {
            while (rs.next()) {
                String kind = rs.getString(3), title = rs.getString(4), who = rs.getString(6);
                long rep = rs.getLong(2);
                boolean isDone = rs.getInt(8) != 0;
                String due = nz(rs.getString(5));
                if (onlyMine != null && onlyMine.isSelected() && !Session.name.equals(who)) continue;
                if (!bar.match(String.valueOf(rep), kind, title, who, due)) continue;
                rows.add(new Vector<>(Arrays.asList(rs.getLong(1), rep == 0 ? "" : "№" + rep,
                    kind, title, due, who, rs.getInt(7), isDone ? "да" : "нет",
                    !isDone && overdue(due) ? "CRITICAL" : "NORMAL")));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        table.setModel(new DefaultTableModel(rows, new Vector<>(Arrays.asList(
            "№", "Заявка", "Тип", "Что сделать", "Срок", "Ответственный",
            "Повтор, дн.", "Готово", "tag"))) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        });
        table.getColumn("tag").setMinWidth(0);
        table.getColumn("tag").setMaxWidth(0);
        for (int i = 0; i < table.getColumnCount() - 1; i++)
            table.getColumnModel().getColumn(i).setCellRenderer(
                Theme.slaRenderer(table, table.getColumnCount() - 1));
    }

    /** Напоминание при запуске: дела со сроком на сегодня и просроченные. */
    static void remind() {
        String list = due();
        if (list == null) return;
        JOptionPane.showMessageDialog(null, list, "Напоминание: дела по сроку",
            JOptionPane.PLAIN_MESSAGE);
        try {
            if (!Settings.get("smtp_host").trim().isEmpty())
                Export.smtp("Сервис центр PSR: дела по сроку", list);
        } catch (Exception ignored) { }
    }

    /** Строка напоминания или null, если дел по сроку нет. */
    static String due() {
        StringBuilder sb = new StringBuilder();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT repair_id, kind, title, due, who FROM tasks"
                 + " WHERE done=0 ORDER BY due, id")) {
            while (rs.next()) {
                String due = nz(rs.getString(4));
                if (due.isEmpty() || !overdue(due)) continue;
                long rep = rs.getLong(1);
                sb.append(due).append("  ").append(rs.getString(2)).append(" «")
                  .append(rs.getString(3)).append("»")
                  .append(rep == 0 ? "" : "  — заявка №" + rep)
                  .append("  · ").append(nz(rs.getString(5))).append('\n');
            }
        } catch (Exception ignored) { }
        return sb.length() == 0 ? null : "Просрочены или на сегодня:\n" + sb;
    }

    static boolean overdue(String due) {
        try { return day(due) < today(); } catch (Exception e) { return false; }
    }

    static long day(String s) throws Exception {
        return new SimpleDateFormat("dd.MM.yyyy").parse(s.trim()).getTime();
    }

    static String dateText() {
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date());
    }

    /** Полночь сегодняшнего дня — порог «срок наступил». */
    static long today() {
        try { return day(dateText()); }
        catch (Exception e) { return 0; }
    }

    private static long num(String s) {
        try { return (long) Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private Tasks() { }
}
