package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Импорт наборов ЗЧ из файла (CSV, XLSX, XML, HTML).
 * Формат: Набор;Тип техники;Артикул ЗЧ;Название ЗЧ;Количество
 * Дубли наборов определяются по названию + типу техники.
 */
final class KitsImport {

    static final int KIT_NAME = 0, EQUIP_TYPE = 1, ARTICLE = 2, PART_NAME = 3, QTY = 4;
    static final String[] LABELS = { "Название набора", "Тип техники", "Артикул ЗЧ", "Название ЗЧ", "Количество" };

    static final String[][] KEYS = {
        { "набор", "название набора", "kit", "kitname", "название" },
        { "тип", "техника", "оборудование", "equipment", "type" },
        { "артикул", "код", "article", "sku", "partnumber" },
        { "название зч", "наименование", "деталь", "partname", "name" },
        { "количество", "колво", "qty", "число" },
    };

    static final Importer.Target TARGET = new Importer.Target() {
        @Override public String gen() { return "наборов ЗЧ"; }
        @Override public String files() { return ".xlsx, .csv, .txt, .xml или HTML-таблица"; }
        @Override public String noun() { return "позиций"; }
        @Override public String[] labels() { return LABELS; }
        @Override public String[][] keys() { return KEYS; }
        @Override public String check(int[] col) {
            return col[KIT_NAME] < 0 ? "Укажите колонку названия набора" : null;
        }
        @Override public String note() {
            return "Наборы создаются по названию и типу техники. Повторный импорт того же файла"
                + " дополняет существующие наборы новыми позициями. Пустые строки игнорируются.";
        }
        @Override public int[] apply(Table t, int[] col, boolean dry) throws Exception {
            return KitsImport.apply(t, col, dry);
        }
    };

    static void dialog(java.awt.Component parent) {
        if (!Session.edits(Roles.KITS)) {
            Warn.show("Наборы ЗЧ из файла загружает администратор, руководитель или заправщик");
            return;
        }
        Importer.dialog(parent, TARGET);
    }

    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        final Map<String, Long> kitIds = new HashMap<>();
        return Importer.each(t, col, dry,
            (row, c, isDry) -> write(row, c, kitIds, isDry));
    }

    private static int write(String[] row, int[] col, Map<String, Long> kitIds, boolean dry) throws Exception {
        String kitName = Table.cell(row, col[KIT_NAME]).trim();
        if (kitName.isEmpty()) return -1;

        String equipType = col[EQUIP_TYPE] >= 0 ? Table.cell(row, col[EQUIP_TYPE]).trim() : "";
        String article = col[ARTICLE] >= 0 ? Table.cell(row, col[ARTICLE]).trim() : "";
        String partName = col[PART_NAME] >= 0 ? Table.cell(row, col[PART_NAME]).trim() : "";

        if (partName.isEmpty() && article.isEmpty()) return -1;

        double qty = col[QTY] >= 0 ? RepairForm.parse(Table.cell(row, col[QTY])) : 1;
        if (qty <= 0) qty = 1;

        String key = kitName + "|" + equipType;
        Long kitId = kitIds.get(key);

        if (kitId == null) {
            try (Statement st = Db.get().createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM kits WHERE name='" + Db.esc(kitName)
                     + "' AND equipment_type='" + Db.esc(equipType) + "'")) {
                if (rs.next()) {
                    kitId = rs.getLong(1);
                }
            }
        }

        if (kitId == null) {
            if (!dry) {
                Db.exec("INSERT INTO kits(name,equipment_type) VALUES('" + Db.esc(kitName) + "','"
                    + Db.esc(equipType) + "')");
                try (Statement st = Db.get().createStatement();
                     ResultSet rs = st.executeQuery("SELECT id FROM kits WHERE name='" + Db.esc(kitName)
                         + "' AND equipment_type='" + Db.esc(equipType) + "' ORDER BY id DESC LIMIT 1")) {
                    if (rs.next()) kitId = rs.getLong(1);
                }
            }
            if (kitId != null) kitIds.put(key, kitId);
        }

        if (kitId == null) return -1;

        if (!dry) {
            Db.exec("INSERT INTO kit_parts(kit_id,article,name,qty) VALUES(" + kitId + ",'"
                + Db.esc(article) + "','" + Db.esc(partName) + "'," + qty + ")");
        }
        return 1;
    }
}
