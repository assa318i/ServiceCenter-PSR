package psr.sc;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JTextPane;

/** Проверка разбивки акта по страницам: конечное число страниц, целые строки, дампы PNG. */
public class PagTest {
    public static void main(String[] args) throws Exception {
        JTextPane src = Print.pane();
        Print.addLine(src, "АКТ о выполненных работах №3 от 21.10.2026", false, true);
        for (int i = 0; i < 70; i++)
            Print.addLine(src, "Позиция " + (i + 1) + " — работы выполнены в объёме, указанном в заявке;"
                + " стороны претензий друг к другу не имеют.", false, false);
        Print.addLine(src, "Подпись исполнителя ________________", false, false);

        PageFormat f = new PageFormat();
        Paper paper = new Paper();
        paper.setSize(595.28, 841.89);
        paper.setImageableArea(36, 36, 595.28 - 72, 841.89 - 72);
        f.setPaper(paper);
        int pw = (int) f.getImageableWidth(), ph = (int) f.getImageableHeight();

        JTextPane doc = Print.forPrint(src, pw, ph);
        List<Integer> tops = Print.pageTops(doc, ph);
        System.out.println("область печати " + pw + "x" + ph + " pt, высота текста "
            + doc.getHeight() + " pt, страниц: " + tops.size() + " tops=" + tops);

        Printable pr = Print.printable(doc, tops, pw, ph);
        System.out.println("запрос страницы " + tops.size() + " (за последней) -> "
            + pr.print(null, f, tops.size()) + " (0 = NO_SUCH_PAGE, стоп)");
        System.out.println("запрос страницы 500 -> " + pr.print(null, f, 500));

        for (int i = 0; i < tops.size(); i++) {
            BufferedImage img = new BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 595, 842);
            pr.print(g, f, i);
            g.dispose();
            int dark = 0;
            for (int y = 0; y < 842; y += 2) for (int x = 0; x < 595; x += 2)
                if ((img.getRGB(x, y) & 0xFFFFFF) < 0x808080) dark++;
            System.out.println("page" + (i + 1) + " тёмных пикселей " + dark);
            ImageIO.write(img, "png", new File("build/test/page" + (i + 1) + ".png"));
        }
    }
}
