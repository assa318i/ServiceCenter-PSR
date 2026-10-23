package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Импорт списка заявок из Excel, CSV или выгрузки другой сервисной программы — чтобы перенести
 * работу, начатую до программы. Дубль ищется по серийному № и дате открытия, поэтому повторный
 * импорт того же файла правит карточки, а не удваивает список.
 */
final class RepairImport {

    static final int CUSTOMER = 0, EQUIPMENT = 1, SERIAL = 2, PROBLEM = 3, STATUS = 4, SLA = 5,
        ENGINEER = 6, MANAGER = 7, OPENED = 8, WAIT = 9, ISSUED = 10, PRICE = 11, NOTES = 12;
    static final String[] LABELS = { "Заказчик (организация или ФИО)", "Оборудование",
        "Серийный/инвентарный №", "Неисправность", "Статус", "Service Level", "Инженер",
        "Менеджер приёма", "Дата открытия", "Ожидание запчасти до", "Выдан заказчику",
        "Цена, руб.", "Примечание" };
    static final String[][] KEYS = {
        { "заказчик", "клиент", "организация", "контрагент", "фио" },
        { "оборудование", "аппарат", "устройство", "изделие", "модель", "наименование", "товар" },
        { "серийн", "инвентарн", "заводской", "sn" },
        { "неисправност", "проблем", "описани", "дефект", "жалоб" },
        { "статус", "стадия", "этап", "status" },
        { "service", "sla", "уровень", "приоритет" },
        { "инженер", "мастер", "исполнител", "engineer" },
        { "менеджер", "прием", "принял" },
        { "открыт", "датаприем", "поступил", "принят", "регистрац" },
        { "ожидани", "запчаст", "wait" },
        { "выдан", "возврат", "issued", "закрыт" },
        { "цена", "стоимость", "сумм", "price", "прайс" },
        { "примечание", "комментар", "notes", "дополнитель" },
    };

    static final Importer.Target TARGET = new Importer.Target() {
        @Override public String gen() { return "списка заявок"; }
        @Override public String files() { return ".xlsx, .csv, .txt, .xml или HTML-таблица"; }
        @Override public String noun() { return "заявок"; }
        @Override public String[] labels() { return LABELS; }
        @Override public String[][] keys() { return KEYS; }
        @Override public String check(int[] col) {
            return col[EQUIPMENT] < 0 && col[SERIAL] < 0 && col[PROBLEM] < 0
                ? "Укажите колонку оборудования, серийного номера или неисправности — иначе заявку"
                    + " нечем опознать" : null;
        }
        @Override public String note() {
            return "Дубль ищется по серийному № и дате открытия; у заявки без серийного № строка"
                + " файла всегда считается новой. Заказчик ищется по организации или ФИО — если такого"
                + " в базе нет, заявка создаётся без заказчика, свяжите его в карточке. Статус и"
                + " Service Level принимаются по русским подписям из программы. Даты нужны в виде"
                + " дд.мм.гггг (Excel-даты подходят). Комплектность, гарантия и согласование в файл"
                + " не переносим — их заполняют в карточке.";
        }
        @Override public int[] apply(Table t, int[] col, boolean dry) throws Exception {
            return RepairImport.apply(t, col, dry);
        }
    };

    /** Кнопка «Импорт из файла» в окне заявок. */
    static void dialog(java.awt.Component parent) {
        if (!Session.edits(Roles.REPAIRS)) {
            Warn.show("Список заявок из файла загружает тот, кто ведёт раздел «Заявки»:"
                + " администратор, руководитель сервисных инженеров, инженер, консультант,"
                + " сотрудник колл-центра или водитель");
            return;
        }
        Importer.dialog(parent, TARGET);
    }

