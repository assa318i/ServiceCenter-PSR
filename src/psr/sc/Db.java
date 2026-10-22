package psr.sc;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** База SQLite приложения. Файл data/psr.db — его же можно перенести на Android. */
public final class Db {
    public static final File DATA_DIR = new File("data");
    private static Connection conn;

    public static synchronized Connection get() throws Exception {
        if (conn == null || conn.isClosed()) {
            DATA_DIR.mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + new File(DATA_DIR, "psr.db").getPath());
            init();
        }
        return conn;
    }

    private static void init() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS customers(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "org TEXT DEFAULT '', fio TEXT DEFAULT '', phone TEXT DEFAULT '', address TEXT DEFAULT '',"
                + "contact_phone TEXT DEFAULT '', attorney TEXT DEFAULT '', driver TEXT DEFAULT '',"
                + "finance_manager TEXT DEFAULT '', payment_method TEXT DEFAULT '', payment_date TEXT DEFAULT '',"
                + "prepay REAL DEFAULT 0, prepay_date TEXT DEFAULT '', notes TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS repairs(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER, equipment TEXT DEFAULT '', problem TEXT DEFAULT '',"
                + "status TEXT DEFAULT 'OPEN', sla TEXT DEFAULT 'NORMAL', engineer TEXT DEFAULT '',"
                + "wait_until TEXT DEFAULT '', opened TEXT DEFAULT '', issued TEXT DEFAULT '',"
                + "price REAL DEFAULT 0, notes TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS repair_history(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "repair_id INTEGER, ts TEXT, info TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS attachments(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER, repair_id INTEGER, kind TEXT DEFAULT '', file TEXT DEFAULT '', added TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT, unit TEXT DEFAULT 'шт', qty REAL DEFAULT 0, min_qty REAL DEFAULT 0, price REAL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stock_orders(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "item_id INTEGER, qty REAL, status TEXT DEFAULT 'NEW', created TEXT, note TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "pin TEXT UNIQUE, name TEXT DEFAULT '', role TEXT DEFAULT 'ENGINEER',"
                + "created TEXT DEFAULT '')");
            addColumn(st, "repairs", "serial TEXT DEFAULT ''");
            addColumn(st, "repairs", "completeness TEXT DEFAULT ''");
            addColumn(st, "repairs", "warranty_days INTEGER DEFAULT 0");
            addColumn(st, "repairs", "manager TEXT DEFAULT ''");
            addColumn(st, "repairs", "agreed TEXT DEFAULT ''");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS cart_parts(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "article TEXT DEFAULT '', name TEXT DEFAULT '', grams_std REAL DEFAULT 0,"
                + "toner_g REAL DEFAULT 0, qty_refill REAL DEFAULT 0, qty_store REAL DEFAULT 0,"
                + "min_qty REAL DEFAULT 0, price REAL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS cart_ops(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts TEXT, part_id INTEGER, op TEXT, amount REAL DEFAULT 0,"
                + "repair_id INTEGER DEFAULT 0, who TEXT DEFAULT '', note TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kits(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT, equipment_type TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kit_parts(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kit_id INTEGER, article TEXT DEFAULT '', name TEXT, qty REAL DEFAULT 1)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS chat(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts TEXT, author TEXT, role TEXT, text TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts TEXT, customer_id INTEGER, dir TEXT DEFAULT 'IN', amount REAL, method TEXT,"
                + "note TEXT DEFAULT '', repair_id INTEGER DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS presets(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "module TEXT, name TEXT, spec TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "repair_id INTEGER DEFAULT 0, kind TEXT DEFAULT 'Задача', title TEXT DEFAULT '',"
                + "due TEXT DEFAULT '', done INTEGER DEFAULT 0, who TEXT DEFAULT '',"
                + "created TEXT DEFAULT '', repeat_days INTEGER DEFAULT 0)");
            addColumn(st, "repairs", "rev INTEGER DEFAULT 0");
            addColumn(st, "repair_history", "who TEXT DEFAULT ''");
            addColumn(st, "repair_history", "kind TEXT DEFAULT 'UPDATE'");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS watch(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "repair_id INTEGER, who TEXT, seen_id INTEGER DEFAULT 0)");
        }
    }

    /** SQLite не умеет ADD COLUMN IF NOT EXISTS — тихий пропуск существующей колонки. */
    private static void addColumn(Statement st, String table, String column) {
        try { st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column); }
        catch (Exception ignored) { }
    }

    public static void exec(String sql) throws Exception {
        try (Statement st = get().createStatement()) { st.executeUpdate(sql); }
    }

    /** То же, но с числом изменённых строк — нужно для проверки версии записи при сохранении. */
    public static int update(String sql) throws Exception {
        try (Statement st = get().createStatement()) { return st.executeUpdate(sql); }
    }

    public static String esc(String s) { return s == null ? "" : s.replace("'", "''"); }

    private Db() { }
}
