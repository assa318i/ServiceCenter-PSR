package psr.sc;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/** Экспорт в CSV, резервные копии, перенос базы в папку CloudDisk и SMTP-отправка. */
public final class Export {

    /** CSV с BOM и «;» — Excel в Windows открывает без танцев с кодировкой. */
    public static File csv(String name, String sql, String[] cols) throws Exception {
        File dir = new File(Db.DATA_DIR, "export");
        dir.mkdirs();
        File out = new File(dir, name + ".csv");
        try (PrintWriter w = new PrintWriter(out, "UTF-8");
             Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            w.write('\uFEFF');
            w.println(String.join(";", cols));
            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= cols.length; i++) {
                    if (i > 1) line.append(';');
                    String v = rs.getString(i);
                    v = v == null ? "" : v.replace(";", ",");
                    line.append('"').append(v).append('"');
                }
                w.println(line);
            }
        }
        return out;
    }

    public static void allCsv() throws Exception {
        csv("zakazchiki", "SELECT org, fio, phone, address, contact_phone, attorney, driver,"
            + " finance_manager, payment_method, payment_date, prepay, prepay_date FROM customers"
            + " ORDER BY org", new String[] { "Организация", "ФИО", "Телефон", "Адрес",
            "Контактный тел.", "Доверенное лицо", "Водитель", "Финменеджер", "Способ оплаты",
            "Дата оплаты", "Предоплата", "Дата предоплаты" });
        csv("remonty", "SELECT id, opened, status, sla, wait_until, issued, equipment, serial,"
            + " problem, engineer, price, warranty_days FROM repairs ORDER BY id",
            new String[] { "№", "Открыта", "Статус", "Service Level", "Ожидание до", "Выдан",
            "Оборудование", "Серийный №", "Неисправность", "Инженер", "Цена", "Гарантия дн." });
        csv("sklad", "SELECT article, name, grp, unit, qty, min_qty, price, barcode FROM items"
            + " ORDER BY name", new String[] { "Артикул", "Наименование", "Группа", "Ед.",
            "Остаток", "Мин. норма", "Цена", "Штрихкод" });
        csv("zhurnal-sklada", "SELECT o.ts, COALESCE(i.name,'позиция удалена'), o.op, o.amount,"
            + " o.repair_id, o.who, o.note FROM stock_ops o LEFT JOIN items i ON i.id=o.item_id"
            + " ORDER BY o.id", new String[] { "Дата", "Позиция", "Операция", "Количество",
            "Заявка", "Кто", "Примечание" });
    }

    /** Опись последних дополнительных копий: «\nСкопировано: …\nНедоступны: …» — для окна «Резервная копия». */
    public static String lastCopies = "";

    /**
     * Резервная копия базы в data\backups; затем — во все папки из настроек (настройки →
     * «Папки для дополнительных копий», несколько путей через «;»: второй диск, флешка,
     * сетевая папка, синхронизированная папка облака).
     */
    public static File backup() throws Exception {
        File dir = new File(Db.DATA_DIR, "backups");
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        File src = new File(Db.DATA_DIR, "psr.db");
        File out = new File(dir, "psr_" + stamp + ".db");
        Files.copy(src.toPath(), out.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        StringBuilder ok = new StringBuilder(), bad = new StringBuilder();
        for (String p : Settings.get("cloud_path").split(";")) {
            String path = p.trim();
            if (path.isEmpty()) continue;
            File to = new File(path);
            if (!to.isDirectory()) { append(bad, path); continue; }
            try {
                Files.copy(src.toPath(), new File(to, "psr_" + stamp + ".db").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.copy(src.toPath(), new File(to, "psr.db").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                append(ok, path);
            } catch (Exception e) { append(bad, path); }
        }
        lastCopies = (ok.length() == 0 ? "" : "\nСкопировано в:\n" + ok)
            + (bad.length() == 0 ? "" : "\nПапки недоступны (проверьте путь и права):\n" + bad);
        return out;
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }

    /**
     * Установщик может предложить папки для копий (data\backup-dirs.txt, по одной в строке).
     * Если в настройках ещё ничего не задано — принять предложенное и файл убрать.
     */
    static void seedCopies() {
        File f = new File(Db.DATA_DIR, "backup-dirs.txt");
        if (!f.isFile() || !Settings.get("cloud_path").trim().isEmpty()) return;
        try {
            byte[] raw = Files.readAllBytes(f.toPath());
            String all = new String(raw, "UTF-8");
            // установщик пишет файл в системной кодировке (ANSI), Блокнот сохраняет UTF-8
            if (all.indexOf('\uFFFD') >= 0) all = new String(raw, "windows-1251");
            if (all.length() > 0 && all.charAt(0) == '\uFEFF') all = all.substring(1);
            StringBuilder sb = new StringBuilder();
            for (String line : all.split("\\r?\\n")) {
                String p = line.trim();
                if (p.isEmpty() || p.startsWith("#")) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append(p);
            }
            if (sb.length() == 0) return;
            Settings.set("cloud_path", sb.toString());
            f.delete();
        } catch (Exception ignored) { }
    }

    /** Пути из настройки «cloud_path», у которых уже посчитано состояние — для проверки в Настройках. */
    public static String checkCopies(String paths) {
        StringBuilder ok = new StringBuilder(), bad = new StringBuilder();
        for (String p : paths.split(";")) {
            String path = p.trim();
            if (path.isEmpty()) continue;
            append(new File(path).isDirectory() ? ok : bad, path);
        }
        if (ok.length() == 0 && bad.length() == 0) return "Папки для копий не заданы — копия лежит только в data\\backups.";
        return (ok.length() == 0 ? "" : "Доступны (" + count(ok) + "):\n" + ok)
            + (bad.length() == 0 ? "" : "\nНе найдены (" + count(bad) + "):\n" + bad);
    }

    private static int count(StringBuilder sb) { return sb.toString().split("\n").length; }


    /** Автоотправка письма через SMTP из настроек. */
    public static void smtp(String subject, String body) throws Exception {
        String host = Settings.get("smtp_host").trim();
        if (host.isEmpty()) throw new Exception("В Настройках не заполнен SMTP-сервер");
        Properties pr = new Properties();
        pr.put("mail.smtp.host", host);
        pr.put("mail.smtp.port", Settings.get("smtp_port").trim().isEmpty() ? "465"
            : Settings.get("smtp_port").trim());
        pr.put("mail.smtp.ssl.enable", "true");
        pr.put("mail.smtp.auth", "true");
        String user = Settings.get("smtp_user").trim();
        javax.mail.Session s = javax.mail.Session.getInstance(pr,
            new javax.mail.Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, Settings.get("smtp_pass"));
                }
            });
        MimeMessage m = new MimeMessage(s);
        m.setFrom(new InternetAddress(user));
        m.setRecipients(Message.RecipientType.TO,
            InternetAddress.parse(Settings.get("smtp_to").trim()));
        m.setSubject(subject, "UTF-8");
        m.setText(body, "UTF-8");
        Transport.send(m);
    }

    /** Универсальная выгрузка XML для любого ЭДО/1С за период (дд.мм.гггг). */
    public static File xml(String from, String to) throws Exception {
        File dir = new File(Db.DATA_DIR, "export");
        dir.mkdirs();
        File out = new File(dir, "edo_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".xml");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<PSRServiceCenter Generated=\"")
          .append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())).append("\">\n");
        sb.append("  <Requisites><Name>").append(x(Settings.get("name")))
          .append("</Name><INN>").append(x(Settings.get("inn")))
          .append("</INN><Address>").append(x(Settings.get("address"))).append("</Address></Requisites>\n");
        sb.append("  <Period From=\"").append(x(from)).append("\" To=\"").append(x(to)).append("\"/>\n");
        String dateSql = "substr(opened,7,4)||substr(opened,4,2)||substr(opened,1,2) BETWEEN '"
            + Documents.toSqlDate(from) + "' AND '" + Documents.toSqlDate(to) + "'";
        try (Statement st = Db.get().createStatement()) {
            sb.append("  <Customers>\n");
            dump(st.executeQuery("SELECT * FROM customers ORDER BY id"), "Customer", sb);
            sb.append("  </Customers>\n  <Repairs>\n");
            dump(st.executeQuery("SELECT * FROM repairs WHERE " + dateSql + " ORDER BY id"), "Repair", sb);
            sb.append("  </Repairs>\n  <Payments>\n");
            dump(st.executeQuery("SELECT * FROM payments WHERE"
                + " substr(ts,7,4)||substr(ts,4,2)||substr(ts,1,2) BETWEEN '"
                + Documents.toSqlDate(from) + "' AND '" + Documents.toSqlDate(to) + "' ORDER BY id"),
                "Payment", sb);
            sb.append("  </Payments>\n");
            ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(price),0) FROM repairs WHERE " + dateSql);
            rs.next();
            sb.append("  <Totals RepairCount=\"").append(rs.getLong(1))
              .append("\" RepairSum=\"").append(rs.getDouble(2)).append("\"/>\n");
            rs.close();
        }
        sb.append("</PSRServiceCenter>\n");
        Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return out;
    }

    private static void dump(ResultSet rs, String tag, StringBuilder sb) throws Exception {
        java.sql.ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            sb.append("    <").append(tag).append(">\n");
            for (int i = 1; i <= md.getColumnCount(); i++)
                sb.append("      <").append(md.getColumnName(i)).append(">")
                  .append(x(rs.getString(i))).append("</").append(md.getColumnName(i)).append(">\n");
            sb.append("    </").append(tag).append(">\n");
        }
    }

    private static String x(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private Export() { }
}
