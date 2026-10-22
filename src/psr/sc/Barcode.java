package psr.sc;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Штрихкод Code 128 через ZXing (Apache-2.0) — эталонный генератор, тем же алгоритмом
 * пользуются сканеры. Толщина (percent) меняет долю ширины, занимаемую самим кодом:
 * меньше 100 — тоньше штрих и больше поля, больше 100 — упор в физический предел ширины.
 */
public final class Barcode {

    /** Нарисовать штрихкод в рамке, обычная толщина. */
    public static void draw(Graphics2D g, String code, int x, int y, int w, int h) {
        draw(g, code, x, y, w, h, 100);
    }

    public static void draw(Graphics2D g, String code, int x, int y, int w, int h, int percent) {
        try {
            BitMatrix m = new Code128Writer().encode(code, BarcodeFormat.CODE_128,
                Math.max(60, w * percent / 100), h);
            double s = Math.min(1.0, (double) w / m.getWidth());
            int ox = x + (int) ((w - m.getWidth() * s) / 2);
            g.setColor(Color.BLACK);
            int mid = m.getHeight() / 2;
            int px = 0;
            while (px < m.getWidth()) {
                if (m.get(px, mid)) {
                    int a = ox + (int) (px * s), b = ox + (int) ((px + 1) * s);
                    g.fillRect(a, y, Math.max(1, b - a), h);
                }
                px++;
            }
        } catch (Exception e) {
            Warn.show("Штрихкод не получился (" + code + "): " + e.getMessage());
        }
    }

    private Barcode() { }
}
