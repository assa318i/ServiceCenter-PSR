package psr.sc;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

/**
 * Импорт справочников из чужого файла: заказчики, заявки и ЗЧ картриджей идут через одну машину
 * Importer. Проверяется угадывание колонок по подписям, поиск дубля, что пустая клетка файла не
 * затирает базу, разбор даты и числа, откат всей загрузки при сбое одной строки и обратный импорт
 * собственными CSV. База — своя (-Dpsr.data=build\test\tmp-import), data\psr.db не затрагивается.
 */
public class ImportCheck {

    static int bad;

    public static void main(String[] args) throws Exception {
        Db.get();
        check("проверка идёт на отдельной базе", !Db.DATA_DIR.getPath().equals("data"));
        wipe();
        common();
        customers();
        repairs();
        cartParts();
        roundTrip();
        docHeads();
        roles();
        ui();
        System.out.println(bad == 0 ? "ВСЁ ЧИСТО" : "ЕСТЬ ЗАМЕЧАНИЯ: " + bad);
        if (bad > 0) System.exit(1);
    }

    private static void wipe() throws Exception {
        for (String t : new String[] { "repair_history", "watch", "tasks", "repairs", "customers",
            "cart_ops", "cart_parts", "stock_ops", "items" })
            Db.exec("DELETE FROM " + t);
    }

    // ─────────────────────────────────── общее ───────────────────────────────────

    private static void common() throws Exception {
        check("число из файла: «1 234,50»", Importer.number("1 234,50") == 1234.5);
        check("число из файла: «1.234,50»", Importer.number("1.234,50") == 1234.5);
        check("число из файла: пустая клетка — ноль", Importer.number("") == 0);
        check("число из файла: текст — ноль", Importer.number("нет данных") == 0);
        check("число из файла: «NaN» — ноль, а не сбой импорта", Importer.number("NaN") == 0);
        check("число из файла: «Infinity» — ноль, а не сбой импорта", Importer.number("Infinity") == 0);

        Table t = csv("Ключ;Метка", "Первая;1", "BAD;2", "Вторая;3");
        int before = count("customers");
        boolean threw = false;
        try {
            Importer.each(t, new int[] { 0, 1 }, false, (row, col, dry) -> {
                if (row[0].equals("BAD")) throw new Exception("обрыв на строке файла");
                Db.exec("INSERT INTO customers(org) VALUES('" + Db.esc(row[0]) + "')");
                return 1;
            });
        } catch (Exception expected) { threw = true; }
        check("сбой одной строки поднимается вызывающему", threw);
        check("сбой откатывает весь импорт", count("customers") == before);
        check("пустой файл нечем импортировать", CustomerImport.TARGET.check(new int[] { -1, -1,
            2, 3, 4, 5, 6, 7 }) != null && CustomerImport.TARGET.check(new int[] { 0, 1, 2, 3,
            4, 5, 6, 7 }) == null);
    }

    // ────────────────────────────────── заказчики ──────────────────────────────────

    private static final String C_HEAD = "Организация;ФИО клиента;Телефон;Адрес;Контактный телефон;"
        + "Доверенное лицо;Способ оплаты;Примечание";

