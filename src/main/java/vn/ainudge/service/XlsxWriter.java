package vn.ainudge.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tạo file Excel (.xlsx) thật, không cần thư viện ngoài.
 * Mọi ô đều là chữ (không bị Excel tự đổi "10/10" thành ngày, không chạy công thức), tiếng Việt luôn đúng font.
 * Dòng đầu tiên của bảng là dòng tiêu đề.
 */
public final class XlsxWriter {
    private XlsxWriter() { }

    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static String contentTypes(int sheets) {
        StringBuilder sb = new StringBuilder(HEAD)
            .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheets; i++)
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
          .append("</Types>");
        return sb.toString();
    }

    private static final String ROOT_RELS = HEAD
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"" + REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>";

    private static String workbookRels(int sheets) {
        StringBuilder sb = new StringBuilder(HEAD)
            .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheets; i++)
            sb.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL)
              .append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        sb.append("<Relationship Id=\"rId").append(sheets + 1).append("\" Type=\"").append(REL)
          .append("/styles\" Target=\"styles.xml\"/></Relationships>");
        return sb.toString();
    }

    /** Kiểu 0: mặc định · 1: tiêu đề (chữ trắng đậm, nền tím) · 2: nội dung (tự xuống dòng). */
    private static final String STYLES = HEAD
        + "<styleSheet xmlns=\"" + NS + "\">"
        + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>"
        + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF6C5CE7\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"3\">"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
        + "</cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>";

    /** Một sheet. rows: dòng đầu là tiêu đề. Giá trị null được coi là ô trống. */
    public static byte[] build(String sheetName, List<List<String>> rows) {
        return build(Map.of(sheetName, rows));
    }

    /** Nhiều sheet (theo thứ tự của map). Mỗi sheet: dòng đầu là tiêu đề. */
    public static byte[] build(Map<String, List<List<String>>> sheets) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                int n = sheets.size();
                put(z, "[Content_Types].xml", contentTypes(n));
                put(z, "_rels/.rels", ROOT_RELS);
                StringBuilder names = new StringBuilder();
                int i = 1;
                for (String name : sheets.keySet())
                    names.append("<sheet name=\"").append(esc(safeName(name))).append("\" sheetId=\"").append(i)
                         .append("\" r:id=\"rId").append(i++).append("\"/>");
                put(z, "xl/workbook.xml", HEAD + "<workbook xmlns=\"" + NS + "\" xmlns:r=\"" + REL + "\">"
                    + "<sheets>" + names + "</sheets></workbook>");
                put(z, "xl/_rels/workbook.xml.rels", workbookRels(n));
                put(z, "xl/styles.xml", STYLES);
                i = 1;
                for (List<List<String>> rows : sheets.values()) put(z, "xl/worksheets/sheet" + (i++) + ".xml", sheet(rows));
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tên sheet của Excel: tối đa 31 ký tự, không có \ / ? * [ ] : */
    private static String safeName(String n) {
        String s = n.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (s.isEmpty()) s = "Sheet";
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private static String sheet(List<List<String>> rows) {
        int cols = 0;
        for (List<String> r : rows) cols = Math.max(cols, r.size());
        int[] width = new int[cols];
        for (List<String> r : rows)
            for (int c = 0; c < r.size(); c++) {
                String v = r.get(c);
                if (v == null) continue;
                int longest = 0;
                for (String line : v.split("\\R")) longest = Math.max(longest, line.length());
                width[c] = Math.max(width[c], longest);
            }

        StringBuilder sb = new StringBuilder(HEAD);
        sb.append("<worksheet xmlns=\"").append(NS).append("\">");
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        if (cols > 0) {
            sb.append("<cols>");
            for (int c = 0; c < cols; c++) {
                int wd = Math.min(60, Math.max(12, width[c] + 3));
                sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                  .append("\" width=\"").append(wd).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            sb.append("<row r=\"").append(i + 1).append("\">");
            for (int c = 0; c < r.size(); c++) {
                String v = r.get(c);
                if (v == null || v.isEmpty()) continue;
                sb.append("<c r=\"").append(colName(c)).append(i + 1).append("\" t=\"inlineStr\" s=\"")
                  .append(i == 0 ? 1 : 2).append("\"><is><t xml:space=\"preserve\">")
                  .append(esc(v)).append("</t></is></c>");
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String colName(int index) {
        StringBuilder s = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int m = (n - 1) % 26;
            s.insert(0, (char) ('A' + m));
            n = (n - 1) / 26;
        }
        return s.toString();
    }

    /** Thoát ký tự đặc biệt của XML và bỏ ký tự điều khiển không hợp lệ. */
    private static String esc(String v) {
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') continue;
            if (ch == 0xFFFE || ch == 0xFFFF) continue;
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
