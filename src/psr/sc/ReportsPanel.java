package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/** Отчётность: еженедельно / ежемесячно / ежеквартально / ежегодно; печать и отправка на почту. */
public class ReportsPanel extends JPanel {

    static final String[] PERIODS = { "Неделя", "Месяц", "Квартал", "Год" };
    private final JComboBox<String> period = new JComboBox<>(PERIODS);
    private final JTextPane doc = Print.pane();

    public ReportsPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Период отчётности: "));
        period.setFont(Theme.fontB(15));
        period.addActionListener(e -> build());
        tools.add(period);
        tools.add(Box.createHorizontalStrut(12));
        tools.add(Theme.on("Сформировать отчёт", Theme.YELLOW, e -> build()));
        final JComboBox<String> copies = new JComboBox<>(new String[] { "1", "2", "3" });
        tools.add(new JLabel("Экземпляров: "));
        tools.add(copies);
        tools.add(Theme.on("Печать отчёта", Theme.BLUE, e ->
            Print.print(doc, "Отчёт " + PERIODS[period.getSelectedIndex()],
                Integer.parseInt(String.valueOf(copies.getSelectedItem())))));
        tools.add(Theme.on("Акт взаиморасчётов — печать", Theme.GRAY_PANEL, e -> balance(false)));
        tools.add(Theme.on("Акт взаиморасчётов — на почту", Theme.BRIGHT_RED, e -> balance(true)));
        tools.add(Theme.on("Акт — отправить SMTP", Theme.BLUE, e -> balanceSmtp()));
        tools.add(Theme.on("Экспорт CSV (Excel)", Theme.YELLOW, e -> toCsv()));
        tools.add(Theme.on("Выгрузка XML (ЭДО/1С)", Theme.BLUE, e -> toXml()));
        tools.add(Theme.on("Резервная копия базы", Theme.GRAY_PANEL, e -> toBackup()));
        tools.add(Theme.on("Экспорт базы для Android", Theme.GRAY_PANEL, e -> export()));
        add(tools, BorderLayout.NORTH);
        add(new JScrollPane(doc), BorderLayout.CENTER);
        build();
    }

    private void balance(boolean mail) {
        Date[] range = range();
        String from = fmt(range[0]), to = fmt(range[1]);
        JTextPane d = Documents.balanceAct(from, to);
        if (mail) {
            String addr = Settings.get("mail");
            if (addr.trim().isEmpty()) { Warn.show("В Настройках не указан почтовый адрес получателя"); return; }
            Print.mail(addr, "Акт взаиморасчётов " + from + " — " + to + ", " + Settings.get("name"), d.getText());
            if (JOptionPane.showConfirmDialog(this,
                    "Почтовое приложение открыто. Отметить письмо как отправленное?",
                    "Акт взаиморасчётов", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                log("Акт взаиморасчётов " + from + " — " + to + " отправлен на " + addr);
        } else Documents.previewDoc(null, d, "Акт взаиморасчётов " + from + " — " + to);
    }

    private void balanceSmtp() {
        Date[] range = range();
        String from = fmt(range[0]), to = fmt(range[1]);
        try {
            Export.smtp("Акт взаиморасчётов " + from + " — " + to + ", " + Settings.get("name"),
                Documents.balanceAct(from, to).getText());
            JOptionPane.showMessageDialog(this, "Акт отправлен на " + Settings.get("smtp_to"),
                "SMTP", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            Warn.show("SMTP не сработал: " + ex.getMessage()
                + "\nЗаполните SMTP в Настройках или используйте кнопку «на почту».");
        }
    }

    private void toCsv() {
        try {
            Export.allCsv();
            JOptionPane.showMessageDialog(this,
                "CSV готовы в " + new java.io.File(Db.DATA_DIR, "export").getAbsolutePath()
                + ":\nzakazchiki.csv, remonty.csv, sklad.csv, zhurnal-sklada.csv, kartridzhi.csv",
                "Экспорт CSV", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void toXml() {
        Date[] r = range();
        try {
            java.io.File f = Export.xml(fmt(r[0]), fmt(r[1]));
            JOptionPane.showMessageDialog(this, "XML для ЭДО/1С выгружен:\n" + f.getAbsolutePath()
                + "\nПериод: " + fmt(r[0]) + " — " + fmt(r[1]),
                "Выгрузка XML", JOptionPane.PLAIN_MESSAGE);
            log("Выгрузка XML (ЭДО): " + f.getName());
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void toBackup() {
        try {
            java.io.File f = Export.backup();
            JOptionPane.showMessageDialog(this, "Копия: " + f.getAbsolutePath()
                + Export.lastCopies, "Резервная копия", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void export() {
        try {
            java.io.File f = Print.exportDb();
            JOptionPane.showMessageDialog(this, "База экспортирована для Android-версии:\n"
                + f.getAbsolutePath(), "Экспорт", JOptionPane.PLAIN_MESSAGE);
            log("Экспорт базы для Android: " + f.getName());
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    /** Границы выбранного периода: [начало, конец]. */
    private Date[] range() {
        Calendar c = Calendar.getInstance();
        Date now = c.getTime();
        switch (period.getSelectedIndex()) {
            case 0: c.add(Calendar.DAY_OF_YEAR, -7); break;
            case 1: c.add(Calendar.MONTH, -1); break;
            case 2: c.add(Calendar.MONTH, -3); break;
            default: c.add(Calendar.YEAR, -1); break;
        }
        return new Date[] { c.getTime(), now };
    }

    static String fmt(Date d) { return new SimpleDateFormat("dd.MM.yyyy").format(d); }

    static String iso(Date d) { return new SimpleDateFormat("yyyyMMdd").format(d); }

    void build() {
        doc.setText("");
        Date[] r = range();
        String sqlFrom = iso(r[0]), sqlTo = iso(r[1]);
        Print.addLine(doc, "ОТЧЁТ — " + PERIODS[period.getSelectedIndex()].toUpperCase()
            + " за " + fmt(r[0]) + " — " + fmt(r[1]), false, true);
        Print.addLine(doc, Settings.get("name") + " · ИНН " + Settings.get("inn")
            + " · " + Settings.get("address"), false, false);
        Print.addLine(doc, " ", false, false);
        String where = "WHERE substr(opened,7,4)||substr(opened,4,2)||substr(opened,1,2)"
            + " BETWEEN '" + sqlFrom + "' AND '" + sqlTo + "'";
        try (Statement st = Db.get().createStatement()) {
            Print.addLine(doc, "— Заявки и ремонты —", false, true);
            for (int i = 0; i < RepairsPanel.STATUS.length; i++) {
                ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(price),0) FROM repairs "
                    + where + " AND status='" + RepairsPanel.STATUS[i] + "'");
                rs.next();
                long n = rs.getLong(1);
                double sum = rs.getDouble(2);
                rs.close();
                boolean bad = "WAIT_PARTS".equals(RepairsPanel.STATUS[i]) && n > 0;
                Print.addLine(doc, RepairsPanel.STATUS_RU[i] + ": " + n + " шт., "
                    + String.format("%,.2f руб.", sum), bad, false);
            }
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM repairs " + where
                + " AND sla IN ('Внимание','Опасно')");
            rs.next();
            long sla = rs.getLong(1);
            rs.close();
            Print.addLine(doc, "Заявок с повышенным/опасным Service Level: " + sla, sla > 0, false);
            rs = st.executeQuery("SELECT COUNT(*) FROM repairs " + where
                + " AND status='WAIT_PARTS' AND wait_until<>''"
                + " AND substr(wait_until,7,4)||substr(wait_until,4,2)||substr(wait_until,1,2) < "
                + iso(new Date()));
            rs.next();
            long late = rs.getLong(1);
            rs.close();
            if (late > 0)
                Print.addLine(doc, "ПРОСРОЧЕНО ожидание запчастей: " + late
                    + " заявок — принять меры!", true, false);

            Print.addLine(doc, " ", false, false);
            Print.addLine(doc, "— Склад —", false, true);
            rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(o.qty*i.price),0) FROM stock_orders o"
                + " LEFT JOIN items i ON i.id=o.item_id WHERE substr(o.created,7,4)"
                + " ||substr(o.created,4,2)||substr(o.created,1,2) BETWEEN '" + sqlFrom + "' AND '" + sqlTo + "'");
            rs.next();
            Print.addLine(doc, "Заказов на склад: " + rs.getLong(1) + ", сумма "
                + String.format("%,.2f руб.", rs.getDouble(2)), false, false);
            rs.close();
            rs = st.executeQuery("SELECT COUNT(*) FROM items WHERE qty < min_qty");
            rs.next();
            long below = rs.getLong(1);
            rs.close();
            Print.addLine(doc, "Позиций ниже минимальной нормы (пополнить): " + below, below > 0, false);

            Print.addLine(doc, " ", false, false);
            Print.addLine(doc, "— Заказчики —", false, true);
            rs = st.executeQuery("SELECT COUNT(DISTINCT customer_id) FROM repairs " + where);
            rs.next();
            Print.addLine(doc, "Заказчиков с ремонтами за период: " + rs.getLong(1), false, false);
            rs.close();
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void log(String info) {
        try {
            Db.exec("INSERT INTO repair_history(repair_id,ts,info) VALUES(0,'"
                + RepairsPanel.stamp() + "','ОТЧЁТ: " + Db.esc(info) + "')");
        } catch (Exception ignored) { }
    }
}
