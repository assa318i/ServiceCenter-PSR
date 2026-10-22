package psr.sc;

import java.io.File;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Чтение чужих выгрузок в один вид «заголовки + строки»: CSV (разделитель и кодировка
 * определяются по файлу), XLSX, XML (1С «обмен данными», Excel 2003 XML, любая таблица
 * из повторяющихся элементов) и HTML-таблицы. Бинарный .xls (Excel 97-2003) не читается —
 * файл такого формата опознаётся по сигнатуре и пользователь получает инструкцию.
 */
public final class Table {

    /** Больше ста тысяч строк в базу сервиса не помещается по смыслу; ограничение — от зависшего UI. */
    static final int CAP = 100000;

    static final String XLS_HINT =
        "Это Excel 97-2003 (.xls) — старый бинарный формат, программа его не читает.\n"
        + "Пересохраните файл одним из способов:\n"
        + "· 1С: «Файл → Сохранить как» → «Excel 2007+ (*.xlsx)»;\n"
        + "· 1С: «Выгрузить все данные всех таблиц» → .xml или .csv;\n"
        + "· Excel / LibreOffice: «Сохранить как» → .xlsx или «Текст CSV (разделители - «;»)».\n"
        + "Программа читает .xlsx, .csv, .txt, .xml и HTML-таблицы.";

    public final String origin;
    public final List<String> headers;
    public final List<String[]> rows;

    Table(String origin, List<String> headers, List<String[]> rows) {
        this.origin = origin;
        this.headers = headers;
        this.rows = rows;
    }

    /** Главный вход: формат определяется по сигнатуре байтов, а не по расширению файла. */
    public static Table read(File f, String... prefer) throws Exception {
        byte[] raw = Files.readAllBytes(f.toPath());
        if (raw.length == 0) throw new Exception("Файл пуст: " + f.getName());
        if (mark(raw, 0x50, 0x4B, 0x03, 0x04)) return xlsx(f, prefer);
        if (mark(raw, 0xD0, 0xCF, 0x11, 0xE0)) throw new Exception(XLS_HINT);
        String text = decode(raw);
        // нулевой байт в тексте = бинарный файл чужой программы (в UTF-16 он «съедается» декодированием)
        if (text.indexOf('\u0000') >= 0) throw new Exception("Файл «" + f.getName()
            + "» — закрытый бинарный формат другой программы, прочитать его как таблицу нельзя.\n"
            + "Экспортируйте базу в .xlsx, .csv или .xml — эти форматы программа читает.");
        String head = text.substring(0, Math.min(text.length(), 8000)).toLowerCase();
        if (head.contains("<?xml") || head.contains("<html") || head.contains("<table")) {
            if (text.contains("schemas-microsoft-com:office:spreadsheet")) return sheetXml(text, f.getName());
            if (head.contains("<html") || head.contains("<table")) return html(text);
            return objectXml(text, prefer);
        }
        return csv(text, f.getName());
    }

    public static String cell(String[] row, int i) {
        return row == null || i < 0 || i >= row.length || row[i] == null ? "" : row[i];
    }

    // ─────────────────────────────────── кодировки ───────────────────────────────────

