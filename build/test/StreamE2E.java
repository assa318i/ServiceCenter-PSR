package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Проверка ленты заявки: виды записей, автор, фильтр, @упоминание в чат, подписка «слежу». */
public class StreamE2E {

    static int bad;
    /** Заявка только для теста ленты: своя, без прежних записей истории. */
    static final long ID = 900099;

    public static void main(String[] args) throws Exception {
        clean();

        Stream.post(ID, "ZZлента реплика", Stream.POST);
        Stream.post(ID, "ZZлента пометка", Stream.NOTE);
        Stream.log(ID, "ZZлента изменение");
        check("реплика подписана автором", count("SELECT COUNT(*) FROM repair_history WHERE repair_id="
            + ID + " AND info='ZZлента реплика' AND who='" + Db.esc(Session.name) + "' AND kind='POST'") == 1);
        check("пометка — отдельный вид", count("SELECT COUNT(*) FROM repair_history"
            + " WHERE info='ZZлента пометка' AND kind='NOTE'") == 1);
        check("служебная запись = изменение", count("SELECT COUNT(*) FROM repair_history"
            + " WHERE info='ZZлента изменение' AND kind='UPDATE'") == 1);

        final JTable t = new JTable();
        load(t, ID, "Всё");
        check("лента заявки содержит три записи", t.getRowCount() == 3);
        check("в ленте виден автор", Session.name.equals(t.getValueAt(0, 2)));
        load(t, ID, "Реплики");
        check("фильтр: только реплики", t.getRowCount() == 1 && "Реплика".equals(t.getValueAt(0, 3)));
        load(t, ID, "Пометки");
        check("фильтр: только пометки", t.getRowCount() == 1 && "Пометка".equals(t.getValueAt(0, 3)));
        load(t, ID, "Изменения");
        check("фильтр: только изменения", t.getRowCount() == 1 && "Изменение".equals(t.getValueAt(0, 3)));
        load(t, ID + 1, "Всё");
        check("у чужой заявки пусто", t.getRowCount() == 0);

        Db.exec("INSERT INTO users(pin,name,role,created) VALUES('990001','ZZтест','ENGINEER','"
            + RepairsPanel.stamp() + "')");
        int n = Stream.mention(ID, "ZZлента @ZZтест и ещё @zzтест и @Никто");
        check("упоминание посчитано один раз", n == 1);
        check("упоминание ушло в чат", count("SELECT COUNT(*) FROM chat"
            + " WHERE text LIKE '!!! ZZтест%' AND text LIKE '%ZZлента%'") == 1);

        check("без подписки напоминаний нет", Stream.sinceText() == null);
        Stream.setWatch(ID, true);
        check("подписка оформлена", Stream.watched(ID));
        check("прочитанное не всплывает", Stream.sinceText() == null);
        Stream.post(ID, "ZZлента новое", Stream.POST);
        String s = Stream.sinceText();
        check("новая запись замечена", s != null && s.contains("Заявка №" + ID));
        check("после прочтения чисто", Stream.sinceText() == null);
        Stream.setWatch(ID, false);
        check("отписка", !Stream.watched(ID));

        clean();
        System.out.println(bad == 0 ? "OK: лента заявки работает" : "ОШИБОК: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    static void load(final JTable t, final long id, final String filter) throws Exception {
        SwingUtilities.invokeAndWait(() -> Stream.load(t, id, filter));
    }

    static void clean() throws Exception {
        Db.exec("DELETE FROM repair_history WHERE info LIKE 'ZZлента%' OR repair_id=" + ID);
        Db.exec("DELETE FROM chat WHERE text LIKE '!!! ZZтест%'");
        Db.exec("DELETE FROM users WHERE pin='990001'");
        Db.exec("DELETE FROM watch WHERE repair_id=" + ID);
    }

    static int count(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    static void check(String what, boolean ok) {
        if (!ok) { bad++; System.out.println("ПРОВАЛ: " + what); }
        else System.out.println("ok    : " + what);
    }
}
