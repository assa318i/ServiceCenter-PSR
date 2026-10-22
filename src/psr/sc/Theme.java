package psr.sc;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Тема программы: Montserrat, серый фон; кнопки — скруглённые, мятный градиент в стиле Windows 7 Aero. */
public final class Theme {
    public static final Color GRAY_BG = new Color(0xE6E6E6);
    public static final Color GRAY_PANEL = new Color(0xD4D4D4);
    public static final Color YELLOW = new Color(0xF2C200);
    public static final Color BLUE = new Color(0x1B6FD1);
    public static final Color BRIGHT_RED = new Color(0xE60000);
    public static final Color RED = new Color(0xCC0000);
    public static final Color BLACK = Color.BLACK;
    /** Мята (Aero): верх/низ градиента, рамка, варианты при наведении и нажатии. */
    public static final Color MINT_TOP = new Color(0xC2F2E3);
    public static final Color MINT_BOTTOM = new Color(0x4FC3A1);
    private static final Color MINT_BORDER = new Color(0x2E9979);
    private static final Color MINT_HOVER = new Color(0x7FD9BE);
    private static final Color MINT_PRESSED = new Color(0x35A583);

    public static Font plain;
    public static Font medium;
    public static Font bold;

    static {
        plain = load("Montserrat-Regular.ttf", Font.PLAIN, 14);
        medium = load("Montserrat-Medium.ttf", Font.PLAIN, 14);
        bold = load("Montserrat-Bold.ttf", Font.BOLD, 14);
        load("Montserrat-SemiBold.ttf", Font.PLAIN, 14);
        load("Montserrat-Black.ttf", Font.PLAIN, 14);
    }

    private static Font load(String res, int style, int size) {
        try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + res)) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f.deriveFont(style, (float) size);
            }
        } catch (Exception ignored) { }
        return new Font("SansSerif", style, size);
    }

    public static Font font(int size) { return plain.deriveFont((float) size); }
    public static Font fontB(int size) { return bold.deriveFont((float) size); }

    /** Кнопка с округлыми углами и мятным Aero-градиентом; цвет из аргумента не используется. */
    public static JButton btn(String text, Color bg) {
        JButton b = new JButton(text) {
            private boolean hover, pressed;
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorder(new EmptyBorder(6, 14, 6, 14));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = pressed = false; repaint(); }
                    @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                    @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1, h = getHeight() - 1;
                if (!isEnabled()) {
                    g2.setColor(new Color(0xD9D9D9));
                    g2.fillRoundRect(0, 0, w, h, 18, 18);
                    g2.setColor(new Color(0xAFAFAF));
                    g2.drawRoundRect(0, 0, w, h, 18, 18);
                } else {
                    Color top = pressed ? MINT_HOVER : MINT_TOP;
                    Color bottom = pressed ? MINT_PRESSED : (hover ? MINT_HOVER : MINT_BOTTOM);
                    g2.setPaint(new java.awt.GradientPaint(0, 0, top, 0, h, bottom));
                    g2.fillRoundRect(0, 0, w, h, 18, 18);
                    g2.setColor(MINT_BORDER);
                    g2.drawRoundRect(0, 0, w, h, 18, 18);
                    // блик в стиле Aero
                    g2.setColor(new Color(255, 255, 255, 90));
                    g2.fillRoundRect(2, 2, w - 4, h / 2, 14, 14);
                }
                g2.dispose();
                super.paintComponent(g);
            }
            @Override protected void paintBorder(Graphics g) { }
        };
        b.setFont(fontB(14));
        b.setForeground(BLACK);
        return b;
    }

    public static JButton on(String text, Color bg, java.awt.event.ActionListener l) {
        JButton b = btn(text, bg);
        b.addActionListener(l);
        return b;
    }

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(fontB(20));
        l.setForeground(BLACK);
        l.setBorder(new EmptyBorder(8, 12, 8, 12));
        return l;
    }

    /** Метка красным — для важных/опасных сообщений. */
    public static JLabel danger(String text) {
        JLabel l = new JLabel(text);
        l.setFont(fontB(14));
        l.setForeground(BRIGHT_RED);
        return l;
    }

    public static void styleTable(JTable t) {
        t.setFont(medium);
        t.setRowHeight(30);
        t.setGridColor(new Color(0xA9A9A9));
        t.setBackground(Color.WHITE);
        t.setSelectionBackground(BLUE);
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        JTableHeader h = t.getTableHeader();
        h.setFont(fontB(14));
        h.setBackground(GRAY_PANEL);
        h.setForeground(BLACK);
    }

    /** Рендерер: красный текст для строк с критическим/опасным статусом (значение в колонке tagColumn). */
    public static DefaultTableCellRenderer slaRenderer(final JTable table, final int tagColumn) {
        return new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                setHorizontalAlignment(SwingConstants.LEFT);
                String tag = String.valueOf(t.getValueAt(r, tagColumn));
                if (!sel) {
                    if ("CRITICAL".equals(tag) || "DANGER".equals(tag)) setForeground(BRIGHT_RED);
                    else if ("WARNING".equals(tag)) setForeground(RED);
                    else setForeground(BLACK);
                }
                setFont(tag.startsWith("WARN") || tag.startsWith("CRIT") ? fontB(14) : font(14));
                return this;
            }
        };
    }

    private Theme() { }
}