    /** UTF-8 (в том числе с BOM), UTF-16, иначе — системная кириллица windows-1251. */
    static String decode(byte[] raw) {
        if (raw.length >= 2 && raw[0] == (byte) 0xFF && raw[1] == (byte) 0xFE)
            return strip(new String(raw, 2, raw.length - 2, StandardCharsets.UTF_16LE));
        if (raw.length >= 2 && raw[0] == (byte) 0xFE && raw[1] == (byte) 0xFF)
            return strip(new String(raw, 2, raw.length - 2, StandardCharsets.UTF_16BE));
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF)
            return new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        if (utf16Le(raw)) return new String(raw, StandardCharsets.UTF_16LE);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(raw, Charset.forName("windows-1251"));
        }
    }

    private static boolean utf16Le(byte[] raw) {
        int n = Math.min(raw.length, 512), zeros = 0;
        for (int i = 1; i < n; i += 2) if (raw[i] == 0) zeros++;
        return n >= 32 && zeros * 2 > n / 2;
    }

    private static String strip(String s) {
        return s.length() > 0 && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }

    private static boolean mark(byte[] b, int... signature) {
        if (b.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((b[i] & 0xFF) != signature[i]) return false;
        return true;
    }

    /** Сличение заголовков: регистр, «ё» и пробелы/пунктуация не важны; «№» = «номер». */
    static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase().replace('ё', 'е').replace("№", "номер");
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static XMLStreamReader open(String text) throws Exception {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        return xif.createXMLStreamReader(new java.io.StringReader(text));
    }

    /** StAX запрещает getLocalName() на CHARACTERS — имя спрашиваем только у начала и конца тега. */
    private static String nameAt(XMLStreamReader r, int event) {
        return event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.END_ELEMENT
            ? r.getLocalName() : "";
    }

    // ─────────────────────────────────── CSV ───────────────────────────────────

    static Table csv(String text, String name) {
        char d = delimiter(text);
        List<List<String>> lines = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length() && lines.size() < CAP; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == d) {
                cur.add(cell.toString().trim()); cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                cur.add(cell.toString().trim()); cell.setLength(0);
                lines.add(cur); cur = new ArrayList<>();
            } else cell.append(c);
        }
        cur.add(cell.toString().trim());
        if (!cur.isEmpty()) lines.add(cur);

        List<String[]> rows = new ArrayList<>();
        for (List<String> l : lines) {
            String[] r = l.toArray(new String[0]);
            if (blank(r) || footer(r)) continue;
            rows.add(r);
        }
        if (rows.isEmpty()) throw new EmptyTable(name + ": в файле нет ни одной строки с данными");
        return shaped(name + " · CSV, разделитель «" + d + "»", rows);
    }

    /**
     * Заголовок таблицы — первая строка с двумя и более непустыми ячейками: 1С и Excel ставят
     * выше название отчёта одной ячейкой, принимать его за имена столбцов нельзя.
     */
    static int headerRow(List<String[]> rows) {
        for (int i = 0; i < rows.size() && i < 10; i++) {
            int filled = 0;
            for (String s : rows.get(i)) if (s != null && !s.isEmpty()) filled++;
            if (filled >= 2) return i;
        }
        return 0;
    }

    /** Разделитель берётся по первой строке: у 1С это «;», у Excel по-русски «;», по-английски «,». */
    private static char delimiter(String text) {
        String line = text.split("\r?\n", 2)[0];
        if (line.length() > 500) line = line.substring(0, 500);
        char best = ';';
        int bestN = count(line, ';');
        int tabs = count(line, '\t');
        if (tabs > bestN) { best = '\t'; bestN = tabs; }
        int commas = count(line, ',');
        if (commas > bestN) best = ',';
        return best;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /** Строки-итоги 1С-выгрузки («Всего: 3224 строки») и пустые разделители в мусор не превращаем. */
    private static boolean footer(String[] r) {
        int filled = 0;
        String only = "";
        for (String s : r) if (s != null && !s.isEmpty()) { filled++; only = s; }
        if (filled != 1) return false;
        String t = norm(only);
        return t.startsWith("итого") || t.startsWith("всего") || t.startsWith("строк") || t.startsWith("кон");
    }

    private static boolean blank(String[] r) {
        for (String s : r) if (s != null && !s.isEmpty()) return false;
        return true;
    }

    static String[] pad(String[] r, int w) {
        if (r.length == w) {
            for (int i = 0; i < w; i++) if (r[i] == null) r[i] = "";
            return r;
        }
        String[] x = new String[w];
        System.arraycopy(r, 0, x, 0, Math.min(r.length, w));
        for (int i = 0; i < w; i++) if (x[i] == null) x[i] = "";
        return x;
    }

    // ─────────────────────────────────── XLSX ───────────────────────────────────

    static Table xlsx(File f, String... prefer) throws Exception {
        List<Table> sheets = new ArrayList<>();
        try (ZipFile z = new ZipFile(f)) {
            List<String> shared = shared(z);
            Set<Integer> dates = dateStyles(z);
            Map<String, String> rels = rels(z);
            for (String[] s : book(z, rels)) {
                List<String[]> cells = sheetCells(z, s[1], shared, dates);
                if (cells.isEmpty()) continue;
                try { sheets.add(grid(s[0], cells)); }
                catch (EmptyTable onlyTotals) { continue; }
            }
        }
        if (sheets.isEmpty()) throw new EmptyTable(f.getName() + ": в книге нет непустых листов");
        return best(sheets, prefer);
    }

    /** Строка с ≥2 заполненными ячейками — заголовки; всё выше неё (название отчёта) выбрасываем. */
    static Table grid(String origin, List<String[]> cells) {
        List<String[]> rows = new ArrayList<>();
        for (String[] r : cells) if (!blank(r) && !footer(r)) rows.add(r);
        if (rows.isEmpty()) throw new EmptyTable(origin + ": лист пуст");
        return shaped(origin, rows);
    }

    /** Общий хвост для табличных форматов: сдвиг на строку заголовков, одинаковая ширина, пустые имена. */
    private static Table shaped(String origin, List<String[]> rows) {
        int w = 0;
        for (String[] r : rows) w = Math.max(w, r.length);
        for (int drop = headerRow(rows); drop > 0 && !rows.isEmpty(); drop--) rows.remove(0);
        List<String> headers = new ArrayList<>();
        String[] head = pad(rows.remove(0), w);
        for (int i = 0; i < w; i++) headers.add(head[i].isEmpty() ? "Столбец " + (i + 1) : head[i]);
        List<String[]> body = new ArrayList<>();
        for (String[] r : rows) body.add(pad(r, w));
        return new Table(origin, headers, body);
    }

    private static List<String> shared(ZipFile z) throws Exception {
        List<String> out = new ArrayList<>();
        ZipEntry e = z.getEntry("xl/sharedStrings.xml");
        if (e == null) return out;
        XMLStreamReader r = open(read(z, e));
        StringBuilder sb = new StringBuilder();
        boolean inT = false, inRph = false;
        while (r.hasNext()) {
            int ev = r.next();
            String n = nameAt(r, ev);
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (n.equals("si")) sb.setLength(0);
                else if (n.equals("rPh")) inRph = true;   // фонетические разборки в текст не входят
                else if (n.equals("t") && !inRph) inT = true;
            } else if (ev == XMLStreamConstants.CHARACTERS && inT) sb.append(r.getText());
            else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (n.equals("t")) inT = false;
                else if (n.equals("rPh")) inRph = false;
                else if (n.equals("si")) out.add(sb.toString().trim());
            }
        }
        r.close();
        return out;
    }

    /** Номера стилей, у которых формат — дата: без этого «05.03.2024» приедет числом 45356. */
    private static Set<Integer> dateStyles(ZipFile z) throws Exception {
        Set<Integer> out = new HashSet<>();
        ZipEntry e = z.getEntry("xl/styles.xml");
        if (e == null) return out;
        Map<Integer, String> custom = new HashMap<>();
        XMLStreamReader r = open(read(z, e));
        boolean inCellXfs = false;
        int xfIndex = 0;
        while (r.hasNext()) {
            int ev = r.next();
            String n = nameAt(r, ev);
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (n.equals("numFmt")) {
                    custom.put(num(r.getAttributeValue(null, "numFmtId")), r.getAttributeValue(null, "formatCode"));
                } else if (n.equals("cellXfs")) inCellXfs = true;

                else if (n.equals("xf")) {
                    if (inCellXfs) {
                        int id = num(r.getAttributeValue(null, "numFmtId"));
                        String code = custom.get(id);
                        if (BUILTIN_DATES.contains(id) || (code != null && isDateFormat(code))) out.add(xfIndex);
                        xfIndex++;
                    }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (n.equals("cellXfs")) inCellXfs = false;
            }
        }
        r.close();
        return out;
    }

    private static final Set<Integer> BUILTIN_DATES = new HashSet<>(Arrays.asList(
        14, 15, 16, 17, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        45, 46, 47, 50, 51, 52, 53, 54, 55, 56, 57, 58));

    /** «г-мм-дд», «[h]:мм» — да; «#,##0.00" ₽"» — нет. Литералы и скобки сначала вырезаются. */
    static boolean isDateFormat(String code) {
        StringBuilder sb = new StringBuilder();
        boolean literal = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"') { literal = !literal; continue; }
            if (literal || c == '\\' || c == '*') continue;
            if (c == '[') { while (i < code.length() && code.charAt(i) != ']') i++; continue; }
            sb.append(Character.toLowerCase(c));
        }
        String t = sb.toString();
        boolean md = t.indexOf('m') >= 0;
        return t.indexOf('y') >= 0 || t.indexOf('d') >= 0 || t.indexOf('h') >= 0 || t.indexOf('s') >= 0
            || (md && (t.indexOf('d') >= 0 || t.indexOf('y') >= 0));
    }

    private static Map<String, String> rels(ZipFile z) throws Exception {
        Map<String, String> out = new HashMap<>();
        ZipEntry e = z.getEntry("xl/_rels/workbook.xml.rels");
        if (e == null) return out;
        XMLStreamReader r = open(read(z, e));
        while (r.hasNext()) if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("Relationship"))
            out.put(r.getAttributeValue(null, "Id"), r.getAttributeValue(null, "Target"));
        r.close();
        return out;
    }

    private static List<String[]> book(ZipFile z, Map<String, String> rels) throws Exception {
        List<String[]> out = new ArrayList<>();
        ZipEntry e = z.getEntry("xl/workbook.xml");
        if (e == null) throw new EmptyTable("В файле нет xl/workbook.xml — это не XLSX");
        XMLStreamReader r = open(read(z, e));
        while (r.hasNext()) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("sheet")) {
                String name = r.getAttributeValue(null, "name");
                String rid = null;
                for (int i = 0; i < r.getAttributeCount(); i++)
                    if (r.getAttributeLocalName(i).equals("id")) rid = r.getAttributeValue(i);
                String target = rid == null ? null : rels.get(rid);
                if (name != null && target != null) out.add(new String[] { name, sheetPath(target) });
            }
        }
        r.close();
        if (out.isEmpty()) throw new EmptyTable("В книге нет листов");
        return out;
    }

    private static String sheetPath(String target) {
        String t = target.replace('\\', '/');
        if (t.startsWith("/xl/")) return t.substring(1);
        if (t.startsWith("xl/")) return t;
        return "xl/" + (t.startsWith("/") ? t.substring(1) : t);
    }

    private static List<String[]> sheetCells(ZipFile z, String entry, List<String> shared, Set<Integer> dates)
        throws Exception {
        ZipEntry e = z.getEntry(entry);
        if (e == null) return new ArrayList<>();
        List<String[]> out = new ArrayList<>();
        XMLStreamReader r = open(read(z, e));
        List<String> row = null;
        int rowWidth = 0, seq = 0;
        String type = null, ref = null;
        int style = -1;
        StringBuilder v = new StringBuilder(), inline = new StringBuilder();
        boolean inV = false, inT = false;
        while (r.hasNext() && out.size() < CAP) {
            int ev = r.next();
            String n = nameAt(r, ev);
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (n.equals("row")) { row = new ArrayList<>(); rowWidth = 0; seq = 0; }
                else if (n.equals("c")) {
                    type = r.getAttributeValue(null, "t");
                    ref = r.getAttributeValue(null, "r");
                    style = num(r.getAttributeValue(null, "s"), -1);
                    v.setLength(0); inline.setLength(0);
                } else if (n.equals("v")) inV = true;
                else if (n.equals("t")) inT = true;
            } else if (ev == XMLStreamConstants.CHARACTERS) {
                if (inV) v.append(r.getText());
                else if (inT) inline.append(r.getText());
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (n.equals("v")) inV = false;
                else if (n.equals("t")) inT = false;
                else if (n.equals("c") && row != null) {
                    int at = ref == null ? seq : col(ref);
                    seq = at + 1;
                    while (row.size() < at) row.add("");
                    if (row.size() == at) row.add(cellValue(type, v.toString(), inline.toString(), shared, dates, style));
                    rowWidth = Math.max(rowWidth, row.size());
                } else if (n.equals("row") && row != null) {
                    out.add(pad(row.toArray(new String[0]), rowWidth));
                    row = null;
                }
            }
        }
        r.close();
        return out;
    }

    private static String cellValue(String type, String v, String inline, List<String> shared,
                                    Set<Integer> dates, int style) {
        String t = type == null ? "" : type;
        if (t.equals("s")) {
            int i = num(v, -1);
            return i >= 0 && i < shared.size() ? shared.get(i) : "";
        }
        if (t.equals("inlineStr")) return inline.trim();
        if (t.equals("str") || t.equals("e")) return v.trim();
        if (t.equals("b")) return v.trim().equals("1") ? "да" : "нет";
        if (v.trim().isEmpty()) return "";
        if (style >= 0 && dates.contains(style)) return excelDate(v);
        try { return new BigDecimal(v.trim()).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException notNumber) { return v.trim(); }
    }

    /** Excel считает дни от 1899-12-30; считаем в UTC, чтобы пояс не сдвигал дату на сутки. */
    private static String excelDate(String v) {
        try {
            double d = Double.parseDouble(v.trim());
            long ms = Math.round((d - 25569d) * 86400000d);
            boolean midnight = Math.abs(d - Math.floor(d)) < 1e-6;
            SimpleDateFormat f = new SimpleDateFormat(midnight ? "dd.MM.yyyy" : "dd.MM.yyyy HH:mm");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.format(new Date(ms));
        } catch (NumberFormatException bad) { return v.trim(); }
    }

    private static int col(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = Character.toUpperCase(ref.charAt(i));
            if (c < 'A' || c > 'Z') break;
            n = n * 26 + (c - 'A' + 1);
        }
        return Math.max(0, n - 1);
    }

    private static String read(ZipFile z, ZipEntry e) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        try (java.io.InputStream in = z.getInputStream(e)) {
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return decode(bos.toByteArray());
    }

    private static int num(String s) { return num(s, 0); }

    private static int num(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    // ───────────────────────────── XML выгрузок ─────────────────────────────

    /**
     * Произвольный XML: «строка» = элемент, у которого есть дети-элементы; поля — имена этих
     * детей и атрибуты. Так разбирается и 1С «обмен данными» (CatalogObject.Номенклатура), и
     * CRM-выгрузки (<client><name>…</name></client>), и собственный XML программы (Repair, Customer).
     * Группы собираются все, выбирается подходящая под импортируемую базу.
     */
    static Table objectXml(String text, String... prefer) throws Exception {
        XMLStreamReader r = open(text);
        Deque<Ctx> stack = new ArrayDeque<>();
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                Ctx p = stack.peek();
                stack.push(new Ctx(r.getName().getLocalPart(), p == null ? "" : p.path, r));
            } else if (ev == XMLStreamConstants.CHARACTERS) {
                Ctx top = stack.peek();
                if (top != null) top.text.append(r.getText());
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                Ctx top = stack.pop();
                if (top.children > 0) {
                    if (top.fields.size() > 0)
                        groups.computeIfAbsent(top.path + "/" + top.name, k -> new ArrayList<>()).add(top.fields);
                } else if (!stack.isEmpty()) {
                    Ctx p = stack.peek();
                    p.children++;
                    p.field(top.name, top.text.toString().trim());
                }
            }
        }
        r.close();
        List<Table> c = new ArrayList<>();
        Map<String, String> byUuid = uuidNames(groups);
        for (Map.Entry<String, List<Map<String, String>>> g : groups.entrySet()) {
            List<Map<String, String>> rows = g.getValue();
            if (rows.isEmpty()) continue;
            c.add(rowsTable(g.getKey(), rows, byUuid));
        }
        if (c.isEmpty()) throw new EmptyTable("В XML не найдено ни одной таблицы из повторяющихся записей");
        return best(c, prefer);
    }

    /** Одна и та же группа может встретиться под разными родителем — имя для отчёта берём без пути. */
    private static Table rowsTable(String key, List<Map<String, String>> rows, Map<String, String> byUuid) {
        String origin = key.substring(key.lastIndexOf('/') + 1);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Map<String, String> m : rows) names.addAll(m.keySet());
        List<String> headers = new ArrayList<>(names);
        List<String[]> body = new ArrayList<>();
        int n = 0;
        for (Map<String, String> m : rows) {
            String[] r = new String[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                String v = m.get(headers.get(i));
                if (v == null) v = "";
                if (isUuid(v)) v = resolve(v, byUuid);
                r[i] = v;
            }
            body.add(r);
            if (++n >= CAP) break;
        }
        return new Table(origin, headers, body);
    }

    /**
     * 1С в полях-ссылках кладёт UUID (единица измерения, контрагент, родитель). Чтобы импорт
     * показывал «шт», а не «a70e1e78-…», собираем по всем объектам файл «uuid → наименование».
     */
    private static Map<String, String> uuidNames(Map<String, List<Map<String, String>>> groups) {
        Map<String, String> out = new HashMap<>();
        String[] idKeys = { "ref", "ссылка", "ссылкаитд", "ид", "uuid", "uniqueid", "уникальныйидентификатор", "id" };
        String[] nameKeys = { "description", "наименование", "наименованиеполное", "имя", "name", "title",
            "имяпредопределенныхданных", "описание", "value", "код" };
        for (List<Map<String, String>> rows : groups.values()) {
            for (Map<String, String> m : rows) {
                String id = first(m, idKeys);
                if (id == null || !isUuid(id)) continue;
                String name = first(m, nameKeys);
                if (name != null && !name.isEmpty()) out.put(id.toLowerCase(), name);
            }
        }
        return out;
    }

    private static String first(Map<String, String> m, String... keys) {
        for (Map.Entry<String, String> e : m.entrySet()) {
            String k = norm(e.getKey());
            for (String want : keys) if (k.equals(want) || k.endsWith(want)) {
                if (!e.getValue().isEmpty()) return e.getValue();
            }
        }
        return null;
    }

    private static String resolve(String uuid, Map<String, String> byUuid) {
        String u = uuid.toLowerCase();
        if (u.startsWith("00000000-0000")) return "";
        String name = byUuid.get(u);
        return name == null ? "" : name;
    }

    static boolean isUuid(String s) {
        if (s == null || s.length() != 36) return false;
        for (int i = 0; i < 36; i++) {
            char c = s.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) { if (c != '-') return false; }
            else if (Character.digit(c, 16) < 0) return false;
        }
        return true;
    }

    private static final class Ctx {
        final String name, path;
        final Map<String, String> fields = new LinkedHashMap<>();
        final StringBuilder text = new StringBuilder();
        int children;

        Ctx(String name, String parentPath, XMLStreamReader r) {
            this.name = name;
            this.path = parentPath.isEmpty() ? name : parentPath + "/" + name;
            for (int i = 0; i < r.getAttributeCount(); i++) {
                String an = r.getAttributeLocalName(i);
                if (an.equals("type") && r.getAttributeNamespace(i).contains("xsi")) continue;
                fields.put(an, r.getAttributeValue(i));
            }
        }

        void field(String n, String value) {
            String old = fields.get(n);
            fields.put(n, old == null || old.isEmpty() ? value : old + ", " + value);
        }
    }

    /** Excel 2003 XML (1С часто сохраняет «таблицу» именно так) — колонки по позиции, не по именам. */
    static Table sheetXml(String text, String name) throws Exception {
        XMLStreamReader r = open(text);
        List<Table> sheets = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        List<String> row = null;
        String sheet = name;
        int at = 0;
        StringBuilder data = new StringBuilder();
        boolean inData = false;
        while (r.hasNext()) {
            int ev = r.next();
            String n = nameAt(r, ev);
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (n.equals("Worksheet")) { sheet = attr(r, "Name", name); rows = new ArrayList<>(); }
                else if (n.equals("Row")) { row = new ArrayList<>(); at = 0; }
                else if (n.equals("Cell")) {
                    if (row != null) {
                        int i = numInt(attr(r, "Index", "0"));
                        if (i > 0) at = i - 1;
                    }
                } else if (n.equals("Data")) { inData = true; data.setLength(0); }
            } else if (ev == XMLStreamConstants.CHARACTERS && inData) data.append(r.getText());
            else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (n.equals("Data")) {
                    inData = false;
                    if (row != null) {
                        while (row.size() < at) row.add("");
                        if (row.size() == at) row.add(data.toString().trim());
                        at++;
                    }
                } else if (n.equals("Row") && row != null) {
                    rows.add(pad(row.toArray(new String[0]), row.size()));
                    row = null;
                } else if (n.equals("Worksheet") && !rows.isEmpty()) {
                    try { sheets.add(grid(sheet, rows)); }
                    catch (EmptyTable onlyTotals) { }
                    rows = new ArrayList<>();
                }
            }
        }
        r.close();
        if (sheets.isEmpty()) throw new EmptyTable(name + ": в Excel XML нет данных");
        return sheets.size() == 1 ? sheets.get(0) : best(sheets, new String[0]);
    }

    /** HTML-таблица: то, что открывают из браузеров и веб-CRM; берём таблицу с наибольшим числом строк. */
    static Table html(String text) {
        List<List<String[]>> tables = new ArrayList<>();
        int p = 0;
        while (true) {
            int t = find(text, "<table", p);
            if (t < 0) break;
            int end = find(text, "</table", t);
            if (end < 0) break;
            tables.add(htmlRows(text.substring(t, end)));
            p = end + 7;
        }
        List<String[]> best = null;
        for (List<String[]> x : tables) if (best == null || x.size() > best.size()) best = x;
        if (best == null || best.isEmpty()) throw new EmptyTable("В HTML нет таблицы с данными");
        return grid("HTML-таблица", best);
    }

    private static List<String[]> htmlRows(String chunk) {
        List<String[]> out = new ArrayList<>();
        int p = 0;
        while (out.size() < CAP) {
            int tr = find(chunk, "<tr", p);
            if (tr < 0) break;
            int end = find(chunk, "</tr", tr);
            if (end < 0) break;
            List<String> cells = new ArrayList<>();
            int q = tr;
            while (true) {
                int td = findAny(chunk, q, "<td", "<th");
                if (td < 0 || td > end) break;
                int close = findAny(chunk, td, "</td", "</th");
                if (close < 0 || close > end) break;
                cells.add(entities(tags(chunk.substring(td, close))));
                q = close + 5;
            }
            if (!cells.isEmpty()) out.add(pad(cells.toArray(new String[0]), cells.size()));
            p = end + 5;
        }
        return out;
    }

    private static int find(String s, String needle, int from) {
        return indexOfIgnoreCase(s, needle, from);
    }

    private static int findAny(String s, int from, String... needles) {
        int best = -1;
        for (String n : needles) {
            int i = indexOfIgnoreCase(s, n, from);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        String low = s.toLowerCase();
        return low.indexOf(needle.toLowerCase(), from);
    }

    private static String tags(String html) {
        StringBuilder sb = new StringBuilder();
        int p = 0;
        while (p < html.length()) {
            int lt = html.indexOf('<', p);
            if (lt < 0) { sb.append(html.substring(p)); break; }
            sb.append(html, p, lt);
            int gt = html.indexOf('>', lt);
            p = gt < 0 ? html.length() : gt + 1;
        }
        return sb.toString().trim();
    }

    private static String entities(String s) {
        String t = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"");
        int p;
        while ((p = t.indexOf("&#")) >= 0) {
            int semi = t.indexOf(';', p);
            if (semi < 0) break;
            String body = t.substring(p + 2, semi);
            try {
                int cp = body.startsWith("x") || body.startsWith("X")
                    ? Integer.parseInt(body.substring(1), 16) : Integer.parseInt(body);
                t = t.substring(0, p) + (char) cp + t.substring(semi + 1);
            } catch (Exception bad) { t = t.substring(0, p) + t.substring(semi + 1); }
        }
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String attr(XMLStreamReader r, String local, String def) {
        for (int i = 0; i < r.getAttributeCount(); i++)
            if (r.getAttributeLocalName(i).equals(local)) return r.getAttributeValue(i);
        return def;
    }

    private static int numInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ─────────────────────────── выбор подходящей таблицы ───────────────────────────

    /**
     * Из нескольких листов/групп берём тот, где заголовки или имя группы подходят под
     * импортируемую базу; при равенстве — самый длинный.
     */
    static Table best(List<Table> c, String... prefer) {
        String[] keys = prefer == null ? new String[0] : prefer;
        Table win = null;
        long winScore = Long.MIN_VALUE;
        for (Table t : c) {
            long score = 0;
            String origin = norm(t.origin);
            for (String k : keys) {
                String nk = norm(k);
                if (nk.isEmpty()) continue;
                if (origin.contains(nk)) score += 100000;
                for (String h : t.headers) if (norm(h).contains(nk)) { score += 50; break; }
            }
            score += t.rows.size();
            if (score > winScore) { winScore = score; win = t; }
        }
        return win;
    }

    /** Ошибка, текст которой показывается пользователю как есть. */
    public static class EmptyTable extends RuntimeException {
        EmptyTable(String message) { super(message); }
    }
}
