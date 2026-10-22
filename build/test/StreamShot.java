package psr.sc;

import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Как выглядит окно «Лента заявки №…» с репликой, пометкой и служебной записью. */
public class StreamShot {

    public static void main(String[] args) throws Exception {
        long id;
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM repairs ORDER BY id LIMIT 1")) {
            id = rs.next() ? rs.getLong(1) : 900099;
        }
        Db.exec("DELETE FROM repair_history WHERE info LIKE 'ZZshot%'");
        Stream.log(id, "ZZshot Заявка создана: Открыт ремонт (приём)");
        Stream.post(id, "ZZshot Клиент просил позвонить после 18:00, днём не дозвониться", Stream.POST);
        Stream.log(id, "ZZshot Статус → В работе");
        Stream.post(id, "ZZshot Пометка для своих: крышка под заказ, не выдавать до поступления", Stream.NOTE);

        final JFrame owner = new JFrame("owner");
        owner.setSize(400, 300);
        final long rep = id;
        SwingUtilities.invokeAndWait(() -> {
            owner.setVisible(true);
            Stream.show(owner, rep);
        });
        Thread.sleep(1200);
        for (Window w : Window.getWindows()) {
            if (!(w instanceof JDialog)) continue;
            JPanel content = (JPanel) ((JDialog) w).getContentPane();
            BufferedImage img = new BufferedImage(content.getWidth(), content.getHeight(),
                BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            content.paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File("build/test/ui_stream.png"));
            System.out.println("shot " + content.getWidth() + "x" + content.getHeight());
            break;
        }
        Db.exec("DELETE FROM repair_history WHERE info LIKE 'ZZshot%'");
        System.exit(0);
    }
}
