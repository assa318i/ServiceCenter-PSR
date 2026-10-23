package psr.sc;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Машина импорта справочника из чужого файла: выбор файла, окно «какая колонка что значит»,
 * предпросмотр и применение. Формат файла определяет Table, поле ищется по заголовку, а
 * справочник задаёт только свои подписи и то, как строка попадает в базу.
 */
final class Importer {

    /** Один загружаемый справочник: подписи полей и запись строк. */
    interface Target {
        String gen();      // родительный падеж — «склада», идёт в заголовки окон
        String files();    // что принимает выборщик файлов
        String noun();     // родительный падеж множественного числа — «позиций»
        String[] labels();
        /** Синонимы заголовков: строчные и без пробелов — сличаются с Table.norm(заголовка). */
        String[][] keys();
        String check(int[] col);   // чем опознать строку; null — всё в порядке
        String note();     // что означают числа файла — абзац предпросмотра
        int[] apply(Table t, int[] col, boolean dry) throws Exception;   // {новых, обновлённых, пустых}
    }

    /** Одна строка файла: −1 пустая, 0 обновлена, 1 добавлена. В dry=true только считает. */
    interface Row {
        int apply(String[] row, int[] col, boolean dry) throws Exception;
    }

    /** Открыть выборщик и провести импорт. Права сотрудника проверяет вызывающая сторона. */
    static void dialog(java.awt.Component parent, Target tg) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Файл " + tg.gen() + " — " + tg.files());
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        Table t;
        try {
            Vector<String> prefer = new Vector<>();
            for (String[] k : tg.keys()) for (String s : k) prefer.add(s);
            t = Table.read(f, prefer.toArray(new String[0]));
        } catch (Exception ex) { Warn.show("Файл не прочитан.\n" + ex.getMessage()); return; }
        if (t.headers.isEmpty() || t.rows.isEmpty()) {
            Warn.show("В файле нет таблицы с заголовками — проверьте, что первая строка это заголовки колонок");
            return;
        }
        int[] col = choose(parent, tg, t, f);
        if (col == null) return;
        String bad = tg.check(col);
        if (bad != null) { Warn.show(bad); return; }
        try {
            int[] dry = tg.apply(t, col, true);
            if (JOptionPane.showConfirmDialog(parent, "Строк в файле: " + t.rows.size()
                    + "\nНовых " + tg.noun() + ": " + dry[0] + "\nУже в базе, обновится: " + dry[1]
                    + "\nПустых строк: " + dry[2] + "\n\n" + tg.note(),
                "Импорт " + tg.gen(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                    != JOptionPane.OK_OPTION) return;
            int[] n = tg.apply(t, col, false);
            JOptionPane.showMessageDialog(parent, "Импорт завершён: добавлено " + n[0]
                + ", обновлено " + n[1] + ", пустых строк пропущено " + n[2],
                "Импорт " + tg.gen(), JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show("Импорт отменён, база не изменена.\n" + ex.getMessage()); }
    }

    /** Окно соответствия колонок полям справочника; угаданные колонки уже выбраны. */
    static int[] choose(java.awt.Component parent, Target tg, Table t, File f) {
        String[] labels = tg.labels();
        Vector<JComboBox<String>> boxes = new Vector<>();
        for (int field = 0; field < labels.length; field++) {
            Vector<String> items = new Vector<>();
            items.add("— не брать —");
            for (String h : t.headers) items.add(h);
            JComboBox<String> box = new JComboBox<>(items);
            box.setFont(Theme.font(14));
            box.setSelectedIndex(guess(t.headers, tg.keys()[field]));
            boxes.add(box);
        }
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 12, 4));
        grid.setOpaque(false);
        for (int field = 0; field < labels.length; field++) {
            grid.add(new javax.swing.JLabel(labels[field] + ":"));
            grid.add(boxes.get(field));
        }
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {
            "Файл «" + f.getName() + "», колонок: " + t.headers.size()
                + ", строк: " + t.rows.size() + ". Проверьте, какие колонки что значат:", grid },
            "Импорт " + tg.gen() + ": колонки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return null;
        int[] col = new int[labels.length];
        for (int field = 0; field < labels.length; field++) col[field] = boxes.get(field).getSelectedIndex() - 1;
        return col;
    }

    /** Индекс колонки (+1 для списка с «не брать» сверху) или 0, если не угадали. */
    static int guess(java.util.List<String> headers, String[] keys) {
        int best = 0, bestScore = 0;
        for (int i = 0; i < headers.size(); i++) {
            String h = Table.norm(headers.get(i));
            if (h.isEmpty()) continue;
            for (String key : keys) {
                int score = h.equals(key) ? 3 : (h.startsWith(key) || h.contains(key) ? 2 : 0);
                if (score > bestScore) { bestScore = score; best = i + 1; }
            }
        }
        return best;
    }

    /** Прогон строк в одной транзакции: не получилось одной — не получилось ничего. */
    static int[] each(Table t, int[] col, boolean dry, Row row) throws Exception {
        int added = 0, updated = 0, skipped = 0;
        Connection c = Db.get();
        boolean auto = c.getAutoCommit();
        if (!dry) c.setAutoCommit(false);
        try {
            for (String[] r : t.rows) {
                int n = row.apply(r, col, dry);
                if (n < 0) skipped++; else if (n > 0) added++; else updated++;
            }
            if (!dry) c.commit();
        } catch (Exception ex) {
            if (!dry) c.rollback();
            throw ex;
        } finally {
            if (!dry) c.setAutoCommit(auto);
        }
        return new int[] { added, updated, skipped };
    }

    /** «1 234,50», «1.234,50», «1,234.50», «8,5» → число. Пустое и нечисловое — 0. */
    static double number(String s) {
        String t = s == null ? "" : s.replace("\u00A0", "").replace(" ", "").trim();
        if (t.isEmpty()) return 0;
        int comma = t.lastIndexOf(','), dot = t.lastIndexOf('.');
        // разделитель целого от дробного — тот, что правее; остальные это группировка тысяч
        if (comma > dot) t = t.replace(".", "").replace(',', '.');
        else if (dot > comma) t = t.replace(",", "");
        double d = RepairForm.parse(t);
        // «Infinity» и «NaN» — тоже слова, которые parseDouble понимает как число; в базу им не место
        return Double.isFinite(d) ? d : 0;
    }

    private Importer() { }
}
