package psr.sc;

import java.awt.Component;
import java.awt.Container;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Проверка сотрудников: блокировка на отпуск по датам, личные права сверх роли,
 * защита единственного администратора и кнопки окна.
 * Идёт на чистой базе (-Dpsr.data=build\test\tmp-users), чтобы не трогать рабочих людей.
 */
public class UsersCheck {

    static int bad;

    public static void main(String[] args) throws Exception {
        Db.get();
        check("проверка идёт на отдельной базе", !Db.DATA_DIR.getPath().equals("data"));
        Db.exec("DELETE FROM users");
        check("чистая база пуста", Users.all().isEmpty());

        dates();
        blocking();
        personalAccess();
        lastAdmin();
        validation();
        buttons();

        System.out.println(bad == 0 ? "ВСЁ ЧИСТО" : "ЕСТЬ ЗАМЕЧАНИЯ: " + bad);
        if (bad > 0) System.exit(1);
    }

    private static void dates() {
        check("пустое поле — не блокировка", !Users.blocked(""));
        check("сегодня блокирует включительно", Users.blocked(Tasks.dateText()));
        check("завтра блокирует", Users.blocked(date(1)));
        check("вчера уже отпустило", !Users.blocked(date(-1)));
        check("нечитаемая дата доступ не открывает", Users.blocked("не дата"));
    }

    private static void blocking() throws Exception {
        long id = Users.add("9111", "Тест заправщик", "Заправщик");
        Users.Row r = Users.find(id);
        check("сотрудник добавлен", r != null && "REFILLER".equals(r.role)
            && "доступен".equals(r.status()));

        Users.block(id, date(3), "отпуск");
        r = Users.find(id);
        check("блокировка видна в статусе", r.status().startsWith("заблокирован до " + date(3))
            && r.status().contains("отпуск"));
        check("заблокированный не войдёт", r.locked());

        Users.block(id, date(-1), "отгул");
        check("просроченная блокировка держит дверь открытой", !Users.find(id).locked());
        check("просроченная показана как вышедшая", Users.find(id).status().contains("срок вышел"));

        Users.block(id, "31.02.2099", "больничный");
        check("переполненная дата читается как следующая", Users.find(id).locked());

        Users.unblock(id);
        check("разблокировка вернула доступ", "доступен".equals(Users.find(id).status()));
        Users.remove(id);
    }

    private static void personalAccess() throws Exception {
        long id = Users.add("9222", "Тест руководитель", "Руководитель сервисных инженеров");
        check("роль по умолчанию правит заявки", enter(id) && Session.edits(Roles.REPAIRS));

        Users.setAccess(id, Roles.FINANCE + "+", Roles.REPAIRS + "+");
        enter(id);
        check("личное «можно править» открыло раздел роли", Session.may(Roles.FINANCE)
            && Session.edits(Roles.FINANCE));
        check("личное «только смотреть» сняло правку", Session.may(Roles.REPAIRS)
            && !Session.edits(Roles.REPAIRS));

        Users.setAccess(id, "", Roles.CUSTOMERS);
        enter(id);
        check("личное «закрыт» убрало и просмотр", !Session.may(Roles.CUSTOMERS));

        Users.setAccess(id, "", "");
        enter(id);
        check("без личных прав роль восстанавливается", Session.edits(Roles.REPAIRS)
            && !Session.may(Roles.FINANCE));

        Users.setAccess(id, Roles.SETTINGS, "");
        enter(id);
        check("личное право открыло раздел, которого в роли нет", Session.may(Roles.SETTINGS)
            && !Session.edits(Roles.SETTINGS));
        Users.remove(id);
    }

    private static void lastAdmin() throws Exception {
        long a = Users.add("9333", "Тест админ", "Администратор");
        check("в базе один админ", count("ADMIN") == 1);
        check("единственного админа нельзя разжалобить", fails(() -> Users.setRole(a, "Инженер")));
        check("его роль осталась админской", "ADMIN".equals(Users.find(a).role));
        check("единственного админа нельзя удалить", fails(() -> Users.remove(a)));

        long b = Users.add("9444", "Тест админ 2", "Администратор");
        check("когда админов двое — удалить можно", !fails(() -> Users.remove(b)));
        check("второй админ исчез", Users.find(b) == null);
        check("оставшийся админ уцелел", count("ADMIN") == 1
            && "ADMIN".equals(Users.find(a).role));
        check("снова один — разжалобить нельзя", fails(() -> Users.setRole(a, "Инженер")));

        long c = Users.add("9777", "Тест админ 3", "Администратор");
        check("когда админов двое — разжалобить можно", !fails(() -> Users.setRole(a, "Инженер")));
        check("роль сменилась", "ENGINEER".equals(Users.find(a).role));
        check("новый единственный админ защищён", fails(() -> Users.setRole(c, "Инженер")));
    }

