package psr.sc;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Диалог создания/изменения заявки на ремонт. */
public final class RepairForm {

    public static void show(App app, Long id, RepairsPanel refresh) {
        JDialog d = new JDialog(app, id == null ? "Новая заявка на ремонт" : "Заявка №" + id, true);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.GRAY_BG);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<Object> customer = new JComboBox<>();
        customer.addItem("— без заказчика —");
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, org, fio, phone FROM customers ORDER BY org, fio")) {
            while (rs.next())
                customer.addItem(new Entry(rs.getLong(1), (rs.getString(2) + " " + Privacy.fio(rs.getString(3))
                    + "  тел. " + Privacy.phone(rs.getString(4))).trim()));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        customer.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, v, i, sel, focus);
                setFont(Theme.font(14));
                setText(v instanceof Entry ? ((Entry) v).text : String.valueOf(v));
                return this;
            }
        });

        JTextField eq = new JTextField(24), prob = new JTextField(24), eng = new JTextField(18),
            mgr = new JTextField(18),
            wait = new JTextField(10), price = new JTextField(10), notes = new JTextField(30),
            serial = new JTextField(24), completeness = new JTextField(30), warranty = new JTextField(6);
        wait.setToolTipText("Примерный срок ожидания запчасти, дд.мм.гггг");
        warranty.setToolTipText("Гарантия на выполненный ремонт, дней");
        JComboBox<String> status = new JComboBox<>(RepairsPanel.STATUS_RU);
        JComboBox<String> sla = new JComboBox<>(RepairsPanel.SLA_RU);
        status.setFont(Theme.font(14)); sla.setFont(Theme.font(14));

        // Версия записи при открытии формы — по ней видно, что заявку успел изменить другой сотрудник.
        final long[] base = new long[1];
        final Object[][] ver = {
            { "equipment", "Оборудование", eq }, { "serial", "Серийный №", serial },
            { "completeness", "Комплектность", completeness }, { "problem", "Неисправность", prob },
            { "manager", "Менеджер приёма", mgr }, { "engineer", "Инженер", eng },
            { "wait_until", "Ожидание ЗЧ", wait }, { "price", "Цена, руб.", price },
            { "notes", "Примечание", notes }, { "warranty_days", "Гарантия, дн.", warranty },
            { "status", "Статус", status }, { "sla", "Service Level", sla } };

        JLabel waitWarn = Theme.danger("Срок ожидания просрочен — Service Level станет ОПАСНО");
        waitWarn.setVisible(false);

        int y = 0;
        add(p, g, y++, "Заказчик:", customer);
        add(p, g, y++, "Оборудование:", eq);
        add(p, g, y++, "Серийный/инвентарный №:", serial);
        add(p, g, y++, "Комплектность (что принято):", completeness);
        add(p, g, y++, "Неисправность:", prob);
        add(p, g, y++, "Менеджер приёма:", mgr);
        add(p, g, y++, "Статус:", status);
        add(p, g, y++, "Service Level:", sla);
        add(p, g, y++, "Ожидание запчасти до:", wait);
        add(p, g, y++, "Инженер:", eng);
        add(p, g, y++, "Цена ремонта, руб.:", price);
        add(p, g, y++, "Гарантия на ремонт, дней:", warranty);
        add(p, g, y++, "Примечание:", notes);
        g.gridx = 1; g.gridy = y++; p.add(waitWarn, g);

        if (id != null) {
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM repairs WHERE id=" + id)) {
                if (rs.next()) {
                    eq.setText(RepairsPanel.nz(rs.getString("equipment")));
                    serial.setText(RepairsPanel.nz(rs.getString("serial")));
                    completeness.setText(RepairsPanel.nz(rs.getString("completeness")));
                    warranty.setText(String.valueOf(rs.getInt("warranty_days")));
                    prob.setText(RepairsPanel.nz(rs.getString("problem")));
                    mgr.setText(RepairsPanel.nz(rs.getString("manager")));
                    eng.setText(RepairsPanel.nz(rs.getString("engineer")));
                    wait.setText(RepairsPanel.nz(rs.getString("wait_until")));
                    notes.setText(RepairsPanel.nz(rs.getString("notes")));
                    price.setText(String.valueOf(rs.getDouble("price")));
                    base[0] = rs.getLong("rev");
                    status.setSelectedItem(RepairsPanel.ru(rs.getString("status")));
                    sla.setSelectedItem(RepairsPanel.nz(rs.getString("sla")));
                    long cust = rs.getLong("customer_id");
                    for (int i = 0; i < customer.getItemCount(); i++)
                        if (customer.getItemAt(i) instanceof Entry && ((Entry) customer.getItemAt(i)).id == cust)
                            customer.setSelectedIndex(i);
                }
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        }

        wait.addActionListener(e -> waitWarn.setVisible(!wait.getText().trim().isEmpty()
            && RepairsPanel.overdue(wait.getText())));

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(Theme.on("Сохранить", Theme.YELLOW, e -> {
            String warrantyHit = underWarranty(serial.getText(), id);
            if (warrantyHit != null) {
                int go = javax.swing.JOptionPane.showConfirmDialog(d,
                    Theme.danger("<html><b>" + warrantyHit + "</b><br>Продолжить и создать заявку?"
                        + "</html>"), "Гарантийный случай",
                    javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
                if (go != javax.swing.JOptionPane.YES_OPTION) return;
            }
            long custId = customer.getSelectedItem() instanceof Entry ? ((Entry) customer.getSelectedItem()).id : 0;
            String sql = "status='" + RepairsPanel.STATUS[status.getSelectedIndex()] + "', sla='"
                + sla.getSelectedItem() + "', customer_id=" + custId + ", equipment='"
                + Db.esc(eq.getText()) + "', serial='" + Db.esc(serial.getText()) + "', completeness='"
                + Db.esc(completeness.getText()) + "', warranty_days="
                + (long) RepairForm.parse(warranty.getText()) + ", problem='"
                + Db.esc(prob.getText()) + "', engineer='"
                + Db.esc(eng.getText()) + "', manager='" + Db.esc(mgr.getText())
                + "', wait_until='" + Db.esc(wait.getText().trim())
                + "', price=" + parse(price.getText()) + ", notes='" + Db.esc(notes.getText()) + "'";
            try {
                if (id == null) {
                    Db.exec("INSERT INTO repairs(customer_id,equipment,serial,completeness,warranty_days,"
                        + "problem,status,sla,engineer,manager,wait_until,price,notes,opened) VALUES(" + custId
                        + ",'" + Db.esc(eq.getText()) + "','" + Db.esc(serial.getText()) + "','"
                        + Db.esc(completeness.getText()) + "'," + (long) parse(warranty.getText()) + ",'"
                        + Db.esc(prob.getText()) + "','" + RepairsPanel.STATUS[status.getSelectedIndex()]
                        + "','" + sla.getSelectedItem() + "','" + Db.esc(eng.getText()) + "','"
                        + Db.esc(mgr.getText()) + "','"
                        + Db.esc(wait.getText().trim()) + "'," + parse(price.getText()) + ",'"
                        + Db.esc(notes.getText()) + "','" + RepairsPanel.stamp() + "')");
                    try (Statement st = Db.get().createStatement();
                         ResultSet rs = st.executeQuery("SELECT MAX(id) FROM repairs")) {
                        long newId = rs.next() ? rs.getLong(1) : 0;
                        Stream.log(newId, "Заявка создана: "
                            + RepairsPanel.STATUS_RU[status.getSelectedIndex()]
                            + (warrantyHit == null ? "" : " · ГАРАНТИЙНЫЙ СЛУЧАЙ: " + warrantyHit));
                    }
                } else {
                    int n = Db.update("UPDATE repairs SET " + sql + ", rev=rev+1 WHERE id=" + id
                        + " AND rev=" + base[0]);
                    if (n == 0) {
                        int c = conflict(d, id, ver);
                        if (c == 0) return;
                        if (c == 1) {
                            Db.update("UPDATE repairs SET " + sql + ", rev=rev+1 WHERE id=" + id);
                            Stream.log(id, "Данные заявки изменены (перезаписана версия другого сотрудника)");
                        } else {
                            Stream.log(id, "Правка отменена: в базе осталась версия другого сотрудника");
                        }
                        d.dispose();
                        refresh.load();
                        return;
                    }
                    Stream.log(id, "Данные заявки изменены");
                }
                d.dispose();
                refresh.load();
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        }));
        buttons.add(Theme.on("Отмена", Theme.GRAY_PANEL, e -> d.dispose()));
        g.gridx = 0; g.gridwidth = 2; g.gridy = y++; g.fill = GridBagConstraints.NONE;
        p.add(buttons, g);

        d.setContentPane(wrap(p, id));
        d.setSize(560, 640);
        d.setLocationRelativeTo(app);
        d.setVisible(true);
    }

    private static javax.swing.JComponent wrap(JPanel form, Long id) {
        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        root.setBackground(Theme.GRAY_BG);
        root.add(Settings.headerBar(12), java.awt.BorderLayout.NORTH);
        root.add(new javax.swing.JScrollPane(form), java.awt.BorderLayout.CENTER);
        return root;
    }

    private static void add(JPanel p, GridBagConstraints g, int row, String label, java.awt.Component c) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(14));
        if (c instanceof JTextField) c.setFont(Theme.font(14));
        g.gridx = 0; g.gridy = row; g.weightx = 0; p.add(l, g);
        g.gridx = 1; g.weightx = 1; p.add(c, g);
    }

    /**
     * Заявку уже изменил другой сотрудник. Возвращает: 0 — показать отличия и остаться в форме,
     * 1 — записать мою версию, 2 — оставить в базе чужую версию.
     */
    private static int conflict(java.awt.Component owner, long id, Object[][] ver) {
        String d = diff(id, ver);
        int r = javax.swing.JOptionPane.showOptionDialog(owner,
            "Пока вы заполняли карточку, заявку №" + id + " изменил другой сотрудник.\n"
            + (d.isEmpty() ? "Отличий от вашей версии не найдено." : "Найдены отличия — стоит посмотреть их.")
            + "\n\nКак поступить?", "Конфликт сохранения", javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE, null,
            new Object[] { "Показать отличия", "Записать мою версию", "Оставить версию базы" },
            "Оставить версию базы");
        if (r == 0) Warn.show(d.isEmpty() ? "Отличий нет" : d);
        return r == 1 ? 1 : (r == 2 ? 2 : 0);
    }

    /** Построчное сравнение того, что в базе, с тем, что набрано в форме. */
    static String diff(long id, Object[][] ver) {
        StringBuilder sb = new StringBuilder();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM repairs WHERE id=" + id)) {
            if (!rs.next()) return "";
            for (Object[] v : ver) {
                String col = (String) v[0];
                String cur = RepairsPanel.nz(rs.getString(col)).trim();
                if ("status".equals(col)) cur = RepairsPanel.ru(cur);
                String mine = val(v[2]);
                if (!same(cur, mine))
                    sb.append(v[1]).append(": в базе «").append(cur).append("» · у меня «")
                      .append(mine).append("»\n");
            }
        } catch (Exception ignored) { }
        return sb.toString();
    }

    private static String val(Object w) {
        if (w instanceof JComboBox) return String.valueOf(((JComboBox<?>) w).getSelectedItem());
        return ((JTextField) w).getText().trim();
    }

    /** Числа сравниваем как числа: «1500» и «1500.0» — одно и то же. */
    static boolean same(String a, String b) {
        if (a.equals(b)) return true;
        try {
            return Double.parseDouble(a.replace(',', '.')) == Double.parseDouble(b.replace(',', '.'));
        } catch (Exception e) { return false; }
    }

    /** Если та же техника (по серийному №) уже отремонтирована и гарантия идёт — строка-предупреждение. */
    static String underWarranty(String serial, Long excludeId) {
        if (serial == null || serial.trim().isEmpty()) return null;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, issued, warranty_days FROM repairs"
                 + " WHERE serial='" + Db.esc(serial.trim()) + "'"
                 + (excludeId == null ? "" : " AND id<>" + excludeId)
                 + " AND issued<>'' AND warranty_days>0 ORDER BY id DESC")) {
            while (rs.next()) {
                try {
                    long issuedMs = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                        .parse(rs.getString(2)).getTime();
                    long until = issuedMs + rs.getLong(3) * 86_400_000L;
                    if (System.currentTimeMillis() < until)
                        return "Повторная заявка по технике с серийным № " + serial.trim()
                            + ": ремонт №" + rs.getLong(1) + " выдан " + rs.getString(2)
                            + ", гарантия " + rs.getLong(3) + " дн. ещё действует";
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return null;
    }

    static double parse(String s) {
        try { return Double.parseDouble(s.replace(",", ".").trim()); } catch (Exception e) { return 0; }
    }

    private static class Entry {
        final long id; final String text;
        Entry(long id, String text) { this.id = id; this.text = text; }
    }

    private RepairForm() { }
}
