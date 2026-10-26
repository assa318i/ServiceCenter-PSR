package psr.sc;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
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
        JButton addCustBtn = Theme.on("＋", Theme.BLUE, e -> {
            try {
                Db.exec("INSERT INTO customers(org) VALUES('')");
                long newId = 0;
                try (Statement st = Db.get().createStatement();
                     ResultSet rs = st.executeQuery("SELECT MAX(id) FROM customers")) {
                    if (rs.next()) newId = rs.getLong(1);
                }
                if (newId > 0) {
                    new CustomerCard(app, newId, null).setVisible(true);
                    customer.removeAllItems();
                    customer.addItem("— без заказчика —");
                    try (Statement st = Db.get().createStatement();
                         ResultSet rs = st.executeQuery("SELECT id, org, fio, phone FROM customers ORDER BY org, fio")) {
                        while (rs.next())
                            customer.addItem(new Entry(rs.getLong(1), (rs.getString(2) + " " + Privacy.fio(rs.getString(3))
                                + "  тел. " + Privacy.phone(rs.getString(4))).trim()));
                    }
                    for (int i = 0; i < customer.getItemCount(); i++) {
                        Object item = customer.getItemAt(i);
                        if (item instanceof Entry && ((Entry) item).id == newId) {
                            customer.setSelectedIndex(i);
                            break;
                        }
                    }
                }
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        });
        addCustBtn.setToolTipText("Добавить нового заказчика");
        addCustBtn.setMargin(new java.awt.Insets(2, 6, 2, 6));

        JComboBox<String> eq = new JComboBox<>();
        eq.setEditable(true);
        eq.setFont(Theme.font(14));
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM equipment_types ORDER BY name")) {
            while (rs.next()) eq.addItem(rs.getString(1));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        JButton addEquipBtn = Theme.on("＋", Theme.BLUE, e -> {
            String val = (String) eq.getSelectedItem();
            if (val == null || val.trim().isEmpty()) { Warn.show("Введите тип оборудования"); return; }
            val = val.trim();
            for (int i = 0; i < eq.getItemCount(); i++)
                if (val.equalsIgnoreCase(eq.getItemAt(i))) { eq.setSelectedIndex(i); return; }
            try {
                Db.exec("INSERT INTO equipment_types(name) VALUES('" + Db.esc(val) + "')");
                eq.insertItemAt(val, 0);
                eq.setSelectedIndex(0);
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        });
        addEquipBtn.setToolTipText("Добавить тип оборудования в справочник");
        addEquipBtn.setMargin(new java.awt.Insets(2, 6, 2, 6));

        JComboBox<String> prob = new JComboBox<>();
        prob.setEditable(true);
        prob.setFont(Theme.font(14));
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM fault_types ORDER BY name")) {
            while (rs.next()) prob.addItem(rs.getString(1));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        JButton addFaultBtn = Theme.on("＋", Theme.BLUE, e -> {
            String val = (String) prob.getSelectedItem();
            if (val == null || val.trim().isEmpty()) { Warn.show("Введите тип неисправности"); return; }
            val = val.trim();
            for (int i = 0; i < prob.getItemCount(); i++)
                if (val.equalsIgnoreCase(prob.getItemAt(i))) { prob.setSelectedIndex(i); return; }
            try {
                Db.exec("INSERT INTO fault_types(name) VALUES('" + Db.esc(val) + "')");
                prob.insertItemAt(val, 0);
                prob.setSelectedIndex(0);
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
        });
        addFaultBtn.setToolTipText("Добавить тип неисправности в справочник");
        addFaultBtn.setMargin(new java.awt.Insets(2, 6, 2, 6));

        JComboBox<String> eng = new JComboBox<>();
        eng.setFont(Theme.font(14));
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM users WHERE role IN ('ENGINEER','ADMIN')"
                 + " AND (blocked_until IS NULL OR blocked_until = '' OR blocked_until < date('now'))"
                 + " ORDER BY name")) {
            while (rs.next()) eng.addItem(rs.getString(1));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        JTextField mgr = new JTextField(18),
            wait = new JTextField(10), price = new JTextField(10), notes = new JTextField(30),
            serial = new JTextField(24), completeness = new JTextField(30), warranty = new JTextField(6),
            prepay = new JTextField(10);
        wait.setToolTipText("Примерный срок ожидания запчасти, дд.мм.гггг");
        warranty.setToolTipText("Гарантия на выполненный ремонт, дней");
        prepay.setToolTipText("Сумма предоплаты, руб.");
        JButton calBtn = Theme.on("📅", Theme.BLUE, e -> {
            String picked = pickDate(d, wait.getText());
            if (picked != null) wait.setText(picked);
        });
        calBtn.setToolTipText("Выбрать дату из календаря");
        calBtn.setMargin(new java.awt.Insets(2, 6, 2, 6));
        JComboBox<String> status = new JComboBox<>(RepairsPanel.STATUS_RU);
        JComboBox<String> sla = new JComboBox<>(RepairsPanel.SLA_RU);
        JComboBox<String> paymentMethod = new JComboBox<>(new String[] {
            "", "Наличные деньги", "По QR коду", "На расчетный счет"
        });
        status.setFont(Theme.font(14)); sla.setFont(Theme.font(14));
        paymentMethod.setFont(Theme.font(14));

        // Версия записи при открытии формы — по ней видно, что заявку успел изменить другой сотрудник.
        final long[] base = new long[1];
        final Object[][] ver = {
            { "equipment", "Оборудование", eq }, { "serial", "Серийный №", serial },
            { "completeness", "Комплектность", completeness }, { "problem", "Неисправность", prob },
            { "manager", "Менеджер приёма", mgr }, { "engineer", "Инженер", eng },
            { "wait_until", "Ожидание ЗЧ", wait }, { "price", "Цена, руб.", price },
            { "payment_method", "Способ оплаты", paymentMethod }, { "prepay", "Предоплата", prepay },
            { "notes", "Примечание", notes }, { "warranty_days", "Гарантия, дн.", warranty },
            { "status", "Статус", status }, { "sla", "Service Level", sla } };

        JLabel waitWarn = Theme.danger("Срок ожидания просрочен — Service Level станет ОПАСНО");
        waitWarn.setVisible(false);

        int y = 0;
        JPanel custRow = new JPanel(new java.awt.BorderLayout(4, 0));
        custRow.setOpaque(false);
        custRow.add(customer, java.awt.BorderLayout.CENTER);
        custRow.add(addCustBtn, java.awt.BorderLayout.EAST);
        add(p, g, y++, "Заказчик:", custRow);
        JPanel eqRow = new JPanel(new java.awt.BorderLayout(4, 0));
        eqRow.setOpaque(false);
        eqRow.add(eq, java.awt.BorderLayout.CENTER);
        eqRow.add(addEquipBtn, java.awt.BorderLayout.EAST);
        add(p, g, y++, "Оборудование:", eqRow);
        add(p, g, y++, "Серийный/инвентарный №:", serial);
        add(p, g, y++, "Комплектность (что принято):", completeness);
        JPanel probRow = new JPanel(new java.awt.BorderLayout(4, 0));
        probRow.setOpaque(false);
        probRow.add(prob, java.awt.BorderLayout.CENTER);
        probRow.add(addFaultBtn, java.awt.BorderLayout.EAST);
        add(p, g, y++, "Неисправность:", probRow);
        add(p, g, y++, "Менеджер приёма:", mgr);
        add(p, g, y++, "Статус:", status);
        add(p, g, y++, "Service Level:", sla);
        JPanel waitRow = new JPanel(new java.awt.BorderLayout(4, 0));
        waitRow.setOpaque(false);
        waitRow.add(wait, java.awt.BorderLayout.CENTER);
        waitRow.add(calBtn, java.awt.BorderLayout.EAST);
        add(p, g, y++, "Ожидание запчасти до:", waitRow);
        add(p, g, y++, "Инженер:", eng);
        add(p, g, y++, "Цена ремонта, руб.:", price);
        add(p, g, y++, "Способ оплаты:", paymentMethod);
        add(p, g, y++, "Сумма предоплаты, руб.:", prepay);
        add(p, g, y++, "Гарантия на ремонт, дней:", warranty);
        add(p, g, y++, "Примечание:", notes);
        g.gridx = 1; g.gridy = y++; p.add(waitWarn, g);

        if (id != null) {
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM repairs WHERE id=" + id)) {
                if (rs.next()) {
                    eq.setSelectedItem(RepairsPanel.nz(rs.getString("equipment")));
                    serial.setText(RepairsPanel.nz(rs.getString("serial")));
                    completeness.setText(RepairsPanel.nz(rs.getString("completeness")));
                    warranty.setText(String.valueOf(rs.getInt("warranty_days")));
                    prob.setSelectedItem(RepairsPanel.nz(rs.getString("problem")));
                    mgr.setText(RepairsPanel.nz(rs.getString("manager")));
                    eng.setSelectedItem(RepairsPanel.nz(rs.getString("engineer")));
                    wait.setText(RepairsPanel.nz(rs.getString("wait_until")));
                    notes.setText(RepairsPanel.nz(rs.getString("notes")));
                    price.setText(String.valueOf(rs.getDouble("price")));
                    paymentMethod.setSelectedItem(RepairsPanel.nz(rs.getString("payment_method")));
                    prepay.setText(String.valueOf(rs.getDouble("prepay")));
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
                + Db.esc(String.valueOf(eq.getSelectedItem())) + "', serial='" + Db.esc(serial.getText()) + "', completeness='"
                + Db.esc(completeness.getText()) + "', warranty_days="
                + (long) RepairForm.parse(warranty.getText()) + ", problem='"
                + Db.esc(String.valueOf(prob.getSelectedItem())) + "', engineer='"
                + Db.esc(String.valueOf(eng.getSelectedItem())) + "', manager='" + Db.esc(mgr.getText())
                + "', wait_until='" + Db.esc(wait.getText().trim())
                + "', price=" + parse(price.getText())
                + ", payment_method='" + Db.esc(String.valueOf(paymentMethod.getSelectedItem())) + "'"
                + ", prepay=" + parse(prepay.getText())
                + ", notes='" + Db.esc(notes.getText()) + "'";
            try {
                if (id == null) {
                    Db.exec("INSERT INTO repairs(customer_id,equipment,serial,completeness,warranty_days,"
                        + "problem,status,sla,engineer,manager,wait_until,price,payment_method,prepay,notes,opened) VALUES(" + custId
                        + ",'" + Db.esc(String.valueOf(eq.getSelectedItem())) + "','" + Db.esc(serial.getText()) + "','"
                        + Db.esc(completeness.getText()) + "'," + (long) parse(warranty.getText()) + ",'"
                        + Db.esc(String.valueOf(prob.getSelectedItem())) + "','" + RepairsPanel.STATUS[status.getSelectedIndex()]
                        + "','" + sla.getSelectedItem() + "','" + Db.esc(String.valueOf(eng.getSelectedItem())) + "','"
                        + Db.esc(mgr.getText()) + "','"
                        + Db.esc(wait.getText().trim()) + "'," + parse(price.getText())
                        + ",'" + Db.esc(String.valueOf(paymentMethod.getSelectedItem())) + "',"
                        + parse(prepay.getText()) + ",'"
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
        d.setSize(750, 640);
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

    /** Диалог выбора даты с визуальным календарём. Возвращает дату в формате дд.мм.гггг или null если отменено. */
    private static String pickDate(java.awt.Dialog owner, String current) {
        int day = 1, month = 1, year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (current != null && current.trim().length() == 10) {
            try {
                day = Integer.parseInt(current.substring(0, 2));
                month = Integer.parseInt(current.substring(3, 5));
                year = Integer.parseInt(current.substring(6, 10));
            } catch (Exception ignored) { }
        }
        final int[] d = { day, month, year };
        final JDialog dlg = new JDialog(owner, "Выбор даты", true);
        dlg.setLayout(new java.awt.BorderLayout(6, 6));
        
        // Визуальный календарь
        final JPanel calendarPanel = new JPanel(new java.awt.BorderLayout());
        final JPanel monthNav = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        final JLabel monthLabel = new JLabel();
        monthLabel.setFont(Theme.font(16));
        JButton prevMonth = Theme.btn("◄", Theme.BLUE);
        prevMonth.setIcon(leftTriangle());
        prevMonth.addActionListener(e -> {
            d[1]--;
            if (d[1] < 1) { d[1] = 12; d[2]--; }
            updateCalendar(calendarPanel, d, monthLabel);
        });
        JButton nextMonth = Theme.btn("►", Theme.BLUE);
        nextMonth.setIcon(rightTriangle());
        nextMonth.addActionListener(e -> {
            d[1]++;
            if (d[1] > 12) { d[1] = 1; d[2]++; }
            updateCalendar(calendarPanel, d, monthLabel);
        });
        monthNav.add(prevMonth);
        monthNav.add(monthLabel);
        monthNav.add(nextMonth);
        calendarPanel.add(monthNav, java.awt.BorderLayout.NORTH);
        
        updateCalendar(calendarPanel, d, monthLabel);
        calendarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dlg.add(calendarPanel, java.awt.BorderLayout.CENTER);
        
        // Спиннеры для точной настройки
        JPanel spinners = new JPanel(new java.awt.GridLayout(3, 2, 4, 4));
        spinners.setBorder(BorderFactory.createTitledBorder("Точная настройка"));
        javax.swing.JSpinner daySpin = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(day, 1, 31, 1));
        javax.swing.JSpinner monthSpin = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(month, 1, 12, 1));
        javax.swing.JSpinner yearSpin = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(year, 2020, 2099, 1));
        ((javax.swing.JComponent) daySpin.getEditor()).setFont(Theme.font(14));
        ((javax.swing.JComponent) monthSpin.getEditor()).setFont(Theme.font(14));
        ((javax.swing.JComponent) yearSpin.getEditor()).setFont(Theme.font(14));
        spinners.add(new JLabel("День:"));
        spinners.add(daySpin);
        spinners.add(new JLabel("Месяц:"));
        spinners.add(monthSpin);
        spinners.add(new JLabel("Год:"));
        spinners.add(yearSpin);
        
        // Синхронизация спиннеров с календарём
        javax.swing.event.ChangeListener spinnerListener = e -> {
            d[0] = (Integer) daySpin.getValue();
            d[1] = (Integer) monthSpin.getValue();
            d[2] = (Integer) yearSpin.getValue();
            updateCalendar(calendarPanel, d, monthLabel);
        };
        daySpin.addChangeListener(spinnerListener);
        monthSpin.addChangeListener(spinnerListener);
        yearSpin.addChangeListener(spinnerListener);
        
        dlg.add(spinners, java.awt.BorderLayout.SOUTH);
        
        JPanel buttons = new JPanel();
        buttons.add(Theme.on("Выбрать", Theme.YELLOW, e -> {
            d[0] = (Integer) daySpin.getValue();
            d[1] = (Integer) monthSpin.getValue();
            d[2] = (Integer) yearSpin.getValue();
            dlg.dispose();
        }));
        buttons.add(Theme.on("Отмена", Theme.GRAY_PANEL, e -> {
            d[0] = 0;
            dlg.dispose();
        }));
        
        JPanel southContainer = new JPanel(new java.awt.BorderLayout());
        southContainer.add(spinners, java.awt.BorderLayout.CENTER);
        southContainer.add(buttons, java.awt.BorderLayout.SOUTH);
        dlg.add(southContainer, java.awt.BorderLayout.SOUTH);
        
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        if (d[0] == 0) return null;
        return String.format("%02d.%02d.%04d", d[0], d[1], d[2]);
    }
    
    /** Обновляет визуальный календарь. */
    private static void updateCalendar(JPanel calendarPanel, int[] d, JLabel monthLabel) {
        String[] monthNames = { "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь" };
        monthLabel.setText(monthNames[d[1] - 1] + " " + d[2]);
        
        // Удаляем старую сетку календаря
        if (calendarPanel.getComponentCount() > 1) {
            calendarPanel.remove(1);
        }
        
        // Создаём новую сетку
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 7, 2, 2));
        grid.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        
        // Заголовки дней недели
        String[] dayNames = { "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс" };
        for (String dayName : dayNames) {
            JLabel lbl = new JLabel(dayName, javax.swing.SwingConstants.CENTER);
            lbl.setFont(Theme.font(12));
            lbl.setForeground(java.awt.Color.GRAY);
            grid.add(lbl);
        }
        
        // Вычисляем первый день месяца
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(d[2], d[1] - 1, 1);
        int firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        // Преобразуем: Пн=1, Вт=2, ..., Вс=7 (в Java: Вс=1, Пн=2, ..., Сб=7)
        firstDayOfWeek = firstDayOfWeek == 1 ? 7 : firstDayOfWeek - 1;
        
        // Количество дней в месяце
        int daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        
        // Пустые ячейки до первого дня
        for (int i = 1; i < firstDayOfWeek; i++) {
            grid.add(new JLabel(""));
        }
        
        // Дни месяца
        for (int day = 1; day <= daysInMonth; day++) {
            final int dayNum = day;
            JButton dayBtn = new JButton(String.valueOf(day));
            dayBtn.setFont(Theme.font(12));
            dayBtn.setMargin(new java.awt.Insets(4, 4, 4, 4));
            
            // Подсветка выбранного дня
            if (day == d[0]) {
                dayBtn.setBackground(Theme.YELLOW);
                dayBtn.setForeground(java.awt.Color.BLACK);
            }
            
            // Подсветка выходных
            int dayOfWeek = (firstDayOfWeek + day - 2) % 7 + 1;
            if (dayOfWeek >= 6) {
                dayBtn.setForeground(java.awt.Color.RED);
            }
            
            dayBtn.addActionListener(e -> {
                d[0] = dayNum;
                // Обновляем календарь
                java.awt.Container parent = ((JButton)e.getSource()).getParent();
                JPanel calGrid = (JPanel) parent;
                for (java.awt.Component comp : calGrid.getComponents()) {
                    if (comp instanceof JButton) {
                        JButton btn = (JButton) comp;
                        if (btn.getText().equals(String.valueOf(dayNum))) {
                            btn.setBackground(Theme.YELLOW);
                            btn.setForeground(java.awt.Color.BLACK);
                        } else {
                            btn.setBackground(javax.swing.UIManager.getColor("Button.background"));
                            btn.setForeground(java.awt.Color.BLACK);
                            // Восстанавливаем цвет выходных
                            int idx = java.util.Arrays.asList(calGrid.getComponents()).indexOf(btn);
                            if (idx >= 7) { // Пропускаем заголовки
                                int dow = (idx - 7) % 7;
                                if (dow >= 5) btn.setForeground(java.awt.Color.RED);
                            }
                        }
                    }
                }
            });
            
            grid.add(dayBtn);
        }
        
        calendarPanel.add(grid, java.awt.BorderLayout.CENTER);
        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    /** Стрелка влево: в Montserrat этого глифа нет, рисуем иконкой. */
    private static javax.swing.Icon leftTriangle() {
        return new javax.swing.Icon() {
            @Override public int getIconWidth() { return 9; }
            @Override public int getIconHeight() { return 12; }
            @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                g.setColor(java.awt.Color.WHITE);
                int[] px = { x + 9, x, x + 9 };
                int[] py = { y, y + 6, y + 12 };
                g.fillPolygon(px, py, 3);
            }
        };
    }

    /** Стрелка вправо: в Montserrat этого глифа нет, рисуем иконкой. */
    private static javax.swing.Icon rightTriangle() {
        return new javax.swing.Icon() {
            @Override public int getIconWidth() { return 9; }
            @Override public int getIconHeight() { return 12; }
            @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                g.setColor(java.awt.Color.WHITE);
                int[] px = { x, x + 9, x };
                int[] py = { y, y + 6, y + 12 };
                g.fillPolygon(px, py, 3);
            }
        };
    }

    private static class Entry {
        final long id; final String text;
        Entry(long id, String text) { this.id = id; this.text = text; }
    }

    private RepairForm() { }
}
