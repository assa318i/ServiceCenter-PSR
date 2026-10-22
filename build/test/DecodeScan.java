package psr.sc;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Декод штрихкода с реального скана: масштаб + нарезка кадра, чтобы найти наклейку на листе А4. */
public class DecodeScan {
    public static void main(String[] args) throws Exception {
        File f = new File(args.length > 0 ? args[0] : "build/test/scan.bmp");
        BufferedImage src = ImageIO.read(f);
        if (src == null) { System.out.println("FAIL: не читается " + f); return; }
        int w = src.getWidth(), h = src.getHeight(), dark = 0;
        for (int y = 0; y < h; y += 3) for (int x = 0; x < w; x += 3)
            if ((src.getRGB(x, y) & 0xFFFFFF) < 0x808080) dark++;
        System.out.println("скан " + w + "x" + h + "px (" + Math.round(w / 11.81) + "x"
            + Math.round(h / 11.81) + " мм при 300 dpi), тёмных пикселей "
            + (dark * 100 / ((w / 3) * (h / 3))) + "%");

        String[] tries = { "весь кадр", "кадр 50%", "кадр 25%" };
        double[] scales = { 1.0, 0.5, 0.25 };
        for (int i = 0; i < scales.length; i++) {
            String r = PrintSim.decode(zoom(src, scales[i]), 0);
            System.out.println(tries[i] + " -> " + (r == null ? "не найден" : r));
            if (r != null) return;
        }
        for (int gy = 0; gy < 3; gy++) for (int gx = 0; gx < 3; gx++) {
            for (double s : new double[] { 1.0, 0.6 }) {
                BufferedImage crop = zoom(src.getSubimage(w * gx / 3, h * gy / 3,
                    Math.min(w - w * gx / 3, w / 2), Math.min(h - h * gy / 3, h / 2)), s);
                String r = PrintSim.decode(crop, 0);
                if (r != null) { System.out.println("фрагмент " + gx + gy + " (s=" + s + ") -> " + r); return; }
            }
        }
        System.out.println("ни в одном фрагменте штрихкод не найден");
    }

    static BufferedImage zoom(BufferedImage src, double s) {
        if (s == 1.0) return src;
        BufferedImage out = new BufferedImage((int) (src.getWidth() * s),
            (int) (src.getHeight() * s), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }
}
