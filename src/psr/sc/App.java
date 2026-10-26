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
    public static final String VERSION = "1.0.7";
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

        header = makeHeader();
        add(header, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setBackground(Theme.GRAY_BG);
        nav.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        navBtn(nav, "Заявки на ремонт", REPAIRS, Theme.YELLOW);
        navBtn(nav, "Заказчики", CUSTOMERS, Theme.BLUE);
        navBtn(nav, "Склад", WAREHOUSE, Theme.BRIGHT_RED);
        navBtn(nav, "Отчётность", REPORTS, Color.WHITE);
        navBtn(nav, "Бухгалтерия", FINANCE, Theme.RED);
        navBtn(nav, "Учёт времени", Roles.TIMES, Theme.MINT_TOP);
        navBtn(nav, "Настройки", "settings", Theme.GRAY_PANEL);
        JButton tasksBtn = Theme.btn("Дела", null);
        tasksBtn.addActionListener(e -> Tasks.show(content, null));
        if (Session.may(Roles.TASKS)) nav.add(tasksBtn);
        JButton calBtn = Theme.btn("Календарь", null);
        calBtn.addActionListener(e -> ProdCalendar.open(this));
        if (Session.may(Roles.CALENDAR)) nav.add(calBtn);
        add(nav, BorderLayout.SOUTH);

        content.setBackground(Theme.GRAY_BG);
        content.add(new RepairsPanel(this), REPAIRS);
        content.add(new CustomersPanel(this), CUSTOMERS);
        content.add(new WarehousePanel(), WAREHOUSE);
        content.add(new ReportsPanel(), REPORTS);
        content.add(new FinancePanel(), FINANCE);
        content.add(new TimesheetPanel(), Roles.TIMES);
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
        cards.show(content, firstCard());
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

    /**
     * Шапка: название организации слева, справа — кто вошёл и служебные кнопки. Ряд разделов
     * внизу окна забит кнопками модулей, поэтому их держим здесь: иначе в 1280 пикселей
     * последние кнопки просто не видны.
     */
    private JPanel makeHeader() {
        JPanel h = Settings.headerBar(14);
        JButton maxBtn = Theme.btn("Во весь экран", null);
        maxBtn.addActionListener(e -> setExtendedState((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0
            ? Frame.MAXIMIZED_BOTH : Frame.NORMAL));
        h.add(maxBtn);
        JButton trayBtn = Theme.btn("В трей", null);
        trayBtn.addActionListener(e -> minimizeToTray());
        h.add(trayBtn);
        JLabel who = new JLabel(Session.name + " · " + Session.roleTitle());
        who.setFont(Theme.font(13));
        who.setForeground(Session.isAdmin() ? Theme.BLACK : Theme.BLUE);
        h.add(who);
        return h;
    }

    public void rebuildHeader() {
        remove(header);
        header = makeHeader();
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

    /** Кнопка раздела — только если раздел открыт роли; коды карточек совпадают с кодами разделов. */
    private void navBtn(JPanel nav, String text, String card, Color bg) {
        if (Session.may(card)) nav.add(navBtn(text, card, bg));
    }

    /** Первый раздел, доступный роли: у инженера это заявки, у бухгалтера — заказчики. */
    private String firstCard() {
        String[] order = { REPAIRS, CUSTOMERS, WAREHOUSE, REPORTS, FINANCE };
        for (String c : order) if (Session.may(c)) return c;
        return REPAIRS;
    }

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Db.get();
        Export.seedCopies();
        SwingUtilities.invokeLater(() -> {
            try {
                Session.login(null);
            } catch (Exception e) { System.exit(0); }
            App app = new App();
            app.setVisible(true);
            AutoUpdater.checkAndPrompt();
        });
    }
}
