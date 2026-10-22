package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.JFrame;
import javax.swing.JPasswordField;

/** Текущая сессия: кто работает (PIN) и его роль. */
public final class Session {
    public static String role = "ADMIN";
    public static String name = "Администратор";

    public static boolean isAdmin() { return "ADMIN".equals(role); }

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
                 ResultSet rs = st.executeQuery("SELECT name, role FROM users WHERE pin='"
                     + Db.esc(pin.trim()) + "'")) {
                if (rs.next()) { name = rs.getString(1); role = rs.getString(2); return; }
            }
            Warn.show("Неверный PIN");
        }
    }

    private static String ask(JFrame owner, String q) {
        JPasswordField pf = new JPasswordField();
        pf.setFont(Theme.fontB(22));
        int ok = javax.swing.JOptionPane.showConfirmDialog(owner, pf, q,
            javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        return ok == javax.swing.JOptionPane.OK_OPTION ? new String(pf.getPassword()) : null;
    }

    private Session() { }
}
