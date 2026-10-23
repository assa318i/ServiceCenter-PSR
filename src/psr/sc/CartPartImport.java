package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Импорт каталога заправочных материалов и ЗЧ картриджа из файла поставщика или выгрузки
 * программы заправки. Дубль ищется по артикулу, затем по названию, поэтому повторный импорт
 * обновляет граммовку и остатки на двух складах, а не заводит вторую фотобарабанную позицию.
 */
final class CartPartImport {

    static final int ARTICLE = 0, NAME = 1, GRAMS = 2, TONER = 3, REFILL = 4, STORE = 5,
        MIN = 6, PRICE = 7;
    static final String[] LABELS = { "Артикул", "Название ЗЧ", "Грамовка (тонер на картридж, г)",
        "Тонер на складе, г", "Склад заправщика, шт", "Склад кладовщика, шт", "Мин. норма",
        "Цена, руб." };
    // Подписи колонок в нижнем регистре и без пробелов — сличаем с Table.norm(заголовка).
    // «склад» в ключ не кладем: он есть и в «Склад заправщика», и в «Склад кладовщика».
    static final String[][] KEYS = {
        { "артикул", "код", "sku", "партномер", "partnumber", "штрихкод" },
        { "название", "наименование", "позиция", "товар", "запчасть", "name" },
        { "грамм", "грам", "вместим", "std" },
        { "тонер", "порош" },
        { "заправщ", "вработ" },
        { "кладовщ", "хранен" },
        { "мин", "норма", "min" },
        { "цена", "стоимость", "price", "прайс" },
    };

    static final Importer.Target TARGET = new Importer.Target() {
        @Override public String gen() { return "ЗЧ картриджей"; }
        @Override public String files() { return ".xlsx, .csv, .txt, .xml или HTML-таблица"; }
        @Override public String noun() { return "позиций каталога"; }
        @Override public String[] labels() { return LABELS; }
        @Override public String[][] keys() { return KEYS; }
        @Override public String check(int[] col) {
            return col[NAME] < 0 && col[ARTICLE] < 0
                ? "Укажите колонку названия или артикула — иначе ЗЧ нечем опознать" : null;
        }
        @Override public String note() {
            return "Количество штуками и граммы тонера в файле — это фактические остатки двух складов:"
                + " они заменятся у совпавших позиций. Журнал картриджей импорт не пишет (в отличие от"
                + " журнала склада). Пустая клетка файла ничего не затирает.";
        }
        @Override public int[] apply(Table t, int[] col, boolean dry) throws Exception {
            return CartPartImport.apply(t, col, dry);
        }
    };

    /** Кнопка «Импорт из файла» во вкладке каталога ЗЧ. */
    static void dialog(java.awt.Component parent) {
        if (!Session.edits(Roles.CARTRIDGES)) {
            Warn.show("Каталог ЗЧ картриджа из файла загружает тот, кто ведёт раздел «Картриджи»:"
                + " администратор, руководитель сервисных инженеров или заправщик");
            return;
        }
        Importer.dialog(parent, TARGET);
    }

    /** Применить таблицу к каталогу; dry=true только считает. {новых, обновлённых, пустых}. */
    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        final Map<String, Long> byArticle = new HashMap<>(), byName = new HashMap<>();
        Statement st0 = Db.get().createStatement();
        ResultSet rs0 = st0.executeQuery("SELECT id, article, name FROM cart_parts");
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

    private static void save(Long id, String name, String article, String[] row, int[] col,
            Map<String, Long> byArticle, Map<String, Long> byName) throws Exception {
        String grams = Table.cell(row, col[GRAMS]).trim();
        String toner = Table.cell(row, col[TONER]).trim();
        String refill = Table.cell(row, col[REFILL]).trim();
        String store = Table.cell(row, col[STORE]).trim();
        String min = Table.cell(row, col[MIN]).trim();
        String price = Table.cell(row, col[PRICE]).trim();
        if (id == null) {
            Db.exec("INSERT INTO cart_parts(article,name,grams_std,toner_g,qty_refill,qty_store,"
                + "min_qty,price) VALUES('" + Db.esc(article) + "','" + Db.esc(name) + "',"
                + Stock.num(Importer.number(grams)) + "," + Stock.num(Importer.number(toner)) + ","
                + Stock.num(Importer.number(refill)) + "," + Stock.num(Importer.number(store)) + ","
                + Stock.num(Importer.number(min)) + "," + Stock.num(Importer.number(price)) + ")");
            id = Stock.lastId();
            if (!article.isEmpty()) byArticle.put(Table.norm(article), id);
            if (!name.isEmpty()) byName.put(Table.norm(name), id);
            return;
        }
        StringBuilder set = new StringBuilder();
        if (!name.isEmpty()) set.append("name='").append(Db.esc(name)).append("',");
        if (!article.isEmpty()) set.append("article='").append(Db.esc(article)).append("',");
        num(set, "grams_std", grams);
        num(set, "toner_g", toner);
        num(set, "qty_refill", refill);
        num(set, "qty_store", store);
        num(set, "min_qty", min);
        num(set, "price", price);
        if (set.length() == 0) return;   // пустая строка файла не должна затирать каталог
        set.setLength(set.length() - 1);
        Db.exec("UPDATE cart_parts SET " + set + " WHERE id=" + id);
    }

    private static void num(StringBuilder set, String column, String raw) {
        if (!raw.trim().isEmpty()) set.append(column).append('=').append(Stock.num(Importer.number(raw))).append(',');
    }

    private CartPartImport() { }
}
