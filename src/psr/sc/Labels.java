package psr.sc;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Печать наклеек со штрихкодом на ремонтируемое оборудование: выбор заявок, размеры, листы А4. */
public final class Labels {

    private static final int PPM = 8; // пикселей на миллиметр этикетки
    private static final String[] SIZES = { "48×30 мм (малая)", "58×40 мм (типовая)", "90×60 мм (крупная)" };
    static final String[] THICK = { "Тонкая (600 dpi)", "Обычная", "Жирная (300 dpi)", "Очень жирная (203 dpi)" };
    static final int[] THICK_PCT = { 75, 100, 150, 200 };
    private static final String[] MODES = { "Листом А4 (обычный принтер)",
        "По одной на страницу (принтер этикеток)" };

    static void batchDialog(java.awt.Frame owner) {
        final JDialog d = new JDialog(owner, "Наклейки со штрихкодом на оборудование", false);
        JTable table = new JTable();
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT r.id, r.equipment, r.serial,"
                 + " TRIM(COALESCE(c.org,'') || ' ' || COALESCE(c.fio,'')) AS cust, r.opened"
                 + " FROM repairs r LEFT JOIN customers c ON c.id=r.customer_id"
                 + " WHERE r.status <> 'DONE_ISSUED' ORDER BY r.id")) {
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) rows.add(new Vector<Object>(java.util.Arrays.asList(
                Boolean.TRUE, rs.getLong(1), RepairsPanel.nz(rs.getString(2)),
                RepairsPanel.nz(rs.getString(3)), RepairsPanel.nz(rs.getString(4)),
                RepairsPanel.nz(rs.getString(5)))));
            table.setModel(new DefaultTableModel(rows, new Vector<String>(java.util.Arrays.asList(
                "Печатать", "№", "Оборудование", "Серийный №", "Заказчик", "Принят"))) {
                @Override public Class<?> getColumnClass(int c) {
                    return c == 0 ? Boolean.class : Object.class;
                }
                @Override public boolean isCellEditable(int r, int c) { return c == 0; }
            });
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
        Theme.styleTable(table);
        table.setColumnSelectionAllowed(false);

        final JComboBox<String> size = new JComboBox<>(SIZES);
        final JComboBox<String> thick = new JComboBox<>(THICK);
        thick.setSelectedIndex(1);
        final JComboBox<String> copies = new JComboBox<>(new String[] { "1", "2", "3", "4" });
        final JComboBox<String> mode = new JComboBox<>(MODES);
        copies.setSelectedItem("2");

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.add(new JLabel("Размер наклейки: "));
        tools.add(size);
        tools.add(Box.createHorizontalStrut(10));
        tools.add(new JLabel("Толщина штрихкода: "));
        tools.add(thick);
        tools.add(Box.createHorizontalStrut(10));
        tools.add(new JLabel("Штук на единицу: "));
        tools.add(copies);
        tools.add(Box.createHorizontalStrut(10));
        tools.add(new JLabel("Печать: "));
        tools.add(mode);
        tools.add(Box.createHorizontalStrut(10));
        tools.add(Theme.on("Все / ничего", Theme.GRAY_PANEL, e -> toggleAll(table)));
        JButton printBtn = Theme.on("Печать наклеек", Theme.YELLOW,
            e -> print(d, table, size, thick, copies, mode));
        tools.add(printBtn);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(Theme.GRAY_BG);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(Settings.headerBar(12), BorderLayout.NORTH);
        root.add(tools, BorderLayout.CENTER);
        root.add(new JScrollPane(table), BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(860, 560);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static void toggleAll(JTable table) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        boolean check = false;
        for (int i = 0; i < m.getRowCount(); i++)
            if (!Boolean.TRUE.equals(m.getValueAt(i, 0))) { check = true; break; }
        for (int i = 0; i < m.getRowCount(); i++) m.setValueAt(check, i, 0);
    }

    private static void print(java.awt.Component owner, JTable table, JComboBox<String> size,
            JComboBox<String> thick, JComboBox<String> copies, JComboBox<String> mode) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++)
            if (Boolean.TRUE.equals(m.getValueAt(i, 0))) ids.add((Long) m.getValueAt(i, 1));
        if (ids.isEmpty()) { Warn.show("Отметьте галочками заявки для наклеек"); return; }
        int nCopies = Integer.parseInt(String.valueOf(copies.getSelectedItem()));
        int[] mm = mm((String) size.getSelectedItem());
        int wmm = mm[0], hmm = mm[1];
        int pct = THICK_PCT[Math.max(0, thick.getSelectedIndex())];

        List<BufferedImage> labels = new ArrayList<>();
        for (Long id : ids)
            for (int c = 0; c < nCopies; c++) {
                BufferedImage img = one(id, wmm, hmm, pct);
                if (img != null) labels.add(img);
            }
        if (labels.isEmpty()) { Warn.show("Не удалось собрать наклейки"); return; }

        if (mode.getSelectedIndex() == 0) {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            java.awt.print.PageFormat pf = job.defaultPage();
            int w = Math.max(1, (int) pf.getImageableWidth()), h = Math.max(1, (int) pf.getImageableHeight());
            Print.pages(sheet(labels, w, h), "Наклейки " + ids.size() + " заявок");
        } else {
            Print.pages(labels, "Наклейки " + ids.size() + " заявок");
        }
    }

    /** "48×30 мм (малая)" → [48, 30]. */
    private static int[] mm(String s) {
        String[] p = s.split("[^0-9]+");
        return new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
    }

    /** Одна наклейка на ремонт: штрихкод сверху, код текстом, данные заявки. */
    static BufferedImage one(long id, int wmm, int hmm, int pct) {
        String equip = "", serial = "", cust = "", opened = "";
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT r.equipment, r.serial, r.opened,"
                 + " TRIM(COALESCE(c.org,'') || ' ' || COALESCE(c.fio,'')) AS cust"
                 + " FROM repairs r LEFT JOIN customers c ON c.id=r.customer_id WHERE r.id=" + id)) {
            if (!rs.next()) return null;
            equip = RepairsPanel.nz(rs.getString(1));
            serial = RepairsPanel.nz(rs.getString(2));
            opened = RepairsPanel.nz(rs.getString(3));
            cust = RepairsPanel.nz(rs.getString(4));
        } catch (Exception ex) { Warn.show(ex.getMessage()); return null; }

        List<String> text = new ArrayList<>();
        text.add("№" + id + "  " + equip);
        if (!serial.isEmpty()) text.add("S/N: " + serial);
        if (!cust.isEmpty()) text.add(cust);
        text.add("Принят " + opened);
        return render("PSR-R" + id, text, wmm, hmm, pct);
    }

    /**
     * Наклейка складской позиции. Код — штрихкод поставщика, если он вбит, иначе наш PSR-P<№>;
     * сканер на кассе склада читает и то, и другое (см. Stock.find).
     */
    static BufferedImage item(long id, int wmm, int hmm, int pct) {
        String name = "", article = "", barcode = "", unit = "", grp = "";
        double qty = 0;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT name, article, barcode, unit, grp, qty"
                 + " FROM items WHERE id=" + id)) {
            if (!rs.next()) return null;
            name = RepairsPanel.nz(rs.getString(1));
            article = RepairsPanel.nz(rs.getString(2));
            barcode = RepairsPanel.nz(rs.getString(3));
            unit = RepairsPanel.nz(rs.getString(4));
            grp = RepairsPanel.nz(rs.getString(5));
            qty = rs.getDouble(6);
        } catch (Exception ex) { Warn.show(ex.getMessage()); return null; }

        List<String> text = new ArrayList<>();
        text.add(name);
        if (!article.isEmpty()) text.add("Артикул: " + article);
        if (!grp.isEmpty()) text.add("Группа: " + grp);
        text.add("Остаток: " + WarehousePanel.fmt(qty) + " " + unit);
        return render(barcode.isEmpty() ? "PSR-P" + id : barcode, text, wmm, hmm, pct);
    }

    /** Одна наклейка позиции с выбором размера и толщины штрихкода. */
    static void itemLabel(java.awt.Component parent, long itemId) {
        JComboBox<String> size = new JComboBox<>(SIZES);
        JComboBox<String> thick = new JComboBox<>(THICK);
        thick.setSelectedIndex(1);
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {
            "Размер наклейки:", size, "Толщина штрихкода:", thick },
            "Этикетка позиции склада №" + itemId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        int[] mm = mm((String) size.getSelectedItem());
        BufferedImage img = item(itemId, mm[0], mm[1], THICK_PCT[Math.max(0, thick.getSelectedIndex())]);
        if (img != null) Print.image(img, "Наклейка позиции №" + itemId);
    }

    /** Общая раскладка: штрихкод в верхней части, под ним код и строки текста по убывающему кеглю. */
    private static BufferedImage render(String code, List<String> text, int wmm, int hmm, int pct) {
        int w = wmm * PPM, h = hmm * PPM, pad = 2 * PPM;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(java.awt.Color.BLACK);
        g.drawRect(0, 0, w - 1, h - 1);

        int barH = (int) (h * 0.38);
        Barcode.draw(g, code, pad, pad, w - pad * 2, barH, pct);

        List<String> lines = new ArrayList<>();
        lines.add(code);
        lines.addAll(text);
        int maxW = w - pad * 2;
        int fs = Math.max(11, Math.min(w / 26, h / 10));
        g.setFont(new Font(Theme.plain.getFamily(), Font.PLAIN, fs));
        FontMetrics fm = g.getFontMetrics();
        int line = fm.getHeight();
        int y = pad + barH + fs + 2;
        if (y + lines.size() * line > h - 2) {
            g.setFont(new Font(Theme.plain.getFamily(), Font.PLAIN, fs = Math.max(9, fs - 3)));
            fm = g.getFontMetrics();
            line = fm.getHeight();
        }
        for (String s : lines) {
            y += line;
            g.drawString(clip(s, fm, maxW), pad, y);
        }
        g.dispose();
        return img;
    }

    /** Раскладка наклеек по страницам А4 (px — размер области печати в точках). */
    private static List<BufferedImage> sheet(List<BufferedImage> labels, int pageWpx, int pageHpx) {
        int lw = labels.get(0).getWidth(), lh = labels.get(0).getHeight(), gap = 2 * PPM, marg = 4 * PPM;
        int cols = Math.max(1, (pageWpx - 2 * marg + gap) / (lw + gap));
        int rows = Math.max(1, (pageHpx - 2 * marg + gap) / (lh + gap));
        int ppm = cols * rows;
        List<BufferedImage> pages = new ArrayList<>();
        double fit = Math.min(1.0, Math.min((double) pageWpx / (cols * lw + (cols - 1) * gap + 2 * marg),
            (double) pageHpx / (rows * lh + (rows - 1) * gap + 2 * marg)));
        BufferedImage page = null;
        Graphics2D pg = null;
        for (int i = 0; i < labels.size(); i++) {
            if (i % ppm == 0) {
                if (pg != null) { pg.dispose(); pages.add(page); }
                page = new BufferedImage((int) ((cols * lw + (cols - 1) * gap + 2 * marg) * fit),
                    (int) ((rows * lh + (rows - 1) * gap + 2 * marg) * fit), BufferedImage.TYPE_INT_RGB);
                pg = page.createGraphics();
                pg.setColor(java.awt.Color.WHITE);
                pg.fillRect(0, 0, page.getWidth(), page.getHeight());
                if (fit < 1.0) pg.scale(fit, fit);
            }
            int k = i % ppm;
            pg.drawImage(labels.get(i), marg + (k % cols) * (lw + gap),
                marg + (k / cols) * (lh + gap), null);
            if (i == labels.size() - 1) { pg.dispose(); pages.add(page); }
        }
        return pages;
    }

    private static String clip(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid) + "...") <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "...";
    }

    private Labels() { }
}
