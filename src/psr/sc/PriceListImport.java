package psr.sc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Импорт прайс-листа из файла (CSV, XLSX, XML, HTML).
 * Поддерживает форматы:
 * - Категория;Наименование;Цена;Ед.;Примечание
 * - №;Наименование;Цена1;Цена2;Цена3 (несколько уровней цен)
 */
final class PriceListImport {

    static final int CATEGORY = 0, NAME = 1, PRICE = 2, UNIT = 3, NOTE = 4;
    static final String[] LABELS = { "Категория", "Наименование", "Цена", "Ед. измерения", "Примечание" };

    static final String[][] KEYS = {
        { "категория", "группа", "тип", "category", "type", "group" },
        { "наименование", "название", "услуга", "работа", "name", "title", "service" },
        { "цена", "стоимость", "price", "cost" },
        { "ед", "единица", "unit" },
        { "примечание", "описание", "note", "description" },
    };

    static final Importer.Target TARGET = new Importer.Target() {
        @Override public String gen() { return "прайс-листа"; }
        @Override public String files() { return ".xlsx, .csv, .txt, .xml или HTML-таблица"; }
        @Override public String noun() { return "позиций"; }
        @Override public String[] labels() { return LABELS; }
        @Override public String[][] keys() { return KEYS; }
        @Override public String check(int[] col) {
            return col[NAME] < 0 ? "Укажите колонку наименования" : null;
        }
        @Override public String note() {
            return "Прайс-лист импортируется полностью. Повторный импорт заменяет существующие позиции."
                + " Если в файле несколько ценовых колонок, каждая создаёт отдельную запись с уровнем сложности."
                + " Пустые строки игнорируются.";
        }
        @Override public int[] apply(Table t, int[] col, boolean dry) throws Exception {
            return PriceListImport.apply(t, col, dry);
        }
    };

    static void dialog(java.awt.Component parent) {
        Importer.dialog(parent, TARGET);
    }

    static int[] apply(Table t, int[] col, boolean dry) throws Exception {
        if (!dry) {
            Db.exec("DELETE FROM price_list");
        }
        return Importer.each(t, col, dry,
            (row, c, isDry) -> write(row, c, isDry, t.headers));
    }

    private static int write(String[] row, int[] col, boolean dry, java.util.List<String> headers) throws Exception {
        String name = Table.cell(row, col[NAME]).trim();
        if (name.isEmpty()) return -1;

        String baseCategory = col[CATEGORY] >= 0 ? Table.cell(row, col[CATEGORY]).trim() : "";

        // Проверяем, есть ли несколько ценовых колонок (после NAME идут числовые колонки)
        java.util.List<Integer> priceCols = new java.util.ArrayList<>();
        for (int i = col[NAME] + 1; i < row.length; i++) {
            String val = Table.cell(row, i).trim();
            if (!val.isEmpty() && isNumeric(val)) {
                priceCols.add(i);
            }
        }

        // Если найдено несколько ценовых колонок, создаём отдельную запись для каждой
        if (priceCols.size() > 1) {
            int count = 0;
            for (int i = 0; i < priceCols.size(); i++) {
                int priceColIdx = priceCols.get(i);
                String priceStr = Table.cell(row, priceColIdx).trim();
                double price = RepairForm.parse(priceStr);
                if (price <= 0) continue;

                // Определяем уровень сложности из заголовка или по порядку
                String level = getPriceLevel(headers, priceColIdx, i);
                String category = baseCategory.isEmpty() ? level : baseCategory + " (" + level + ")";

                if (!dry) {
                    Db.exec("INSERT INTO price_list(category,name,price,unit,note) VALUES('"
                        + Db.esc(category) + "','" + Db.esc(name) + "'," + price + ",'руб.','')");
                }
                count++;
            }
            return count > 0 ? 1 : -1;
        }

        // Стандартный формат: одна цена
        String priceStr = col[PRICE] >= 0 ? Table.cell(row, col[PRICE]).trim() : "0";
        String unit = col[UNIT] >= 0 ? Table.cell(row, col[UNIT]).trim() : "руб.";
        String note = col[NOTE] >= 0 ? Table.cell(row, col[NOTE]).trim() : "";

        if (unit.isEmpty()) unit = "руб.";

        double price = RepairForm.parse(priceStr);

        if (!dry) {
            Db.exec("INSERT INTO price_list(category,name,price,unit,note) VALUES('"
                + Db.esc(baseCategory) + "','" + Db.esc(name) + "'," + price + ",'"
                + Db.esc(unit) + "','" + Db.esc(note) + "')");
        }
        return 1;
    }

    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String getPriceLevel(java.util.List<String> headers, int colIdx, int levelIdx) {
        if (colIdx < headers.size()) {
            String header = headers.get(colIdx).trim();
            if (!header.isEmpty() && !isNumeric(header)) {
                return header;
            }
        }
        // Если заголовок не подходит, используем стандартные названия
        String[] levels = { "Простой", "Средний", "Сложный" };
        return levelIdx < levels.length ? levels[levelIdx] : "Уровень " + (levelIdx + 1);
    }
}
