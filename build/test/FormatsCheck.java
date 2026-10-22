import psr.sc.Table;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Проверка читателя форматов на реальных файлах и на специально собранных образцах. */
public class FormatsCheck {

    static int bad = 0;

    public static void main(String[] a) throws Exception {
        // папка с выгрузками 1С для живых проверок; нет файлов — считаются пропущенными, образцы свои
        String dir = a.length > 0 ? a[0] : System.getProperty("user.home") + "/Desktop/базы";
        oneCXml(new File(dir, "пример_1.xml"));
        binaryXls(new File(dir, "пример_2.xls"));
        binaryXls(new File(dir, "маленькая_42_25.bin"));
        csvCp1251();
        csvUtf16();
        xlsxReal();
        spreadsheetMl();
        htmlTable();
        System.out.println(bad == 0 ? "OK: читатель форматов" : "ОШИБОК: " + bad);
        if (bad > 0) System.exit(1);
    }

    static void oneCXml(File f) throws Exception {
        if (!f.isFile()) { System.out.println("skip  : нет файла " + f); return; }
        Table t = Table.read(f, "номенклатура", "товар", "артикул");
        check(t.rows.size() > 3000, "1С XML: строк " + t.rows.size());
        check(t.origin.contains("Номенклатура"), "1С XML: выбрана группа " + t.origin);
        int desc = idx(t, "Description"), unit = idx(t, "ЕдиницаИзмерения"), code = idx(t, "Code");
        check(desc >= 0 && unit >= 0 && code >= 0, "1С XML: поля Description/ЕдиницаИзмерения/Code");
        String[] r = t.rows.get(0);
        System.out.println("        1-я строка: " + Arrays.toString(r));
        check(Table.cell(r, unit).length() != 36, "1С XML: UUID единицы заменён на имя («" + Table.cell(r, unit) + "»)");
    }

    /** 1С часто пишет CSV в UTF-16 LE без BOM — там каждый второй байт «\0», читатель не должен спотыкаться. */
    static void csvUtf16() throws Exception {
        File f = File.createTempFile("psr-", ".csv");
        String body = "Организация;Телефон;ИНН\r\nООО Ромашка;+7 900 111-22-33;7712345678\r\n";
        Files.write(f.toPath(), body.getBytes("UTF-16LE"));
        Table t = Table.read(f, "заказчик", "организация");
        check(t.rows.size() == 1, "CSV UTF-16 без BOM: строк " + t.rows.size());
        check(Table.cell(t.rows.get(0), 0).equals("ООО Ромашка"),
            "CSV UTF-16 без BOM: кириллица («" + Table.cell(t.rows.get(0), 0) + "»)");
        f.delete();
    }

    static void binaryXls(File f) throws Exception {
        if (!f.isFile()) { System.out.println("skip  : нет файла " + f); return; }
        try {
            Table.read(f, "номенклатура");
            check(false, "бинарный .xls должен отклоняться с инструкцией");
        } catch (Exception e) {
            check(e.getMessage() != null && e.getMessage().contains("xlsx"),
                "бинарный .xls → понятная инструкция (первые 60 симв.: "
                    + e.getMessage().substring(0, Math.min(60, e.getMessage().length())) + ")");
        }
    }

