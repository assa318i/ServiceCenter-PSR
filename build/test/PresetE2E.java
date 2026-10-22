package psr.sc;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Пресет фильтра: сохранённая строка в БД → текст + флаг → фильтр таблицы. */
public class PresetE2E {

    public static void main(String[] args) throws Exception {
        Db.exec("DELETE FROM presets WHERE module='Заявки' AND name='тест'");
        Db.exec("INSERT INTO presets(module,name,spec) VALUES('Заявки','тест','сервер\nназначено мне')");

        final RepairsPanel[] p = new RepairsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            Session.name = "Кузнецов";
            p[0] = new RepairsPanel(null);
        });
        JTable table = (JTable) ((JScrollPane) p[0].getComponent(1)).getViewport().getView();
        JComboBox<?> presets = (JComboBox<?>) findCombo(p[0], "тест");
        JTextField query = (JTextField) find(p[0], JTextField.class);
        JCheckBox mine = (JCheckBox) findFlag(p[0], "назначено мне");

        int bad = 0;
        if (presets == null) { System.out.println("ПРОВАЛ: пресет не появился в списке"); bad++; }
        else {
            presets.setSelectedItem("тест");
            if (!"сервер".equals(query.getText())) { System.out.println("ПРОВАЛ: текст=" + query.getText()); bad++; }
            if (!mine.isSelected()) { System.out.println("ПРОВАЛ: флаг не восстановлен"); bad++; }
            if (table.getRowCount() != 1) { System.out.println("ПРОВАЛ: строк " + table.getRowCount()); bad++; }
        }
        Db.exec("DELETE FROM presets WHERE module='Заявки' AND name='тест'");
        System.out.println(bad == 0 ? "OK: пресет восстановился" : "ОШИБОК: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    static Component findCombo(Container c, String item) {
        for (Component x : c.getComponents()) {
            if (x instanceof JComboBox) {
                JComboBox<?> cb = (JComboBox<?>) x;
                for (int i = 0; i < cb.getItemCount(); i++)
                    if (item.equals(cb.getItemAt(i))) return cb;
            }
            if (x instanceof Container) {
                Component r = findCombo((Container) x, item);
                if (r != null) return r;
            }
        }
        return null;
    }

    static Component find(Container c, Class<?> kind) {
        for (Component x : c.getComponents()) {
            if (kind.isInstance(x)) return x;
            if (x instanceof Container) {
                Component r = find((Container) x, kind);
                if (r != null) return r;
            }
        }
        return null;
    }

    static Component findFlag(Container c, String label) {
        for (Component x : c.getComponents()) {
            if (x instanceof JCheckBox && label.equals(((JCheckBox) x).getText())) return x;
            if (x instanceof Container) {
                Component r = findFlag((Container) x, label);
                if (r != null) return r;
            }
        }
        return null;
    }
}
