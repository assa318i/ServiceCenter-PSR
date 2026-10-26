package psr.sc;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;

import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Карточка заказчика: все данные крупным шрифтом, контакты, доверенности, акты. */
public class CustomerCard extends JDialog {

    private final long id;
    private final CustomersPanel back;
    private final JTextField org = big(), fio = big(), phone = big(), address = big(),
        contactPhone = big(), attorney = big(), driver = big(), finance = big(),
        payMethod = big(), payDate = big(), prepay = big(), prepayDate = big(), notes = big();
    private final AttachmentList att = new AttachmentList();

    public CustomerCard(java.awt.Window owner, long customerId, CustomersPanel back) {
        super(owner, "Карточка заказчика №" + customerId);
        this.id = customerId;
        this.back = back;
        setSize(1000, 780);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModal(false);
        getContentPane().setBackground(Theme.GRAY_BG);
        setLayout(new BorderLayout());
        add(Settings.headerBar(14), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.GRAY_BG);
        form.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        int y = 0;
        y = row(form, g, y, "Название организации:", org);
        y = row(form, g, y, "ФИО клиента (имя, отчество, фамилия):", fio);
        setupPhoneField(phone);
        y = row(form, g, y, "Телефон:", phone);
        y = row(form, g, y, "Адрес:", address);
        y = section(form, g, y, "Контактная информация (дозаполнение):");
        setupPhoneField(contactPhone);
        y = row(form, g, y, "Контактный телефон:", contactPhone);
        y = row(form, g, y, "Лицо с доверенностью:", attorney);
        y = row(form, g, y, "Водитель (при наличии):", driver);
        y = row(form, g, y, "Финансовый менеджер:", finance);
        y = section(form, g, y, "Оплата за услуги и ремонт:");
        y = row(form, g, y, "Способ оплаты:", payMethod);
        y = row(form, g, y, "Дата оплаты (дд.мм.гггг):", payDate);
        y = row(form, g, y, "Предоплата, руб.:", prepay);
        y = row(form, g, y, "Дата получения предоплаты:", prepayDate);
        y = row(form, g, y, "Примечание:", notes);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(Theme.on("Сохранить", Theme.YELLOW, e -> save()));
        tools.add(Theme.on("Приложить PDF/JPG (доверенность)", Theme.BLUE, e -> addAttach()));
        tools.add(Theme.on("Открыть последний файл", Theme.GRAY_PANEL, e -> openLast()));
        tools.add(Theme.on("Печать акта о выполненных работах", Theme.BRIGHT_RED, e -> printAct()));
        tools.add(Theme.on("Закрыть", Theme.GRAY_PANEL, e -> { if (back != null) back.load(); dispose(); }));
        g.gridx = 0; g.gridwidth = 2; g.gridy = y;
        form.add(tools, g);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(att.panel(), BorderLayout.EAST);
        load();
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (org.getText().trim().isEmpty()) {
                    try {
                        Db.exec("DELETE FROM customers WHERE id=" + id);
                    } catch (Exception ignored) { }
                }
            }
        });
    }

    private static JTextField big() {
        JTextField t = new JTextField(26);
        t.setFont(Theme.font(18));
        t.setBorder(BorderFactory.createLineBorder(Theme.BLACK, 1));
        return t;
    }

    private static void setupPhoneField(JTextField field) {
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (field.getText().trim().isEmpty()) {
                    field.setText("+7(");
                    field.setCaretPosition(3);
                }
            }
        });
    }

    private static JLabel lbl(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.fontB(16));
        l.setForeground(Theme.BLACK);
        return l;
    }

    private int row(JPanel p, GridBagConstraints g, int y, String label, JTextField f) {
        g.gridx = 0; g.gridy = y; g.weightx = 0; p.add(lbl(label), g);
        g.gridx = 1; g.weightx = 1; p.add(f, g);
        return y + 1;
    }

    private int section(JPanel p, GridBagConstraints g, int y, String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.fontB(17));
        l.setForeground(Theme.BLUE);
        g.gridx = 0; g.gridwidth = 2; g.gridy = y; p.add(l, g);
        g.gridwidth = 1;
        return y + 1;
    }

    private void load() {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM customers WHERE id=" + id)) {
            if (rs.next()) {
                org.setText(RepairsPanel.nz(rs.getString("org")));
                fio.setText(RepairsPanel.nz(rs.getString("fio")));
                phone.setText(RepairsPanel.nz(rs.getString("phone")));
                address.setText(RepairsPanel.nz(rs.getString("address")));
                contactPhone.setText(RepairsPanel.nz(rs.getString("contact_phone")));
                attorney.setText(RepairsPanel.nz(rs.getString("attorney")));
                driver.setText(RepairsPanel.nz(rs.getString("driver")));
                finance.setText(RepairsPanel.nz(rs.getString("finance_manager")));
                payMethod.setText(RepairsPanel.nz(rs.getString("payment_method")));
                payDate.setText(RepairsPanel.nz(rs.getString("payment_date")));
                prepay.setText(String.valueOf(rs.getDouble("prepay")));
                prepayDate.setText(RepairsPanel.nz(rs.getString("prepay_date")));
                notes.setText(RepairsPanel.nz(rs.getString("notes")));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        att.load();
    }

    private void save() {
        try {
            Db.exec("UPDATE customers SET org='" + Db.esc(org.getText()) + "', fio='"
                + Db.esc(fio.getText()) + "', phone='" + Db.esc(phone.getText()) + "', address='"
                + Db.esc(address.getText()) + "', contact_phone='" + Db.esc(contactPhone.getText())
                + "', attorney='" + Db.esc(attorney.getText()) + "', driver='"
                + Db.esc(driver.getText()) + "', finance_manager='" + Db.esc(finance.getText())
                + "', payment_method='" + Db.esc(payMethod.getText()) + "', payment_date='"
                + Db.esc(payDate.getText()) + "', prepay=" + RepairForm.parse(prepay.getText())
                + ", prepay_date='" + Db.esc(prepayDate.getText()) + "', notes='"
                + Db.esc(notes.getText()) + "' WHERE id=" + id);
            if (back != null) back.load();
            JOptionPane.showMessageDialog(this, "Сохранено", "Карточка заказчика",
                JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void addAttach() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Доверенность на получение / выдачу (PDF или JPG)");
        fc.setFileFilter(new FileNameExtensionFilter("PDF, JPG", "pdf", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String kind = (String) JOptionPane.showInputDialog(this, "Тип документа:",
            "Доверенность", JOptionPane.QUESTION_MESSAGE, null,
            new String[] { "Доверенность на получение", "Доверенность на выдачу", "Иной документ" },
            "Доверенность на получение");
        if (kind == null) return;
        try {
            File src = fc.getSelectedFile();
            File dir = new File(Db.DATA_DIR, "attaches");
            dir.mkdirs();
            String name = System.currentTimeMillis() + "_" + src.getName();
            File dst = new File(dir, name);
            Files.copy(src.toPath(), dst.toPath());
            Db.exec("INSERT INTO attachments(customer_id,kind,file,added) VALUES(" + id + ",'"
                + Db.esc(kind) + "','" + Db.esc(dst.getPath()) + "','" + RepairsPanel.stamp() + "')");
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        att.load();
    }

    private void openLast() {
        File f = att.lastFile();
        if (f == null) { Warn.show("Нет приложенных файлов"); return; }
        try { Desktop.getDesktop().open(f); } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    private void printAct() {
        Vector<long[]> ids = new Vector<>();
        Vector<String> labels = new Vector<>();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, equipment, problem, price FROM repairs"
                 + " WHERE customer_id=" + id + " ORDER BY id DESC")) {
            while (rs.next()) {
                ids.add(new long[] { rs.getLong(1) });
                labels.add("№" + rs.getLong(1) + " — " + RepairsPanel.nz(rs.getString(2))
                    + " — " + RepairsPanel.nz(rs.getString(3)));
            }
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        if (labels.isEmpty()) { Warn.show("У этого заказчика нет заявок на ремонт"); return; }
        String pick = (String) JOptionPane.showInputDialog(this, "Акт по какому ремонту печатать:",
            "Акт о выполненных работах", JOptionPane.QUESTION_MESSAGE, null,
            labels.toArray(), labels.get(0));
        if (pick == null) return;
        long repairId = ids.get(labels.indexOf(pick))[0];
        Documents.act(this, id, repairId);
    }

    /** Список вложений заказчика справа в карточке. */
    class AttachmentList {
        private final javax.swing.JList<String> list = new javax.swing.JList<>();
        private final Vector<String> files = new Vector<>();

        javax.swing.JComponent panel() {
            list.setFont(Theme.font(13));
            JPanel p = new JPanel(new BorderLayout(4, 4));
            p.setBackground(Theme.GRAY_PANEL);
            p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JLabel t = new JLabel("Приложенные файлы");
            t.setFont(Theme.fontB(15));
            JButton open = Theme.on("Открыть выбранный", Theme.BLUE, e -> openSelected());
            JButton del = Theme.on("Удалить запись", Theme.BRIGHT_RED, e -> delete());
            p.add(t, BorderLayout.NORTH);
            p.add(new JScrollPane(list), BorderLayout.CENTER);
            JPanel b = new JPanel();
            b.setOpaque(false);
            b.add(open); b.add(del);
            p.add(b, BorderLayout.SOUTH);
            return p;
        }

        void load() {
            Vector<String> v = new Vector<>();
            files.clear();
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT kind, file FROM attachments"
                     + " WHERE customer_id=" + id + " ORDER BY id")) {
                while (rs.next()) {
                    v.add(rs.getString(1) + " — " + new File(rs.getString(2)).getName());
                    files.add(rs.getString(2));
                }
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
            list.setListData(v);
        }

        File lastFile() { return files.isEmpty() ? null : new File(files.get(files.size() - 1)); }

        private void openSelected() {
            int i = list.getSelectedIndex();
            if (i < 0) { Warn.show("Выберите файл"); return; }
            try { Desktop.getDesktop().open(new File(files.get(i))); }
            catch (Exception ex) { Warn.show(ex.getMessage()); }
        }

        private void delete() {
            int i = list.getSelectedIndex();
            if (i < 0) { Warn.show("Выберите файл"); return; }
            try {
                Db.exec("DELETE FROM attachments WHERE customer_id=" + id + " AND file='"
                    + Db.esc(files.get(i)) + "'");
            } catch (Exception ex) { Warn.show(ex.getMessage()); }
            load();
        }
    }
}
