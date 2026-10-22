package psr.sc;

/**
 * Роли сотрудников и разделы, открытые каждой роли. В users.role хранится код (ENGINEER,
 * REFILLER…), в интерфейсе — русское название. Список разделов в строке через запятую,
 * суффикс «+» означает, что роль не только видит раздел, но и ведёт его (справочник, нормы,
 * каталог). Раздел «pii» — доступ к персональным данным заказчика без обезличивания.
 */
public final class Roles {

    public static final String REPAIRS = "repairs", CUSTOMERS = "customers", WAREHOUSE = "warehouse",
        CARTRIDGES = "cartridges", KITS = "kits", REPORTS = "reports", FINANCE = "finance",
        SETTINGS = "settings", TASKS = "tasks", PII = "pii",
        TIMES = "times", CALENDAR = "calendar";

    /** Порядок и подписи — как в окне «Сотрудники, роли и доступ». */
    public static final String[][] LIST = {
        { "ADMIN", "Администратор", "*" },
        { "SERVICE_LEAD", "Руководитель сервисных инженеров",
            "repairs+,customers+,warehouse+,cartridges+,kits+,reports+,times+,tasks,pii,calendar" },
        { "FINANCE_DIRECTOR", "Финансовый директор",
            "finance+,reports+,customers,warehouse,times+,tasks,pii,calendar" },
        { "ACCOUNTANT", "Бухгалтер", "customers+,finance+,reports+,warehouse,times+,tasks,pii,calendar" },
        { "LAWYER", "Юрист", "customers+,repairs,reports,tasks,pii,calendar" },
        { "ENGINEER", "Инженер", "repairs+,warehouse,cartridges,kits,times,tasks,calendar" },
        { "REFILLER", "Заправщик", "repairs,warehouse,cartridges+,kits+,times,tasks,calendar" },
        { "CONSULTANT", "Консультант", "repairs+,customers,warehouse,reports,tasks,pii,calendar" },
        { "CALL_CENTER", "Сотрудник колл-центра", "repairs+,customers,pii,tasks,calendar" },
        { "DRIVER", "Водитель", "repairs+,customers,pii,tasks,calendar" },
    };

    /** Названия для выпадающего списка (порядок = порядок LIST). */
    public static String[] titles() {
        String[] out = new String[LIST.length];
        for (int i = 0; i < LIST.length; i++) out[i] = LIST[i][1];
        return out;
    }

    /** Код роли по её названию из списка; пусто — ENGINEER, чтобы старый справочник не ломался. */
    public static String codeOf(String title) {
        for (String[] r : LIST) if (r[1].equals(title)) return r[0];
        return "ENGINEER";
    }

    public static String title(String code) {
        for (String[] r : LIST) if (r[0].equals(code)) return r[1];
        return "Сотрудник";
    }

    /** Раздел открыт роли (в том числе с правом править). */
    public static boolean may(String code, String section) {
        return has(code, section, false) || has(code, section, true);
    }

    /** Раздел открыт и разрешено менять в нём данные. */
    public static boolean edits(String code, String section) {
        return has(code, section, true);
    }

    private static boolean has(String code, String section, boolean editable) {
        String spec = specOf(code);
        if (spec == null) spec = specOf("ENGINEER");   // незнакомый код — права инженера, не администратора
        if (spec.equals("*")) return true;
        return editable ? plus(spec, section) : bare(spec, section) || plus(spec, section);
    }

    private static String specOf(String code) {
        for (String[] r : LIST) if (r[0].equals(code)) return r[2];
        return null;
    }

    /** Запись «раздел» без суффикса: доступ на просмотр. */
    public static boolean bare(String spec, String section) { return listed(spec, section, ""); }

    /** Запись «раздел+»: право менять данные в разделе. */
    public static boolean plus(String spec, String section) { return listed(spec, section, "+"); }

    private static boolean listed(String spec, String section, String suffix) {
        if (spec == null || spec.isEmpty()) return false;
        for (String s : spec.split(",")) if (s.trim().equals(section + suffix)) return true;
        return false;
    }

    /** Разделы, которые админ может выдать сотруднику лично или закрыть ему. */
    public static final String[][] SECTIONS = {
        { REPAIRS, "Заявки на ремонт" }, { CUSTOMERS, "Заказчики" }, { WAREHOUSE, "Склад" },
        { CARTRIDGES, "Картриджи и граммовка" }, { KITS, "Наборы запасных частей" },
        { REPORTS, "Отчётность" }, { FINANCE, "Бухгалтерия" }, { TIMES, "Учёт рабочего времени" },
        { SETTINGS, "Настройки программы" }, { TASKS, "Дела и напоминания" },
        { CALENDAR, "Производственный календарь" }, { PII, "Персональные данные заказчиков" },
    };

    private Roles() { }
}
