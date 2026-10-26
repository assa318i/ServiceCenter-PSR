package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/** Формирование печатных документов поверх данных базы. */
public final class Documents {

    private static void header(JTextPane p, String docName) {
        Print.addLine(p, Settings.get("name"), true, true);
        Print.addLine(p, "ИНН " + Settings.get("inn") + "   " + Settings.get("address"), false, false);
        Print.addLine(p, docName, false, true);
        Print.addLine(p, "Дата формирования: " + new SimpleDateFormat("dd.MM.yyyy").format(new Date()), false, false);
        Print.addLine(p, " ", false, false);
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    /** Акт о выполненных работах по ремонту repairId для заказчика customerId. */
    public static void act(JFrame owner, long customerId, long repairId) {
        JTextPane p = Print.pane();
        header(p, "АКТ о выполненных работах №" + repairId);
        try {
            String line = "";
            try (Statement st = Db.get().createStatement();
                 ResultSet c = st.executeQuery("SELECT * FROM customers WHERE id=" + customerId)) {
                if (c.next()) {
                    line += "Заказчик: " + c.getString("org") + ", " + c.getString("fio") + "\n";
                    line += "Адрес: " + c.getString("address") + "   Тел.: " + c.getString("phone") + "\n";
                    if (!empty(c.getString("attorney")))
                        line += "Лицо с доверенностью: " + c.getString("attorney") + "\n";
                    if (!empty(c.getString("driver")))
                        line += "Водитель: " + c.getString("driver") + "\n";
                    if (!empty(c.getString("finance_manager")))
                        line += "Финансовый менеджер: " + c.getString("finance_manager") + "\n";
                    line += "\nСпособ оплаты: " + RepairsPanel.nz(c.getString("payment_method"))
                        + "   Дата оплаты: " + RepairsPanel.nz(c.getString("payment_date")) + "\n";
                    if (c.getDouble("prepay") > 0)
                        line += "Предоплата получена " + c.getString("prepay_date") + " в размере "
                            + String.format("%,.2f руб.", c.getDouble("prepay")) + "\n";
                }
            }
            try (Statement st = Db.get().createStatement();
                 ResultSet r = st.executeQuery("SELECT * FROM repairs WHERE id=" + repairId)) {
                if (r.next()) {
                    line += " \nВыполнены работы по ремонту: " + r.getString("equipment") + "\n";
                    line += "Заявленная неисправность: " + r.getString("problem") + "\n";
                    line += "Статус: " + RepairsPanel.ru(r.getString("status"))
                        + "   Service Level: " + r.getString("sla") + "\n";
                    line += "Открыт: " + r.getString("opened") + "   Выдан: " + RepairsPanel.nz(r.getString("issued")) + "\n";
                    if (!empty(r.getString("notes"))) line += "Примечание: " + r.getString("notes") + "\n";
                    line += "\nОплата за ремонт: " + String.format("%,.2f руб.", r.getDouble("price"))
                        + " (уточняется актом сверки)\n";
                    line += "\nРаботы выполнены полностью, заказчиком приняты.\n \n";
                    line += "Инженер ______________ / " + RepairsPanel.nz(r.getString("engineer")) + "\n";
                }
            }
            for (String l : line.split("\n"))
                Print.addLine(p, l, l.contains("ОПАСНО"), false);
            Print.addLine(p, "Заказчик ______________ / " + customerIdName(customerId), false, false);
            if (!empty(Settings.get("inn")))
                Print.addLine(p, "Исполнитель: " + Settings.get("name") + ", ИНН " + Settings.get("inn"), false, false);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        preview(owner, p, "Акт о выполненных работах №" + repairId);
    }

    private static String customerIdName(long id) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT fio FROM customers WHERE id=" + id)) {
            if (rs.next()) return RepairsPanel.nz(rs.getString(1));
        } catch (Exception ignored) { }
        return "";
    }

