package psr.sc;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

/** Имитация реального отпечатка и сканирования с декодом: dpi принтера, тонер-гейн, 1-бит растер, масштаб драйвера. */
public class PrintSim {

    static final double MM = 25.4;
    static final int SCAN_DPI = 300;   // оптическое разрешение типичного сканера

    public static void main(String[] args) throws Exception {
        int[] pcts = { 75, 100, 150, 200 };
        int[] dpis = { 600, 300 };
        double[] gains = { 0.03, 0.08 };   // распыление тонера, мм (норма / старый картридж)
        double[] scales = { 1.0, 0.95 };   // 1.0 = 100%, 0.95 = драйвер «вписать в страницу»
        int[][] sizes = { { 48, 30 }, { 58, 40 }, { 90, 60 } };
        int ok = 0, total = 0;
        for (int[] sz : sizes) {
            for (int pct : pcts) {
                BufferedImage label = Labels.one(1, sz[0], sz[1], pct);
                for (int dpi : dpis) for (double gain : gains) for (double sc : scales) {
                    total++;
                    BufferedImage printed = scan(simulate(label, dpi, gain, sc), dpi);
                    String r = decode(printed, 0);
                    if (r == null) r = decode(printed, 2.5);      // наклейка криво приложена к стеклу
                    if (r == null) r = decode(printed, -2.5);
                    boolean good = "PSR-R1".equals(r);
                    if (good) ok++;
                    else System.out.println("FAIL " + sz[0] + "x" + sz[1] + " pct=" + pct + " dpi=" + dpi
                        + " gain=" + gain + " scale=" + sc + " -> " + r);
                }
            }
        }
        System.out.println("прочитано " + ok + " из " + total);
    }

    /** Сканирование: усреднение света по пикселу матрицы (300 dpi оптика) + шум и неравномерная подсветка. */
    static BufferedImage scan(BufferedImage printed, int printDpi) {
        double r = printDpi / (double) SCAN_DPI;   // пикселов печати на один пиксел сканера
        int w = (int) Math.round(printed.getWidth() / r);
        int h = (int) Math.round(printed.getHeight() / r);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        java.util.Random rnd = new java.util.Random(7);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int x0 = (int) Math.floor(x * r), x1 = Math.max(x0 + 1, (int) Math.ceil((x + 1) * r));
            int y0 = (int) Math.floor(y * r), y1 = Math.max(y0 + 1, (int) Math.ceil((y + 1) * r));
            double s = 0; int n = 0;
            for (int yy = y0; yy < Math.min(y1, printed.getHeight()); yy++)
                for (int xx = x0; xx < Math.min(x1, printed.getWidth()); xx++) {
                    s += printed.getRaster().getSample(xx, yy, 0); n++;
                }
            double v = n == 0 ? 255 : s / n;
            v *= 0.85 + 0.15 * Math.cos(Math.PI * x / w);          // блик по краю стекла
            v += rnd.nextGaussian() * 6;                            // шум матрицы
            out.getRaster().setSample(x, y, 0, (int) Math.max(0, Math.min(255, v)));
        }
        return out;
    }

    /** Печать: передискретизация в dpi принтера → размытие (тонер) → 1-бит растер. */
    static BufferedImage simulate(BufferedImage src, int dpi, double gainMm, double scale) {
        return dither(blur(raster(src, dpi, scale), gainMm * dpi / MM));
    }

    /** Пересчёт наклейки (8 px/мм) в точки принтера. */
    static BufferedImage raster(BufferedImage src, int dpi, double scale) {
        double ppm = dpi / MM;
        int pw = (int) Math.round(src.getWidth() / 8.0 * ppm * scale);
        int ph = (int) Math.round(src.getHeight() / 8.0 * ppm * scale);
        BufferedImage hi = new BufferedImage(pw, ph, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, pw, ph, null);
        g.dispose();
        return hi;
    }

    static BufferedImage blur(BufferedImage src, double sigma) {
        if (sigma < 0.5) return src;
        int r = (int) Math.ceil(sigma * 3);
        double[] k = new double[2 * r + 1];
        double sum = 0;
        for (int i = -r; i <= r; i++) { k[i + r] = Math.exp(-i * i / (2 * sigma * sigma)); sum += k[i + r]; }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        int w = src.getWidth(), h = src.getHeight();
        double[] a = gray(src), b = new double[w * h], c = new double[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double v = 0;
            for (int i = -r; i <= r; i++) {
                int xx = Math.min(w - 1, Math.max(0, x + i));
                v += a[y * w + xx] * k[i + r];
            }
            b[y * w + x] = v;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double v = 0;
            for (int i = -r; i <= r; i++) {
                int yy = Math.min(h - 1, Math.max(0, y + i));
                v += b[yy * w + x] * k[i + r];
            }
            c[y * w + x] = v;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            out.getRaster().setSample(x, y, 0, (int) Math.max(0, Math.min(255, c[y * w + x])));
        return out;
    }

    static double[] gray(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[] p = new double[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            p[y * w + x] = img.getRaster().getSample(x, y, 0);
        return p;
    }

    /** Флойда–Стейнберга в 1 бит — как GDI-драйвер лазерного принтера печатает полутоны (хранится в сером 0/255). */
    static BufferedImage dither(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        double[] p = gray(src);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            double old = p[i];
            double v = old < 128 ? 0 : 255;
            out.getRaster().setSample(x, y, 0, (int) v);
            double err = old - v;
            if (x + 1 < w) p[i + 1] += err * 7 / 16;
            if (y + 1 < h) {
                if (x > 0) p[i + w - 1] += err * 3 / 16;
                p[i + w] += err * 5 / 16;
                if (x + 1 < w) p[i + w + 1] += err / 16;
            }
        }
        return out;
    }

    static BufferedImage rotate(BufferedImage src, double deg) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        AffineTransform t = new AffineTransform();
        t.translate(out.getWidth() / 2.0, out.getHeight() / 2.0);
        t.rotate(Math.toRadians(deg));
        t.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    static String decode(BufferedImage img, double deg) {
        try {
            BufferedImage s = deg == 0 ? img : rotate(img, deg);
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS,
                java.util.Arrays.asList(BarcodeFormat.CODE_128));
            BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(s)));
            return new MultiFormatReader().decode(bb, hints).getText();
        } catch (Exception e) {
            return null;
        }
    }
}
