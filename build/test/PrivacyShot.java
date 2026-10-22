package psr.sc;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Как видит базу заказчиков инженер: ФИО, телефоны и адреса обезличены. */
public class PrivacyShot {

    public static void main(String[] args) throws Exception {
        Db.exec("DELETE FROM customers WHERE org LIKE 'ZZпдн%'");
        Db.exec("INSERT INTO customers(org,fio,phone,address,attorney)"
            + " VALUES('ZZпдн Ремонттехника','Иванов Иван Иванович','+7 921 123-45-67',"
            + "'г. Тула, ул. Мира, 1, оф. 5','Петров П.П.')");
        Db.exec("INSERT INTO customers(org,fio,phone,address,attorney)"
            + " VALUES('ZZпдн Офис Сервис','Смирнова Ольга Петровна','+7 4872 55-66-77',"
            + "'г. Тула, пр. Ленина, 90','')");

        Session.role = "ENGINEER";
        Session.name = "Инженер";
        final JFrame f = new JFrame("engineer");
        SwingUtilities.invokeAndWait(() -> {
            f.setContentPane(new CustomersPanel(null));
            f.setSize(1120, 320);
            f.setVisible(true);
        });
        Thread.sleep(900);
        final JPanel content = (JPanel) f.getContentPane();
        BufferedImage img = new BufferedImage(content.getWidth(), content.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            content.paint(g);
            g.dispose();
        });
        try {
            ImageIO.write(img, "png", new File("build/test/ui_privacy.png"));
            System.out.println("shot " + img.getWidth() + "x" + img.getHeight());
        } catch (Exception e) { throw new RuntimeException(e); }
        Db.exec("DELETE FROM customers WHERE org LIKE 'ZZпдн%'");
        System.exit(0);
    }
}
