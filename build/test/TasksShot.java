package psr.sc;

import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Как выглядит окно «Дела и напоминания» со строкой фильтра. */
public class TasksShot {

    public static void main(String[] args) throws Exception {
        Db.exec("DELETE FROM tasks WHERE title LIKE 'ZZshot%'");
        Db.exec("INSERT INTO tasks(repair_id,kind,title,due,done,who,created,repeat_days)"
            + " VALUES(1,'Звонок','ZZshot уточнить цену у заказчика','15.10.2026',0,'Кузнецов',"
            + "'20.10.2026 09:00',0)");
        Db.exec("INSERT INTO tasks(repair_id,kind,title,due,done,who,created,repeat_days)"
            + " VALUES(3,'Встреча','ZZshot приёмка у клиента','25.10.2026',0,'Смирнов',"
            + "'21.10.2026 09:00',7)");

        final JFrame owner = new JFrame("owner");
        owner.setSize(400, 300);
        SwingUtilities.invokeAndWait(() -> {
            owner.setVisible(true);
            Tasks.show(owner, null);
        });
        Thread.sleep(1200);
        final Window[] ws = Window.getWindows();
        for (int i = 0; i < ws.length; i++) {
            final int k = i;
            if (!(ws[k] instanceof JDialog)) continue;
            SwingUtilities.invokeAndWait(() -> {
                JPanel content = (JPanel) ((JDialog) ws[k]).getContentPane();
                BufferedImage img = new BufferedImage(content.getWidth(), content.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                content.paint(g);
                g.dispose();
                try {
                    ImageIO.write(img, "png", new File("build/test/ui_tasks.png"));
                    System.out.println("shot " + content.getWidth() + "x" + content.getHeight());
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            break;
        }
        Db.exec("DELETE FROM tasks WHERE title LIKE 'ZZshot%'");
        System.exit(0);
    }
}
