package psr.sc;

import java.awt.Component;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Настройки сервисного центра: название, логотип, ИНН, адрес, почта для отчётов. */
public final class Settings {
    public static final Map<String, String> DEF = new LinkedHashMap<>();
    static {
        DEF.put("name", "Сервис центр от PSR");
        DEF.put("inn", "");
        DEF.put("address", "");
        DEF.put("logo", "");
        DEF.put("mail", "");
        DEF.put("sla_days", "14");
        DEF.put("smtp_host", "");
        DEF.put("smtp_port", "465");
        DEF.put("smtp_user", "");
        DEF.put("smtp_pass", "");
        DEF.put("smtp_to", "");
        DEF.put("cloud_path", "");
    }

    public static String get(String key) {
        try {
            Connection c = Db.get();
            try (PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key=?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT OR IGNORE INTO settings(key,value) VALUES(?,?)")) {
                ins.setString(1, key); ins.setString(2, DEF.get(key));
                ins.executeUpdate();
            }
        } catch (Exception ignored) { }
        return DEF.getOrDefault(key, "");
    }

    public static void set(String key, String value) throws Exception {
        try (PreparedStatement ps = Db.get().prepareStatement(
                "INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key); ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /** Шапка с логотипом (слева вверху) и названием/ИНН/адресом — на каждое окно программы. */
    public static JPanel headerBar(int bigText) {
        JPanel p = new JPanel();
        p.setBackground(Theme.GRAY_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.BLACK));
        File logo = new File(get("logo"));
        if (logo.isFile()) {
            Icon ic = new ImageIcon(getLogoPath(logo));
            p.add(new JLabel(ic));
        }
        JLabel t = new JLabel("<html><b>" + esc(get("name")) + "</b><br>"
            + "ИНН " + esc(get("inn")) + (get("address").isEmpty() ? "" : " · " + esc(get("address"))) + "</html>");
        t.setFont(Theme.font(bigText));
        t.setForeground(Theme.BLACK);
        p.add(t);
        p.add(Box.createHorizontalGlue());
        return p;
    }

    /** Подгонка размера логотипа под высоту шапки. */
    private static java.awt.Image getLogoPath(File f) {
        ImageIcon src = new ImageIcon(f.getPath());
        int h = 40;
        int w = src.getIconWidth() > 0 ? src.getIconWidth() * h / src.getIconHeight() : h;
        return src.getImage().getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
    }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;"); }

    private Settings() { }
}
