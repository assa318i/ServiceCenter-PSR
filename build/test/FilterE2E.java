package psr.sc;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Сквозная проверка строки фильтра в окне «Заявки» на рабочих данных из data/psr.db. */
public class FilterE2E {

    static RepairsPanel panel;
    static JTable table;
    static JTextField query;
    static JCheckBox mine;
    static int bad;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Session.name = "Кузнецов";
            panel = new RepairsPanel(null);
        });
        table = (JTable) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
        query = (JTextField) find(panel, JTextField.class);
        mine = (JCheckBox) findFlag(panel, "назначено мне");

        check("", 3);
        check("сервер", 1);
        check("СЕРВЕР", 1);
        check("IBM*", 1);
        check("ноутбук", 1);
        check("сажа", 1);
        check("принт*", 1);
        check("иванов", 3);
        check("ромашка", 3);
        check("кузнецов", 1);
        check("иванов сажа", 1);
        check("сервер ноутбук", 0);
        check("нет-такого", 0);
        flag(false, 3);
        flag(true, 1);
        flagQuery(true, "сажа", 0);
        flagQuery(true, "сервер", 1);

        System.out.println(bad == 0 ? "OK: фильтр работает" : "ОШИБОК: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(String q, int want) {
        query.setText(q);
        panel.load();
        report("фильтр «" + q + "»", table.getRowCount(), want);
    }

    static void flag(boolean on, int want) {
        query.setText("");
        mine.setSelected(on);
        panel.load();
        report("флаг «назначено мне»=" + on, table.getRowCount(), want);
    }

    static void flagQuery(boolean on, String q, int want) {
        query.setText("");
        mine.setSelected(on);
        query.setText(q);
        panel.load();
        report("флаг=" + on + " + «" + q + "»", table.getRowCount(), want);
    }

    static void report(String what, int got, int want) {
        if (got != want) { bad++; System.out.println("ПРОВАЛ: " + what + " → строк " + got + ", ждалося " + want); }
        else System.out.println("ok    : " + what + " → строк " + got);
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
