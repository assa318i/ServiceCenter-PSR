package psr.sc;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import javax.swing.JOptionPane;

/**
 * Персональные данные: в общем списке заказчиков инженер видит ФИО, телефон и адрес обезличенно,
 * а по заявлению субъекта карточка полностью обезличивается (с резервной копией базы).
 */
public final class Privacy {

    /** true — текущий сотрудник не администратор, ПДн в списках скрыты. */
    static boolean hidden() { return !Session.isAdmin(); }

    static String fio(String s) {
        if (!hidden()) return s;
        String v = nz(s).trim();
        return v.isEmpty() ? "" : v.charAt(0) + "***";
    }

    /** Телефон обезличен, но последние две цифры видны — различить двух клиентов на приёмке. */
    static String phone(String s) {
        if (!hidden()) return s;
        String v = nz(s).trim();
        return v.length() < 3 ? "***" : "*** **" + v.substring(v.length() - 2);
    }

    static String address(String s) {
        if (!hidden()) return s;
        String v = nz(s).trim();
        int c = v.indexOf(',');
        return v.isEmpty() ? "" : (c > 1 ? v.substring(0, c) : v) + " ***";
    }

    /** Очистка ПДн по заявлению субъекта: копия базы, затем обезличивание карточки и удаление её сканов. */
    static void purge(java.awt.Component owner, Long id) {
        if (id == null) { Warn.show("Выберите заказчика"); return; }
        if (hidden()) { Warn.show("Обезличивать карточки может только администратор"); return; }
        String who = card(owner, id);
        if (who == null) return;
        int ok = JOptionPane.showConfirmDialog(owner,
            "Заявление субъекта о обработке ПДн — обезличить карточку «" + who + "»?\n\n"
            + "· ФИО, телефоны, адрес, доверенные лица и примечания будут стёрты\n"
            + "· сканы и фото этой карточки будут удалены с диска\n"
            + "· заявки, оплаты и долги останутся в учёте — отчётность не пострадает\n"
            + "· перед очисткой программа создаст резервную копию базы",
            "Очистка персональных данных", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            int files = anonymize(id);
            JOptionPane.showMessageDialog(owner, "Карточка обезличена. Удалено вложений: " + files
                + ". Резервная копия — в data\\backups", "Очистка ПДн", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { Warn.show(ex.getMessage()); }
    }

    /** Копия базы → удаление сканов карточки → стирание персональных данных. Возвращает число удалённых вложений. */
    static int anonymize(long id) throws Exception {
        Export.backup();
        int files = dropAttachments(id);
        Db.exec("UPDATE customers SET org='Заказчик №" + id + " · ПДн очищены', fio='', phone='',"
            + " address='', contact_phone='', attorney='', driver='', finance_manager='',"
            + " notes='ПДн обезличены по заявлению субъекта " + RepairsPanel.stamp() + "' WHERE id=" + id);
        return files;
    }

    private static String card(java.awt.Component owner, long id) {
        try (Statement st = Db.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT org, fio FROM customers WHERE id=" + id)) {
            return rs.next() ? (nz(rs.getString(1)) + " " + nz(rs.getString(2))).trim() : null;
        } catch (Exception ex) { Warn.show(ex.getMessage()); return null; }
    }

    private static int dropAttachments(long customerId) {
        Vector<String> paths = new Vector<>();
        int rows = 0;
        try (Statement st = Db.get().createStatement()) {
            ResultSet rs = st.executeQuery("SELECT file FROM attachments WHERE customer_id=" + customerId);
            while (rs.next()) paths.add(rs.getString(1));
            rs.close();
            rows = st.executeUpdate("DELETE FROM attachments WHERE customer_id=" + customerId);
        } catch (Exception ignored) { }
        for (String p : paths) {
            File f = new File(p);
            if (f.isFile()) try { java.nio.file.Files.delete(f.toPath()); } catch (Exception ignored) { }
        }
        return rows;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private Privacy() { }
}
