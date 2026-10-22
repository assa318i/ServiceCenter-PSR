package psr.sc;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * Склад: остаток меняется только через Stock, и каждая смена цифры оставляет строку в журнале.
 * Проверяется приход по заказу, расход на заявку и на набор, ручная правка, импорт из чужого
 * файла (повтор по артикулу не плодит дублей, пустая клетка остатка не обнуляет склад), поиск
 * позиции по коду со сканера и матрица прав. База — своя (-Dpsr.data=build\test\tmp-wh),
 * рабочих позиций из data\psr.db проверка не касается.
 */
public class WarehouseCheck {

    static int bad;

    public static void main(String[] args) throws Exception {
        Db.get();
        check("проверка идёт на отдельной базе", !Db.DATA_DIR.getPath().equals("data"));
        wipe();
        schema();
        moves();
        matching();
        importFile();
        kit();
        roles();
        ui();
        System.out.println(bad == 0 ? "ВСЁ ЧИСТО" : "ЕСТЬ ЗАМЕЧАНИЯ: " + bad);
        if (bad > 0) System.exit(1);
    }

    private static void wipe() throws Exception {
        for (String t : new String[] { "stock_ops", "stock_orders", "kit_parts", "kits", "items" })
            Db.exec("DELETE FROM " + t);
    }