    private static void customers() throws Exception {
        Table t = csv(C_HEAD,
            "ООО Ромашка;Иванов И.И.;+7 900 111-22-33;Москва, Ленина 1;;;безнал;постоянный",
            "ИП Петров;Петров П.П.;8 900 222-33-44;Тверь;;;наличный;");
        int[] col = { 0, 1, 2, 3, 4, 5, 6, 7 };
        check("колонки заказчиков угадываются по подписям",
            Importer.guess(t.headers, CustomerImport.KEYS[CustomerImport.ORG]) == 1
                && Importer.guess(t.headers, CustomerImport.KEYS[CustomerImport.FIO]) == 2
                && Importer.guess(t.headers, CustomerImport.KEYS[CustomerImport.PHONE]) == 3
                && Importer.guess(t.headers, CustomerImport.KEYS[CustomerImport.ADDRESS]) == 4);
        int before = count("customers");
        int[] dry = CustomerImport.apply(t, col, true);
        check("предпросмотр считает новых заказчиков", dry[0] == 2 && dry[1] == 0);
        check("предпросмотр базу не трогает", count("customers") == before);

        int[] n = CustomerImport.apply(t, col, false);
        check("импорт добавил двух заказчиков", n[0] == 2 && count("customers") == before + 2);
        check("телефон лёг в карточку как есть",
            field("customers", "phone", "org='ООО Ромашка'").equals("+7 900 111-22-33"));
        check("адрес с запятой не развалился на колонки",
            field("customers", "address", "org='ООО Ромашка'").equals("Москва, Ленина 1"));

        int[] twice = CustomerImport.apply(t, col, false);
        check("повтор файла не плодит дублей заказчиков", twice[0] == 0 && twice[1] == 2
            && count("customers") == before + 2);

        Table blanks = csv(C_HEAD, "ООО Ромашка;Иванов И.И.;;;;;;срочно");
        CustomerImport.apply(blanks, col, false);
        check("пустая клетка телефона карточку не затирает",
            field("customers", "phone", "org='ООО Ромашка'").equals("+7 900 111-22-33"));
        check("заполненная клетка примечания дописалась",
            field("customers", "notes", "org='ООО Ромашка'").equals("срочно"));

        Table other = csv(C_HEAD, "ООО Ромашка;Сидоров С.С.;8 900 333-33-33;;;;;");
        int[] one = CustomerImport.apply(other, col, false);
        check("та же организация с другим ФИО — новый заказчик", one[0] == 1
            && count("customers") == before + 3);

        Table quote = csv(C_HEAD, "Ромашка'Вектор;Кузнецов А.А.;8 900 444-44-44;;;;;");
        CustomerImport.apply(quote, col, false);
        check("апостроф в названии не ломает запись",
            field("customers", "org", "fio='Кузнецов А.А.'").equals("Ромашка'Вектор"));
        check("у импортного заказчика можно спросить телефон",
            field("customers", "phone", "fio='Кузнецов А.А.'").equals("8 900 444-44-44"));
    }

    // ──────────────────────────────────── заявки ────────────────────────────────────

    private static final String R_HEAD = "Заказчик;Оборудование;Серийный №;Неисправность;Статус;"
        + "Service Level;Инженер;Менеджер;Дата открытия;Ожидание запчасти;Выдан;Цена;Примечание";

