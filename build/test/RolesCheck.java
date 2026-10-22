package psr.sc;

/**
 * Проверка матрицы ролей без Swing: что какой роли открыто и кто может править.
 * Тест фиксирует и обратную совместимость: старый ENGINEER по-прежнему не видит деньги и ПДн.
 */
public class RolesCheck {

    static int bad = 0;

    public static void main(String[] args) throws Exception {
        yes("ADMIN", Roles.SETTINGS, true);
        yes("ADMIN", Roles.FINANCE, true);
        see("ADMIN", Roles.PII);

        // инженер: заявки ведёт, склада и каталога касаться не может, денег и ПДн не видит
        yes("ENGINEER", Roles.REPAIRS, true);
        yes("ENGINEER", Roles.WAREHOUSE, false);
        no("ENGINEER", Roles.FINANCE);
        no("ENGINEER", Roles.SETTINGS);
        no("ENGINEER", Roles.PII);
        yes("ENGINEER", Roles.CARTRIDGES, false);

        yes("SERVICE_LEAD", Roles.CARTRIDGES, true);
        yes("SERVICE_LEAD", Roles.WAREHOUSE, true);
        yes("SERVICE_LEAD", Roles.REPORTS, true);
        see("SERVICE_LEAD", Roles.PII);
        no("SERVICE_LEAD", Roles.SETTINGS);
        no("SERVICE_LEAD", Roles.FINANCE);

        yes("REFILLER", Roles.CARTRIDGES, true);
        yes("REFILLER", Roles.KITS, true);
        no("REFILLER", Roles.CUSTOMERS);
        no("REFILLER", Roles.FINANCE);

        yes("DRIVER", Roles.REPAIRS, true);
        yes("DRIVER", Roles.CUSTOMERS, false);
        see("DRIVER", Roles.PII);
        no("DRIVER", Roles.WAREHOUSE);

        yes("LAWYER", Roles.CUSTOMERS, true);
        see("LAWYER", Roles.PII);
        no("LAWYER", Roles.CARTRIDGES);
        no("LAWYER", Roles.FINANCE);

        yes("ACCOUNTANT", Roles.FINANCE, true);
        yes("ACCOUNTANT", Roles.REPORTS, true);
        yes("ACCOUNTANT", Roles.CUSTOMERS, true);
        see("ACCOUNTANT", Roles.PII);
        no("ACCOUNTANT", Roles.REPAIRS);
        no("ACCOUNTANT", Roles.SETTINGS);

        yes("FINANCE_DIRECTOR", Roles.FINANCE, true);
        yes("FINANCE_DIRECTOR", Roles.REPORTS, true);
        no("FINANCE_DIRECTOR", Roles.SETTINGS);
        no("FINANCE_DIRECTOR", Roles.CARTRIDGES);

        yes("CONSULTANT", Roles.WAREHOUSE, false);
        yes("CONSULTANT", Roles.CUSTOMERS, false);
        no("CONSULTANT", Roles.FINANCE);

        yes("CALL_CENTER", Roles.REPAIRS, true);
        see("CALL_CENTER", Roles.PII);
        no("CALL_CENTER", Roles.KITS);

        // неизвестный код ведёт себя как инженер, а не как администратор
        no("UNKNOWN_ROLE", Roles.FINANCE);
        no("UNKNOWN_ROLE", Roles.PII);
        yes("UNKNOWN_ROLE", Roles.REPAIRS, true);

        // выпадающий список в окне пользователей: название ↔ код
        for (String[] r : Roles.LIST) {
            check(Roles.codeOf(r[1]).equals(r[0]), "код роли «" + r[1] + "»");
            check(Roles.title(r[0]).equals(r[1]), "название роли " + r[0]);
        }
        check(Roles.titles().length == 10, "ролей в справочнике: " + Roles.titles().length);
        check(Roles.title("ENGINEER").equals("Инженер"), "подпись в чате и шапке");

        System.out.println(bad == 0 ? "OK: матрица ролей" : "ОШИБОК: " + bad);
        if (bad > 0) System.exit(1);
    }

    /** Раздел открыт; право «править» к нему не применяется (ПДн — доступ, а не редактирование). */
    private static void see(String role, String section) {
        check(Roles.may(role, section), role + ": раздел " + section + " открыт");
    }

    private static void yes(String role, String section, boolean editable) {
        check(Roles.may(role, section), role + ": раздел " + section + " открыт");
        if (editable) check(Roles.edits(role, section), role + ": раздел " + section + " можно править");
        else check(!Roles.edits(role, section), role + ": раздел " + section + " только для чтения");
    }

    private static void no(String role, String section) {
        check(!Roles.may(role, section), role + ": раздела " + section + " нет");
    }

    private static void check(boolean ok, String what) {
        if (!ok) { bad++; System.out.println("FAIL  : " + what); }
    }

    private RolesCheck() { }
}
