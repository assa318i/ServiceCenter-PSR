package psr.sc;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Производственный календарь и учёт времени: нормы 2026 года сверены с официально
 * опубликованными, короткие дни — со ст. 95 ТК, доступ к разделам — с матрицей ролей.
 * Табель проверяется на отдельной базе (-Dpsr.data=build\test\tmp-cal), чтобы не трогать
 * рабочих людей из data\psr.db.
 */
public class CalendarCheck {

    /** Официальные нормы 2026 года: {рабочих дней, часов} по месяцам. */
    private static final int[][] NORM = {
        { 15, 120 }, { 19, 152 }, { 21, 168 }, { 22, 175 }, { 19, 151 }, { 21, 167 },
        { 23, 184 }, { 21, 168 }, { 22, 176 }, { 22, 176 }, { 20, 159 }, { 22, 176 },
    };
    private static final String[] MONTHS = { "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь" };

    static int bad;

    public static void main(String[] args) throws Exception {
        norms();
        offDays();
        roles();
        hours();
        Db.get();
        check("проверка идёт на отдельной базе", !Db.DATA_DIR.getPath().equals("data"));
        Db.exec("DELETE FROM timesheet");
        sheet();
        System.out.println(bad == 0 ? "ВСЁ ЧИСТО" : "ЕСТЬ ЗАМЕЧАНИЯ: " + bad);
        if (bad > 0) System.exit(1);
    }

    private static void norms() {
        int days = 0, hours = 0;
        for (int m = 0; m < 12; m++) {
            int[] n = ProdCalendar.norm(2026, m);
            check(MONTHS[m] + " — " + n[0] + " дн. / " + n[1] + " ч.",
                n[0] == NORM[m][0] && n[1] == NORM[m][1]);
            days += n[0];
            hours += n[1];
        }
        check("за год 247 рабочих дней", days == 247);
        check("за год 1972 нормы часов", hours == 1972);
        check("переносы 2026 года заданы", ProdCalendar.known(2026));
        check("год без постановления помечается", !ProdCalendar.known(2027));
        check("коротких дней в 2026 четыре", shortOf(2026).size() == 4);
        check("короткие дни 2026 совпадают с переносами", shortOf(2026).equals(Arrays.asList(
            "30.04.2026", "08.05.2026", "11.06.2026", "03.11.2026")));
    }

    private static void offDays() {
        String[][] rest = {
            { "01.01.2026", "новый год" }, { "07.01.2026", "рождество" },
            { "09.01.2026", "перенос по постановлению" }, { "23.02.2026", "23 февраля" },
            { "08.03.2026", "8 марта" }, { "09.03.2026", "перенос с воскресенья 8 марта" },
            { "01.05.2026", "весенний праздник" }, { "09.05.2026", "день победы" },
            { "11.05.2026", "перенос с субботы 9 мая" }, { "12.06.2026", "день России" },
            { "04.11.2026", "день народного единства" }, { "24.10.2026", "обычная суббота" },
            { "31.12.2026", "перенос с воскресенья 4 января" },
        };
        for (String[] r : rest) check(r[1] + " — выходной", ProdCalendar.isOff(r[0]));
        String[][] work = {
            { "16.10.2026", "обычный рабочий день" }, { "20.02.2026", "пятница, а не канун праздника" },
            { "30.04.2026", "короткий, но рабочий" }, { "08.05.2026", "короткий перед 9 мая" },
            { "11.06.2026", "короткий перед днём России" }, { "03.11.2026", "короткий перед 4 ноября" },
        };
        for (String[] w : work) check(w[1] + " — рабочий", !ProdCalendar.isOff(w[0]));
        check("кривая дата не выходной", !ProdCalendar.isOff("32.13.2026"));
        check("пустое поле — не выходной", !ProdCalendar.isOff(""));
    }

    private static void roles() {
        for (String[] r : Roles.LIST)
            check("календарь открыт роли «" + r[1] + "»", Roles.may(r[0], Roles.CALENDAR));
        for (String code : new String[] { "ADMIN", "SERVICE_LEAD", "FINANCE_DIRECTOR", "ACCOUNTANT" })
            check("ведёт табель: " + Roles.title(code), Roles.edits(code, Roles.TIMES));
        for (String code : new String[] { "ENGINEER", "REFILLER" })
            check("табель только виден: " + Roles.title(code),
                Roles.may(code, Roles.TIMES) && !Roles.edits(code, Roles.TIMES));
        for (String code : new String[] { "LAWYER", "CONSULTANT", "CALL_CENTER", "DRIVER" })
            check("раздела табеля нет: " + Roles.title(code), !Roles.may(code, Roles.TIMES));
    }

