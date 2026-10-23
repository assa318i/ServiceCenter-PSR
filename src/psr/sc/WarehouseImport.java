package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Импорт склада из чужого файла (выгрузка 1С, прайс поставщика, таблица из Excel).
 * Дубль ищется по артикулу, поэтому повторный импорт того же файла обновляет остатки,
 * а не удваивает склад. Окно колонок и транзакция — общие, см. Importer.
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

    static final Importer.Target TARGET = new Importer.Target() {
        @Override public String gen() { return "склада"; }
        @Override public String files() { return ".xlsx, .csv, .txt, .xml или HTML-таблица"; }
        @Override public String noun() { return "позиций"; }
        @Override public String[] labels() { return LABELS; }
        @Override public String[][] keys() { return KEYS; }
        @Override public String check(int[] col) {
            return col[NAME] < 0 && col[ARTICLE] < 0
                ? "Укажите колонку наименования или артикула — иначе позицию нечем опознать" : null;
        }
        @Override public String note() {
            return "Остаток в файле — фактическое количество на складе: у совпавших позиций он"
                + " заменится, а в журнал склада попадёт разница. Пустая клетка файла ничего не затирает.";
        }
        @Override public int[] apply(Table t, int[] col, boolean dry) throws Exception {
            return WarehouseImport.apply(t, col, dry);
        }
    };

    /** Кнопка «Импорт из файла» в окне склада. */
    static void dialog(java.awt.Component parent) {
        if (!Session.isAdmin()) {
            Warn.show("Каталог и остатки склада из файла загружает только администратор: "
                + "импорт меняет весь справочник сразу");
            return;
        }
        Importer.dialog(parent, TARGET);
    }

    /** Индекс колонки (+1 для списка с «не брать» сверху) или 0, если не угадали. */
    static int guess(java.util.List<String> headers, int field) {
        return Importer.guess(headers, KEYS[field]);
    }

    /**
     * Применить таблицу к складу. dry=true только считает (для предпросмотра).
     * Возвращает {новых, обновлённых, пропущенных} — всё в одной транзакции.
     */
    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        final Map<String, Long> byArticle = new HashMap<>(), byName = new HashMap<>();
        Statement st0 = Db.get().createStatement();
        ResultSet rs0 = st0.executeQuery("SELECT id, article, name FROM items");
        while (rs0.next()) {
            String a = Table.norm(rs0.getString(2)), n = Table.norm(rs0.getString(3));
            if (!a.isEmpty() && !byArticle.containsKey(a)) byArticle.put(a, rs0.getLong(1));
            if (!n.isEmpty() && !byName.containsKey(n)) byName.put(n, rs0.getLong(1));
        }
        rs0.close();
        st0.close();
        return Importer.each(t, col, dry,
            (row, c, isDry) -> write(row, c, byArticle, byName, isDry));
    }

    /** Одна строка файла: −1 пустая, 0 обновлённая позиция, 1 новая. */
    private static int write(String[] row, int[] col, Map<String, Long> byArticle,
            Map<String, Long> byName, boolean dry) throws Exception {
        String name = Table.cell(row, col[NAME]).trim();
        String article = Table.cell(row, col[ARTICLE]).trim();
        if (name.isEmpty() && article.isEmpty()) return -1;
        Long id = !article.isEmpty() ? byArticle.get(Table.norm(article)) : null;
        if (id == null && !name.isEmpty()) id = byName.get(Table.norm(name));
        if (dry) return id == null ? 1 : 0;
        save(id, name, article, row, col, byArticle, byName);
        return id == null ? 1 : 0;
    }

    /** Добавить или обновить одну позицию; остаток меняется только через Stock — с записью в журнал. */
    private static void save(Long id, String name, String article, String[] row, int[] col,
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
    static double number(String s) { return Importer.number(s); }

    private WarehouseImport() { }
}
