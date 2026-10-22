package psr.sc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Производственный календарь РФ: праздники по ст. 112 ТК, переносы выходных по постановлениям
 * Правительства и сокращённые на час дни по ст. 95. Нужен, чтобы сотрудник сам проверил дату
 * выходного, праздника или короткого дня, не выходя из программы.
 */
public class ProdCalendar extends JDialog {

    private static final String[] NAMES = { "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь" };
    private static final String[] WEEK = { "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс" };

    /** Нерабочие праздничные дни по ст. 112 ТК РФ: {месяц с нуля, день}. */
    private static final int[][] HOLIDAYS = {
        { 0, 1 }, { 0, 2 }, { 0, 3 }, { 0, 4 }, { 0, 5 }, { 0, 6 }, { 0, 7 }, { 0, 8 },
        { 1, 23 }, { 2, 8 }, { 4, 1 }, { 4, 9 }, { 5, 12 }, { 10, 4 },
    };

    /** Выходные, перенесённые постановлением Правительства: {год, месяц с нуля, день}. */
    private static final int[][] BY_DECREE = {
        { 2026, 0, 9 },     // с субботы 3 января — постановление от 24.09.2025 № 1466
        { 2026, 11, 31 },   // с воскресенья 4 января
    };

    private static final Color OFF_BG = new Color(0xFFDCDC);
    private static final Color SHORT_BG = new Color(0xFFF0C2);

    private final JPanel months = new JPanel(new GridLayout(4, 3, 10, 10));
    private final JComboBox<Integer> year = new JComboBox<>();
    private final JLabel notice = new JLabel(" ");

    public ProdCalendar(Window owner) {
        super(owner, "Производственный календарь РФ", ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBackground(Theme.GRAY_BG);
        body.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setContentPane(body);

        int now = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = now - 1; y <= now + 2; y++) year.addItem(y);
        year.setSelectedItem(now);
        year.setFont(Theme.font(15));
        year.addActionListener(e -> rebuild());

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Год: "));
        tools.add(year);
        tools.add(new JLabel("    красным — выходной или праздник,   жёлтым — день на час короче"));
        tools.add(Theme.on("Открыть в браузере", Theme.BLUE, e -> openBrowser()));
        tools.add(Theme.on("Закрыть", Theme.GRAY_PANEL, e -> dispose()));
        notice.setFont(Theme.font(13));
        notice.setForeground(Theme.BRIGHT_RED);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(tools, BorderLayout.NORTH);
        top.add(notice, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);

        months.setBackground(Theme.GRAY_BG);
        body.add(new JScrollPane(months), BorderLayout.CENTER);
        rebuild();
        setSize(1080, 760);
        setLocationRelativeTo(owner);
    }

    public static void open(Window owner) {
        new ProdCalendar(owner).setVisible(true);
    }

    private void rebuild() {
        int y = (Integer) year.getSelectedItem();
        boolean[][] off = off(y);
        boolean[][] shorty = shortened(y, off);
        months.removeAll();
        for (int m = 0; m < 12; m++) months.add(monthPanel(y, m, off, shorty));
        notice.setText(known(y) ? " "
            : "Переносы выходных на " + y + " год постановлением Правительства в программе"
                + " не заданы — показаны только праздники по ст. 112 ТК и совпадения с выходным."
                + " Точные даты смотрите в официальном календаре (кнопка «Открыть в браузере»).");
        months.revalidate();
        months.repaint();
    }

