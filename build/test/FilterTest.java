import java.lang.reflect.Method;
import psr.sc.FilterBar;

/** Проверка маски фильтра без Swing: слово=подстрока, *=шаблон, слова через пробел=И. */
public class FilterTest {

    static Method hit;
    static int bad;

    public static void main(String[] args) throws Exception {
        hit = FilterBar.class.getDeclaredMethod("hit", String.class, String.class);
        hit.setAccessible(true);

        yes("canon", "Принтер Canon 2500i");
        yes("CANON", "Принтер canon 2500i");
        yes("принт", "Принтер");
        yes("са*300", "Самсунг 300");
        yes("2500*", "Canon 2500i");
        yes("328*", "SN-328X");
        yes("*328", "SN-328");
        yes("a*b*c", "xx-a-yy-b-zz-c");
        yes("*", "что угодно");
        no("300", "Самсунг 2500");
        no("2500i", "Canon 2500 X");
        no("са*301", "Самсунг 300");
        yes("(налич*", "(наличные)");

        System.out.println(bad == 0 ? "OK: все проверки прошли" : "ОШИБОК: " + bad);
        if (bad > 0) System.exit(1);
    }

    static boolean match(String query, String... values) throws Exception {
        for (String word : query.trim().split("\\s+")) {
            boolean hitOne = false;
            for (String v : values) if (v != null && (Boolean) hit.invoke(null, word, v)) hitOne = true;
            if (!hitOne) return false;
        }
        return true;
    }

    static void yes(String q, String v) throws Exception {
        check(q + " → «" + v + "» должен пройти", match(q, v), true);
    }

    static void no(String q, String v) throws Exception {
        check(q + " → «" + v + "» не должен пройти", match(q, v), false);
    }

    static void check(String what, boolean got, boolean want) {
        if (got != want) { bad++; System.out.println("ПРОВАЛ: " + what); }
        else System.out.println("ok    : " + what);
    }
}