    private static void repairs() throws Exception {
        check("статус по русской подписи", RepairImport.status("В работе") == 4);
        check("статус по подписи с окончанием", RepairImport.status("Завершён, выдан") == 7);
        check("статус по коду программы", RepairImport.status("WAIT_PARTS") == 2);
        check("непонятный статус не выдумывается",
            RepairImport.status("полный привет") == -1 && RepairImport.status("") == -1);
        check("уровень по русской подписи", "Опасно".equals(RepairImport.sla("Опасно")));
        check("уровень по чужой подписи", "Обычный".equals(RepairImport.sla("normal")));
        check("непонятный уровень остаётся как был", RepairImport.sla("срочно") == null);
        check("дата как в программе", RepairImport.ruDate("05.10.2026").equals("05.10.2026"));
        check("дата год-месяц-день переворачивается", RepairImport.ruDate("2026-10-01").equals("01.10.2026"));
        check("дата с временем сохраняется целиком",
            RepairImport.ruDate("5/10/2026 14:00").equals("05.10.2026 14:00"));
        check("дата без нулей дополняется", RepairImport.ruDate("1.2.2026").equals("01.02.2026"));
        check("текст вместо даты остаётся текстом",
            RepairImport.ruDate("пока не ясно").equals("пока не ясно"));
        check("пустая дата не превращается в дату", RepairImport.ruDate("").isEmpty());

        Table t = csv(R_HEAD,
            "ООО Ромашка;HP LaserJet 1010;CN-77;не печатает;В работе;Обычный;Сидоров;Иванова;"
                + "05.10.2026;12.10.2026;;1 234,50;перенос из журнала",
            "Нет такого;Canon MF4450;SN-99;сканер молчит;Завершён, выдан;Опасно;Петров;;"
                + "2026-10-01;;10.10.2026;15000;");
        int[] col = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        check("колонки заявок угадываются по подписям",
            Importer.guess(t.headers, RepairImport.KEYS[RepairImport.CUSTOMER]) == 1
                && Importer.guess(t.headers, RepairImport.KEYS[RepairImport.SERIAL]) == 3
                && Importer.guess(t.headers, RepairImport.KEYS[RepairImport.STATUS]) == 5
                && Importer.guess(t.headers, RepairImport.KEYS[RepairImport.ISSUED]) == 11
                && Importer.guess(t.headers, RepairImport.KEYS[RepairImport.PRICE]) == 12);
        int before = count("repairs");
        int[] dry = RepairImport.apply(t, col, true);
        check("предпросмотр считает новые заявки", dry[0] == 2 && dry[1] == 0);
        check("предпросмотр заявки не создаёт", count("repairs") == before);

        int[] n = RepairImport.apply(t, col, false);
        check("импорт добавил две заявки", n[0] == 2 && count("repairs") == before + 2);
        long r = repairBySerial("CN-77");
        check("статус из русской подписи лёг кодом", "IN_WORK".equals(rep(r, "status")));
        check("заказчик из файла найден и связан",
            scalar("SELECT customer_id FROM repairs WHERE id=" + r) == custId("ООО Ромашка"));
        check("заявка про заказчика, которого нет, создаётся без заказчика",
            scalar("SELECT customer_id FROM repairs WHERE serial='SN-99'") == 0);
        check("цена из «1 234,50» стала числом", repPrice(r) == 1234.5);
        check("ожидание запчасти перенесено", rep(r, "wait_until").equals("12.10.2026"));
        check("дата год-месяц-день переписана по-русски",
            rep(repairBySerial("SN-99"), "opened").equals("01.10.2026"));
        check("дата выдачи лёгла", rep(repairBySerial("SN-99"), "issued").equals("10.10.2026"));
        check("уровень из файла лёг русской подписью", rep(r, "sla").equals("Обычный"));
        check("в ленте заявки видно, что это импорт",
            field("repair_history", "info", "repair_id=" + r).contains("импортом"));

        int[] twice = RepairImport.apply(t, col, false);
        check("повтор файла не плодит дублей заявок", twice[0] == 0 && twice[1] == 2
            && count("repairs") == before + 2);

        Table blanks = csv(R_HEAD, "ООО Ромашка;;CN-77;не печатает;;;;;05.10.2026;;;;");
        RepairImport.apply(blanks, col, false);
        check("пустая клетка оборудования заявку не затирает",
            rep(r, "equipment").equals("HP LaserJet 1010"));
        check("правка импортом поднимает версию записи", scalar("SELECT rev FROM repairs WHERE id=" + r) >= 1);

        Table noSerial = csv(R_HEAD, "ООО Ромашка;Xerox 3000;;;Открыт ремонт (приём);;;;06.10.2026;;;500;");
        RepairImport.apply(noSerial, col, false);
        RepairImport.apply(noSerial, col, false);
        check("заявка без серийного номера дублем не считается", count("repairs") == before + 4);
        check("заявке без серийного номера цена легла", repPrice(repairBySerial("")) == 500);
        check("непонятная подпись статуса не превращает заявку в завершённую",
            count("repairs WHERE status='OPEN'") == 2);
    }

    // ──────────────────────────────── ЗЧ картриджей ────────────────────────────────

    private static final String K_HEAD = "Артикул;Название ЗЧ;Грамовка, г;Тонер на складе, г;"
        + "Склад заправщика, шт;Склад кладовщика, шт;Мин. норма;Цена, руб.";

    private static void cartParts() throws Exception {
        Table t = csv(K_HEAD, "TP-115;Фотобарабан 115;120;500;4;40;2;850,50",
            "CR-303;Чип 303;;100;;30;10;150");
        int[] col = { 0, 1, 2, 3, 4, 5, 6, 7 };
        check("колонки каталога ЗЧ угадываются по подписям",
            Importer.guess(t.headers, CartPartImport.KEYS[CartPartImport.ARTICLE]) == 1
                && Importer.guess(t.headers, CartPartImport.KEYS[CartPartImport.GRAMS]) == 3
                && Importer.guess(t.headers, CartPartImport.KEYS[CartPartImport.TONER]) == 4
                && Importer.guess(t.headers, CartPartImport.KEYS[CartPartImport.REFILL]) == 5
                && Importer.guess(t.headers, CartPartImport.KEYS[CartPartImport.STORE]) == 6
                && Importer.guess(t.headers, CartPartImport.KEYS[CartPartImport.PRICE]) == 8);
        int before = count("cart_parts");
        int[] n = CartPartImport.apply(t, col, false);
        check("импорт добавил две позиции каталога", n[0] == 2 && count("cart_parts") == before + 2);
        long p = cartByArticle("TP-115");
        check("граммовка из файла легла", cartNum(p, "grams_std") == 120);
        check("оба склада заполнены", cartNum(p, "qty_refill") == 4 && cartNum(p, "qty_store") == 40);
        check("цена из «850,50» стала числом", cartNum(p, "price") == 850.5);
        check("пустая клетка в новой позиции — ноль, а не ошибка",
            cartNum(cartByArticle("CR-303"), "qty_refill") == 0);

        int[] twice = CartPartImport.apply(t, col, false);
        check("повтор файла не плодит дублей ЗЧ", twice[0] == 0 && twice[1] == 2
            && count("cart_parts") == before + 2);
        Table blanks = csv(K_HEAD, "TP-115;Фотобарабан 115;;;;;;");
        CartPartImport.apply(blanks, col, false);
        check("пустые клетки остатков каталог не обнуляют",
            cartNum(p, "grams_std") == 120 && cartNum(p, "qty_store") == 40);
        check("импорт каталога журнал картриджей не пишет", count("cart_ops") == 0);
    }

