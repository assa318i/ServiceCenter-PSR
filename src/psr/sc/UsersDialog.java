package psr.sc;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

/**
 * Окно сотрудников: добавить, удалить, сменить роль, закрыть вход на время отпуска или отгула
 * и выдать личные права сверх роли. Открывается из Настроек и только администратору.
 */
public class UsersDialog extends JDialog {

    /** Что админ выбирает по каждому разделу для конкретного сотрудника. */
    private static final String[] CHOICES = { "по роли", "виден всегда", "можно править",
        "только смотреть", "закрыт" };

    private final JTable table = new JTable() {
        /** Ячейки узких столбцов обрезаются — по наведению видно значение целиком. */
        @Override public String getToolTipText(java.awt.event.MouseEvent e) {
            int row = rowAtPoint(e.getPoint()), col = columnAtPoint(e.getPoint());
            if (row < 0 || col < 0) return null;
            Object v = getValueAt(row, col);
            return v == null ? null : v.toString();
        }
    };

    public UsersDialog(Window owner) {
        super(owner, "Сотрудники, роли и доступ", ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setContentPane(body);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Settings.headerBar(12), BorderLayout.NORTH);
        top.add(hint(), BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.styleTable(table);
        body.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(buttons(), BorderLayout.SOUTH);
        reload();
        setSize(1150, 620);
        setLocationRelativeTo(owner);
    }

    private JLabel hint() {
        JLabel l = new JLabel("Блокировка закрывает вход по PIN до указанной даты включительно."
            + " Личные права и роль применяются после входа по PIN.");
        l.setFont(Theme.font(13));
        l.setForeground(Theme.BLUE);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        return l;
    }

    /** Две строки: деловые кнопки и служебные — иначе ряд не влезает в окно даже на широком экране. */
    private JPanel buttons() {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JPanel work = new JPanel();
        work.setOpaque(false);
        work.add(Theme.on("Добавить сотрудника", Theme.YELLOW, e -> add()));
        work.add(Theme.on("Сменить роль", Theme.BLUE, e -> changeRole()));
        work.add(Theme.on("Личный доступ", Theme.BLUE, e -> access()));
        work.add(Theme.on("В отпуск или отгул", Theme.BRIGHT_RED, e -> block()));
        work.add(Theme.on("Разблокировать", Theme.GRAY_PANEL, e -> selected(r ->
            go(() -> { Users.unblock(r.id); reload(); }))));
        work.add(Theme.on("Удалить", Theme.RED, e -> remove()));
        JPanel aux = new JPanel();
        aux.setOpaque(false);
        aux.add(Theme.on("Обновить список", Theme.GRAY_PANEL, e -> reload()));
        aux.add(Theme.on("Закрыть", Theme.GRAY_PANEL, e -> dispose()));
        p.add(work);
        p.add(aux);
        return p;
    }

    private void reload() {
        try {
            Vector<Vector<Object>> data = new Vector<>();
            for (Users.Row r : Users.all()) data.add(new Vector<Object>(java.util.Arrays.asList(
                r.id, r.name.isEmpty() ? "без имени" : r.name, mask(r.pin), Roles.title(r.role),
                r.status(), human(r.allow, r.deny))));
            table.setModel(new DefaultTableModel(data, new Vector<String>(java.util.Arrays.asList(
                "№", "Сотрудник", "PIN", "Роль", "Доступ сейчас", "Личные права"))) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            });
            widths();
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    /** Модель пересоздаётся при каждом обновлении — ширины столбцов тоже задаются заново. */
    private void widths() {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumn("№").setPreferredWidth(40);
        table.getColumn("Сотрудник").setPreferredWidth(180);
        table.getColumn("PIN").setPreferredWidth(64);
        table.getColumn("Роль").setPreferredWidth(190);
        table.getColumn("Доступ сейчас").setPreferredWidth(210);
    }

    /** PIN открыт частично: админ понимает, чья это строка, но не копирует чужой PIN целиком. */
    private static String mask(String pin) {
        if (pin.length() < 5) return "····";
        return pin.substring(0, 2) + "··" + pin.substring(pin.length() - 2);
    }

    private static String human(String allow, String deny) {
        StringBuilder sb = new StringBuilder();
        for (String s : list(allow))
            sb.append("+ ").append(section(s)).append(s.endsWith("+") ? " (править) · " : " · ");
        for (String s : list(deny))
            sb.append("× ").append(section(s)).append(s.endsWith("+") ? " (не править) · " : " · ");
        return sb.length() == 0 ? "— только роль —" : sb.toString().trim();
    }

    private static String[] list(String spec) {
        return spec == null || spec.trim().isEmpty() ? new String[0] : spec.split(",");
    }

    private static String section(String key) {
        String k = key.endsWith("+") ? key.substring(0, key.length() - 1) : key;
        for (String[] s : Roles.SECTIONS) if (s[0].equals(k)) return s[1];
        return k;
    }

    /** Действие по выбранной строке списка. */
    private void selected(java.util.function.Consumer<Users.Row> action) {
        int row = table.getSelectedRow();
        if (row < 0) { Warn.show("Выберите сотрудника в списке"); return; }
        long id = (Long) table.getValueAt(row, 0);
        try {
            Users.Row r = Users.find(id);
            if (r == null) { Warn.show("Сотрудник уже удалён — обновите список"); return; }
            action.accept(r);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void go(Throwing action) {
        try { action.call(); } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private interface Throwing { void call() throws Exception; }

    private void add() {
        JPasswordField pin = new JPasswordField(12);
        JTextField name = new JTextField(18);
        pin.setFont(Theme.font(16));
        name.setFont(Theme.font(16));
        JComboBox<String> role = new JComboBox<>(Roles.titles());
        role.setSelectedItem(Roles.title("ENGINEER"));   // админ по умолчанию — лишний шанс создать второго админа
        role.setFont(Theme.font(16));
        int ok = JOptionPane.showConfirmDialog(this, new Object[] {
            "PIN (4–8 цифр):", pin, Theme.danger("Имя сотрудника:"), name, "Роль:", role },
            "Новый сотрудник", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        String code = new String(pin.getPassword()), who = name.getText();
        go(() -> {
            Users.add(code, who, (String) role.getSelectedItem());
            reload();
        });
    }

    private void changeRole() {
        selected(r -> {
            JComboBox<String> role = new JComboBox<>(Roles.titles());
            role.setSelectedItem(Roles.title(r.role));
            role.setFont(Theme.font(16));
            int ok = JOptionPane.showConfirmDialog(this,
                new Object[] { "Роль для «" + r.name + "»:", role }, "Сменить роль",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            go(() -> {
                Users.setRole(r.id, (String) role.getSelectedItem());
                reload();
                if (Users.isSelf(r)) Warn.show("Вы меняете роль самому себе: разделы изменятся"
                    + " после выхода и входа по PIN.");
            });
        });
    }

    /** По каждому разделу: оставить как в роли, выдать лично или закрыть. */
    private void access() {
        selected(r -> {
            @SuppressWarnings("unchecked")
            JComboBox<String>[] boxes = new JComboBox[Roles.SECTIONS.length];
            JPanel grid = new JPanel(new GridLayout(0, 2, 16, 4));
            grid.setOpaque(false);
            for (int i = 0; i < Roles.SECTIONS.length; i++) {
                JComboBox<String> box = new JComboBox<>(CHOICES);
                box.setFont(Theme.font(15));
                box.setSelectedItem(current(r, Roles.SECTIONS[i][0]));
                boxes[i] = box;
                grid.add(new JLabel(Roles.SECTIONS[i][1]));
                grid.add(box);
            }
            int ok = JOptionPane.showConfirmDialog(this, new Object[] {
                "Личный доступ для «" + r.name + "» (роль — " + Roles.title(r.role) + "):", grid },
                "Личный доступ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            StringBuilder allow = new StringBuilder(), deny = new StringBuilder();
            for (int i = 0; i < boxes.length; i++) {
                String key = Roles.SECTIONS[i][0];
                Object pick = boxes[i].getSelectedItem();
                if (CHOICES[1].equals(pick)) append(allow, key);
                else if (CHOICES[2].equals(pick)) append(allow, key + "+");
                else if (CHOICES[3].equals(pick)) append(deny, key + "+");
                else if (CHOICES[4].equals(pick)) append(deny, key);
            }
            String a = allow.toString(), d = deny.toString();
            go(() -> {
                Users.setAccess(r.id, a, d);
                reload();
                if (Users.isSelf(r)) Warn.show("Вы меняете доступ самому себе: новые права"
                    + " применятся после входа по PIN.");
            });
        });
    }

    private static String current(Users.Row r, String key) {
        if (Roles.bare(r.deny, key)) return CHOICES[4];
        if (Roles.plus(r.deny, key)) return CHOICES[3];
        if (Roles.plus(r.allow, key)) return CHOICES[2];
        if (Roles.bare(r.allow, key)) return CHOICES[1];
        return CHOICES[0];
    }

    private static void append(StringBuilder sb, String key) {
        if (sb.length() > 0) sb.append(',');
        sb.append(key);
    }

    private void block() {
        selected(r -> {
            JTextField until = new JTextField(Tasks.dateText(), 12);
            until.setFont(Theme.font(16));
            JComboBox<String> reason = new JComboBox<>(Users.REASONS);
            reason.setEditable(true);
            reason.setFont(Theme.font(16));
            int ok = JOptionPane.showConfirmDialog(this, new Object[] {
                "Закрыть вход «" + r.name + "» до даты включительно:",
                Theme.danger("Дата (дд.мм.гггг):"), until, "Причина:", reason },
                "Блокировка на отпуск или отгул", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            String date = until.getText(), why = (String) reason.getEditor().getItem();
            go(() -> {
                Users.block(r.id, date, why);
                reload();
                if (Users.isSelf(r)) Warn.show("Вы заблокировали сами себя: после выхода из"
                    + " программы вход под этим PIN будет закрыт до " + date + ".");
            });
        });
    }

    private void remove() {
        selected(r -> {
            int ok = JOptionPane.showConfirmDialog(this, "Удалить сотрудника «" + r.name + "» ("
                + Roles.title(r.role) + ")?\n\nЗаявки, складские операции и лента с его именем"
                + " останутся — исчезнет только вход по PIN.", "Удаление сотрудника",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
            go(() -> {
                Users.remove(r.id);
                reload();
            });
        });
    }
}