    private static void schema() throws Exception {
        List<String> cols = new ArrayList<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(items)")) {
            while (rs.next()) cols.add(rs.getString(2));
        }
        for (String c : new String[] { "article", "grp", "barcode", "last_op" })
            check("у позиции склада есть поле «" + c + "»", cols.contains(c));
        List<String> tables = new ArrayList<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        check("журнал движений склада заведён", tables.contains("stock_ops"));
    }

    private static void moves() throws Exception {
        long id = item("Вал первичный", "VAL-001", "шт");
        Stock.move(id, Stock.PRIHOD, 10, 0, "заказ №1");
        check("приход поднял остаток", eq(Stock.qty(id), 10));
        check("приход записан в журнал", op(id, Stock.PRIHOD) != null);
        check("приход помечен в позиции по дате", !RepairsPanel.nz(lastOp(id)).isEmpty());
        check("в журнале записано, кто двигал", !RepairsPanel.nz(who(id)).isEmpty());

        Stock.move(id, Stock.RASHOD, -3, 777, "заявка №777");
        check("расход уменьшил остаток", eq(Stock.qty(id), 7));
        check("журнал помнит заявку", op(id, Stock.RASHOD).endsWith("|777|-3.0"));

        int n = ops(id);
        Stock.correct(id, 7, "правка в окне склада");
        check("правка без изменения остатка журнал не засоряет", ops(id) == n);
        Stock.correct(id, 9, "правка в окне склада");
        check("ручная правка меняет остаток", eq(Stock.qty(id), 9));
        check("ручная правка видна в журнале", Stock.ru(Stock.CORRECT).equals("Ручная корректировка")
            && op(id, Stock.CORRECT) != null);
        Stock.move(id, Stock.RASHOD, -20, 0, "списание в минус");
        check("списание больше остатка уходит в минус (спрашивает вызывающий код)",
            eq(Stock.qty(id), -11));
        check("подписи операций по-русски", Stock.ru(Stock.PRIHOD).equals("Приход")
            && Stock.ru(Stock.RASHOD).equals("Расход на ремонт")
            && Stock.ru(Stock.IMPORT).equals("Импорт"));
    }

    private static void matching() throws Exception {
        Long id = item("Фотобарабан верхний", "OVP-77", "шт");
        Db.exec("UPDATE items SET barcode='4600000000001' WHERE id=" + id);
        check("артикул находится без учёта регистра", id.equals(Stock.matchItem("ovp-77", "")));
        check("название находится, когда артикула нет", id.equals(Stock.matchItem("", "ФОТОБАРАБАН ВЕРХНИЙ")));
        check("«ё» и лишние пробелы поиску не мешают",
            id.equals(Stock.matchItem("", "  фотобарабан верхний  ")));
        check("того, чего нет, поиск не выдаёт", Stock.matchItem("ХЗ-000", "нет такой позиции") == null);
        check("код нашей наклейки позиции", id.equals(Stock.find("PSR-P" + id)));
        check("штрихкод поставщика", id.equals(Stock.find("4600000000001")));
        check("артикул, продиктованный с этикетки", id.equals(Stock.find("OVP-77")));
        check("код заявки позицией не считается", Stock.find("PSR-R1") == null);
        check("пустой скан не находится", Stock.find("  ") == null);
    }

    private static void importFile() throws Exception {
        check("число из файла: «1 234,50»", WarehouseImport.number("1 234,50") == 1234.5);
        check("число из файла: «1.234,50»", WarehouseImport.number("1.234,50") == 1234.5);
        check("число из файла: «1,234.50»", WarehouseImport.number("1,234.50") == 1234.5);
        check("число из файла: пустая клетка — ноль", WarehouseImport.number("") == 0);
        check("число из файла: текст — ноль", WarehouseImport.number("нет данных") == 0);

        List<String> hs = Arrays.asList("Артикул", "Наименование", "Группа", "Ед. изм.",
            "Количество", "Минимальная норма", "Цена");
        check("колонки файла угадываются по подписям",
            WarehouseImport.guess(hs, WarehouseImport.ARTICLE) == 1
                && WarehouseImport.guess(hs, WarehouseImport.NAME) == 2
                && WarehouseImport.guess(hs, WarehouseImport.GROUP) == 3
                && WarehouseImport.guess(hs, WarehouseImport.UNIT) == 4
                && WarehouseImport.guess(hs, WarehouseImport.QTY) == 5
                && WarehouseImport.guess(hs, WarehouseImport.MIN) == 6
                && WarehouseImport.guess(hs, WarehouseImport.PRICE) == 7);
        check("остаток угадывается и по другой подписи",
            WarehouseImport.guess(Arrays.asList("Наименование", "Остаток на складе"),
                WarehouseImport.QTY) == 2);

        int[] col = { 0, 1, 2, 3, 4, 5, 6 };
        Table t = csv("ART-IMP-1;Барабан первичный;Барабаны;шт;12;2;1 234,50",
            "ART-IMP-2;Чип региона;Чипы;шт;3;1;350");
        int before = count("items");
        int[] dry = WarehouseImport.apply(t, col, true);
        check("предпросмотр считает новые позиции", dry[0] == 2 && dry[1] == 0);
        check("предпросмотр базу не трогает", count("items") == before);

        int[] n = WarehouseImport.apply(t, col, false);
        check("импорт добавил две позиции", n[0] == 2 && n[1] == 0 && count("items") == before + 2);
        long b = idOf("ART-IMP-1");
        check("остаток из файла встал как факт", eq(Stock.qty(b), 12));
        check("группа и цена из файла сохранены", grp(b).equals("Барабаны") && price(b) == 1234.5);
        check("импорт записан в журнал", op(b, Stock.IMPORT) != null);

        Table again = csv("ART-IMP-1;Барабан первичный;Барабаны;шт;12;2;1 234,50",
            "ART-IMP-2;Чип региона;Чипы;шт;3;1;350");
        int[] twice = WarehouseImport.apply(again, col, false);
        check("повтор файла не плодит дубли", twice[0] == 0 && twice[1] == 2
            && count("items") == before + 2);
        check("повтор файла остаток не удваивает", eq(Stock.qty(b), 12));

        Table emptyQty = csv("ART-IMP-1;Барабан первичный;Барабаны;шт;;2;1 234,50");
        WarehouseImport.apply(emptyQty, col, false);
        check("пустая клетка остатка склад не обнуляет", eq(Stock.qty(b), 12));

        Table fact = csv("ART-IMP-1;Барабан первичный;Барабаны;шт;5;2;1 234,50");
        WarehouseImport.apply(fact, col, false);
        check("пересчёт по файлу показывает разницу в журнале",
            eq(Stock.qty(b), 5) && op(b, Stock.IMPORT).endsWith("|-7.0"));
    }

    private static void kit() throws Exception {
        long enough = item("Плёнка термозакрепления", "FILM-1", "шт");
        Stock.move(enough, Stock.PRIHOD, 5, 0, "заказ №2");
        long shortItem = item("Ролик подачи", "ROL-9", "шт");
        Stock.move(shortItem, Stock.PRIHOD, 1, 0, "заказ №3");
        long kit = testKit();
        part(kit, "film-1", "Плёнка термозакрепления", 2);
        part(kit, "ROL-9", "Ролик подачи", 4);
        part(kit, "", "Лезвие очистки", 1);

        String sum = KitsPanel.takeFromStock(kit, "Набор тестовый", 4242);
        check("набор списывает то, что есть на складе", eq(Stock.qty(enough), 3));
        check("расход набора в журнале с заявкой", op(enough, Stock.RASHOD).contains("|4242|"));
        check("из позиции уходит ровно столько, сколько нужно", eq(Stock.qty(shortItem), 0));
        check("на нехватку создан заказ склада", eq(orderQty(shortItem), 3.0));
        check("заказ на нехватку помечен набором", orderNote(shortItem).contains("Набор тестовый"));
        check("детали вне каталога не выдумываются", sum.contains("Нет в каталоге")
            && sum.contains("Лезвие очистки"));
        check("сумма расходует то, что есть, и дозаказывает остальное",
            sum.contains("Списано со склада") && sum.contains("Созданы заказы"));
    }

    private static void roles() {
        check("склад открыт инженеру", Roles.may("ENGINEER", Roles.WAREHOUSE));
        check("инженер каталог и нормы не правит", !Roles.edits("ENGINEER", Roles.WAREHOUSE));
        check("заправщик склад видит", Roles.may("REFILLER", Roles.WAREHOUSE));
        check("руководитель и админ каталог правят",
            Roles.edits("SERVICE_LEAD", Roles.WAREHOUSE) && Roles.edits("ADMIN", Roles.WAREHOUSE));
        for (String code : new String[] { "LAWYER", "CALL_CENTER", "DRIVER" })
            check("расходовать ЗЧ «" + Roles.title(code) + "» некому",
                !Roles.may(code, Roles.WAREHOUSE));
    }

    private static void ui() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            WarehousePanel p = new WarehousePanel();
            List<String> buttons = new ArrayList<>();
            collect(p, buttons);
            check("есть кнопка импорта из файла", buttons.contains("Импорт из файла"));
            check("есть кнопка поиска по штрихкоду", buttons.contains("Найти по штрихкоду"));
            check("есть кнопка этикетки позиции", buttons.contains("Этикетка позиции"));
            List<String> tabs = new ArrayList<>();
            for (Component c : all(p))
                if (c instanceof JTabbedPane)
                    for (int i = 0; i < ((JTabbedPane) c).getTabCount(); i++)
                        tabs.add(((JTabbedPane) c).getTitleAt(i));
            check("вкладка «Журнал склада» открыта", tabs.contains("Журнал склада"));
            List<String> heads = new ArrayList<>(), cells = cells(p);
            for (Component c : all(p))
                if (c instanceof JTable)
                    for (int i = 0; i < ((JTable) c).getColumnCount(); i++)
                        heads.add(((JTable) c).getColumnName(i));
            check("в остатках видны артикул и группа",
                heads.contains("Артикул") && heads.contains("Группа"));
            check("в остатках видно, когда позицию двигали", heads.contains("Обновлена"));
        check("числа остатков без хвоста «.0»", cells.contains("-11") && !cells.contains("-11.0"));
            check("журнал показывает приход по-русски", cells.contains("Приход"));
            check("журнал показывает движение со знаком", cells.contains("+10"));
        });
    }

    // ─────────────────────────────────── помощники ───────────────────────────────────

    private static long item(String name, String article, String unit) throws Exception {
        Db.exec("INSERT INTO items(name,article,unit,qty,min_qty,price) VALUES('" + Db.esc(name)
            + "','" + Db.esc(article) + "','" + unit + "',0,0,0)");
        return Stock.lastId();
    }

    private static void part(long kitId, String article, String name, double qty) throws Exception {
        Db.exec("INSERT INTO kit_parts(kit_id,article,name,qty) VALUES(" + kitId + ",'"
            + Db.esc(article) + "','" + Db.esc(name) + "'," + Stock.num(qty) + ")");
    }

    private static Table csv(String... lines) throws Exception {
        StringBuilder sb = new StringBuilder(
            "Артикул;Наименование;Группа;Ед. изм.;Количество;Минимальная норма;Цена\n");
        for (String l : lines) sb.append(l).append('\n');
        File f = new File(Db.DATA_DIR, "wh-import.csv");
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return Table.read(f);
    }

    /** Строка журнала по позиции и операции: «примечание|заявка|количество» или null. */
    private static String op(long itemId, String op) throws Exception {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT note, repair_id, amount FROM stock_ops"
                 + " WHERE item_id=" + itemId + " AND op='" + op + "' ORDER BY id DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) + "|" + rs.getLong(2) + "|" + rs.getDouble(3) : null;
        }
    }

    private static int ops(long itemId) throws Exception {
        return count("stock_ops WHERE item_id=" + itemId);
    }

    private static int count(String from) throws Exception {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + from)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static double scalar(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private static String text(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static long idOf(String article) throws Exception {
        return (long) scalar("SELECT id FROM items WHERE article='" + Db.esc(article) + "'");
    }

    private static boolean eq(double a, double b) { return Math.abs(a - b) < 0.0001; }

    private static long testKit() throws Exception {
        Db.exec("INSERT INTO kits(name,equipment_type) VALUES('Набор тестовый','Принтер Test 1200')");
        return Stock.lastId();
    }

    private static String lastOp(long id) throws Exception {
        return text("SELECT last_op FROM items WHERE id=" + id);
    }

    private static String who(long id) throws Exception {
        return text("SELECT who FROM stock_ops WHERE item_id=" + id + " ORDER BY id LIMIT 1");
    }

    private static String grp(long id) throws Exception {
        return text("SELECT grp FROM items WHERE id=" + id);
    }

    private static double price(long id) throws Exception {
        return scalar("SELECT price FROM items WHERE id=" + id);
    }

    private static double orderQty(long id) throws Exception {
        return scalar("SELECT qty FROM stock_orders WHERE item_id=" + id + " ORDER BY id DESC LIMIT 1");
    }

    private static String orderNote(long id) throws Exception {
        return text("SELECT note FROM stock_orders WHERE item_id=" + id + " ORDER BY id DESC LIMIT 1");
    }

    private static List<Component> all(Container c) {
        List<Component> out = new ArrayList<>();
        for (Component x : c.getComponents()) {
            out.add(x);
            if (x instanceof Container) out.addAll(all((Container) x));
        }
        return out;
    }

    private static void collect(Container c, List<String> out) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton) out.add(((AbstractButton) x).getText());
            if (x instanceof Container) collect((Container) x, out);
        }
    }

    private static List<String> cells(Container c) {
        List<String> out = new ArrayList<>();
        for (Component x : c.getComponents()) {
            if (x instanceof JTable) {
                JTable t = (JTable) x;
                for (int r = 0; r < t.getRowCount(); r++)
                    for (int k = 0; k < t.getColumnCount(); k++)
                        out.add(String.valueOf(t.getValueAt(r, k)));
            }
            if (x instanceof Container) out.addAll(cells((Container) x));
        }
        return out;
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) bad++;
    }
}
