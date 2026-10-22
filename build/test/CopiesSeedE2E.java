package psr.sc;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Установщик пишет data\backup-dirs.txt в системной кодировке (ANSI), а Блокнот
 * может пересохранить его в UTF-8 (иногда с BOM). Программа обязана принять оба
 * варианта и не подставить комментарий как путь. Запускать из пустой папки —
 * тест создаёт data\ относительно текущего каталога (см. seed-test.bat).
 */
public class CopiesSeedE2E {
    private static int ok, bad;

    public static void main(String[] args) throws Exception {
        Db.get();
        run("ANSI (cp1251)", "windows-1251", false);
        run("UTF-8 без BOM", "UTF-8", false);
        run("UTF-8 с BOM", "UTF-8", true);
        System.out.println("ok=" + ok + " fail=" + bad);
        if (bad > 0) System.exit(1);
    }

    private static void run(String label, String charset, boolean bom) throws Exception {
        Settings.set("cloud_path", "");
        File a = new File("data/копии PSR");
        File b = new File("data/внешняя папка");
        a.mkdirs();
        b.mkdirs();
        String text = "# Папки для дополнительных копий базы (по одной в строке).\r\n"
            + a.getAbsolutePath() + "\r\n" + b.getAbsolutePath() + "\r\n";
        File f = new File("data/backup-dirs.txt");
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (bom) out.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
            out.write(text.getBytes(charset));
        }
        Export.seedCopies();
        String got = Settings.get("cloud_path");
        check(label + ": приняты обе папки",
            got.contains(a.getAbsolutePath()) && got.contains(b.getAbsolutePath()));
        check(label + ": кириллица в пути не искажена", got.contains("копии PSR"));
        check(label + ": комментарий не стал путём", !got.contains("#"));
        check(label + ": файл предложения убран", !f.exists());
    }

    private static void check(String name, boolean good) {
        if (good) { ok++; System.out.println("ok   " + name); }
        else { bad++; System.out.println("ПРОВАЛ " + name + " cloud_path=" + Settings.get("cloud_path")); }
    }
}
