package psr.sc;

import java.io.*;
import java.net.*;
import javax.swing.*;

/**
 * Автоматическое обновление: проверяет GitHub releases, сравнивает версии,
 * скачивает установщик и запускает его.
 */
public class AutoUpdater {
    private static final String GITHUB_API =
        "https://api.github.com/repos/assa318i/ServiceCenter-PSR/releases/latest";

    /**
     * Проверяет наличие новой версии и предлагает обновиться.
     * Вызывается из App.main() после показа окна.
     */
    public static void checkAndPrompt() {
        new Thread(() -> {
            try {
                String json = fetchJson();
                if (json == null) return;
                String latest = extractTag(json);
                if (latest == null || !isNewer(latest, App.VERSION)) return;
                String installerUrl = extractInstallerUrl(json, latest);
                if (installerUrl == null) return;

                SwingUtilities.invokeLater(() -> promptUpdate(latest, installerUrl));
            } catch (Exception ignored) {
                // Ошибки сети — обновление не критично
            }
        }, "AutoUpdater").start();
    }

    private static void promptUpdate(String version, String installerUrl) {
        int choice = JOptionPane.showConfirmDialog(null,
            "Доступна новая версия " + version + " (у вас " + App.VERSION + ").\n"
            + "Скачать и установить обновление?",
            "Обновление доступно",
            JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                File temp = File.createTempFile("PSR-Update-", ".exe");
                downloadFile(installerUrl, temp);
                SwingUtilities.invokeLater(() -> {
                    int run = JOptionPane.showConfirmDialog(null,
                        "Обновление скачано. Запустить установщик?\n"
                        + "Приложение будет закрыто.",
                        "Готово", JOptionPane.YES_NO_OPTION);
                    if (run == JOptionPane.YES_OPTION) launchAndExit(temp);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    Warn.show("Ошибка обновления: " + e.getMessage()));
            }
        }, "Downloader").start();
    }

    private static String fetchJson() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(GITHUB_API).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("User-Agent", "PSR-ServiceCenter/" + App.VERSION);
        if (c.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    /** Извлекает tag_name и убирает префикс "v". */
    private static String extractTag(String json) {
        String val = jsonString(json, "tag_name");
        if (val == null) return null;
        return val.startsWith("v") ? val.substring(1) : val;
    }

    /** Ищет browser_download_url для .exe-ассета с нужным именем. */
    private static String extractInstallerUrl(String json, String version) {
        String marker = "PSR-ServiceCenter-Setup-" + version + ".exe";
        int idx = json.indexOf(marker);
        if (idx < 0) return null;
        int u = json.lastIndexOf("\"browser_download_url\"", idx);
        if (u < 0) return null;
        u = json.indexOf('"', u + 22);
        if (u < 0) return null;
        int e = json.indexOf('"', u + 1);
        return e < 0 ? null : json.substring(u + 1, e);
    }

    /** Вытаскивает строковое значение JSON-ключа простым поиском. */
    private static String jsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        i = json.indexOf('"', i + key.length() + 2);
        if (i < 0) return null;
        int e = json.indexOf('"', i + 1);
        return e < 0 ? null : json.substring(i + 1, e);
    }

    private static boolean isNewer(String latest, String current) {
        try {
            String[] l = latest.split("\\."), c = current.split("\\.");
            for (int i = 0; i < Math.max(l.length, c.length); i++) {
                int lv = i < l.length ? Integer.parseInt(l[i]) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static void downloadFile(String urlStr, File dest) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestProperty("User-Agent", "PSR-ServiceCenter/" + App.VERSION);
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        try (InputStream in = c.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void launchAndExit(File installer) {
        try {
            new ProcessBuilder(installer.getAbsolutePath()).start();
        } catch (IOException e) {
            Warn.show("Не удалось запустить установщик: " + e.getMessage());
            return;
        }
        System.exit(0);
    }
}
