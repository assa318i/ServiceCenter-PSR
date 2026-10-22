package psr.sc;

import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Files;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Окно настроек: название, логотип на все окна, ИНН, адрес, почта, целевой срок ремонта. */
public class SettingsPanel extends JPanel {

    private final App app;
    private final JTextField name = f(Settings.get("name"));
    private final JTextField inn = f(Settings.get("inn"));
    private final JTextField address = f(Settings.get("address"));
    private final JTextField mail = f(Settings.get("mail"));
    private final JTextField slaDays = f(Settings.get("sla_days"));
    private final JTextField smtpHost = f(Settings.get("smtp_host"));
    private final JTextField smtpPort = f(Settings.get("smtp_port"));
    private final JTextField smtpUser = f(Settings.get("smtp_user"));
    private final javax.swing.JPasswordField smtpPass =
        new javax.swing.JPasswordField(Settings.get("smtp_pass"), 24);
    private final JTextField smtpTo = f(Settings.get("smtp_to"));
    private final JTextField cloudPath = f(Settings.get("cloud_path"));
    private final JLabel logoPath = new JLabel(Settings.get("logo"));

    public SettingsPanel(App app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        add(Settings.headerBar(16), BorderLayout.NORTH);

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setBackground(Theme.GRAY_BG);
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.insets = new java.awt.Insets(6, 6, 6, 6);
        g.fill = java.awt.GridBagConstraints.HORIZONTAL;
        int y = 0;
        y = row(form, g, y, "Название сервисного центра:", name);
        y = row(form, g, y, "ИНН организации:", inn);
        y = row(form, g, y, "Адрес сервисного центра:", address);
        y = row(form, g, y, "Почта для актов взаиморасчётов (mailto):", mail);
        y = row(form, g, y, "Целевой срок ремонта, дней (SLA):", slaDays);
        smtpPass.setFont(Theme.font(16));
        y = row(form, g, y, "SMTP-сервер (автоотправка):", smtpHost);
        y = row(form, g, y, "SMTP порт:", smtpPort);
        y = row(form, g, y, "SMTP логин (ваша почта):", smtpUser);
        y = row(form, g, y, "SMTP пароль:", smtpPass);
        y = row(form, g, y, "Отправлять акту на адрес:", smtpTo);

        JPanel copiesRow = new JPanel(new BorderLayout(6, 0));
        copiesRow.setOpaque(false);
        copiesRow.add(cloudPath, BorderLayout.CENTER);
        copiesRow.add(Theme.on("Проверить папки копий", Theme.BLUE, e -> checkCopies()),
            BorderLayout.EAST);
        g.gridx = 0; g.gridy = y; g.weightx = 0;
        form.add(label("Папки для дополнительных копий базы (несколько — через «;»):"), g);
        g.gridx = 1; g.weightx = 1;
        form.add(copiesRow, g);
        y++;

        JPanel logoRow = new JPanel(new BorderLayout(6, 0));
        logoRow.setOpaque(false);
        logoRow.add(logoPath, BorderLayout.CENTER);
        logoRow.add(Theme.on("Выбрать логотип…", Theme.BLUE, e -> pickLogo()), BorderLayout.EAST);
        g.gridx = 0; g.gridy = y; g.weightx = 0;
        form.add(label("Логотип (показывается слева вверху каждого окна):"), g);
        g.gridx = 1; g.weightx = 1;
        form.add(logoRow, g);
        y++;

        g.gridx = 0; g.gridwidth = 2; g.gridy = y; g.fill = java.awt.GridBagConstraints.NONE;
        JPanel b = new JPanel();
        b.setOpaque(false);
        b.add(Theme.on("Сохранить настройки", Theme.YELLOW, e -> save()));
        b.add(Theme.on("Тест SMTP", Theme.BLUE, e -> testSmtp()));
        b.add(Theme.on("Пользователи (PIN)", Theme.BLUE, e -> manageUsers()));
        form.add(b, g);
        add(form, BorderLayout.CENTER);
    }

