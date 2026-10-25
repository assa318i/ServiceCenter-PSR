package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Заявки на ремонт: сквозной контроль статусов и Service Level на всём сроке ремонта. */
public class RepairsPanel extends JPanel {

    public static final String[] STATUS = {
        "OPEN", "AGREEMENT", "WAIT_PARTS", "PARTS_LATER", "IN_WORK", "AT_ENGINEER",
        "EXECUTION", "DONE_ISSUED" };
    public static final String[] STATUS_RU = {
        "Открыт ремонт (приём)", "Согласование ЗЧ/цены с заказчиком", "В ожидании запчасти",
        "Запчасть подвезут позже", "В работе", "На столе у инженера", "Исполнение",
        "Завершён, выдан" };
    public static final String[] SLA_RU = { "Обычный", "Внимание", "Опасно" };

    private final App app;
    private final JTable table = new JTable();
    private final JComboBox<String> filter = new JComboBox<>();
    private final FilterBar bar = new FilterBar("Заявки", () -> load());
    private final JCheckBox mine;
    private final JCheckBox late;
    private String lastFilter = "Все";

    public RepairsPanel(App app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Статус: "));
        filter.addItem("Все");
        for (String s : STATUS_RU) filter.addItem(s);
        filter.setFont(Theme.font(14));
        filter.addActionListener(e -> { lastFilter = (String) filter.getSelectedItem(); load(); });
        tools.add(filter);
        tools.add(Box.createHorizontalStrut(12));
        tools.add(Theme.on("Новая заявка", Theme.YELLOW, e -> RepairForm.show(app, null, this)));
        tools.add(Theme.on("Изменить", Theme.BLUE, e -> RepairForm.show(app, selectedId(), this)));
        tools.add(Theme.on("Обновить", Theme.BLUE, e -> load()));
        tools.add(menuBtn("Печать", new String[] {
            "Квитанция о приёме", "Чек (оплата)", "Этикетка со штрихкодом", "Наклейки (пакет)"
        }, new Runnable[] {
            () -> printReceipt(), () -> printBill(), () -> printLabel(), () -> Labels.batchDialog(null)
        }));
        tools.add(menuBtn("Запчасти", new String[] {
            "Набор ЗЧ к заявке", "ЗЧ на заявку"
        }, new Runnable[] {
            () -> applyKit(), () -> consumeParts()
        }));
        tools.add(menuBtn("Заявка", new String[] {
            "Статус", "Service Level", "Лента заявки", "Дела по заявке"
        }, new Runnable[] {
            () -> changeStatus(), () -> setSla(), () -> showStream(), () -> Tasks.show(this, selectedId())
        }));
        tools.add(menuBtn("Фото", new String[] {
            "Фото к заявке +", "Открыть фото"
        }, new Runnable[] {
            () -> addPhoto(), () -> openPhotos()
        }));
        tools.add(Theme.on("Импорт из файла", Theme.GRAY_PANEL, e -> RepairImport.dialog(this)));
        tools.add(Theme.on("Найти по штрихкоду", Theme.BLUE, e -> scanFind()));
        mine = bar.flag("назначено мне");
        late = bar.flag("просроченные");
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(tools, BorderLayout.CENTER);
        north.add(bar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        Theme.styleTable(table);
        load();
    }

    Long selectedId() {
        int r = table.getSelectedRow();
        return r < 0 ? null : (Long) table.getValueAt(r, 0);
    }

    void changeStatus() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        String cur = (String) table.getValueAt(table.getSelectedRow(), 4);
        String s = (String) JOptionPane.showInputDialog(this, "Новый статус ремонта:",
            "Статус", JOptionPane.QUESTION_MESSAGE, null, STATUS_RU, cur);
        if (s == null || s.equals(cur)) return;
        int code = Arrays.asList(STATUS_RU).indexOf(s);
        try {
            String now = stamp();
            Db.exec("UPDATE repairs SET status='" + STATUS[code] + "'"
                + (code == STATUS.length - 1 ? ", issued='" + now + "'" : "")
                + (code == 1 ? ", agreed='" + now + "'" : "") + ", rev=rev+1 WHERE id=" + id);
            Stream.log(id, "Статус → " + STATUS_RU[code]);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    void setSla() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        String s = (String) JOptionPane.showInputDialog(this,
            "Service Level (Внимание/Опасно отображаются красным):",
            "Service Level", JOptionPane.QUESTION_MESSAGE, null, SLA_RU, SLA_RU[0]);
        if (s == null) return;
        try {
            Db.exec("UPDATE repairs SET sla='" + s + "', rev=rev+1 WHERE id=" + id);
            Stream.log(id, "Service Level → " + s);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        load();
    }

    void showStream() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        Stream.show(this, id);
    }

    void printReceipt() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        Documents.receipt(null, id);
    }

