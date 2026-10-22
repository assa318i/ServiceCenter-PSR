package psr.sc;

import java.util.Vector;
import java.util.regex.Pattern;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Строка фильтра как в EspoCRM: текст со звёздочкой-маской, флаги и сохранённые пресеты.
 * Фильтрует Java-код, а не SQL: LIKE/NOCASE в SQLite не понимает регистр кириллицы.
 */
public class FilterBar extends JPanel {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private final String module;
    private final Runnable reload;
    private final JTextField text = new JTextField(22);
    private final JComboBox<String> presets = new JComboBox<>();
    private final Vector<JCheckBox> flags = new Vector<>();
    private boolean filling;

    public FilterBar(String module, Runnable reload) {
        this.module = module;
        this.reload = reload;
        setOpaque(false);
        add(new JLabel("Фильтр: "));
        text.setFont(Theme.font(14));
        text.setToolTipText("Слово — подстрока, маска со * — шаблон (Canon 2500*, *328*). "
            + "Несколько слов через пробел — все сразу.");
        text.addActionListener(e -> reload.run());
        add(text);
        add(Theme.on("Сброс", Theme.GRAY_PANEL, e -> {
            clear();
            presets.setSelectedIndex(-1);
            reload.run();
        }));
        presets.setFont(Theme.font(14));
        presets.addActionListener(e -> {
            if (filling) return;
            String spec = specOf((String) presets.getSelectedItem());
            if (spec != null) { apply(spec); reload.run(); }
        });
        add(new JLabel("Пресет: "));
        add(presets);
        add(Theme.on("Сохранить", Theme.YELLOW, e -> save()));
        add(Theme.on("Удалить", Theme.BRIGHT_RED, e -> remove()));
        reloadPresets();
    }

    /** Дополнительный чекбокс-фильтр; попадает в пресеты. */
    public JCheckBox flag(String label) {
        JCheckBox cb = new JCheckBox(label);
        cb.setOpaque(false);
        cb.setFont(Theme.font(14));
        cb.addActionListener(e -> reload.run());
        flags.add(cb);
        add(cb);
        return cb;
    }

    public boolean on(JCheckBox cb) { return cb.isSelected(); }

    /** Снять текстовый фильтр и все флаги (без перезагрузки таблицы). */
    public void clear() {
        text.setText("");
        for (JCheckBox cb : flags) cb.setSelected(false);
    }

    /** True, если хоть одно значение проходит фильтр. Пустой фильтр проходит всё. */
    public boolean match(String... values) {
        for (String word : text.getText().trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            boolean hit = false;
            for (String v : values) if (v != null && hit(word, v)) hit = true;
            if (!hit) return false;
        }
        return true;
    }

    private static boolean hit(String word, String value) {
        String[] parts = word.split("\\*", -1);
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) re.append(".*");
            re.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(re.toString(), FLAGS).matcher(value).find();
    }

    private String spec() {
        StringBuilder sb = new StringBuilder(text.getText().trim());
        for (JCheckBox cb : flags) if (cb.isSelected()) sb.append('\n').append(cb.getText());
        return sb.toString();
    }

    private void apply(String spec) {
        String[] lines = spec.split("\n");
        text.setText(lines.length == 0 ? "" : lines[0]);
        for (JCheckBox cb : flags) {
            cb.setSelected(false);
            for (int i = 1; i < lines.length; i++)
                if (cb.getText().equals(lines[i])) cb.setSelected(true);
        }
    }

    private void reloadPresets() {
        // Первый addItem сам выбирается и дёргает ActionListener — без this.filling панель
        // перезагрузилась бы до того, как её поля инициализированы (RepairsPanel.bar создаётся
        // раньше lastFilter), и это окно «this.lastFilter is null».
        filling = true;
        presets.removeAllItems();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM presets WHERE module='"
                 + Db.esc(module) + "' ORDER BY name")) {
            while (rs.next()) presets.addItem(rs.getString(1));
        } catch (Exception ignored) { }
        filling = false;
    }

    private String specOf(String name) {
        if (name == null) return null;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT spec FROM presets WHERE module='"
                 + Db.esc(module) + "' AND name='" + Db.esc(name) + "'")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception ex) { Warn.show(ex.getMessage()); return null; }
    }

    private void save() {
        String name = JOptionPane.showInputDialog(this, "Название пресета:", "Пресет фильтра",
            JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        try {
            Db.exec("DELETE FROM presets WHERE module='" + Db.esc(module) + "' AND name='"
                + Db.esc(name) + "'");
            Db.exec("INSERT INTO presets(module,name,spec) VALUES('" + Db.esc(module) + "','"
                + Db.esc(name) + "','" + Db.esc(spec()) + "')");
        } catch (Exception ex) { Warn.show(ex.getMessage()); return; }
        reloadPresets();
        presets.setSelectedItem(name);
    }

    private void remove() {
        String name = (String) presets.getSelectedItem();
        if (name == null) { Warn.show("Выберите пресет"); return; }
        try {
            Db.exec("DELETE FROM presets WHERE module='" + Db.esc(module) + "' AND name='"
                + Db.esc(name) + "'");
        } catch (Exception ex) { Warn.show(ex.getMessage()); return; }
        reloadPresets();
    }
}
