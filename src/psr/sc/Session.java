package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.JFrame;
import javax.swing.JPasswordField;

/** Текущая сессия: кто работает (PIN), его роль, личные права и блокировка на отпуск. */
public final class Session {
    public static String role = "ADMIN";
    public static String name = "Администратор";
    /** Личные права поверх роли и личные запреты — строки разделов, «раздел+» значит «можно править». */
    public static String allow = "";
    public static String deny = "";

    public static boolean isAdmin() { return "ADMIN".equals(role); }

    /** Раздел открыт сотруднику: роль плюс личное, минус личное «запрещено». */
    public static boolean may(String section) {
        if (Roles.bare(deny, section)) return false;
        return Roles.may(role, section) || Roles.bare(allow, section) || Roles.plus(allow, section);
    }

    /** Раздел открыт и разрешено менять в нём данные. */
    public static boolean edits(String section) {
        if (Roles.bare(deny, section) || Roles.plus(deny, section)) return false;
        return Roles.edits(role, section) || Roles.plus(allow, section);
    }

    /** Название текущей роли для шапки и подписей. */
    public static String roleTitle() { return Roles.title(role); }

    /** Первый запуск — нет ни одного пользователя: просим задать PIN администратора. */
    public static void login(JFrame owner) throws Exception {
        boolean hasUsers;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            hasUsers = rs.next() && rs.getLong(1) > 0;
        }
        if (!hasUsers) {
            String pin = ask(owner, "Первый запуск. Задайте PIN администратора (4–8 цифр):");
            if (pin == null || pin.trim().isEmpty()) pin = "1111";
            Db.exec("INSERT INTO users(pin,name,role,created) VALUES('" + Db.esc(pin.trim())
                + "','Администратор','ADMIN','" + RepairsPanel.stamp() + "')");
            role = "ADMIN"; name = "Администратор";
            return;
        }
        while (true) {
            String pin = ask(owner, "Вход: введите PIN:");
            if (pin == null) pin = "";
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT name, role, allow, deny, blocked_until,"
                     + " block_note FROM users WHERE pin='" + Db.esc(pin.trim()) + "'")) {
                if (rs.next()) {
                    String blocked = rs.getString(5);
                    if (Users.blocked(blocked)) {
                        Warn.show("Сотрудник «" + rs.getString(1) + "» недоступен до " + blocked
                            + " включительно" + (nz(rs.getString(6)).isEmpty()
                                ? "" : " (" + rs.getString(6) + ")")
                            + ".\nВойдите под PIN другого сотрудника.");
                        continue;
                    }
                    name = rs.getString(1); role = rs.getString(2);
                    allow = nz(rs.getString(3)); deny = nz(rs.getString(4));
                    Users.currentPin = pin.trim();
                    return;
                }
            }
            Warn.show("Неверный PIN");
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String ask(JFrame owner, String q) {
        JPasswordField pf = new JPasswordField();
        pf.setFont(Theme.fontB(22));
        int ok = javax.swing.JOptionPane.showConfirmDialog(owner, pf, q,
            javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        return ok == javax.swing.JOptionPane.OK_OPTION ? new String(pf.getPassword()) : null;
    }

    private Session() { }
}