    void printBill() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        Documents.bill(null, id);
    }

    void printLabel() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        Documents.label(null, id);
    }

    /** Поиск по наклейке: сканер вводит код с клавиатуры — заявка находится и выделяется. */
    void scanFind() {
        Object s = JOptionPane.showInputDialog(this,
            "Отсканируйте наклейку или введите код (PSR-R№):", "Поиск по штрихкоду",
            JOptionPane.QUESTION_MESSAGE);
        if (s == null) return;
        String code = String.valueOf(s).trim().toUpperCase();
        if (!code.startsWith("PSR-R")) { Warn.show("Не наш код наклейки: " + code); return; }
        long id;
        try { id = Long.parseLong(code.substring(5).replaceAll("[^0-9].*$", "")); }
        catch (Exception ex) { Warn.show("Не удалось прочитать номер в коде: " + code); return; }
        filter.setSelectedItem("Все");
        bar.clear();
        load();
        for (int i = 0; i < table.getRowCount(); i++) {
            if (Long.valueOf(id).equals(table.getValueAt(i, 0))) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
        Warn.show("Ремонт №" + id + " не найден среди заявок");
    }

    void applyKit() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        String equipment = String.valueOf(table.getValueAt(table.getSelectedRow(), 2));
        KitsPanel.applyToRepair(this, id, equipment);
    }

    /** Запчасть ушла на эту заявку: остаток минусуется и в журнале склада появляется строка. */
    void consumeParts() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        Stock.consumeFromRepair(this, id);
    }

    /** Фотографии (до/после ремонта) хранятся как вложения с repair_id. */
    void addPhoto() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Фото к заявке (JPG/PNG)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JPG, PNG", "jpg", "jpeg", "png"));
        if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        try {
            java.io.File src = fc.getSelectedFile();
            java.io.File dir = new java.io.File(Db.DATA_DIR, "attaches");
            dir.mkdirs();
            java.io.File dst = new java.io.File(dir, System.currentTimeMillis() + "_" + src.getName());
            java.nio.file.Files.copy(src.toPath(), dst.toPath());
            Db.exec("INSERT INTO attachments(repair_id,kind,file,added) VALUES(" + id + ",'Фото','"
                + Db.esc(dst.getPath()) + "','" + stamp() + "')");
            Stream.log(id, "Приложено фото: " + dst.getName());
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    void openPhotos() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заявку"); return; }
        java.util.Vector<String> names = new java.util.Vector<>();
        java.util.Vector<String> paths = new java.util.Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT file FROM attachments WHERE repair_id=" + id
                 + " AND kind='Фото' ORDER BY id")) {
            while (rs.next()) {
                names.add(new java.io.File(rs.getString(1)).getName());
                paths.add(rs.getString(1));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (names.isEmpty()) { Warn.show("К заявке нет фотографий"); return; }
        String pick = (String) JOptionPane.showInputDialog(this, "Какое фото открыть:",
            "Фото заявки", JOptionPane.PLAIN_MESSAGE, null, names.toArray(), names.get(0));
        if (pick == null) return;
        try { java.awt.Desktop.getDesktop().open(new java.io.File(paths.get(names.indexOf(pick)))); }
        catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    void load() {
        Vector<Vector<Object>> rows = new Vector<>();
        String sql = "SELECT r.id, TRIM(c.org || ' ' || c.fio), r.equipment, r.problem, r.status,"
            + " r.sla, r.wait_until, r.opened, r.engineer, r.price, r.issued, r.serial,"
            + " r.warranty_days FROM repairs r"
            + " LEFT JOIN customers c ON c.id = r.customer_id ORDER BY r.id DESC";
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String statusRu = ru(rs.getString(5));
                if (!"Все".equals(lastFilter) && !lastFilter.equals(statusRu)) continue;
                String slaTag = autoSla(statusRu, nz(rs.getString(7)), nz(rs.getString(6)),
                    nz(rs.getString(8)), nz(rs.getString(11)));
                String eq = nz(rs.getString(3)), problem = nz(rs.getString(4));
                String engineer = nz(rs.getString(9)), serial = nz(rs.getString(12));
                if (!bar.match(rs.getString(2), eq, problem, engineer, serial)) continue;
                if (bar.on(mine) && !Session.name.equals(engineer)) continue;
                if (bar.on(late) && !"CRITICAL".equals(slaTag)) continue;
                rows.add(new Vector<>(Arrays.asList(rs.getLong(1), rs.getString(2), eq,
                    problem, statusRu, ru2sla(rz(slaTag)), nz(rs.getString(7)),
                    nz(rs.getString(8)), engineer, rs.getDouble(10),
                    daysInRepair(nz(rs.getString(8)), nz(rs.getString(11))),
                    serial, rs.getInt(13), slaTag)));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }

        Vector<String> cols = new Vector<>(Arrays.asList("№", "Заказчик", "Оборудование",
            "Неисправность", "Статус", "Service Level", "Ожидание до", "Открыта",
            "Инженер", "Цена", "Дней в ремонте", "Серийный №", "Гарантия, дн.", "tag"));
        table.setModel(new DefaultTableModel(rows, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        });
        table.getColumn("tag").setMinWidth(0);
        table.getColumn("tag").setMaxWidth(0);
        table.getColumn("Цена").setCellRenderer(money());
        for (int i = 0; i < table.getColumnCount() - 1; i++)
            table.getColumnModel().getColumn(i).setCellRenderer(Theme.slaRenderer(table, table.getColumnCount() - 1));
    }

    /** Auto-уровень:danger для просроченных сроков ожидания или выхода за целевой срок ремонта. */
    static String autoSla(String statusRu, String waitUntil, String sla, String opened, String issued) {
        if (statusRu.startsWith("Заверш")) return "NORMAL";
        if ("Опасно".equals(sla)) return "CRITICAL";
        if ("Внимание".equals(sla)) return "WARNING";
        if (statusRu.startsWith("В ожидании") && overdue(waitUntil)) return "CRITICAL";
        try {
            long d = daysInRepair(opened, issued);
            int target = Integer.parseInt(Settings.get("sla_days"));
            if (d > target) return "CRITICAL";
            if (d > target * 3 / 4) return "WARNING";
        } catch (Exception ignored) { }
        return "NORMAL";
    }

    static long daysInRepair(String opened, String issued) {
        try {
            long start = day(opened);
            long end = issued.isEmpty() ? System.currentTimeMillis() : day(issued);
            return Math.max(0, (end - start) / 86_400_000L);
        } catch (Exception e) { return -1; }
    }

    static boolean overdue(String date) {
        try { return day(date) < System.currentTimeMillis(); } catch (Exception e) { return false; }
    }

    static long day(String s) throws Exception {
        return new SimpleDateFormat("dd.MM.yyyy").parse(s.trim()).getTime();
    }

    private static String ru2sla(String tag) {
        if ("CRITICAL".equals(tag)) return "Опасно";
        if ("WARNING".equals(tag)) return "Внимание";
        return "Обычный";
    }

    /** Пользовательский уровень хранится в sla, автоуровень дописывается к нему. */
    private static String rz(String sla) {
        return sla == null || sla.isEmpty() ? "Обычный" : sla;
    }

    private DefaultTableCellRenderer money() {
        return new DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(
                    JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                setText(String.format("%,.2f руб.", v));
                return this;
            }
        };
    }

    static String ru(String code) {
        int i = Arrays.asList(STATUS).indexOf(code);
        return i < 0 ? code : STATUS_RU[i];
    }

    static String nz(String s) { return s == null ? "" : s; }

    static String stamp() { return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date()); }

    /** Кнопка с выпадающим меню: группирует связанные действия, чтобы не загромождать панель. */
    private javax.swing.JButton menuBtn(String title, String[] labels, Runnable[] actions) {
        javax.swing.JButton btn = Theme.btn(title + " ▾", Theme.GRAY_PANEL);
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        for (int i = 0; i < labels.length; i++) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(labels[i]);
            final Runnable action = actions[i];
            item.addActionListener(e -> action.run());
            menu.add(item);
        }
        btn.addActionListener(e -> menu.show(btn, 0, btn.getHeight()));
        return btn;
    }
}
