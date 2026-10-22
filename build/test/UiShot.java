import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import psr.sc.CustomersPanel;
import psr.sc.RepairsPanel;
import psr.sc.WarehousePanel;

/** Офскрин-рендер трёх окон в реальном кадре: видны ли ряд кнопок и строка фильтра. */
public class UiShot {

    static final JFrame frame = new JFrame();

    public static void main(String[] args) throws Exception {
        shot("repairs", new RepairsPanel(null));
        shot("customers", new CustomersPanel(null));
        shot("warehouse", new WarehousePanel());
        System.out.println("shots written");
        System.exit(0);
    }

    static void shot(String name, final JPanel panel) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.setContentPane(panel);
            frame.setSize(1420, 760);
            frame.setVisible(true);
        });
        Thread.sleep(1000);
        final BufferedImage img = new BufferedImage(panel.getWidth(), panel.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            dump(name, panel);
            Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            panel.paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File("build/test/ui_" + name + ".png"));
    }

    static void dump(String name, JPanel panel) {
        System.out.println("== " + name);
        for (java.awt.Component c : panel.getComponents())
            System.out.println("  " + c.getClass().getSimpleName() + " " + c.getBounds());
        Container north = (Container) panel.getComponent(0);
        for (java.awt.Component c : north.getComponents()) {
            System.out.println("      north-child " + c.getClass().getSimpleName() + " "
                + c.getBounds() + " pref=" + c.getPreferredSize());
            if (c instanceof Container)
                for (java.awt.Component g : ((Container) c).getComponents())
                    System.out.println("          row " + g.getClass().getSimpleName()
                        + " " + g.getBounds());
        }
    }
}
