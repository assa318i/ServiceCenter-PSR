package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Сотрудники: учётная запись (PIN), роль, личные права поверх роли и блокировка на время
 * отпуска или отгула. Логика отделена от окна, чтобы её можно было проверять тестом без Swing.
 */
public final class Users {

    /** Причины блокировки — подсказка админу, почему сотрудник недоступен. */
    public static final String[] REASONS = { "отпуск", "отгул", "больничный", "командировка", "обучение" };

    public static final class Row {
        public final long id;
        public final String pin, name, role, allow, deny, blocked, note;

        Row(long id, String pin, String name, String role, String allow, String deny,
            String blocked, String note) {
            this.id = id; this.pin = pin; this.name = name; this.role = role;
            this.allow = allow; this.deny = deny; this.blocked = blocked; this.note = note;
        }

        public boolean locked() { return Users.blocked(blocked); }

        public String status() {
            if (blocked.trim().isEmpty()) return "доступен";
            String reason = note.trim().isEmpty() ? "" : " · " + note.trim();
            return Users.blocked(blocked) ? "заблокирован до " + blocked + reason
                : "срок вышел " + blocked;
        }
    }

    public static List<Row> all() throws Exception {
        List<Row> out = new ArrayList<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, pin, name, role, allow, deny,"
                 + " blocked_until, block_note FROM users ORDER BY name, id")) {
            while (rs.next()) out.add(new Row(rs.getLong(1), nz(rs.getString(2)), nz(rs.getString(3)),
                nz(rs.getString(4)), nz(rs.getString(5)), nz(rs.getString(6)), nz(rs.getString(7)),
                nz(rs.getString(8))));
        }
        return out;
    }

    public static Row find(long id) throws Exception {
        for (Row r : all()) if (r.id == id) return r;
        return null;
    }

    /** true — блокировка ещё действует; дата в поле «до» считается включительно. */
    public static boolean blocked(String until) {
        if (until == null || until.trim().isEmpty()) return false;
        try { return Tasks.day(until) >= Tasks.today(); }
        catch (Exception unreadableDate) { return true; }  // дата повреждена — доступ не открываем
    }

    public static long add(String pin, String name, String roleTitle) throws Exception {
        pin = nz(pin).trim();
        if (!pin.matches("\\d{4,8}")) throw new Exception("PIN — от 4 до 8 цифр, без пробелов и букв");
        if (scalar("SELECT COUNT(*) FROM users WHERE pin='" + Db.esc(pin) + "'") > 0)
            throw new Exception("Такой PIN уже есть у другого сотрудника — придумайте другой");
        name = nz(name).trim();
        if (name.isEmpty())
            throw new Exception("Нужно имя сотрудника: оно записывается в заявки, ленту и дела");
        Db.exec("INSERT INTO users(pin,name,role,created) VALUES('" + Db.esc(pin) + "','"
            + Db.esc(name) + "','" + Roles.codeOf(roleTitle) + "','" + RepairsPanel.stamp() + "')");
        return scalar("SELECT MAX(id) FROM users");
    }

    public static void setRole(long id, String roleTitle) throws Exception {
        String code = Roles.codeOf(roleTitle);
        Row r = need(id);
        if ("ADMIN".equals(r.role) && !"ADMIN".equals(code) && admins() <= 1)
            throw new Exception("Это единственный администратор — без него некому управлять"
                + " сотрудниками и настройками");
        Db.exec("UPDATE users SET role='" + code + "' WHERE id=" + id);
    }

    /** Личные права: что разрешено сверх роли и что запрещено даже если разрешила роль. */
    public static void setAccess(long id, String allow, String deny) throws Exception {
        need(id);
        Db.exec("UPDATE users SET allow='" + Db.esc(clean(allow)) + "', deny='" + Db.esc(clean(deny))
            + "' WHERE id=" + id);
    }

    public static void block(long id, String until, String reason) throws Exception {
        need(id);
        until = nz(until).trim().isEmpty() ? Tasks.dateText() : until.trim();
        try { Tasks.day(until); }
        catch (Exception bad) {
            throw new Exception("Дата в формате дд.мм.гггг, например " + Tasks.dateText());
        }
        String why = nz(reason).trim();
        Db.exec("UPDATE users SET blocked_until='" + Db.esc(until) + "', block_note='"
            + Db.esc(why) + "' WHERE id=" + id);
    }

    public static void unblock(long id) throws Exception {
        need(id);
        Db.exec("UPDATE users SET blocked_until='', block_note='' WHERE id=" + id);
    }

    public static void remove(long id) throws Exception {
        Row r = need(id);
        if ("ADMIN".equals(r.role) && admins() <= 1)
            throw new Exception("Нельзя удалить единственного администратора — сначала"
                + " сделайте администратором ещё кого-нибудь");
        Db.exec("DELETE FROM users WHERE id=" + id);
    }

    private static Row need(long id) throws Exception {
        Row r = find(id);
        if (r == null) throw new Exception("Сотрудник не выбран или уже удалён — обновите список");
        return r;
    }

    /** Проверяет, редактирует ли админ самого себя: права применятся только после нового входа. */
    public static boolean isSelf(Row r) { return r != null && r.pin.equals(currentPin); }

    /** PIN вошедшего сотрудника — чтобы окно предупреждало, что изменения коснутся после входа. */
    public static String currentPin = "";

    private static long admins() throws Exception {
        return scalar("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
    }

    private static long scalar(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Список разделов через запятую без пустых записей и лишних пробелов. */
    private static String clean(String spec) {
        StringBuilder sb = new StringBuilder();
        for (String s : nz(spec).split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private Users() { }
}
