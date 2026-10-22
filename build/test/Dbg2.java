package psr.sc;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Поштамповая отладка PrintSim: декод на каждой стадии + PNG-дампы. */
public class Dbg2 {
    public static void main(String[] a) throws Exception {
        BufferedImage label = Labels.one(1, 58, 40, 100);
        System.out.println("label " + label.getWidth() + "x" + label.getHeight()
            + " type=" + label.getType() + " decode=" + PrintSim.decode(label, 0));

        BufferedImage hi = PrintSim.raster(label, 600, 1.0);
        System.out.println("raster600 " + stats(hi) + " decode=" + PrintSim.decode(hi, 0));
        dump(hi, "d1_raster.png");

        double sigma = 0.03 * 600 / 25.4;
        BufferedImage bl = PrintSim.blur(hi, sigma);
        System.out.println("blur sigma=" + String.format("%.2f", sigma) + " " + stats(bl)
            + " decode=" + PrintSim.decode(bl, 0));
        dump(bl, "d2_blur.png");

        BufferedImage di = PrintSim.dither(bl);
        System.out.println("dither " + stats(di) + " decode=" + PrintSim.decode(di, 0));
        dump(di, "d3_dither.png");

        BufferedImage sc = PrintSim.scan(di, 600);
        System.out.println("scan300 " + stats(sc) + " decode=" + PrintSim.decode(sc, 0));
        dump(sc, "d4_scan.png");
    }

    static String stats(BufferedImage img) {
        int dark = 0, n = img.getWidth() * img.getHeight();
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++)
            if (img.getRaster().getSample(x, y, 0) < 128) dark++;
        return img.getWidth() + "x" + img.getHeight() + " type=" + img.getType()
            + " dark=" + (dark * 100 / n) + "%";
    }

    static void dump(BufferedImage img, String name) throws Exception {
        ImageIO.write(img, "png", new File("build/test/" + name));
    }
}
