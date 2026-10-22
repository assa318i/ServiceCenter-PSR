package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Наборы ЗЧ под единицу техники: каталог наборов и применение набора к заявке. */
public class KitsPanel extends JPanel {

    private final JTable kits = new JTable();

    public KitsPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(Theme.on("Новый набор", Theme.YELLOW, e -> editKit()));
        tools.add(Theme.on("Изменить состав", Theme.BLUE, e -> editKit(selected())));
        tools.add(Theme.on("Удалить", Theme.BRIGHT_RED, e -> deleteKit()));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> load()));
        add(tools, BorderLayout.NORTH);
        add(new JScrollPane(kits), BorderLayout.CENTER);
        Theme.styleTable(kits);
        load();
    }

    private Long selected() {
        int r = kits.getSelectedRow();
        return r < 0 ? null : (Long) kits.getValueAt(r, 0);
    }

    private void editKit() { editKit(null); }

    private void editKit(Long id) {
        if (!Session.edits(Roles.KITS)) {
            Warn.show("Каталоги наборов ведут администратор, руководитель сервисных инженеров и заправщик");
            return;
        }
        String name = JOptionPane.showInputDialog(this,
            "Название набора (например «Ремкомплект CPF-LBP6020»):");
        if (name == null || name.trim().isEmpty()) return;
        String type = JOptionPane.showInputDialog(this,
            "Тип техники, к которому привязан набор (например «Принтер Canon LBP6020»):");
        if (type == null) return;
        try {
            if (id == null)
                Db.exec("INSERT INTO kits(name,equipment_type) VALUES('" + Db.esc(name) + "','"
                    + Db.esc(type) + "')");
            else Db.exec("UPDATE kits SET name='" + Db.esc(name) + "', equipment_type='"
                + Db.esc(type) + "' WHERE id=" + id);
            id = idOf(name);
        } catch (Exception ex) { Warn.show(ex.getMessage()); return; }
        if (id == null) return;
        editParts(id);
        load();
    }

    private Long idOf(String name) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM kits WHERE name='" + Db.esc(name)
                 + "' ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) return rs.getLong(1);
        } catch (Exception ignored) { }
        return null;
    }

    private void editParts(long kitId) {
        StringBuilder cur = new StringBuilder("Состав набора (артикул|название|кол-во, построчно):\n");
        Vector<String> lines = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT article, name, qty FROM kit_parts WHERE kit_id="
                 + kitId)) {
            while (rs.next()) lines.add(rs.getString(1) + "|" + rs.getString(2) + "|"
                + WarehousePanel.fmt(rs.getDouble(3)));
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        for (String l : lines) cur.append(l).append('\n');
        String edited = (String) JOptionPane.showInputDialog(this, cur.toString(),
            "Состав набора", JOptionPane.PLAIN_MESSAGE, null, null, String.join("\n", lines));
        if (edited == null) return;
        try {
            Db.exec("DELETE FROM kit_parts WHERE kit_id=" + kitId);
            for (String l : edited.split("\n")) {
                String[] p = l.split("\\|");
                if (p.length >= 2 && !p[1].trim().isEmpty()) {
                    String art = p.length > 2 ? p[0].trim() : "";
                    String nm = p.length > 2 ? p[1].trim() : p[0].trim();
                    double q = p.length > 2 ? RepairForm.parse(p[2]) : 1;
                    Db.exec("INSERT INTO kit_parts(kit_id,article,name,qty) VALUES(" + kitId + ",'"
                        + Db.esc(art) + "','" + Db.esc(nm) + "'," + q + ")");
                }
            }
        } catch (Exception ex) { Warn.show("Формат строки: артикул|название|кол-во. " + ex.getMessage()); }
    }

    private void deleteKit() {
        Long id = selected();
        if (id == null) { Warn.show("Выберите набор"); return; }
        if (!Session.edits(Roles.KITS)) { Warn.show("Наборы удаляет администратор, руководитель или заправщик"); return; }
        try {
            Db.exec("DELETE FROM kit_parts WHERE kit_id=" + id);
            Db.exec("DELETE FROM kits WHERE id=" + id);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    void load() {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT k.id, k.name, k.equipment_type,"
                 + " (SELECT COUNT(*) FROM kit_parts p WHERE p.kit_id=k.id)"
                 + " FROM kits k ORDER BY k.name")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) rows.add(new Vector<>(java.util.Arrays.asList(rs.getLong(1),
                RepairsPanel.nz(rs.getString(2)), RepairsPanel.nz(rs.getString(3)),
                rs.getLong(4) + " поз.")));
            kits.setModel(new DefaultTableModel(rows, new Vector<String>(java.util.Arrays.asList(
                "№", "Набор", "Привязка к единице техники", "Состав"))) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    /**
     * Применение набора к заявке: список деталей; по кнопке — строка в примечание/историю
     * и создание заказов склада по совпадающим позициям.
     */
    public static void applyToRepair(javax.swing.JComponent parent, long repairId, String equipment) {
        Vector<String> names = new Vector<>();
        Vector<Long> ids = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name || '  (' || equipment_type || ')' FROM kits"
                 + " ORDER BY name")) {
            while (rs.next()) { names.add(rs.getString(2)); ids.add(rs.getLong(1)); }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (names.isEmpty()) { Warn.show("Каталог наборов пуст"); return; }
        JComboBox<String> pick = new JComboBox<>(names);
        for (int i = 0; i < names.size(); i++)
            if (equipment != null && !equipment.isEmpty()
                && names.get(i).toLowerCase().contains(firstWord(equipment.toLowerCase())))
                pick.setSelectedIndex(i);
        pick.setFont(Theme.font(14));
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {
            "Набор ЗЧ под единицу техники (подсказка: «" + RepairsPanel.nz(equipment) + "»):", pick },
            "Привязка набора", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        int picked = pick.getSelectedIndex();
        long kitId = ids.get(picked);
        String kitName = names.get(picked);
        StringBuilder parts = new StringBuilder();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT article, name, qty FROM kit_parts WHERE kit_id=" + kitId)) {
            while (rs.next())
                parts.append(RepairsPanel.nz(rs.getString(1))).append(' ')
                    .append(rs.getString(2)).append(" × ")
                    .append(WarehousePanel.fmt(rs.getDouble(3))).append("; ");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (parts.length() == 0) { Warn.show("В наборе нет деталей"); return; }
        try (Statement st = Db.get().createStatement()) {
            st.executeUpdate("UPDATE repairs SET notes = TRIM(notes || ' | набор: "
                + Db.esc(parts.toString()) + "'), rev=rev+1 WHERE id=" + repairId);
            Stream.log(repairId, "Применён набор «" + kitName + "»: " + parts);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        JOptionPane.showMessageDialog(parent, "Набор применён к заявке №" + repairId + ":\n" + parts,
            "Набор ЗЧ", JOptionPane.PLAIN_MESSAGE);
    }

    private static String firstWord(String s) {
        int i = s.indexOf(' ');
        return i < 0 ? s : s.substring(0, i);
    }
}