    // ─────────────────────────────── свой файл обратно ───────────────────────────────

    private static void roundTrip() throws Exception {
        Db.exec("INSERT INTO items(name,article,unit,qty,min_qty,price,grp) VALUES"
            + "('Тонер 120 г','TN-118','шт',12,2,990,'Тонер')");
        Export.allCsv();
        File dir = new File(Db.DATA_DIR, "export");

        Table cart = Table.read(new File(dir, "kartridzhi.csv"));
        int[] cc = cols(cart.headers, CartPartImport.KEYS);
        int[] n = CartPartImport.apply(cart, cc, false);
        check("выгруженный каталог ЗЧ грузится обратно без дублей",
            n[0] == 0 && n[1] >= 2 && count("cart_parts") == 2);

        Table cus = Table.read(new File(dir, "zakazchiki.csv"));
        int[] kc = cols(cus.headers, CustomerImport.KEYS);
        check("в выгрузке заказчиков способ оплаты не путается с предоплатой",
            kc[CustomerImport.METHOD] == 8 && cus.headers.get(8).equals("Способ оплаты"));
        int[] m = CustomerImport.apply(cus, kc, false);
        check("выгруженные заказчики грузятся обратно без дублей", m[0] == 0 && m[1] >= 4);
        check("обратная загрузка не портит способ оплаты",
            field("customers", "payment_method", "org='ООО Ромашка'").equals("безнал"));

        Table rm = Table.read(new File(dir, "remonty.csv"));
        int[] rc = cols(rm.headers, RepairImport.KEYS);
        check("выгруженные заявки угадываются по своим колонкам",
            rc[RepairImport.OPENED] == 1 && rc[RepairImport.STATUS] == 2
                && rc[RepairImport.SERIAL] == 7 && rc[RepairImport.PRICE] == 10);
        check("в выгрузке заявок заказчика нет — колонка не выдумывается",
            rc[RepairImport.CUSTOMER] == -1);

        Table sk = Table.read(new File(dir, "sklad.csv"));
        int[] sc = cols(sk.headers, WarehouseImport.KEYS);
        check("выгруженный склад угадывается по своим колонкам",
            sc[WarehouseImport.QTY] == 4 && sk.headers.get(sc[WarehouseImport.NAME]).equals("Наименование"));
        int[] w = WarehouseImport.apply(sk, sc, false);
        check("выгруженный склад грузится обратно без дублей", w[0] == 0 && w[1] == 1);
    }

    private static int[] cols(List<String> headers, String[][] keys) {
        int[] col = new int[keys.length];
        for (int i = 0; i < keys.length; i++) col[i] = Importer.guess(headers, keys[i]) - 1;
        return col;
    }

    /**
     * Строки заголовков из docs\ИМПОРТ-И-ЭКСПОРТ.md: инструкция обещает, что они грузятся
     * без единой правки в окне колонок, — значит каждое поле должно встать на свой столбец.
     */
    private static void docHeads() {
        check("пример заголовков заказчиков угадывается целиком",
            aligned(csvHeaders("Организация;ФИО;Телефон;Адрес;Контактный телефон;Доверенное лицо;"
                + "Способ оплаты;Примечание"), CustomerImport.KEYS));
        check("пример заголовков заявок угадывается целиком",
            aligned(csvHeaders("Заказчик;Оборудование;Серийный №;Неисправность;Статус;Service Level;"
                + "Инженер;Менеджер;Открыта;Ожидание до;Выдан;Цена;Примечание"), RepairImport.KEYS));
        List<String> sklad = csvHeaders("Артикул;Наименование;Группа;Ед.;Остаток;Мин. норма;Цена;Штрихкод");
        check("пример заголовков склада угадывается целиком", aligned(sklad, WarehouseImport.KEYS));
        check("штрихкод склада не подменяет артикул",
            cols(sklad, WarehouseImport.KEYS)[WarehouseImport.ARTICLE] == 0);
        check("пример заголовков ЗЧ картриджей угадывается целиком",
            aligned(csvHeaders("Артикул;Название ЗЧ;Грамовка, г;Тонер на складе, г;Склад заправщика, шт;"
                + "Склад кладовщика, шт;Мин. норма;Цена, руб."), CartPartImport.KEYS));
    }

