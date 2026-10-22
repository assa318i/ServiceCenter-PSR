package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;

/**
 * Движение складских позиций. Остаток меняется только через move — тогда цифра в
 * «Склад (остатки)» и журнал приходов-расходов всегда объясняют друг друга, а не
 * расходятся, как было, когда приход писался напрямую в UPDATE.
 */
final class Stock {

    static final String PRIHOD = "ПРИХОД", RASHOD = "РАСХОД", IMPORT = "ИМПОРТ", CORRECT = "КОРРЕКТ";

    /** Подпись операции для журнала и сообщений. */
    static String ru(String op) {
        if (PRIHOD.equals(op)) return "Приход";
        if (RASHOD.equals(op)) return "Расход на ремонт";
        if (IMPORT.equals(op)) return "Импорт";
        if (CORRECT.equals(op)) return "Ручная корректировка";
        return op;
    }

    /** Текущий остаток позиции; 0, если позиции нет (move об этом скажет сам). */
    static double qty(long itemId) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT qty FROM items WHERE id=" + itemId)) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (Exception ex) { Warn.show(ex.getMessage()); return 0; }
    }

    static String name(long itemId) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM items WHERE id=" + itemId)) {
            return rs.next() ? rs.getString(1) : "";
        } catch (Exception ex) { return ""; }
    }

    /**
     * Изменить остаток на delta и вписать строку журнала. Минус не запрещаем: на складе
     * бывает, что израсходовали раньше, чем оприходовали, — но спрашивать должен вызывающий
     * код (см. allowMinus), поэтому здесь только считаем.
     */
    static double move(long itemId, String op, double delta, long repairId, String note) throws Exception {
        String now = RepairsPanel.stamp();
        double after;
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT qty FROM items WHERE id=" + itemId);
            if (!rs.next()) throw new Exception("Позиция склада №" + itemId + " не найдена");
            after = rs.getDouble(1) + delta;
            rs.close();
            st.executeUpdate("UPDATE items SET qty=" + num(after) + ", last_op='" + now + "' WHERE id=" + itemId);
            st.executeUpdate("INSERT INTO stock_ops(ts,item_id,op,amount,repair_id,who,note) VALUES('"
                + now + "'," + itemId + ",'" + op + "'," + num(delta) + "," + repairId + ",'"
                + Db.esc(Session.name) + "','" + Db.esc(note) + "')");
        }
        return after;
    }

    /** Число для SQL-литерала: всегда точка, независимо от локали Windows. */
    static String num(double d) { return String.valueOf(d); }

    /**
     * Остаток задан руками — при создании позиции или в окне склада. В журнале это отдельная
     * строка: иначе цифра в «Склад (остатки)» перестала бы объясняться движением.
     */
    static void correct(long itemId, double after, String note) throws Exception {
        double delta = after - qty(itemId);
        if (delta != 0) move(itemId, CORRECT, delta, 0, note);
    }

    /** № только что добавленной позиции (last_insert_rowid того же соединения). */
    static long lastId() throws Exception {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Позиция каталога по артикулу, а при пустом артикуле — по названию. null — в каталоге такого нет. */
    static Long matchItem(String article, String name) {
        String a = Table.norm(article), n = Table.norm(name);
        if (a.isEmpty() && n.isEmpty()) return null;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, article, name FROM items ORDER BY id")) {
            while (rs.next()) {
                if (!a.isEmpty() && a.equals(Table.norm(rs.getString(2)))) return rs.getLong(1);
                if (!n.isEmpty() && n.equals(Table.norm(rs.getString(3)))) return rs.getLong(1);
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        return null;
    }

    /** False — пользователь не согласен списывать в минус (красным показываем, куда уйдёт). */
    static boolean allowMinus(java.awt.Component parent, double after) {
        if (after >= 0) return true;
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {
            Theme.danger("Остаток уйдёт в минус: " + WarehousePanel.fmt(after)),
            "Такого на складе нет — списать всё равно?" }, "Списание в минус",
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        return ok == JOptionPane.OK_OPTION;
    }

    /**
     * «ЗЧ на заявку»: инженер отмечает, что конкретная позиция ушла на этот ремонт.
     * Право — тот же доступ к складу, что и для заказов: списание это часть работы по заявке,
     * а не ведение справочника и норм (их правит только администратор и руководитель).
     */
    static void consumeFromRepair(javax.swing.JComponent parent, long repairId) {
        if (!Session.may(Roles.WAREHOUSE)) {
            Warn.show("Раздел «Склад» вашей роли закрыт — отметить списание запчастей нельзя");
            return;
        }
        Vector<String> names = new Vector<>();
        Vector<Long> ids = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, article, unit, qty FROM items ORDER BY name")) {
            while (rs.next()) {
                names.add(RepairsPanel.nz(rs.getString(3)) + "  " + rs.getString(2)
                    + "  (остаток " + WarehousePanel.fmt(rs.getDouble(5)) + " " + rs.getString(4) + ")");
                ids.add(rs.getLong(1));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); return; }
        if (names.isEmpty()) { Warn.show("Склад пуст — сначала добавьте позиции"); return; }
        JComboBox<String> pick = new JComboBox<>(names);
        pick.setFont(Theme.font(15));
        javax.swing.JTextField howMany = new javax.swing.JTextField("1", 8);
        howMany.setFont(Theme.font(15));
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {
            "Позиция склада:", pick, Theme.danger("Сколько списано на ремонт:"), howMany },
            "ЗЧ на заявку №" + repairId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        double n = RepairForm.parse(howMany.getText());
        if (n <= 0) { Warn.show("Количество должно быть больше нуля"); return; }
        long itemId = ids.get(pick.getSelectedIndex());
        if (!allowMinus(parent, qty(itemId) - n)) return;
        try {
            move(itemId, RASHOD, -n, repairId, "заявка №" + repairId);
            Stream.log(repairId, "Списано со склада: " + name(itemId) + " × " + WarehousePanel.fmt(n));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    /**
     * Позиция по коду со сканера: наша наклейка PSR-P<№>, штрихкод из справочника поставщика
     * или артикул. Регистр не важен (для латиницы и цифр). null — не найдено.
     */
    static Long find(String code) {
        String c = code == null ? "" : code.trim();
        if (c.isEmpty()) return null;
        if (c.regionMatches(true, 0, "PSR-P", 0, 5)) {
            try { return Long.valueOf(c.substring(5).replaceAll("[^0-9].*$", "")); }
            catch (Exception ignored) { return null; }
        }
        String q = Db.esc(c.toUpperCase(java.util.Locale.ROOT));
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM items WHERE UPPER(barcode)='" + q
                 + "' OR UPPER(article)='" + q + "' ORDER BY id LIMIT 1")) {
            return rs.next() ? rs.getLong(1) : null;
        } catch (Exception ex) { Warn.show(ex.getMessage()); return null; }
    }

    private Stock() { }
}
