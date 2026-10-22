package psr.sc;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Предупреждение красным шрифтом. */
public final class Warn {
    public static void show(String text) {
        JPanel p = new JPanel();
        JLabel l = new JLabel("<html><w>" + text.replace("&", "&amp;").replace("<", "&lt;")
            .replace("\n", "<br>") + "</w></html>");
        l.setForeground(Theme.BRIGHT_RED);
        l.setFont(Theme.fontB(14));
        l.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(l);
        JOptionPane.showConfirmDialog(null, p, "Внимание", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    }

    private Warn() { }
}
