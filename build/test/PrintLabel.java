package psr.sc;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/** Одноразовый тест: собрать наклейку кодом приложения и отправить её на принтер. */
public class PrintLabel {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "list".equals(args[0])) {
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT r.id, r.status, r.equipment FROM repairs r"
                     + " ORDER BY r.id")) {
                while (rs.next()) System.out.println("repair " + rs.getLong(1) + " ["
                    + rs.getString(2) + "] " + rs.getString(3));
            }
            System.out.println("printers:");
            for (PrintService p : PrintServiceLookup.lookupPrintServices(null, null))
                System.out.println("  " + p.getName());
            return;
        }

        long id = Long.parseLong(args[0]);
        int wmm = Integer.parseInt(args[1]);
        int hmm = Integer.parseInt(args[2]);
        int pct = Integer.parseInt(args[3]);
        String out = args[4];
        String printer = args.length > 5 ? args[5] : null;

        BufferedImage img = Labels.one(id, wmm, hmm, pct);
        if (img == null) { System.out.println("FAIL: наклейка не собрана (нет заявки " + id + ")"); return; }
        ImageIO.write(img, "png", new File(out));
        System.out.println("png: " + new File(out).getAbsolutePath()
            + " " + img.getWidth() + "x" + img.getHeight() + "px");

        PrintService ps = null;
        if (printer == null) ps = PrintServiceLookup.lookupDefaultPrintService();
        else for (PrintService c : PrintServiceLookup.lookupPrintServices(null, null))
            if (c.getName().toLowerCase().contains(printer.toLowerCase())) ps = c;
        if (ps == null) { System.out.println("FAIL: принтер не найден (" + printer + ")"); return; }
        System.out.println("printer: " + ps.getName());

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(ps);
        PageFormat pf = job.defaultPage();
        final double ptPerMm = 72.0 / 25.4;
        job.setPrintable((g, page, pi) -> {
            if (pi > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
            double w = wmm * ptPerMm, h = hmm * ptPerMm;
            double x = page.getImageableX() + (page.getImageableWidth() - w) / 2;
            double y = page.getImageableY() + (page.getImageableHeight() - h) / 2;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(img, (int) x, (int) y, (int) Math.round(w), (int) Math.round(h), null);
            return java.awt.print.Printable.PAGE_EXISTS;
        }, pf);
        job.print();
        System.out.println("OK: задание отправлено на " + ps.getName());
        System.exit(0);
    }
}
