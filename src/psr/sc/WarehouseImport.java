package psr.sc;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Импорт склада из чужого файла (выгрузка 1С, прайс поставщика, таблица из Excel).
 * Формат определяет Table по сигнатуре байтов, а какие колонки что значат выбирает
 * человек: подписи в чужих файлах предсказуемы ровно до первого поставщика.
 * Дубль ищется по артикулу, поэтому повторный импорт того же файла обновляет остатки,
 * а не удваивает склад.
 */
final class WarehouseImport {

    static final int ARTICLE = 0, NAME = 1, GROUP = 2, UNIT = 3, QTY = 4, MIN = 5, PRICE = 6;
    static final String[] LABELS = { "Артикул", "Наименование", "Группа", "Ед. изм.",
        "Количество (фактический остаток)", "Минимальная норма", "Цена, руб." };

    /** Заголовки-синонимы для предвыбора колонки (сличиваются через Table.norm). */
    static final String[][] KEYS = {
        { "артикул", "код", "sku", "штрихкод", "partnumber", "itemcode" },
        { "наименование", "название", "товар", "позиция", "описание", "name", "title" },
        { "группа", "категория", "подгруппа", "вид", "group" },
        { "ед", "единица", "упаковка", "unit" },
        { "количество", "остаток", "qty", "колво", "число" },
        { "мин", "норма", "min" },
        { "цена", "стоимость", "price", "прайс" },
    };