    private static void hours() {
        check("пятидневка — 8 часов", TimesheetPanel.hours("09:00", "17:00") == 8.0);
        check("полдня — 8,5 часа", TimesheetPanel.hours("09:00", "17:30") == 8.5);
        check("полчаса — 0,5", TimesheetPanel.hours("09:00", "09:30") == 0.5);
        check("без прихода часов нет", TimesheetPanel.hours("", "17:00") == 0);
        check("уход раньше прихода", TimesheetPanel.hours("17:00", "09:00") == 0);
        check("не время не считается", TimesheetPanel.hours("9.00", "17:00") == 0);
        check("через полночь не считается", TimesheetPanel.hours("22:00", "06:00") == 0);
        check("24:00 недопустимо", TimesheetPanel.hours("00:00", "24:00") == 0);
    }

    /** Отметки видит вся роль с разделом, править может только руководитель, бухгалтер, финдир. */
    private static void sheet() throws Exception {
        long lead = Users.add("24680", "Проверка Руководителя", Roles.title("SERVICE_LEAD"));
        long eng = Users.add("13579", "Проверка Инженера", Roles.title("ENGINEER"));
        String today = Tasks.dateText();
        Db.exec("INSERT INTO timesheet(who,day,t_in,t_out,note,created) VALUES("
            + "'Проверка Руководителя','" + today + "','09:00','17:30','сверка','')");
        enter(lead);
        SwingUtilities.invokeAndWait(() -> {
            TimesheetPanel p = new TimesheetPanel();
            List<String> texts = new ArrayList<>();
            collect(p, texts);
            check("руководитель видит «Добавить отметку»", texts.contains("Добавить отметку"));
            check("руководитель видит «Удалить»", texts.contains("Удалить"));
            check("из табеля открывается календарь", texts.contains("Календарь РФ"));
            check("сегодняшняя отметка в списке", cells(p).contains(today));
            check("часы посчитаны в строке", cells(p).contains(WarehousePanel.fmt(8.5)));
            check("итог месяца считает часы", labels(p).contains("Отметок: 1 · часов: "
                + WarehousePanel.fmt(8.5) + "  ·  Проверка Руководителя " + WarehousePanel.fmt(8.5)));
        });
        enter(eng);
        SwingUtilities.invokeAndWait(() -> {
            TimesheetPanel p = new TimesheetPanel();
            List<String> texts = new ArrayList<>();
            collect(p, texts);
            check("инженеру правка закрыта", !texts.contains("Добавить отметку"));
            check("инженер видит отметки за месяц", cells(p).contains(today));
            check("календарь инженеру открыт", texts.contains("Календарь РФ"));
        });
        Db.exec("DELETE FROM timesheet");
        Users.remove(lead);
        Users.remove(eng);
        Session.role = "ADMIN";
        Session.allow = "";
        Session.deny = "";
    }

    /** Все сокращённые дни года как «дд.мм.гггг» — по порядку месяцев. */
    private static List<String> shortOf(int y) {
        boolean[][] rest = ProdCalendar.off(y);
        boolean[][] shorty = ProdCalendar.shortened(y, rest);
        List<String> out = new ArrayList<>();
        for (int m = 0; m < 12; m++)
            for (int d = 1; d <= 31; d++)
                if (shorty[m][d]) out.add(String.format("%02d.%02d.%d", d, m + 1, y));
        return out;
    }

    /** Как вход по PIN: берём из базы роль и личные права сотрудника. */
    private static void enter(long id) throws Exception {
        Users.Row r = Users.find(id);
        Session.role = r.role;
        Session.allow = r.allow;
        Session.deny = r.deny;
    }

    private static void collect(Container c, List<String> out) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton) out.add(((AbstractButton) x).getText());
            if (x instanceof Container) collect((Container) x, out);
        }
    }

    private static List<String> labels(Container c) {
        List<String> out = new ArrayList<>();
        for (Component x : c.getComponents()) {
            if (x instanceof JLabel) out.add(((JLabel) x).getText());
            if (x instanceof Container) out.addAll(labels((Container) x));
        }
        return out;
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
