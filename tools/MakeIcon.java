// Генератор installer\icon.ico и installer\icon.png из картинки-источника (по умолчанию installer\icon.jpg).
// .ico берут установщик, ярлыки и список «Программы и компоненты»; .png — окно программы
// (Java 8 не читает .ico). Если источника нет, рисуется запасной мятный квадратик с «ПС»,
// как у значка в трее (App.trayImage).
// Использование: javac --release 8 -encoding UTF-8 -d build\icon tools\MakeIcon.java
//                java -cp build\icon MakeIcon installer\icon.ico [путь\к\источнику.png]
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MakeIcon {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] a) throws IOException {
        String out = a.length > 0 ? a[0] : "installer/icon.ico";
        File src = new File(a.length > 1 ? a[1] : "installer/icon.jpg");
        BufferedImage master = src.isFile() ? square(load(src)) : draw(256);
        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int s : SIZES) {
            byte[] dib = dib(scale(master, s));
            ico.write(header(s, dib.length, 6 + SIZES.length * 16 + body.size()));
            body.write(dib);
        }
        try (FileOutputStream f = new FileOutputStream(out)) {
            f.write(new byte[]{0, 0, 1, 0, (byte) SIZES.length, 0});
            f.write(ico.toByteArray());
            f.write(body.toByteArray());
        }
        String png = out.replace(".ico", ".png");
        javax.imageio.ImageIO.write(scale(master, 256), "png", new File(png));
        System.out.println("ICO: " + out + " " + new File(out).length() + " bytes, "
            + "PNG: " + png + " " + new File(png).length() + " bytes, "
            + "src: " + (src.isFile() ? src : "tile (источник не найден)"));
    }

    private static BufferedImage load(File f) throws IOException {
        return javax.imageio.ImageIO.read(f);
    }

    /** Приводит к квадрату по меньшей стороне — иконки не должны вытягиваться. */
    private static BufferedImage square(BufferedImage img) {
        int s = Math.min(img.getWidth(), img.getHeight());
        BufferedImage o = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(img, 0, 0, s, s,
            (img.getWidth() - s) / 2, (img.getHeight() - s) / 2,
            (img.getWidth() + s) / 2, (img.getHeight() + s) / 2, null);
        g.dispose();
        return o;
    }

    /** Последовательное уменьшение вдвое — иначе мелкий размер «прыгает» и мылится. */
    private static BufferedImage scale(BufferedImage from, int to) {
        BufferedImage cur = from;
        while (cur.getWidth() / 2 >= to) cur = half(cur);
        return cur.getWidth() == to ? cur : half2(cur, to);
    }

    private static BufferedImage half(BufferedImage img) {
        return half2(img, Math.max(img.getWidth() / 2, 1));
    }

    private static BufferedImage half2(BufferedImage img, int to) {
        BufferedImage o = new BufferedImage(to, to, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, to, to, null);
        g.dispose();
        return o;
    }

    static BufferedImage draw(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x7FD9BE),
            0, s, new Color(0x2E9979)));
        int arc = Math.max(2, s * 7 / 25);
        g.fillRoundRect(0, 0, s, s, arc, arc);
        g.setColor(new Color(0x1E7256));
        g.drawRoundRect(0, 0, s - 1, s - 1, arc, arc);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(5, Math.round(s * 0.42f))));
        FontMetrics fm = g.getFontMetrics();
        String text = "ПС";
        g.drawString(text, Math.round((s - fm.stringWidth(text)) / 2f),
            Math.round((s - fm.getHeight()) / 2f + fm.getAscent()));
        g.dispose();
        return img;
    }

    private static byte[] header(int s, int len, int offset) {
        byte[] h = new byte[16];
        h[0] = (byte) (s >= 256 ? 0 : s);
        h[1] = (byte) (s >= 256 ? 0 : s);
        h[4] = 1;                       // planes
        h[6] = 32;                      // бит на пиксель
        put(h, 8, len);
        put(h, 12, offset);
        return h;
    }

    private static void put(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    /** Классический BMP-вход ICO (32 бита + AND-маска) — понимают и Windows 7, и Inno Setup. */
    private static byte[] dib(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int maskRow = ((w + 31) / 32) * 4;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] ih = new byte[40];
        put(ih, 0, 40);
        put(ih, 4, w);
        put(ih, 8, h * 2);
        ih[12] = 1;
        ih[14] = 32;
        o.write(ih, 0, ih.length);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                o.write(p & 0xFF);
                o.write((p >> 8) & 0xFF);
                o.write((p >> 16) & 0xFF);
                o.write((p >> 24) & 0xFF);
            }
        }
        o.write(new byte[maskRow * h], 0, maskRow * h);
        return o.toByteArray();
    }
}
