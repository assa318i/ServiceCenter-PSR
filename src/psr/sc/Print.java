package psr.sc;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Печать документов (акты, отчёты), отправка на почту и экспорт базы для Android. */
public final class Print {

    public static JTextPane pane() {
        JTextPane p = new JTextPane();
        p.setFont(Theme.font(14));
        p.setEditable(false);
        p.setBackground(java.awt.Color.WHITE);
        return p;
    }

    /** Добавить абзац; red=true — красный жирный (важные/опасные сообщения). */
    public static void addLine(JTextPane p, String text, boolean red, boolean big) {
        StyledDocument d = p.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, Theme.plain.getFamily());
        StyleConstants.setFontSize(a, big ? 20 : 14);
        StyleConstants.setBold(a, red || big);
        StyleConstants.setForeground(a, red ? Theme.BRIGHT_RED : Theme.BLACK);
        try { d.insertString(d.getLength(), text + "\n", a); } catch (Exception ignored) { }
    }

    /** Печать документа одним экземпляром. */
    public static void print(final JTextPane doc, String name) { print(doc, name, 1); }

    /**
     * Печать текстового документа: разбивка по страницам на границах абзацев, N экземпляров.
     * Панелька для печати копируется — раскладка под ширину страницы не трогает предпросмотр на экране.
     */
    public static void print(final JTextPane src, String name, int copies) {
        try {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            java.awt.print.PageFormat pf = job.defaultPage();
            final int pw = (int) pf.getImageableWidth(), ph = (int) pf.getImageableHeight();
            final JTextPane doc = forPrint(src, pw, ph);
            job.setJobName(name);
            job.setPrintable(printable(doc, pageTops(doc, ph), pw, ph), pf);
            javax.print.attribute.PrintRequestAttributeSet attrs =
                new javax.print.attribute.HashPrintRequestAttributeSet();
            if (copies > 1) attrs.add(new javax.print.attribute.standard.Copies(copies));
            if (!job.printDialog(attrs)) return;
            job.print(attrs);
        } catch (Exception ex) {
            Warn.show("Печать не удалась: " + ex.getMessage());
        }
    }

    /** Копия панельки на том же документе, разложенная под ширину страницы. */
    static JTextPane forPrint(JTextPane src, int pw, int ph) {
        JTextPane doc = new JTextPane(src.getStyledDocument());
        doc.setFont(src.getFont());
        doc.setEditable(false);
        doc.setBackground(java.awt.Color.WHITE);
        doc.setSize(pw, Short.MAX_VALUE);
        doc.validate();
        doc.setSize(pw, Math.max(doc.getPreferredSize().height, ph));
        doc.validate();
        return doc;
    }

    static java.awt.print.Printable printable(final JTextPane doc, final java.util.List<Integer> tops,
            final int pw, final int ph) {
        return new java.awt.print.Printable() {
            @Override public int print(java.awt.Graphics g,
                    java.awt.print.PageFormat f, int pageIndex) {
                if (pageIndex >= tops.size()) return NO_SUCH_PAGE;
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.translate(f.getImageableX(), f.getImageableY());
                g2.setColor(java.awt.Color.WHITE);
                g2.fillRect(0, 0, pw, ph);
                int top = tops.get(pageIndex);
                int band = pageIndex + 1 < tops.size() ? tops.get(pageIndex + 1) - top : ph;
                g2.clipRect(0, 0, pw, Math.min(ph, band));
                g2.translate(0, -top);
                doc.paint(g2);
                g2.dispose();
                return PAGE_EXISTS;
            }
        };
    }

    /**
     * Верхние координаты страниц: новая страница начинается с абзаца, который не влез
     * в предыдущую целиком — абзацы не разрезаются (кроме абзаца выше страницы).
     */
    static java.util.List<Integer> pageTops(JTextPane doc, int pageH) {
        java.util.List<Integer> tops = new java.util.ArrayList<>();
        tops.add(0);
        java.util.List<Integer> ys = new java.util.ArrayList<>();
        javax.swing.text.Element root = doc.getDocument().getDefaultRootElement();
        try {
            for (int i = 0; i < root.getElementCount(); i++) {
                java.awt.Rectangle r = doc.modelToView(root.getElement(i).getStartOffset());
                if (r != null) ys.add(r.y);
            }
        } catch (javax.swing.text.BadLocationException ignored) { }
        ys.add(Math.max(doc.getHeight(), ys.isEmpty() ? 0 : ys.get(ys.size() - 1)));
        int pageTop = 0, i = 0;
        while (i < ys.size() - 1) {
            if (ys.get(i + 1) - pageTop <= pageH) { i++; continue; }
            int next = ys.get(i) > pageTop ? ys.get(i) : pageTop + pageH;
            tops.add(next);
            pageTop = next;
        }
        return tops;
    }

    /** Печать картинки (этикетка со штрихкодом) вписыванием в область печати. */
    public static void image(java.awt.image.BufferedImage img, String name) {
        pages(java.util.Collections.singletonList(img), name);
    }

    /** Печать нескольких картинок: каждая — своя страница (листы А4 или по этикетке на страницу). */
    public static void pages(final java.util.List<java.awt.image.BufferedImage> imgs, String name) {
        try {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            if (!job.printDialog()) return;
            job.setJobName(name);
            job.setPrintable(new java.awt.print.Printable() {
                @Override public int print(java.awt.Graphics g,
                        java.awt.print.PageFormat f, int pageIndex) {
                    if (pageIndex >= imgs.size()) return NO_SUCH_PAGE;
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                    g2.translate(f.getImageableX(), f.getImageableY());
                    java.awt.image.BufferedImage img = imgs.get(pageIndex);
                    double s = Math.min(f.getImageableWidth() / img.getWidth(),
                        f.getImageableHeight() / img.getHeight());
                    g2.scale(s, s);
                    g2.drawImage(img, 0, 0, null);
                    return PAGE_EXISTS;
                }
            });
            job.print();
        } catch (Exception ex) {
            Warn.show("Печать не удалась: " + ex.getMessage());
        }
    }

    /** Открыть почтовое приложение по умолчанию с письмом (mailto). */
    public static void mail(String to, String subject, String body) {
        try {
            String uri = "mailto:" + to + "?subject=" + uriEnc(subject) + "&body=" + uriEnc(body);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().mail(new URI(uri));
            else Warn.show("Не поддерживается открывание почты");
        } catch (Exception ex) {
            Warn.show("Не удалось открыть почту: " + ex.getMessage()
                + "\n\nСкопируйте текст документа из окна отчёта вручную.");
        }
    }

    private static String uriEnc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return s; }
    }

    /** Экспорт базы в data/export/ — тот же файл можно открыть на Android-версии. */
    public static File exportDb() throws Exception {
        File dir = new File(Db.DATA_DIR, "export");
        dir.mkdirs();
        File out = new File(dir, "psr.db");
        Files.copy(new File(Db.DATA_DIR, "psr.db").toPath(), out.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return out;
    }

    static String fileText(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private Print() { }
}