    /** Кнопка «Импорт из файла» в окне склада. */
    static void dialog(java.awt.Component parent) {
        if (!Session.isAdmin()) {
            Warn.show("Каталог и остатки склада из файла загружает только администратор: "
                + "импорт меняет весь справочник сразу");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Файл склада — .xlsx, .csv, .txt, .xml или HTML-таблица");
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        Table t;
        try {
            Vector<String> prefer = new Vector<>();
            for (String[] k : KEYS) for (String s : k) prefer.add(s);
            t = Table.read(f, prefer.toArray(new String[0]));
        } catch (Exception ex) { Warn.show("Файл не прочитан.\n" + ex.getMessage()); return; }
        if (t.headers.isEmpty() || t.rows.isEmpty()) {
            Warn.show("В файле нет таблицы с заголовками — проверьте, что первая строка это заголовки колонок");
            return;
        }
        int[] col = choose(parent, t, f);
        if (col == null) return;
        if (col[NAME] < 0 && col[ARTICLE] < 0) {
            Warn.show("Укажите колонку наименования или артикула — иначе позицию нечем опознать");
            return;
        }
        try {
            int[] dry = apply(t, col, true);
            String text = "Строк в файле: " + t.rows.size()
                + "\nНовых позиций: " + dry[0] + "\nОбновлено по артикулу или названию: " + dry[1]
                + "\nПустых строк: " + dry[2]
                + "\n\nОстаток в файле означает фактическое количество на складе: у совпавших"
                + " позиций он заменится, а в журнал попадёт разница.";
            if (JOptionPane.showConfirmDialog(parent, text, "Импорт склада",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int[] n = apply(t, col, false);
            JOptionPane.showMessageDialog(parent, "Импорт завершён: добавлено " + n[0]
                + ", обновлено " + n[1] + ", пустых строк пропущено " + n[2], "Импорт склада",
                JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show("Импорт отменён, база не изменена.\n" + ex.getMessage()); }
    }

    /** Окно «какая колонка что значит»; уже угаданные колонки стоят выбранными. */
    private static int[] choose(java.awt.Component parent, Table t, File f) {
        Vector<JComboBox<String>> boxes = new Vector<>();
        for (int field = 0; field < LABELS.length; field++) {
            Vector<String> items = new Vector<>();
            items.add("— не брать —");
            for (String h : t.headers) items.add(h);
            JComboBox<String> box = new JComboBox<>(items);
            box.setFont(Theme.font(14));
            box.setSelectedIndex(guess(t.headers, field));
            boxes.add(box);
        }
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 12, 4));
        grid.setOpaque(false);
        for (int field = 0; field < LABELS.length; field++) {
            grid.add(new javax.swing.JLabel(LABELS[field] + ":"));
            grid.add(boxes.get(field));
        }
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {
            "Файл «" + f.getName() + "», колонок: " + t.headers.size()
                + ", строк: " + t.rows.size() + ". Проверьте, какие колонки что значат:", grid },
            "Импорт склада: колонки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return null;
        int[] col = new int[LABELS.length];
        for (int field = 0; field < LABELS.length; field++) col[field] = boxes.get(field).getSelectedIndex() - 1;
        return col;
    }

    /** Индекс колонки (+1 для списка с «не брать» сверху) или 0, если не угадали. */
    static int guess(java.util.List<String> headers, int field) {
        int best = 0, bestScore = 0;
        for (int i = 0; i < headers.size(); i++) {
            String h = Table.norm(headers.get(i));
            if (h.isEmpty()) continue;
            for (String key : KEYS[field]) {
                int score = h.equals(key) ? 3 : (h.startsWith(key) || h.contains(key) ? 2 : 0);
                if (score > bestScore) { bestScore = score; best = i + 1; }
            }
        }
        return best;
    }

    /**
     * Применить таблицу к складу. dry=true только считает (для предпросмотра).
     * Возвращает {новых, обновлённых, пропущенных}. Одна транзакция: не получилось строки —
     * не получилось ничего, склад не остаётся наполовину загруженным.
     */
    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        Map<String, Long> byArticle = new HashMap<>(), byName = new HashMap<>();
        Statement st0 = Db.get().createStatement();
        ResultSet rs0 = st0.executeQuery("SELECT id, article, name FROM items");
        while (rs0.next()) {
            String a = Table.norm(rs0.getString(2)), n = Table.norm(rs0.getString(3));
            if (!a.isEmpty() && !byArticle.containsKey(a)) byArticle.put(a, rs0.getLong(1));
            if (!n.isEmpty() && !byName.containsKey(n)) byName.put(n, rs0.getLong(1));
        }
        rs0.close();
        st0.close();
        int added = 0, updated = 0, skipped = 0;
        Connection c = Db.get();
        boolean auto = c.getAutoCommit();
        if (!dry) c.setAutoCommit(false);
        try {
            for (String[] row : t.rows) {
                String name = Table.cell(row, col[NAME]).trim();
                String article = Table.cell(row, col[ARTICLE]).trim();
                if (name.isEmpty() && article.isEmpty()) { skipped++; continue; }
                Long id = !article.isEmpty() ? byArticle.get(Table.norm(article)) : null;
                if (id == null && !name.isEmpty()) id = byName.get(Table.norm(name));
                if (dry) { if (id == null) added++; else updated++; continue; }
                if (id == null) added++; else updated++;
                write(id, name, article, row, col, byArticle, byName);
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

    /** Добавить или обновить одну позицию; остаток меняется только через Stock — с записью в журнал. */
    private static void write(Long id, String name, String article, String[] row, int[] col,
            Map<String, Long> byArticle, Map<String, Long> byName) throws Exception {
        String unit = Table.cell(row, col[UNIT]).trim();
        String grp = Table.cell(row, col[GROUP]).trim();
        String rawQty = Table.cell(row, col[QTY]);
        String rawMin = Table.cell(row, col[MIN]);
        String rawPrice = Table.cell(row, col[PRICE]);
        if (id == null) {
            Db.exec("INSERT INTO items(name,unit,qty,min_qty,price,article,grp) VALUES('"
                + Db.esc(name) + "','" + Db.esc(unit.isEmpty() ? "шт" : unit) + "',0,"
                + Stock.num(number(rawMin)) + "," + Stock.num(number(rawPrice)) + ",'"
                + Db.esc(article) + "','" + Db.esc(grp) + "')");
            id = Stock.lastId();
            if (!article.isEmpty()) byArticle.put(Table.norm(article), id);
            if (!name.isEmpty()) byName.put(Table.norm(name), id);
        } else {
            StringBuilder set = new StringBuilder();
            if (!name.isEmpty()) set.append("name='").append(Db.esc(name)).append("',");
            if (!article.isEmpty()) set.append("article='").append(Db.esc(article)).append("',");
            if (!grp.isEmpty()) set.append("grp='").append(Db.esc(grp)).append("',");
            if (!unit.isEmpty()) set.append("unit='").append(Db.esc(unit)).append("',");
            if (!rawMin.trim().isEmpty()) set.append("min_qty=").append(Stock.num(number(rawMin))).append(',');
            if (!rawPrice.trim().isEmpty()) set.append("price=").append(Stock.num(number(rawPrice))).append(',');
            if (set.length() == 0) return;   // пустая строка файла не должна затирать справочник
            set.setLength(set.length() - 1);
            Db.exec("UPDATE items SET " + set + " WHERE id=" + id);
        }
        if (col[QTY] < 0 || rawQty.trim().isEmpty()) return;
        double delta = number(rawQty) - Stock.qty(id);
        if (delta != 0) Stock.move(id, Stock.IMPORT, delta, 0, "импорт из файла");
    }

    /** «1 234,50», «1.234,50», «1,234.50», «8,5» → число. Пустое и нечисловое — 0. */
    static double number(String s) {
        String t = s == null ? "" : s.replace("\u00A0", "").replace(" ", "").trim();
        if (t.isEmpty()) return 0;
        int comma = t.lastIndexOf(','), dot = t.lastIndexOf('.');
        // разделитель целого от дробного — тот, что правее; остальные это группировка тысяч
        if (comma > dot) t = t.replace(".", "").replace(',', '.');
        else if (dot > comma) t = t.replace(",", "");
        return RepairForm.parse(t);
    }

    private WarehouseImport() { }
}