    /** Применить таблицу к заявкам; dry=true только считает. {новых, обновлённых, пустых}. */
    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        final Map<String, Long> byKey = new HashMap<>(), byCustomer = new HashMap<>();
        Statement st0 = Db.get().createStatement();
        ResultSet rs0 = st0.executeQuery("SELECT id, serial, opened FROM repairs ORDER BY id");
        while (rs0.next()) {
            String k = key(rs0.getString(2), rs0.getString(3));
            if (!k.isEmpty() && !byKey.containsKey(k)) byKey.put(k, rs0.getLong(1));
        }
        rs0.close();
        st0 = Db.get().createStatement();
        rs0 = st0.executeQuery("SELECT id, org, fio FROM customers ORDER BY id");
        while (rs0.next()) register(byCustomer, rs0.getString(2), rs0.getString(3), rs0.getLong(1));
        rs0.close();
        st0.close();
        return Importer.each(t, col, dry, (row, c, isDry) -> write(row, c, byKey, byCustomer, isDry));
    }

    /** Одна строка файла: −1 пустая, 0 обновлённая заявка, 1 новая. */
    private static int write(String[] row, int[] col, Map<String, Long> byKey,
            Map<String, Long> byCustomer, boolean dry) throws Exception {
        String equipment = Table.cell(row, col[EQUIPMENT]).trim();
        String serial = Table.cell(row, col[SERIAL]).trim();
        String problem = Table.cell(row, col[PROBLEM]).trim();
        if (equipment.isEmpty() && serial.isEmpty() && problem.isEmpty()) return -1;
        String k = key(serial, ruDate(Table.cell(row, col[OPENED])));
        Long id = k.isEmpty() ? null : byKey.get(k);
        if (dry) return id == null ? 1 : 0;
        save(id, row, col, byKey, byCustomer);
        return id == null ? 1 : 0;
    }

    private static void save(Long id, String[] row, int[] col, Map<String, Long> byKey,
            Map<String, Long> byCustomer) throws Exception {
        String equipment = Table.cell(row, col[EQUIPMENT]).trim();
        String serial = Table.cell(row, col[SERIAL]).trim();
        String problem = Table.cell(row, col[PROBLEM]).trim();
        String engineer = Table.cell(row, col[ENGINEER]).trim();
        String manager = Table.cell(row, col[MANAGER]).trim();
        String notes = Table.cell(row, col[NOTES]).trim();
        String wait = ruDate(Table.cell(row, col[WAIT]));
        String issued = ruDate(Table.cell(row, col[ISSUED]));
        int st = status(Table.cell(row, col[STATUS]));
        String sla = sla(Table.cell(row, col[SLA]));
        String openedRaw = ruDate(Table.cell(row, col[OPENED]));
        String opened = openedRaw.isEmpty() ? RepairsPanel.stamp() : openedRaw;
        if (id == null) {
            Long cust = byCustomer.get(Table.norm(Table.cell(row, col[CUSTOMER])));
            Db.exec("INSERT INTO repairs(customer_id,equipment,serial,problem,status,sla,engineer,"
                + "manager,wait_until,issued,price,notes,opened) VALUES(" + (cust == null ? 0 : cust)
                + ",'" + Db.esc(equipment) + "','" + Db.esc(serial) + "','" + Db.esc(problem) + "','"
                + RepairsPanel.STATUS[st < 0 ? 0 : st] + "','" + (sla == null ? RepairsPanel.SLA_RU[0] : sla)
                + "','" + Db.esc(engineer) + "','" + Db.esc(manager) + "','" + Db.esc(wait) + "','"
                + Db.esc(issued) + "'," + Stock.num(Importer.number(Table.cell(row, col[PRICE])))
                + ",'" + Db.esc(notes) + "','" + Db.esc(opened) + "')");
            long newId = Stock.lastId();
            byKey.put(key(serial, opened), newId);
            Stream.log(newId, "Заявка создана импортом из файла: "
                + RepairsPanel.STATUS_RU[st < 0 ? 0 : st]);
            return;
        }
        StringBuilder set = new StringBuilder();
        text(set, "equipment", equipment);
        text(set, "serial", serial);
        text(set, "problem", problem);
        text(set, "engineer", engineer);
        text(set, "manager", manager);
        text(set, "notes", notes);
        text(set, "wait_until", wait);
        text(set, "issued", issued);
        text(set, "opened", openedRaw);
        if (st >= 0) set.append("status='").append(RepairsPanel.STATUS[st]).append("',");
        if (sla != null) set.append("sla='").append(sla).append("',");
        Long cust = byCustomer.get(Table.norm(Table.cell(row, col[CUSTOMER])));
        if (cust != null) set.append("customer_id=").append(cust).append(',');
        String price = Table.cell(row, col[PRICE]).trim();
        if (!price.isEmpty()) set.append("price=").append(Stock.num(Importer.number(price))).append(',');
        if (set.length() == 0) return;   // пустая строка файла не должна затирать карточку
        set.setLength(set.length() - 1);
        Db.exec("UPDATE repairs SET " + set + ", rev=rev+1 WHERE id=" + id);
        Stream.log(id, "Данные заявки изменены импортом из файла");
    }

    private static void text(StringBuilder set, String column, String value) {
        if (!value.isEmpty()) set.append(column).append("='").append(Db.esc(value)).append("',");
    }

    /** Ключ поиска дубля: серийный № и день открытия. Пустой серийный — дубль не ищем. */
    static String key(String serial, String opened) {
        String s = Table.norm(serial);
        return s.isEmpty() ? "" : s + "|" + day(opened);
    }

    private static String day(String stamp) {
        String s = stamp == null ? "" : stamp.trim();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /** Организация и ФИО заказчика — по всем этим подписям ищем его в базе. */
    private static void register(Map<String, Long> byCustomer, String org, String fio, long id) {
        for (String s : new String[] { org, fio, org + " " + fio, fio + " " + org }) {
            String k = Table.norm(s);
            if (!k.isEmpty() && !byCustomer.containsKey(k)) byCustomer.put(k, id);
        }
    }

    /** Русская подпись статуса, его код или частичное совпадение — индекс в RepairsPanel.STATUS; −1 не поняли. */
    static int status(String raw) {
        String s = Table.norm(raw);
        if (s.isEmpty()) return -1;
        for (int i = 0; i < RepairsPanel.STATUS.length; i++)
            if (s.equals(Table.norm(RepairsPanel.STATUS_RU[i])) || s.equals(Table.norm(RepairsPanel.STATUS[i])))
                return i;
        for (int i = 0; i < RepairsPanel.STATUS.length; i++) {
            String ru = Table.norm(RepairsPanel.STATUS_RU[i]);
            if (ru.startsWith(s) || ru.contains(s)) return i;
        }
        return -1;
    }

    /** Подпись Service Level для колонки sla; null — не поняли и уровень трогать нельзя. */
    static String sla(String raw) {
        String s = Table.norm(raw);
        if (s.isEmpty()) return null;
        if (s.equals("обычный") || s.equals("normal")) return RepairsPanel.SLA_RU[0];
        if (s.equals("внимание") || s.equals("warning")) return RepairsPanel.SLA_RU[1];
        if (s.equals("опасно") || s.equals("critical")) return RepairsPanel.SLA_RU[2];
        return null;
    }

    /** «05.10.2026», «5/10/2026 14:00», «2026-10-05» → дд.мм.гггг[ чч:мм]. Нераспознанное — как в файле. */
    static String ruDate(String raw) {
        String s = raw == null ? "" : raw.replace('\u00A0', ' ').trim();
        if (s.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(.*)$").matcher(s);
        if (m.matches()) return withTail(pad(m.group(1)) + "." + pad(m.group(2)) + "." + m.group(3), m.group(4));
        m = java.util.regex.Pattern.compile("^(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})(.*)$").matcher(s);
        if (m.matches()) return withTail(pad(m.group(3)) + "." + pad(m.group(2)) + "." + m.group(1), m.group(4));
        return s;
    }

    /** Время из файла сохраняем через пробел — его ждёт тот же формат, что и stamp(). */
    private static String withTail(String date, String tail) {
        String t = tail.trim();
        return t.isEmpty() ? date : date + " " + t;
    }

    private static String pad(String digits) {
        return digits.length() == 2 ? digits : "0" + digits;
    }

    private RepairImport() { }
}
