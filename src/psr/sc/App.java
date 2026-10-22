package psr.sc;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Главное окно: шапка с логотипом, панель просрочек, навигация, панели модулей. */
public class App extends JFrame {
    public static final String REPAIRS = "repairs", CUSTOMERS = "customers",
        WAREHOUSE = "warehouse", REPORTS = "reports", FINANCE = "finance";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private JPanel header;
    private final Alerts alerts = new Alerts();

    public App() {
        super("Сервис центр от PSR");
        java.io.File winIcon = new java.io.File("icon.png");
        if (winIcon.isFile()) setIconImages(java.util.Arrays.asList(
            new javax.swing.ImageIcon(winIcon.getPath()).getImage()));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                try { Export.backup(); } catch (Exception ignored) { }
            }
        });
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.GRAY_BG);

        header = Settings.headerBar(16);
        add(header, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setBackground(Theme.GRAY_BG);
        nav.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        nav.add(navBtn("Заявки на ремонт", REPAIRS, Theme.YELLOW));
        nav.add(navBtn("Заказчики", CUSTOMERS, Theme.BLUE));
        nav.add(navBtn("Склад", WAREHOUSE, Theme.BRIGHT_RED));
        nav.add(navBtn("Отчётность", REPORTS, Color.WHITE));
        JButton financeBtn = navBtn("Бухгалтерия", FINANCE, Theme.RED);
        financeBtn.setEnabled(Session.isAdmin());
        nav.add(financeBtn);
        JButton settingsBtn = navBtn("Настройки", "settings", Theme.GRAY_PANEL);
        settingsBtn.setEnabled(Session.isAdmin());
        nav.add(settingsBtn);
        JButton tasksBtn = Theme.btn("Дела", null);
        tasksBtn.addActionListener(e -> Tasks.show(content, null));
        nav.add(tasksBtn);
        JButton maxBtn = Theme.btn("Во весь экран", null);
        maxBtn.addActionListener(e -> setExtendedState((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0
            ? Frame.MAXIMIZED_BOTH : Frame.NORMAL));
        nav.add(maxBtn);
        JButton trayBtn = Theme.btn("В трей", null);
        trayBtn.addActionListener(e -> minimizeToTray());
        nav.add(trayBtn);
        JLabel who = new JLabel(Session.name + " · " + (Session.isAdmin() ? "администратор" : "инженер"));
        who.setFont(Theme.font(13));
        who.setForeground(Session.isAdmin() ? Theme.BLACK : Theme.BLUE);
        nav.add(who);
        add(nav, BorderLayout.SOUTH);

        content.setBackground(Theme.GRAY_BG);
        content.add(new RepairsPanel(this), REPAIRS);
        content.add(new CustomersPanel(this), CUSTOMERS);
        content.add(new WarehousePanel(), WAREHOUSE);
        content.add(new ReportsPanel(), REPORTS);
        content.add(new FinancePanel(), FINANCE);
        content.add(new SettingsPanel(this), "settings");

        java.util.List<String> pluginErrors = new java.util.ArrayList<>();
        int n = 0;
        for (PsrPlugin pl : Plugins.load(pluginErrors)) {
            try {
                String card = "plugin_" + n++;
                nav.add(navBtn(pl.title(), card, Theme.GRAY_PANEL));
                content.add(pl.createPanel(), card);
            } catch (Throwable t) { pluginErrors.add(pl.title() + ": " + t); }
        }
        if (!pluginErrors.isEmpty()) {
            final String msg = String.join("\n", pluginErrors);
            javax.swing.Timer t = new javax.swing.Timer(600,
                e -> Warn.show("Часть модулей из modules/ не загружена:\n" + msg));
            t.setRepeats(false);
            t.start();
        }
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(alerts, BorderLayout.NORTH);
        center.add(content, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        alerts.scan();
        javax.swing.Timer remind = new javax.swing.Timer(900,
            e -> { Tasks.remind(); Stream.notifyNew(); });
        remind.setRepeats(false);
        remind.start();
        initTray();
        cards.show(content, REPAIRS);
    }

    private TrayIcon tray;

    private void initTray() {
        if (!SystemTray.isSupported()) return;
        try {
            TrayIcon ti = new TrayIcon(trayImage(), "Сервис центр от PSR");
            ti.setImageAutoSize(true);
            PopupMenu m = new PopupMenu();
            MenuItem open = new MenuItem("Открыть окно");
            open.addActionListener(e -> showFromTray());
            MenuItem exit = new MenuItem("Выход");
            exit.addActionListener(e -> exitApp());
            m.add(open);
            m.addSeparator();
            m.add(exit);
            ti.setPopupMenu(m);
            ti.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { showFromTray(); }
            });
            SystemTray.getSystemTray().add(ti);
            tray = ti;
        } catch (Exception ignored) { }
    }

    private void minimizeToTray() {
        if (tray == null) { Warn.show("Системный трей недоступен в этой системе"); return; }
        setVisible(false);
    }

    private void showFromTray() {
        setVisible(true);
        setExtendedState(Frame.NORMAL);
        toFront();
        requestFocus();
    }

    private void exitApp() {
        try { Export.backup(); } catch (Exception ignored) { }
        if (tray != null) {
            try { SystemTray.getSystemTray().remove(tray); } catch (Exception ignored) { }
        }
        System.exit(0);
    }

    /** Значок трея: логотип из настроек, иначе мятный квадратик. */
    private java.awt.Image trayImage() {
        java.io.File logo = new java.io.File(Settings.get("logo"));
        if (logo.isFile()) return new javax.swing.ImageIcon(logo.getPath()).getImage();
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.MINT_BOTTOM);
        g.fillRoundRect(0, 0, 15, 15, 7, 7);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 8));
        g.drawString("ПС", 2, 11);
        g.dispose();
        return img;
    }

    public void rebuildHeader() {
        remove(header);
        header = Settings.headerBar(16);
        add(header, BorderLayout.NORTH);
        validate();
        repaint();
    }

    public void show(String card) {
        cards.show(content, card);
        alerts.scan();
    }

    private JButton navBtn(String text, String card, Color bg) {
        JButton b = Theme.btn(text, bg);
        b.addActionListener(e -> show(card));
        return b;
    }

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Db.get();
        Export.seedCopies();
        SwingUtilities.invokeLater(() -> {
            try {
                Session.login(null);
            } catch (Exception e) { System.exit(0); }
            new App().setVisible(true);
        });
    }
}
