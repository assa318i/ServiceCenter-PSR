package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Импорт заказчиков из выгрузки 1С, Excel или CSV. Дубль ищется по связке «организация + ФИО»,
 * поэтому повторный импорт того же файла дополняет карточки, а не заводит вторых Ивановых.
 */
final class CustomerImport {

    static final int ORG = 0, FIO = 1, PHONE = 2, ADDRESS = 3, CONTACT = 4, ATTORNEY = 5,
        METHOD = 6, NOTES = 7;
    static final String[] LABELS = { "Организация", "ФИО клиента", "Телефон", "Адрес",
        "Контактный телефон", "Доверенное лицо", "Способ оплаты", "Примечание" };
    /** Синонимы заголовков: в нижнем регистре и без пробелов — сличаем с Table.norm(заголовка). */
    static final String[][] KEYS = {
        { "организация", "контрагент", "наименование", "компания", "заказчик", "клиент", "партнер", "org" },
        { "фио", "контактноелицо", "работник", "сотрудник", "name" },
        { "телефон", "тел", "мобильный", "phone" },
        { "адрес", "address", "место" },
        { "контакт", "факс", "почта", "email" },
        { "доверенн", "представитель", "атторней" },
        { "способ", "оплата", "расчет", "условия", "payment" },
        { "примечание", "комментар", "дополнитель", "notes" },
    };

    static final Importer.Target TARGET = new Importer.Target() {
        @Override public String gen() { return "базы заказчиков"; }
        @Override public String files() { return ".xlsx, .csv, .txt, .xml или HTML-таблица"; }
        @Override public String noun() { return "заказчиков"; }
        @Override public String[] labels() { return LABELS; }
        @Override public String[][] keys() { return KEYS; }
        @Override public String check(int[] col) {
            return col[ORG] < 0 && col[FIO] < 0
                ? "Укажите колонку организации или ФИО — иначе заказчика нечем опознать" : null;
        }
        @Override public String note() {
            return "Заказчик ищется по организации и ФИО: совпавшую карточку значения из файла"
                + " поправят, новые строки добавятся отдельными заказчиками. Пустая клетка файла"
                + " ничего не затирает. Вводите ПДн только в файл того отдела, которому они положены.";
        }
        @Override public int[] apply(Table t, int[] col, boolean dry) throws Exception {
            return CustomerImport.apply(t, col, dry);
        }
    };

    /** Кнопка «Импорт из файла» в окне заказчиков. */
    static void dialog(java.awt.Component parent) {
        if (!Session.edits(Roles.CUSTOMERS)) {
            Warn.show("Базу заказчиков из файла загружает тот, кто ведёт раздел «Заказчики»:"
                + " администратор, руководитель сервисных инженеров, бухгалтер или юрист");
            return;
        }
        Importer.dialog(parent, TARGET);
    }

    /** Применить таблицу к заказчикам; dry=true только считает. {новых, обновлённых, пустых}. */
    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        final Map<String, Long> byKey = new HashMap<>();
        Statement st0 = Db.get().createStatement();
        ResultSet rs0 = st0.executeQuery("SELECT id, org, fio FROM customers");
        while (rs0.next()) {
            String k = key(rs0.getString(2), rs0.getString(3));
            if (!k.isEmpty() && !byKey.containsKey(k)) byKey.put(k, rs0.getLong(1));
        }
        rs0.close();
        st0.close();
        return Importer.each(t, col, dry, (row, c, isDry) -> write(row, c, byKey, isDry));
    }

    /** Одна строка файла: −1 пустая, 0 обновлённый заказчик, 1 новый. */
    private static int write(String[] row, int[] col, Map<String, Long> byKey, boolean dry)
            throws Exception {
        String org = Table.cell(row, col[ORG]).trim();
        String fio = Table.cell(row, col[FIO]).trim();
        if (org.isEmpty() && fio.isEmpty()) return -1;
        Long id = byKey.get(key(org, fio));
        if (dry) return id == null ? 1 : 0;
        save(id, row, col, byKey, org, fio);
        return id == null ? 1 : 0;
    }

    private static void save(Long id, String[] row, int[] col, Map<String, Long> byKey,
            String org, String fio) throws Exception {
        String phone = Table.cell(row, col[PHONE]).trim();
        String address = Table.cell(row, col[ADDRESS]).trim();
        String contact = Table.cell(row, col[CONTACT]).trim();
        String attorney = Table.cell(row, col[ATTORNEY]).trim();
        String method = Table.cell(row, col[METHOD]).trim();
        String notes = Table.cell(row, col[NOTES]).trim();
        if (id == null) {
            Db.exec("INSERT INTO customers(org,fio,phone,address,contact_phone,attorney,"
                + "payment_method,notes) VALUES('" + Db.esc(org) + "','" + Db.esc(fio) + "','"
                + Db.esc(phone) + "','" + Db.esc(address) + "','" + Db.esc(contact) + "','"
                + Db.esc(attorney) + "','" + Db.esc(method) + "','" + Db.esc(notes) + "')");
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT MAX(id) FROM customers")) {
                if (rs.next()) id = rs.getLong(1);
            }
            if (id != null) byKey.put(key(org, fio), id);
            return;
        }
        StringBuilder set = new StringBuilder();
        put(set, "org", org);
        put(set, "fio", fio);
        put(set, "phone", phone);
        put(set, "address", address);
        put(set, "contact_phone", contact);
        put(set, "attorney", attorney);
        put(set, "payment_method", method);
        put(set, "notes", notes);
        if (set.length() == 0) return;   // пустая строка файла не должна затирать карточку
        set.setLength(set.length() - 1);
        Db.exec("UPDATE customers SET " + set + " WHERE id=" + id);
    }

    private static void put(StringBuilder set, String column, String value) {
        if (!value.isEmpty()) set.append(column).append("='").append(Db.esc(value)).append("',");
    }

    /** Ключ поиска дубля: организация и ФИО в одну нормализованную строку. */
    static String key(String org, String fio) {
        return Table.norm(org) + "|" + Table.norm(fio);
    }

    private CustomerImport() { }
}