    private static void validation() throws Exception {
        check("PIN из трёх цифр не годится", fails(() -> Users.add("123", "Тест", "Инженер")));
        check("PIN с буквой не годится", fails(() -> Users.add("12a4", "Тест", "Инженер")));
        check("пустое имя не годится", fails(() -> Users.add("9555", "   ", "Инженер")));
        int before = Users.all().size();
        long id = Users.add("9555", "Тест инженер", "Инженер");
        check("дубликат PIN отклонён", fails(() -> Users.add("9555", "Тест другой", "Инженер")));
        check("лишняя строка не появилась", Users.all().size() == before + 1);
        check("несуществующего сотрудника нет", Users.find(999999) == null);
        check("пустой запрос не пишет права", !fails(() -> Users.setAccess(id, "  ", null)));

        Users.currentPin = "9555";
        check("свой PIN узнаён", Users.isSelf(Users.find(id)));
        Users.currentPin = "0000";
        check("чужой PIN — не свой", !Users.isSelf(Users.find(id)));
        Users.remove(id);
        check("после удаления строки нет", Users.find(id) == null);
    }

    private static void buttons() throws Exception {
        long id = Users.add("9666", "Тест водитель", "Водитель");
        Users.block(id, date(5), "командировка");
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Frame f = new java.awt.Frame();
            UsersDialog d = new UsersDialog(f);
            List<String> texts = new ArrayList<>();
            collect(d, texts);
            check("кнопка добавления", texts.contains("Добавить сотрудника"));
            check("кнопка смены роли", texts.contains("Сменить роль"));
            check("кнопка личных прав", texts.contains("Личный доступ"));
            check("кнопка блокировки", texts.contains("В отпуск или отгул"));
            check("кнопка разблокировки", texts.contains("Разблокировать"));
            check("кнопка удаления", texts.contains("Удалить"));
            check("список показал блокировку", rows(d).contains("заблокирован до " + date(5)
                + " · командировка"));
            d.dispose();
            f.dispose();
        });
        Users.remove(id);
    }

    private static void collect(Container c, List<String> out) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton) out.add(((AbstractButton) x).getText());
            if (x instanceof Container) collect((Container) x, out);
        }
    }

    private static List<String> rows(Container c) {
        List<String> out = new ArrayList<>();
        for (Component x : c.getComponents()) {
            if (x instanceof JTable) {
                JTable t = (JTable) x;
                for (int r = 0; r < t.getRowCount(); r++)
                    for (int k = 0; k < t.getColumnCount(); k++)
                        out.add(String.valueOf(t.getValueAt(r, k)));
            }
            if (x instanceof Container) out.addAll(rows((Container) x));
        }
        return out;
    }

    /** Как вход по PIN: берём из базы роль и личные права. Возвращает true, если сотрудник доступен. */
    private static boolean enter(long id) throws Exception {
        Users.Row r = Users.find(id);
        Session.role = r.role; Session.allow = r.allow; Session.deny = r.deny;
        return !r.locked();
    }

    private static int count(String role) throws Exception {
        int n = 0;
        for (Users.Row r : Users.all()) if (role.equals(r.role)) n++;
        return n;
    }

    interface Action { void run() throws Exception; }

    private static boolean fails(Action a) {
        try { a.run(); return false; } catch (Exception expected) { return true; }
    }

    private static String date(int offsetDays) {
        return new SimpleDateFormat("dd.MM.yyyy").format(
            new Date(System.currentTimeMillis() + offsetDays * 24L * 60 * 60 * 1000));
    }

    private static void check(String what, boolean ok) {
        if (!ok) { bad++; System.out.println("FAIL : " + what); }
        else System.out.println("ок   : " + what);
    }
}
