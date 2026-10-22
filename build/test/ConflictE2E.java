package psr.sc;

import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.swing.JComboBox;
import javax.swing.JTextField;

/** Проверка блока 4: конфликт сохранения (версия записи) и персональные данные. */
public class ConflictE2E {

    static int bad;

    public static void main(String[] args) throws Exception {
        clean();
        Db.exec("INSERT INTO repairs(equipment,problem,opened,rev) VALUES('ZZконф принтер','нет печати','"
            + RepairsPanel.stamp() + "',0)");
        long id = one("SELECT id FROM repairs WHERE equipment='ZZконф принтер'");
        check("новая заявка начинается с версии 0", one("SELECT rev FROM repairs WHERE id=" + id) == 0);
        check("устаревшая версия не записывается", Db.update("UPDATE repairs SET notes='чужое',"
            + " rev=rev+1 WHERE id=" + id + " AND rev=99") == 0);
        check("данные при этом не изменились", str("SELECT notes FROM repairs WHERE id=" + id).isEmpty());
        check("актуальная версия записывается", Db.update("UPDATE repairs SET notes='моё',"
            + " rev=rev+1 WHERE id=" + id + " AND rev=0") == 1);
        check("версия выросла", one("SELECT rev FROM repairs WHERE id=" + id) == 1);

        JTextField eq = new JTextField("ZZконф принтер");
        JTextField notes = new JTextField("у меня другой текст");
        JComboBox<String> status = new JComboBox<>(RepairsPanel.STATUS_RU);
        status.setSelectedItem("В работе");            // в базе заявка ещё «Открыт ремонт»
        String d = RepairForm.diff(id, new Object[][] {
            { "equipment", "Оборудование", eq }, { "notes", "Примечание", notes },
            { "status", "Статус", status } });
        check("совпавшее поле в diff не попадает", !d.contains("Оборудование"));
        check("diff нашёл примечание", d.contains("Примечание") && d.contains("у меня другой текст"));
        check("diff нашёл статус", d.contains("Статус"));
        check("числа сравниваются как числа", RepairForm.same("1500", "1500.0")
            && !RepairForm.same("1500", "1600"));

        Session.role = "ENGINEER";
        check("инженеру ФИО скрыто", "И***".equals(Privacy.fio("Иванов И.И.")));
        String ph = Privacy.phone("+7 921 123-45-67");
        check("телефон — только последние цифры", ph.contains("***") && ph.endsWith("67"));
        check("город виден, улица нет", "г. Тула ***".equals(Privacy.address("г. Тула, ул. Ленина, 5")));
        check("пустое не превращается в звёздочки", Privacy.fio("").isEmpty()
            && Privacy.address(null).isEmpty());
        Session.role = "ADMIN";
        check("администратор видит данные целиком", "Иванов И.И.".equals(Privacy.fio("Иванов И.И.")));

        Db.exec("INSERT INTO customers(org,fio,phone,address,notes)"
            + " VALUES('ZZконф ООО','Петров П.П.','+7 900 111-22-33','г. Тула, ул. Мира, 1','договорённость')");
        long cid = one("SELECT id FROM customers WHERE org='ZZконф ООО'");
        File scan = new File(Db.DATA_DIR, "attaches/ZZпдн.txt");
        scan.getParentFile().mkdirs();
        Files.write(scan.toPath(), "скан паспорта".getBytes("UTF-8"));
        Db.exec("INSERT INTO attachments(customer_id,kind,file,added) VALUES(" + cid
            + ",'Скан','" + Db.esc(scan.getPath()) + "','" + RepairsPanel.stamp() + "')");
        int dropped = Privacy.anonymize(cid);
        check("вложение убрано из карточки", dropped == 1
            && count("SELECT COUNT(*) FROM attachments WHERE customer_id=" + cid) == 0);
        check("файл скана удалён с диска", !scan.exists());
        check("организация обезличена", str("SELECT org FROM customers WHERE id=" + cid)
            .contains("ПДн очищены"));
        check("ФИО и телефон стёрты", str("SELECT fio FROM customers WHERE id=" + cid).isEmpty()
            && str("SELECT phone FROM customers WHERE id=" + cid).isEmpty());
        check("причина очистки записана", str("SELECT notes FROM customers WHERE id=" + cid)
            .contains("ПДн обезличены"));
        check("резервная копия создана перед очисткой",
            new File(Db.DATA_DIR, "backups").listFiles().length > 0);

        clean();
        System.out.println(bad == 0 ? "OK: конфликт сохранения и ПДн работают" : "ОШИБОК: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    static void clean() throws Exception {
        Db.exec("DELETE FROM repairs WHERE equipment='ZZконф принтер'");
        Db.exec("DELETE FROM customers WHERE org='ZZконф ООО' OR org LIKE 'Заказчик №%ПДн очищены'");
        Db.exec("DELETE FROM repair_history WHERE info LIKE 'ZZконф%'");
    }

    static int count(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    static long one(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    static String str(String sql) throws Exception {
        try (Statement st = Db.get().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? RepairsPanel.nz(rs.getString(1)) : "";
        }
    }

    static void check(String what, boolean ok) {
        if (!ok) { bad++; System.out.println("ПРОВАЛ: " + what); }
        else System.out.println("ok    : " + what);
    }
}
