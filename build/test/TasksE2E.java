package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Проверка дел: напоминание по сроку, таблица, закрытие дела и след в истории заявки. */
public class TasksE2E {

    static int bad;

    public static void main(String[] args) throws Exception {
        Db.exec("DELETE FROM tasks WHERE title LIKE 'ZZtest%'");
        Db.exec("DELETE FROM repair_history WHERE info LIKE 'ZZtest%'");
        String yesterday = date(-1), tomorrow = date(1);
        Db.exec("INSERT INTO tasks(repair_id,kind,title,due,done,who,created,repeat_days)"
            + " VALUES(1,'Звонок','ZZtest позвонить','" + yesterday + "',0,'Кузнецов','"
            + x() + "',0)");
        Db.exec("INSERT INTO tasks(repair_id,kind,title,due,done,who,created,repeat_days)"
            + " VALUES(2,'Задача','ZZtest потом','" + tomorrow + "',0,'Смирнов','" + x() + "',0)");

        String due = Tasks.due();
        check("напоминание нашло просроченное", due != null && due.contains("ZZtest позвонить"));
        check("напоминание указало заявку", due != null && due.contains("заявка №1"));
        check("будущее дело не всплывает", due != null && !due.contains("ZZtest потом"));
        check("пустого списка нет", Tasks.due() != null);

        final JTable t = new JTable();
        final FilterBar bar = new FilterBar("ДелаТест", () -> { });
        SwingUtilities.invokeAndWait(() -> Tasks.load(t, bar, null, null));
        check("в окне все дела", t.getRowCount() == 2);
        SwingUtilities.invokeAndWait(() -> Tasks.load(t, bar, null, 1L));
        check("дела по заявке №1", t.getRowCount() == 1
            && t.getValueAt(0, 3).equals("ZZtest позвонить"));
        bar.clear();
        SwingUtilities.invokeAndWait(() -> Tasks.load(t, bar, null, null));
        check("фильтр пустой — снова 2", t.getRowCount() == 2);
        query(t, bar, "позвонить");
        check("фильтр по слову", t.getRowCount() == 1);
        query(t, bar, "ZZtest звонок");
        check("два слова (И)", t.getRowCount() == 1);
        query(t, bar, "нет-такого");
        check("нет совпадений", t.getRowCount() == 0);

        long id = taskId("ZZtest позвонить");
        check("закрытие дела", Tasks.done(id));
        check("отметка done=1", getInt("SELECT done FROM tasks WHERE id=" + id) == 1);
        check("след в истории заявки", count("SELECT COUNT(*) FROM repair_history"
            + " WHERE repair_id=1 AND info LIKE '%ZZtest позвонить%'") == 1);
        check("напоминание исчезло", Tasks.due() == null);

        Db.exec("DELETE FROM tasks WHERE title LIKE 'ZZtest%'");
        Db.exec("DELETE FROM repair_history WHERE info LIKE '%ZZtest%'");
        System.out.println(bad == 0 ? "OK: дела работают" : "ОШИБОК: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    static void query(JTable t, FilterBar bar, String q) throws Exception {
        java.lang.reflect.Field f = FilterBar.class.getDeclaredField("text");
        f.setAccessible(true);
        ((javax.swing.JTextField) f.get(bar)).setText(q);
        SwingUtilities.invokeAndWait(() -> Tasks.load(t, bar, null, null));
    }

    static long taskId(String title) throws Exception {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM tasks WHERE title='" + Db.esc(title) + "'")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    static int getInt(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    static int count(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    static String date(int shift) {
        return new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(
            System.currentTimeMillis() + shift * 86_400_000L));
    }

    static String x() { return RepairsPanel.stamp(); }

    static void check(String what, boolean ok) {
        if (!ok) { bad++; System.out.println("ПРОВАЛ: " + what); }
        else System.out.println("ok    : " + what);
    }
}