    /** Квитанция о приёме оборудования в ремонт — выдаётся заказчику. */
    public static void receipt(JFrame owner, long repairId) {
        JTextPane p = Print.pane();
        header(p, "КВИТАНЦИЯ о приёме оборудования в ремонт №" + repairId);
        try (Statement st = Db.get().createStatement();
             ResultSet r = st.executeQuery("SELECT r.*, TRIM(c.org || ' ' || c.fio) AS cust,"
                 + " c.phone, c.attorney FROM repairs r LEFT JOIN customers c ON c.id=r.customer_id"
                 + " WHERE r.id=" + repairId)) {
            if (r.next()) {
                Print.addLine(p, "Принято в ремонт: " + RepairsPanel.nz(r.getString("equipment")), false, false);
                Print.addLine(p, "Серийный/инвентарный №: " + RepairsPanel.nz(r.getString("serial")), false, false);
                Print.addLine(p, "Комплектность: " + RepairsPanel.nz(r.getString("completeness")), false, false);
                Print.addLine(p, "Заявленная неисправность: " + RepairsPanel.nz(r.getString("problem")), false, false);
                Print.addLine(p, "Заказчик: " + RepairsPanel.nz(r.getString("cust"))
                    + "   Тел.: " + RepairsPanel.nz(r.getString("phone")), false, false);
                if (!empty(r.getString("attorney")))
                    Print.addLine(p, "Лицо, сдавшее оборудование (доверенность): "
                        + r.getString("attorney"), false, false);
                Print.addLine(p, "Дата приёма: " + RepairsPanel.nz(r.getString("opened"))
                    + "   Инженер: " + RepairsPanel.nz(r.getString("engineer")), false, false);
                Print.addLine(p, "Ориентировочная стоимость: "
                    + String.format("%,.2f руб.", r.getDouble("price")), false, false);
                int w = r.getInt("warranty_days");
                Print.addLine(p, w > 0
                    ? "Гарантия на выполненный ремонт — " + w + " дн. с момента выдачи."
                    : "Гарантия на ремонт не устанавливается.", w > 0, false);
                Print.addLine(p, " ", false, false);
                Print.addLine(p, "Оборудование принято; сроки и статусы — по согласованию с сервисным центром.", false, false);
                Print.addLine(p, " ", false, false);
                Print.addLine(p, "Принял ______________ / " + RepairsPanel.nz(r.getString("engineer")), false, false);
                Print.addLine(p, "Сдал ______________ / " + RepairsPanel.nz(r.getString("cust")), false, false);
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        preview(owner, p, "Квитанция №" + repairId);
    }

    /** Акт взаиморасчётов с организациями за период (дд.мм.гггг — дд.мм.гггг). */
    public static JTextPane balanceAct(String from, String to) {
        JTextPane p = Print.pane();
        header(p, "АКТ взаиморасчётов с организациями за период " + from + " — " + to);
        double total = 0;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT c.org, c.fio, SUM(r.price), SUM(c.prepay), c.payment_date FROM customers c"
                + " JOIN repairs r ON r.customer_id=c.id"
                + " WHERE substr(r.opened,7,4)||substr(r.opened,4,2)||substr(r.opened,1,2)"
                + " BETWEEN '" + toSqlDate(from) + "' AND '" + toSqlDate(to) + "' GROUP BY c.id")) {
            while (rs.next()) {
                double sum = rs.getDouble(3);
                total += sum;
                Print.addLine(p, RepairsPanel.nz(rs.getString(1)) + " (" + RepairsPanel.nz(rs.getString(2))
                    + "): " + String.format("%,.2f руб.", sum) + ", предоплата "
                    + String.format("%,.2f руб.", rs.getDouble(4)) + ", оплата "
                    + RepairsPanel.nz(rs.getString(5)), sum == 0, false);
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        Print.addLine(p, " ", false, false);
        Print.addLine(p, "ИТОГО к взаиморасчёту: " + String.format("%,.2f руб.", total), total == 0, true);
        return p;
    }

    /** Печатная форма чека по заявке: последняя оплата, иначе цена ремонта. */
    public static void bill(JFrame owner, long repairId) {
        JTextPane p = Print.pane();
        header(p, "ЧЕК (печатная форма, не фискальный) №" + repairId);
        try (Statement st = Db.get().createStatement();
             ResultSet r = st.executeQuery("SELECT r.equipment, r.price, r.opened, r.issued,"
                 + " TRIM(COALESCE(c.org,'') || ' ' || COALESCE(c.fio,'')) AS cust,"
                 + " p.amount, p.method, p.ts FROM repairs r"
                 + " LEFT JOIN customers c ON c.id=r.customer_id"
                 + " LEFT JOIN payments p ON p.repair_id=r.id AND p.dir='IN'"
                 + " WHERE r.id=" + repairId + " ORDER BY p.id DESC LIMIT 1")) {
            if (r.next()) {
                double amount = r.getDouble(6);
                if (r.wasNull()) amount = r.getDouble(2);
                Print.addLine(p, "Заказчик: " + RepairsPanel.nz(r.getString(5)), false, false);
                Print.addLine(p, "Услуга: ремонт оборудования " + RepairsPanel.nz(r.getString(1)), false, false);
                Print.addLine(p, "Принято: " + RepairsPanel.nz(r.getString(3))
                    + "   Выдано: " + RepairsPanel.nz(r.getString(4)), false, false);
                Print.addLine(p, "Оплата: " + RepairsPanel.nz(r.getString(7))
                    + " от " + RepairsPanel.nz(r.getString(8)), false, false);
                Print.addLine(p, " ", false, false);
                Print.addLine(p, String.format("ИТОГО К ОПЛАТЕ: %,.2f руб.", amount), false, true);
                Print.addLine(p, " ", false, false);
                Print.addLine(p, "Документ напечатан для сервиса, не является фискальным чеком.", true, false);
            } else Print.addLine(p, "Заявка не найдена", true, false);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        preview(owner, p, "Чек по заявке №" + repairId);
    }

    /** Этикетка со штрихкодом (Code 128) для наклейки на оборудование, с регулятором толщины. */
    public static void label(JFrame owner, final long repairId) {
        final javax.swing.JComboBox<String> thick = new javax.swing.JComboBox<>(Labels.THICK);
        thick.setSelectedIndex(1);
        thick.setFont(Theme.font(14));
        final javax.swing.JLabel view = new javax.swing.JLabel();
        final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
        final JDialog d = new JDialog(owner, "Этикетка №" + repairId, false);
        Runnable rebuild = new Runnable() {
            @Override public void run() {
                img[0] = Labels.one(repairId, 58, 40, Labels.THICK_PCT[Math.max(0, thick.getSelectedIndex())]);
                if (img[0] == null) return;
                view.setIcon(new javax.swing.ImageIcon(img[0]));
                d.pack();
            }
        };
        thick.addActionListener(e -> rebuild.run());
        rebuild.run();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.GRAY_BG);
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.add(new javax.swing.JLabel("Толщина штрихкода: "));
        top.add(thick);
        root.add(top, BorderLayout.NORTH);
        root.add(view, BorderLayout.CENTER);
        JPanel b = new JPanel();
        b.setOpaque(false);
        b.add(Theme.on("Печать этикетки", Theme.YELLOW,
            e -> Print.image(img[0], "Этикетка PSR-R" + repairId)));
        b.add(Theme.on("Закрыть", Theme.GRAY_PANEL, e -> d.dispose()));
        root.add(b, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(600, 500);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    /** Перевод дд.мм.гггг в ггггммдд для сравнения в SQL. */
    static String toSqlDate(String s) {
        try { return s.substring(6, 10) + s.substring(3, 5) + s.substring(0, 2); }
        catch (Exception e) { return "00000000"; }
    }

    static void previewDoc(JFrame owner, JTextPane doc, String title) {
        preview(owner, doc, title);
    }

    private static void preview(JFrame owner, JTextPane doc, String title) {
        JDialog d = new JDialog(owner, title, false);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.GRAY_BG);
        root.add(Settings.headerBar(12), BorderLayout.NORTH);
        root.add(new JScrollPane(doc), BorderLayout.CENTER);
        JPanel b = new JPanel();
        b.setOpaque(false);
        final JComboBox<String> copies = new JComboBox<>(new String[] { "1", "2", "3", "4", "5" });
        copies.setSelectedItem("3");
        b.add(new javax.swing.JLabel("Экземпляров: "));
        b.add(copies);
        b.add(Theme.on("Печать", Theme.YELLOW, e ->
            Print.print(doc, title, Integer.parseInt(String.valueOf(copies.getSelectedItem())))));
        b.add(Theme.on("На почту", Theme.BLUE, e -> Print.mail(Settings.get("mail"), title, doc.getText())));
        b.add(Theme.on("Закрыть", Theme.GRAY_PANEL, e -> d.dispose()));
        root.add(b, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(900, 750);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private Documents() { }
}