    private JPanel monthPanel(int y, int m, boolean[][] off, boolean[][] shorty) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(new Color(0xBFBFBF)));
        JLabel title = new JLabel(NAMES[m], SwingConstants.CENTER);
        title.setFont(Theme.fontB(15));
        p.add(title, BorderLayout.NORTH);

        JPanel g = new JPanel(new GridLayout(0, 7, 1, 1));
        g.setOpaque(false);
        for (String w : WEEK) {
            JLabel l = new JLabel(w, SwingConstants.CENTER);
            l.setFont(Theme.font(11));
            l.setForeground(Color.GRAY);
            g.add(l);
        }
        int lead = (firstWeekday(y, m) + 5) % 7;
        for (int i = 0; i < lead; i++) g.add(new JLabel(" "));
        for (int d = 1; d <= length(y, m); d++) {
            JLabel cell = new JLabel(String.valueOf(d), SwingConstants.CENTER);
            cell.setFont(Theme.font(13));
            cell.setOpaque(true);
            cell.setBackground(off[m][d] ? OFF_BG : shorty[m][d] ? SHORT_BG : Color.WHITE);
            cell.setForeground(off[m][d] ? Theme.BRIGHT_RED : shorty[m][d]
                ? new Color(0x7A5B00) : Theme.BLACK);
            g.add(cell);
        }
        p.add(g, BorderLayout.CENTER);
        int[] n = norm(y, m);
        JLabel sum = new JLabel("рабочих дней " + n[0] + " · часов " + n[1], SwingConstants.CENTER);
        sum.setFont(Theme.font(11));
        sum.setForeground(Theme.BLUE);
        p.add(sum, BorderLayout.SOUTH);
        return p;
    }

    // ---------------- расчёт ----------------

    /** Есть ли в программе переносы по постановлению для этого года. */
    public static boolean known(int y) {
        for (int[] t : BY_DECREE) if (t[0] == y) return true;
        return false;
    }

    /** Дата «дд.мм.гггг» — выходной, праздник или перенесённый выходной? */
    public static boolean isOff(String date) {
        try {
            String s = date.trim();
            return off(Integer.parseInt(s.substring(6, 10)))[Integer.parseInt(s.substring(3, 5)) - 1]
                [Integer.parseInt(s.substring(0, 2))];
        } catch (Exception badDate) {
            return false;
        }
    }

    /** [месяц][день] — нерабочий день: выходной, праздник, перенос по постановлению или автоперенос. */
    public static boolean[][] off(int y) {
        boolean[][] v = new boolean[12][32];
        for (int m = 0; m < 12; m++)
            for (int d = 1; d <= length(y, m); d++)
                v[m][d] = weekend(y, m, d) || holiday(m, d);
        for (int[] t : BY_DECREE) if (t[0] == y) v[t[1]][t[2]] = true;
        // Ч. 2 ст. 112: выходной, совпавший с праздником, уходит на следующий рабочий день.
        // Январский блок (1–8 января) переносит Правительство — его здесь не трогаем.
        for (int[] h : HOLIDAYS) {
            if (h[0] == 0 || !weekend(y, h[0], h[1])) continue;
            int[] next = nextWorking(v, y, h[0], h[1]);
            if (next != null) v[next[0]][next[1]] = true;
        }
        return v;
    }

    /** [месяц][день] — рабочий день перед праздником, сокращённый на час (ст. 95). */
    public static boolean[][] shortened(int y, boolean[][] offDays) {
        boolean[][] s = new boolean[12][32];
        for (int[] h : HOLIDAYS) {
            int[] prev = dayBefore(y, h[0], h[1]);
            if (prev != null && !offDays[prev[0]][prev[1]]) s[prev[0]][prev[1]] = true;
        }
        return s;
    }

    /** Норма {дней, часов} при 40-часовой неделе; час сокращённого дня учтён. */
    public static int[] norm(int y, int month) {
        boolean[][] off = off(y);
        boolean[][] shorty = shortened(y, off);
        int days = 0, shortDays = 0;
        for (int d = 1; d <= length(y, month); d++) {
            if (!off[month][d]) days++;
            if (shorty[month][d]) shortDays++;
        }
        return new int[] { days, days * 8 - shortDays };
    }

    /** Предыдущий день того же года; null — если он относится к прошлому году. */
    private static int[] dayBefore(int y, int month, int day) {
        if (day > 1) return new int[] { month, day - 1 };
        if (month == 0) return null;
        return new int[] { month - 1, length(y, month - 1) };
    }

    /** Первый рабочий день после праздничного, на который уходит совпавший с ним выходной. */
    private static int[] nextWorking(boolean[][] off, int y, int month, int day) {
        for (int guard = 0; guard < 8; guard++) {
            day++;
            if (day > length(y, month)) { day = 1; month++; }
            if (month > 11) return null;
            if (!off[month][day]) return new int[] { month, day };
        }
        return null;
    }

    private static boolean holiday(int month, int day) {
        for (int[] h : HOLIDAYS) if (h[0] == month && h[1] == day) return true;
        return false;
    }

    private static boolean weekend(int y, int month, int day) {
        int idx = (firstWeekday(y, month) + day - 1) % 7;   // 0 = суббота, 1 = воскресенье
        return idx == 0 || idx == 1;
    }

    /** День недели первого числа месяца: Calendar.SUNDAY … Calendar.SATURDAY. */
    private static int firstWeekday(int y, int month) {
        return new GregorianCalendar(y, month, 1).get(Calendar.DAY_OF_WEEK);
    }

    private static int length(int y, int month) {
        return new GregorianCalendar(y, month, 1).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private void openBrowser() {
        String url = "https://www.consultant.ru/law/ref/calendar/proizvodstvennye/"
            + year.getSelectedItem() + "/";
        try {
            if (!java.awt.Desktop.isDesktopSupported()) throw new Exception("нет доступа к браузеру");
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Throwable t) {
            Warn.show("Браузер не открылся. Адрес календаря:\n" + url);
        }
    }
}
