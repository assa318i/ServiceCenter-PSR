package psr.sc;

import java.awt.BorderLayout;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Красная панель: просроченные SLA, ожидания запчастей, необработанные заказы склада. */
public class Alerts extends JPanel {

    private final JLabel label = new JLabel(" ");
    private final Vector<String> problems = new Vector<>();

    public Alerts() {
        setLayout(new BorderLayout());
        setBackground(Theme.BRIGHT_RED);
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        label.setFont(Theme.fontB(14));
        label.setForeground(java.awt.Color.WHITE);
        add(label, BorderLayout.CENTER);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!problems.isEmpty()) Warn.show(String.join("\n", problems));
            }
        });
        setVisible(false);
    }

    public void scan() {
        problems.clear();
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id, opened, wait_until, status FROM repairs"
                + " WHERE status<>'DONE_ISSUED'");
            int target;
            try { target = Integer.parseInt(Settings.get("sla_days")); }
            catch (Exception e) { target = 14; }
            while (rs.next()) {
                long days = RepairsPanel.daysInRepair(RepairsPanel.nz(rs.getString(2)), "");
                if (days > target)
                    problems.add("Заявка №" + rs.getLong(1) + ": в ремонте " + days
                        + " дн. — превышен целевой срок " + target + " дн.");
                String w = RepairsPanel.nz(rs.getString(3));
                if ("WAIT_PARTS".equals(rs.getString(4)) && RepairsPanel.overdue(w))
                    problems.add("Заявка №" + rs.getLong(1) + ": запчасти должны были прийти до " + w);
            }
            rs.close();
            rs = st.executeQuery("SELECT COUNT(*) FROM stock_orders WHERE status='NEW'");
            rs.next();
            long orders = rs.getLong(1);
            rs.close();
            if (orders > 0) problems.add("Заказов на склад без движения: " + orders);
            rs = st.executeQuery("SELECT COUNT(*) FROM items WHERE qty < min_qty");
            rs.next();
            long below = rs.getLong(1);
            rs.close();
            if (below > 0) problems.add("Позиций склада ниже минимальной нормы: " + below);
            rs = st.executeQuery("SELECT title, due FROM tasks WHERE done=0 ORDER BY due");
            while (rs.next())
                if (Tasks.overdue(rs.getString(2)))
                    problems.add("Дело «" + rs.getString(1) + "» со сроком " + rs.getString(2));
            rs.close();
        } catch (Exception ignored) { }
        if (problems.isEmpty()) setVisible(false);
        else {
            setVisible(true);
            label.setText("<html><b>ВНИМАНИЕ: " + problems.get(0)
                + (problems.size() > 1 ? "  … и ещё " + (problems.size() - 1) : "")
                + "  (клик — подробный список)</b></html>");
            revalidate();
        }
    }
}