    /** Каждое поле на своём столбце: колонка с индексом i угадана для поля i. */
    private static boolean aligned(List<String> headers, String[][] keys) {
        int[] col = cols(headers, keys);
        for (int i = 0; i < col.length; i++) if (col[i] != i) return false;
        return true;
    }

    private static List<String> csvHeaders(String line) {
        return java.util.Arrays.asList(line.split(";", -1));
    }

    // ──────────────────────────────────── права ────────────────────────────────────

    private static void roles() throws Exception {
        check("бухгалтер базу заказчиков ведёт", Roles.edits("ACCOUNTANT", Roles.CUSTOMERS));
        check("инженер базу заказчиков из файла не грузит", !Roles.edits("ENGINEER", Roles.CUSTOMERS));
        check("консультант заявки правит", Roles.edits("CONSULTANT", Roles.REPAIRS));
        check("юрист список заявок из файла не грузит", !Roles.edits("LAWYER", Roles.REPAIRS));
        check("заправщик каталог ЗЧ грузит", Roles.edits("REFILLER", Roles.CARTRIDGES));
        check("водитель каталог ЗЧ не грузит", !Roles.edits("DRIVER", Roles.CARTRIDGES));
    }

    private static void ui() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> r = new ArrayList<>();
            collect(new RepairsPanel(null), r);
            check("в окне заявок есть кнопка импорта", r.contains("Импорт из файла"));
            List<String> c = new ArrayList<>();
            collect(new CustomersPanel(null), c);
            check("в окне заказчиков есть кнопка импорта", c.contains("Импорт из файла"));
            List<String> k = new ArrayList<>();
            collect(new CartridgesPanel(), k);
            check("в каталоге ЗЧ есть кнопка импорта", k.contains("Импорт из файла"));
            List<String> w = new ArrayList<>();
            collect(new WarehousePanel(), w);
            check("кнопка импорта склада осталась на месте", w.contains("Импорт из файла"));
        });
    }

    // ─────────────────────────────────── помощники ───────────────────────────────────

    /** Таблица из строк CSV с указанным заголовком — файл кладётся рядом с базой проверки. */
    private static Table csv(String head, String... lines) throws Exception {
        StringBuilder sb = new StringBuilder(head).append('\n');
        for (String l : lines) sb.append(l).append('\n');
        File f = new File(Db.DATA_DIR, "import-check.csv");
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return Table.read(f);
    }

    private static String field(String table, String column, String where) throws Exception {
        return text("SELECT " + column + " FROM " + table + " WHERE " + where + " ORDER BY id LIMIT 1");
    }

    private static long custId(String org) throws Exception {
        return (long) scalar("SELECT id FROM customers WHERE org='" + Db.esc(org) + "'");
    }

    private static long repairBySerial(String serial) throws Exception {
        return (long) scalar("SELECT id FROM repairs WHERE serial='" + Db.esc(serial) + "'");
    }

    private static long cartByArticle(String article) throws Exception {
        return (long) scalar("SELECT id FROM cart_parts WHERE article='" + Db.esc(article) + "'");
    }

    private static String rep(long id, String column) throws Exception {
        return text("SELECT " + column + " FROM repairs WHERE id=" + id);
    }

    private static double repPrice(long id) throws Exception {
        return scalar("SELECT price FROM repairs WHERE id=" + id);
    }

    private static double cartNum(long id, String column) throws Exception {
        return scalar("SELECT " + column + " FROM cart_parts WHERE id=" + id);
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

    private static void collect(Container c, List<String> out) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton) out.add(((AbstractButton) x).getText());
            if (x instanceof Container) collect((Container) x, out);
        }
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) bad++;
    }
}
