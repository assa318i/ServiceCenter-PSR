package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** База заказчиков с сортировкой по организации, ФИО, телефону и адресу. */
public class CustomersPanel extends JPanel {

    static final String[] SORTS = { "По организации", "По ФИО", "По телефону", "По адресу" };
    static final String[] SORT_SQL = { "org COLLATE NOCASE", "fio COLLATE NOCASE",
        "phone", "address COLLATE NOCASE" };

    private final App app;
    private final JTable table = new JTable();
    private final FilterBar bar = new FilterBar("Заказчики", () -> load());
    private final JComboBox<String> sort = new JComboBox<>(SORTS);
    private final JLabel info = new JLabel(" ");

    public CustomersPanel(App app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Theme.GRAY_BG);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Сортировка: "));
        sort.setFont(Theme.font(14));
        sort.addActionListener(e -> load());
        tools.add(sort);
        tools.add(Theme.on("Новый заказчик", Theme.YELLOW, e -> open(null)));
        tools.add(Theme.on("Открыть карточку", Theme.BLUE, e -> openSelected()));
        tools.add(Theme.on("Импорт из файла", Theme.YELLOW, e -> CustomerImport.dialog(this)));
        tools.add(Theme.on("Обновить", Theme.GRAY_PANEL, e -> load()));
        if (Session.isAdmin())
            tools.add(Theme.on("Очистка ПДн", Theme.BRIGHT_RED, e -> Privacy.purge(this, selectedId())));
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(tools, BorderLayout.CENTER);
        north.add(bar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        add(new JScrollPane(table), BorderLayout.CENTER);
        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });

        info.setFont(Theme.font(13));
        add(info, BorderLayout.SOUTH);
        load();
    }

    private Long selectedId() {
        int r = table.getSelectedRow();
        return r < 0 ? null : (Long) table.getValueAt(r, 0);
    }

    private void openSelected() {
        Long id = selectedId();
        if (id == null) { Warn.show("Выберите заказчика"); return; }
        if (Privacy.hidden()) {
            Warn.show("Полная карточка заказчика — только у администратора."
                + " Организация и ФИО заказчика по вашей заявке видны в окне «Заявки».");
            return;
        }
        open(id);
    }

    void open(Long id) {
        if (id == null) {
            try {
                Db.exec("INSERT INTO customers(org) VALUES('Новый заказчик')");
                try (Statement st = Db.get().createStatement();
                     ResultSet rs = st.executeQuery("SELECT MAX(id) FROM customers")) {
                    if (rs.next()) id = rs.getLong(1);
                }
            } catch (Exception ex) { Warn.show(ex.getMessage()); return; }
        }
        new CustomerCard(app, id, this).setVisible(true);
    }

    void load() {
        Vector<Vector<Object>> rows = new Vector<>();
        String sql = "SELECT id, org, fio, phone, address, attorney, payment_method FROM customers"
            + " ORDER BY " + SORT_SQL[sort.getSelectedIndex()];
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] v = { nz(rs.getString(2)), nz(rs.getString(3)), nz(rs.getString(4)),
                    nz(rs.getString(5)), nz(rs.getString(6)), nz(rs.getString(7)) };
                if (!bar.match(v)) continue;
                rows.add(new Vector<>(java.util.Arrays.asList(rs.getLong(1), v[0],
                    Privacy.fio(v[1]), Privacy.phone(v[2]), Privacy.address(v[3]),
                    Privacy.fio(v[4]), v[5])));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        table.setModel(new DefaultTableModel(rows, new Vector<>(java.util.Arrays.asList(
            "№", "Организация", "ФИО клиента", "Телефон", "Адрес",
            "Доверенное лицо", "Способ оплаты"))) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        });
        info.setText("Всего заказчиков: " + rows.size() + "   " + SORTS[sort.getSelectedIndex()]
            + (Privacy.hidden() ? "   · ФИО, телефоны и адреса скрыты (роль «инженер»)" : ""));
        info.setForeground(Theme.BLACK);
    }

    static String nz(String s) { return s == null ? "" : s; }
}