    static void csvCp1251() throws Exception {
        File f = File.createTempFile("psr-", ".csv");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("windows-1251"))) {
            w.write("Отчёт о номенклатуре\r\n");
            w.write("Наименование;Артикул;Штрихкод;Ед.изм.;Цена;Остаток\r\n");
            w.write("\"Вал магнитный CB435\";Арт-1;460000000001;шт;1 250,50;3\r\n");
            w.write("Тонер CF276A, 100 г;Арт-2;;шт;980,00;12\r\n");
            w.write("Всего: 2 строки\r\n");
        }
        Table t = Table.read(f, "артикул", "штрихкод");
        check(t.headers.size() == 6, "CSV cp1251: заголовков " + t.headers.size() + " " + t.headers);
        check(t.rows.size() == 2, "CSV cp1251: строк " + t.rows.size() + " (строка «Всего» отброшена)");
        check(Table.cell(t.rows.get(0), 0).equals("Вал магнитный CB435"),
            "CSV cp1251: кириллица прочитана («" + Table.cell(t.rows.get(0), 0) + "»)");
        f.delete();
    }

    static void xlsxReal() throws Exception {
        File f = File.createTempFile("psr-", ".xlsx");
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f));
        put(z, "[Content_Types].xml",
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
        put(z, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"x\" Target=\"xl/workbook.xml\"/></Relationships>");
        put(z, "xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rIdS\" Type=\"x\" Target=\"/xl/worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rIdT\" Type=\"x\" Target=\"sharedStrings.xml\"/></Relationships>");
        put(z, "xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
            + "<sheet name=\"Заявки\" sheetId=\"1\" r:id=\"rIdS\"/></sheets></workbook>");
        put(z, "xl/styles.xml", "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"dd.mm.yyyy\"/></numFmts>"
            + "<cellXfs count=\"2\"><xf numFmtId=\"0\"/><xf numFmtId=\"164\"/></cellXfs></styleSheet>");
        put(z, "xl/sharedStrings.xml", "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"5\" uniqueCount=\"5\">"
            + "<si><t>№ заявки</t></si><si><t><r><t>Откры</t></r><r><t>та</t></r></t></si>"
            + "<si><t>Заказчик</t></si><si><t>HP LaserJet 1010</t></si><si><t>ООО Ромашка</t></si></sst>");
        put(z, "xl/worksheets/sheet1.xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
            + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c></row>"
            + "<row r=\"2\"><c r=\"A2\"><v>45356</v></c><c r=\"B2\" s=\"1\"><v>45356</v></c>"
            + "<c r=\"C2\" t=\"s\"><v>3</v></c><c r=\"E2\" t=\"inlineStr\"><is><t>пропуск столбца D</t></is></c>"
            + "<c r=\"F2\"><v>1234567890123</v></c></row>"
            + "<row r=\"3\"><c r=\"A3\"><v>1</v></c><c r=\"C3\" t=\"s\"><v>4</v></c></row>"
            + "</sheetData></worksheet>");
        z.close();
        Table t = Table.read(f, "заявка", "заказчик");
        check(t.origin.equals("Заявки"), "XLSX: лист «" + t.origin + "»");
        check(t.headers.size() == 6, "XLSX: заголовков " + t.headers.size() + " " + t.headers);
        check(t.headers.get(1).equals("Открыта"), "XLSX: rich-text «" + t.headers.get(1) + "» склеен");
        check(Table.cell(t.rows.get(0), 1).equals("05.03.2024"),
            "XLSX: дата по стилю → «" + Table.cell(t.rows.get(0), 1) + "»");
        check(Table.cell(t.rows.get(0), 0).equals("45356"), "XLSX: число без стиля осталось числом");
        check(Table.cell(t.rows.get(0), 5).equals("1234567890123"),
            "XLSX: штрихкод не ушёл в экспоненту («" + Table.cell(t.rows.get(0), 5) + "»)");
        check(Table.cell(t.rows.get(0), 4).equals("пропуск столбца D"), "XLSX: пустой столбец D пропущен, E на месте");
        f.delete();
    }

    static void spreadsheetMl() throws Exception {
        File f = File.createTempFile("psr-", ".xls");
        String xml = "<?xml version=\"1.0\"?><Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" "
            + "xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"><Worksheet ss:Name=\"Лист1\"><Table>"
            + "<Row><Cell><Data ss:Type=\"String\">Организация</Data></Cell><Cell><Data ss:Type=\"String\">Телефон</Data></Cell></Row>"
            + "<Row><Cell><Data ss:Type=\"String\">ООО &#1040;&#1041;&#1056;&#1045;&#1042;&#1045;&#1057;&#1058;&#1067;</Data></Cell>"
            + "<Cell><Data ss:Type=\"String\">+7 900 111-22-33</Data></Cell></Row>"
            + "</Table></Worksheet></Workbook>";
        Files.write(f.toPath(), xml.getBytes("windows-1251"));
        Table t = Table.read(f, "заказчик", "контрагент");
        check(t.rows.size() == 1, "Excel XML: строк " + t.rows.size());
        check(Table.cell(t.rows.get(0), 0).equals("ООО АБРЕВЕСТЫ"),
            "Excel XML: коды символов прочитаны («" + Table.cell(t.rows.get(0), 0) + "»)");
        f.delete();
    }

    static void htmlTable() throws Exception {
        File f = File.createTempFile("psr-", ".html");
        String h = "<html><body><table><tr><th>ФИО</th><th>Телефон</th></tr>"
            + "<tr><td>Иванов И.И.</td><td>123</td></tr><tr><td>Петров</td><td>456</td></tr></table></body></html>";
        Files.write(f.toPath(), h.getBytes("UTF-8"));
        Table t = Table.read(f, "заказчик", "фио");
        check(t.rows.size() == 2, "HTML: строк " + t.rows.size());
        check(t.headers.get(0).equals("ФИО"), "HTML: заголовки " + t.headers);
        f.delete();
    }

    static void put(ZipOutputStream z, String name, String body) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(body.getBytes("UTF-8"));
        z.closeEntry();
    }

    static int idx(Table t, String header) {
        for (int i = 0; i < t.headers.size(); i++) if (t.headers.get(i).equalsIgnoreCase(header)) return i;
        return -1;
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok    : " : "ПРОВАЛ: ") + what);
        if (!ok) bad++;
    }
}