    /** Админ задаёт PIN инженеров и администраторов. */
    private void manageUsers() {
        for (;;) {
            StringBuilder sb = new StringBuilder("Пользователи:\n");
            java.util.Vector<String> ids = new java.util.Vector<>();
            try (java.sql.Statement st = Db.get().createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT id, pin, name, role FROM users")) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                    sb.append(rs.getString(1)).append("  PIN **" + rs.getString(2))
                        .append("  ").append(rs.getString(3)).append("  ")
                        .append("ADMIN".equals(rs.getString(4)) ? "админ" : "инженер").append('\n');
                }
            } catch (Exception ex) { Warn.show(ex.getMessage()); return; }
            String act = (String) javax.swing.JOptionPane.showInputDialog(this, sb.toString()
                + "\nДействие: add — новый пользователь, del — удалить, пусто — закрыть",
                "Пользователи", javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (act == null || act.trim().isEmpty()) return;
            try {
                if ("add".equalsIgnoreCase(act.trim())) {
                    String pin = javax.swing.JOptionPane.showInputDialog(this, "PIN (4–8 цифр):");
                    if (pin == null || pin.trim().isEmpty()) continue;
                    String name = javax.swing.JOptionPane.showInputDialog(this, "Имя:");
                    String role = (String) javax.swing.JOptionPane.showInputDialog(this, "Роль:",
                        "Роль", javax.swing.JOptionPane.QUESTION_MESSAGE, null,
                        new String[] { "инженер", "админ" }, "инженер");
                    if (role == null) continue;
                    Db.exec("INSERT INTO users(pin,name,role,created) VALUES('" + Db.esc(pin.trim())
                        + "','" + Db.esc(name == null ? "" : name) + "','"
                        + ("админ".equals(role) ? "ADMIN" : "ENGINEER") + "','"
                        + RepairsPanel.stamp() + "')");
                } else if ("del".equalsIgnoreCase(act.trim())) {
                    String id = javax.swing.JOptionPane.showInputDialog(this,
                        "ID пользователя для удаления:");
                    if (id != null && !id.trim().isEmpty())
                        Db.exec("DELETE FROM users WHERE id=" + Long.parseLong(id.trim())
                            + " AND role<>'ADMIN'");
                    else Warn.show("Администратора удалять нельзя — можно только сменить PIN");
                }
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        }
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.fontB(15));
        return l;
    }

    private int row(JPanel p, java.awt.GridBagConstraints g, int y, String text, JTextField f) {
        g.gridx = 0; g.gridy = y; g.weightx = 0; p.add(label(text), g);
        g.gridx = 1; g.weightx = 1; p.add(f, g);
        return y + 1;
    }

    private static JTextField f(String v) {
        JTextField t = new JTextField(v, 24);
        t.setFont(Theme.font(16));
        return t;
    }

    private void pickLogo() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Изображение (PNG, JPG)", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File src = fc.getSelectedFile();
            File dir = new File(Db.DATA_DIR, "branding");
            dir.mkdirs();
            File dst = new File(dir, "logo" + extension(src.getName()));
            Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logoPath.setText(dst.getPath());
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? ".png" : name.substring(i);
    }

    private void save() {
        try {
            Settings.set("name", name.getText().trim());
            Settings.set("inn", inn.getText().trim());
            Settings.set("address", address.getText().trim());
            Settings.set("mail", mail.getText().trim());
            Settings.set("sla_days", slaDays.getText().trim().isEmpty() ? "14" : slaDays.getText().trim());
            Settings.set("logo", logoPath.getText().trim());
            Settings.set("smtp_host", smtpHost.getText().trim());
            Settings.set("smtp_port", smtpPort.getText().trim());
            Settings.set("smtp_user", smtpUser.getText().trim());
            Settings.set("smtp_pass", new String(smtpPass.getPassword()));
            Settings.set("smtp_to", smtpTo.getText().trim());
            Settings.set("cloud_path", cloudPath.getText().trim());
            app.rebuildHeader();
            JOptionPane.showMessageDialog(this,
                "Настройки сохранены. Логотип и ИНН теперь на каждом окне программы.",
                "Настройки", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void testSmtp() {
        save();
        try {
            Export.smtp("Тест SMTP — " + Settings.get("name"),
                "Проверка почтовой отправки из приложения «Сервис центр от PSR».");
            JOptionPane.showMessageDialog(this, "Письмо отправлено на " + Settings.get("smtp_to"),
                "Тест SMTP", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            Warn.show("SMTP не сработал: " + ex.getMessage()
                + "\nПроверьте сервер/порт/пароль (для Gmail нужен «пароль приложения»).");
        }
    }

    /** Проверка папок из «дополнительные копии»: что доступно, что нет — до первой же резервной копии. */
    private void checkCopies() {
        JOptionPane.showMessageDialog(this, Export.checkCopies(cloudPath.getText()),
            "Папки для дополнительных копий", JOptionPane.PLAIN_MESSAGE);
    }
}
