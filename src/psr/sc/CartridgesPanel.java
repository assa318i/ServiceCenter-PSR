package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

/** Картриджи: каталог ЗЧ, два склада (заправщик/кладовщик), списание по граммовке, внутренний чат. */
public class CartridgesPanel extends JPanel {

    private final javax.swing.JTable parts = new javax.swing.JTable();
    private final javax.swing.JTable ops = new javax.swing.JTable();
    private final ChatTab chat = new ChatTab();

    public CartridgesPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.fontB(14));
        tabs.setBackground(Theme.GRAY_BG);
        tabs.addTab("Каталог ЗЧ картриджа", partsTab());
        tabs.addTab("Два склада: заправщик / кладовщик", moveTab());
        tabs.addTab("Списание по граммовке", gramsTab());
        tabs.addTab("Внутренний чат", chat);
        add(tabs, BorderLayout.CENTER);
        load();
    }

    private JPanel wrap(javax.swing.JTable t, JPanel tools) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        tools.setOpaque(false);
        p.add(tools, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        Theme.styleTable(t);
        return p;
    }

    // ─────────────────────────── Каталог ЗЧ ───────────────────────────
    private JPanel partsTab() {
        JPanel tools = new JPanel();
        tools.add(Theme.on("Добавить ЗЧ", Theme.YELLOW, e -> editPart(null)));
        tools.add(Theme.on("Изменить", Theme.BLUE, e -> editPart(selected(parts))));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> load()));
        return wrap(parts, tools);
    }

    private Long selected(javax.swing.JTable t) {
        int r = t.getSelectedRow();
        return r < 0 ? null : (Long) t.getValueAt(r, 0);
    }

    private void editPart(Long id) {
        if (!Session.edits(Roles.CARTRIDGES)) {
            Warn.show("Каталог ЗЧ ведут администратор, руководитель сервисных инженеров и заправщик");
            return;
        }
        JTextField article = f("Артикул", id, "article");
        JTextField name = f("Название ЗЧ (чип, вал, фотобарабан, тонер...)", id, "name");
        JTextField grams = f("Тонер на картридж, г (граммовка)", id, "grams_std");
        JTextField min = f("Мин. норма", id, "min_qty");
        JTextField price = f("Цена, руб.", id, "price");
        int ok = javax.swing.JOptionPane.showConfirmDialog(this, new Object[] {
            "Артикул:", article, "Название:", name,
            Theme.danger("Грамовка (тонер на 1 картридж, г):"), grams,
            "Мин. норма:", min, "Цена, руб.:", price },
            id == null ? "Новая ЗЧ картриджа" : "ЗЧ картриджа",
            javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok != javax.swing.JOptionPane.OK_OPTION) return;
        String sql = "article='" + Db.esc(article.getText()) + "', name='" + Db.esc(name.getText())
            + "', grams_std=" + RepairForm.parse(grams.getText()) + ", min_qty="
            + RepairForm.parse(min.getText()) + ", price=" + RepairForm.parse(price.getText());
        try {
            if (id == null) Db.exec("INSERT INTO cart_parts(article,name,grams_std,min_qty,price)"
                + " VALUES('" + Db.esc(article.getText()) + "','" + Db.esc(name.getText()) + "',"
                + RepairForm.parse(grams.getText()) + "," + RepairForm.parse(min.getText()) + ","
                + RepairForm.parse(price.getText()) + ")");
            else Db.exec("UPDATE cart_parts SET " + sql + " WHERE id=" + id);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    // ───────────────────── Два склада: заправщик/кладовщик ─────────────────────
    private JPanel moveTab() {
        JPanel tools = new JPanel();
        tools.add(Theme.on("Приход на склад заправщика", Theme.YELLOW, e -> stockOp(true)));
        tools.add(Theme.on("Приход на склад кладовщика", Theme.BLUE, e -> stockOp(false)));
        tools.add(Theme.on("Переместить заправщик → кладовщик", Theme.GRAY_PANEL, e -> move(true)));
        tools.add(Theme.on("Переместить кладовщик → заправщик", Theme.GRAY_PANEL, e -> move(false)));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> load()));
        JPanel p = wrap(ops, tools);
        p.add(new JLabel("Штуки ЗЧ учитываются раздельно: склад заправщика (в работе) и склад кладовщика (хранение)."),
            BorderLayout.SOUTH);
        return p;
    }

    private void stockOp(boolean toRefiller) {
        PartPick pick = pickPart("Приход: выберите позицию");
        if (pick == null) return;
        double qty = RepairForm.parse(pick.qty.getText());
        if (qty <= 0) { Warn.show("Нужно количество больше нуля"); return; }
        try {
            Db.exec("UPDATE cart_parts SET " + (toRefiller ? "qty_refill" : "qty_store")
                + " = " + (toRefiller ? "qty_refill" : "qty_store") + " + " + qty + " WHERE id=" + pick.id);
            Db.exec("INSERT INTO cart_ops(ts,part_id,op,amount,who,note) VALUES('" + RepairsPanel.stamp()
                + "'," + pick.id + ",'" + (toRefiller ? "ПРИХОД_ЗАПРАВЩИК" : "ПРИХОД_КЛАДОВЩИК") + "',"
                + qty + ",'" + Db.esc(Session.name) + "','" + Db.esc(pick.note.getText()) + "')");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    private void move(boolean r2s) {
        PartPick pick = pickPart("Перемещение: выберите позицию");
        if (pick == null) return;
        double qty = RepairForm.parse(pick.qty.getText());
        if (qty <= 0) { Warn.show("Нужно количество больше нуля"); return; }
        String from = r2s ? "qty_refill" : "qty_store", to = r2s ? "qty_store" : "qty_refill";
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT " + from + " FROM cart_parts WHERE id=" + pick.id);
            rs.next();
            double have = rs.getDouble(1);
            rs.close();
            if (have < qty) { Warn.show("Недостаточно на источающем складе: " + WarehousePanel.fmt(have)); return; }
            st.executeUpdate("UPDATE cart_parts SET " + from + " = " + from + " - " + qty
                + ", " + to + " = " + to + " + " + qty + " WHERE id=" + pick.id);
            Db.exec("INSERT INTO cart_ops(ts,part_id,op,amount,who,note) VALUES('" + RepairsPanel.stamp()
                + "'," + pick.id + ",'" + (r2s ? "ПЕРЕМЕЩ_З→К" : "ПЕРЕМЕЩ_К→З") + "'," + qty
                + ",'" + Db.esc(Session.name) + "','" + Db.esc(pick.note.getText()) + "')");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    static class PartPick {
        long id; JTextField qty = new JTextField(8); JTextField note = new JTextField(14);
    }

    private PartPick pickPart(String title) {
        Vector<String> names = new Vector<>();
        Vector<Long> ids = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, article || ' · ' || name"
                 + " || '  [З:' || qty_refill || '/К:' || qty_store || ']' FROM cart_parts"
                 + " ORDER BY name")) {
            while (rs.next()) { names.add(rs.getString(2)); ids.add(rs.getLong(1)); }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (names.isEmpty()) { Warn.show("Каталог ЗЧ пуст — добавьте позиции"); return null; }
        JComboBox<String> pick = new JComboBox<>(names);
        pick.setFont(Theme.font(14));
        PartPick pp = new PartPick();
        pp.qty.setFont(Theme.font(14));
        pp.note.setFont(Theme.font(14));
        int ok = javax.swing.JOptionPane.showConfirmDialog(this, new Object[] {
            title, pick, "Количество (шт):", pp.qty, "Примечание:", pp.note },
            title, javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok != javax.swing.JOptionPane.OK_OPTION) return null;
        pp.id = ids.get(pick.getSelectedIndex());
        return pp;
    }

    // ─────────────────────────── Граммовка ───────────────────────────
    private JPanel gramsTab() {
        JPanel tools = new JPanel();
        tools.add(Theme.on("Списать граммы по ремонту", Theme.BRIGHT_RED, e -> writeOff()));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> load()));
        JPanel p = wrap(ops, tools);
        p.add(Theme.danger("Списание граммов тонера уменьшает склад заправщика. Пустой тонер — заявка «Опасно»."),
            BorderLayout.SOUTH);
        return p;
    }

    private void writeOff() {
        PartPick pp = pickPart("Списание граммов: тонер-позиция");
        if (pp == null) return;
        double grams = RepairForm.parse(pp.qty.getText());
        if (grams <= 0) { Warn.show("Укажите граммы больше нуля"); return; }
        long repairId = activeRepair();
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT toner_g FROM cart_parts WHERE id=" + pp.id);
            rs.next();
            double have = rs.getDouble(1);
            rs.close();
            if (grams > have) {
                int go = javax.swing.JOptionPane.showConfirmDialog(this, Theme.danger(
                    "Тонера на складе заправщика всего " + WarehousePanel.fmt(have)
                    + " г. Списать " + WarehousePanel.fmt(grams) + " г в минус?"),
                    "Нехватка тонера", javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
                if (go != javax.swing.JOptionPane.YES_OPTION) return;
            }
            st.executeUpdate("UPDATE cart_parts SET toner_g = toner_g - " + grams + " WHERE id=" + pp.id);
            st.executeUpdate("INSERT INTO cart_ops(ts,part_id,op,amount,repair_id,who,note) VALUES('"
                + RepairsPanel.stamp() + "'," + pp.id + ",'СПИСАНИЕ_Г', " + grams + "," + repairId
                + ",'" + Db.esc(Session.name) + "','" + Db.esc(pp.note.getText()) + "')");
            if (repairId > 0) Stream.log(repairId, "Списано " + WarehousePanel.fmt(grams) + " г тонера");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    private long activeRepair() {
        Vector<String> names = new Vector<>();
        Vector<Long> ids = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, '№' || id || ' · ' || equipment"
                 + " FROM repairs WHERE status<>'DONE_ISSUED' ORDER BY id DESC")) {
            while (rs.next()) { names.add(rs.getString(2)); ids.add(rs.getLong(1)); }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (names.isEmpty()) return 0;
        String pick = (String) javax.swing.JOptionPane.showInputDialog(this,
            "По какой заявке списываем граммы?", "Граммовка", javax.swing.JOptionPane.PLAIN_MESSAGE,
            null, names.toArray(), names.get(0));
        return pick == null ? 0 : ids.get(names.indexOf(pick));
    }

    // ─────────────────────────── загрузка ───────────────────────────
    void load() {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, article, name, grams_std, toner_g,"
                 + " qty_refill, qty_store, min_qty, price FROM cart_parts ORDER BY name")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) rows.add(new Vector<>(java.util.Arrays.asList(rs.getLong(1),
                RepairsPanel.nz(rs.getString(2)), RepairsPanel.nz(rs.getString(3)),
                WarehousePanel.fmt(rs.getDouble(4)), WarehousePanel.fmt(rs.getDouble(5)),
                WarehousePanel.fmt(rs.getDouble(6)), WarehousePanel.fmt(rs.getDouble(7)),
                WarehousePanel.fmt(rs.getDouble(8)), WarehousePanel.fmt(rs.getDouble(9)))));
            parts.setModel(new DefaultTableModel(rows, new Vector<String>(java.util.Arrays.asList(
                "№", "Артикул", "Название ЗЧ", "Грамовка, г", "Тонер на складе, г",
                "Склад заправщика, шт", "Склад кладовщика, шт", "Мин. норма", "Цена, руб."))) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT o.ts, p.name, o.op, o.amount,"
                 + " CASE o.repair_id WHEN 0 THEN '' ELSE 'заявка №' || o.repair_id END, o.who, o.note"
                 + " FROM cart_ops o LEFT JOIN cart_parts p ON p.id=o.part_id ORDER BY o.id DESC LIMIT 200")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) rows.add(new Vector<>(java.util.Arrays.asList(
                RepairsPanel.nz(rs.getString(1)), RepairsPanel.nz(rs.getString(2)),
                ru(rs.getString(3)), WarehousePanel.fmt(rs.getDouble(4)),
                RepairsPanel.nz(rs.getString(5)), RepairsPanel.nz(rs.getString(6)),
                RepairsPanel.nz(rs.getString(7)))));
            ops.setModel(new DefaultTableModel(rows, new Vector<String>(java.util.Arrays.asList(
                "Дата", "Позиция", "Операция", "Кол-во/граммы", "Заявка", "Кто", "Примечание"))) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private static String ru(String op) {
        switch (op) {
            case "ПРИХОД_ЗАПРАВЩИК": return "Приход: склад заправщика";
            case "ПРИХОД_КЛАДОВЩИК": return "Приход: склад кладовщика";
            case "ПЕРЕМЕЩ_З→К": return "Перемещение З→К";
            case "ПЕРЕМЕЩ_К→З": return "Перемещение К→З";
            case "СПИСАНИЕ_Г": return "Списание по граммовке";
            default: return op;
        }
    }

    private JTextField f(String tip, Long id, String col) {
        JTextField t = new JTextField(16);
        t.setFont(Theme.font(15));
        t.setToolTipText(tip);
        if (id != null) {
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT " + col + " FROM cart_parts WHERE id=" + id)) {
                if (rs.next()) t.setText(rs.getString(1));
            } catch (Exception ignored) { }
        }
        return t;
    }

    /** Внутренний чат: инженер ↔ склад/менеджер. Обновление каждые 4 секунды. */
    static class ChatTab extends JPanel {
        private final JTextArea log = new JTextArea();
        private final JTextField input = new JTextField(40);

        ChatTab() {
            setLayout(new BorderLayout(4, 4));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            log.setEditable(false);
            log.setFont(Theme.font(14));
            log.setBackground(java.awt.Color.WHITE);
            add(new JScrollPane(log), BorderLayout.CENTER);
            JPanel b = new JPanel(new BorderLayout(4, 0));
            b.setOpaque(false);
            input.setFont(Theme.font(15));
            input.addActionListener(e -> send());
            b.add(input, BorderLayout.CENTER);
            b.add(Theme.on("Отправить", Theme.YELLOW, e -> send()), BorderLayout.EAST);
            add(b, BorderLayout.SOUTH);
            new Timer(4000, e -> load()).start();
            load();
        }

        private void send() {
            String text = input.getText().trim();
            if (text.isEmpty()) return;
            try {
                Db.exec("INSERT INTO chat(ts,author,role,text) VALUES('" + RepairsPanel.stamp()
                    + "','" + Db.esc(Session.name) + "','" + Session.role + "','" + Db.esc(text) + "')");
                input.setText("");
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
            load();
        }

        private void load() {
            StringBuilder sb = new StringBuilder();
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT ts, author, role, text FROM chat"
                     + " ORDER BY id DESC LIMIT 100")) {
                Vector<String> lines = new Vector<>();
                while (rs.next()) {
                    boolean urgent = rs.getString(4).startsWith("!!!") || "ОПАСНО".equals(rs.getString(4));
                    lines.add(0, rs.getString(1) + "  [" + Roles.title(rs.getString(3))
                        + " " + rs.getString(2) + "]: " + rs.getString(4)
                        + (urgent ? "   ⚠ КРАСНЫМ — важно" : ""));
                }
                for (String l : lines) sb.append(l).append('\n');
            } catch (Exception ignored) { }
            log.setText(sb.toString());
            log.setCaretPosition(0);
        }
    }
}
