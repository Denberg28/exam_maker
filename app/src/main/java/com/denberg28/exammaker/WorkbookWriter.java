package com.denberg28.exammaker;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class WorkbookWriter {
    private WorkbookWriter() {}
    private static String xml(String s) {
        StringBuilder b=new StringBuilder();
        for(int i=0;i<s.length();i++) { char c=s.charAt(i);
            if(c=='&') b.append("&amp;"); else if(c=='<') b.append("&lt;"); else if(c=='>') b.append("&gt;");
            else if(c=='\"') b.append("&quot;"); else if(c=='\'') b.append("&apos;");
            else if(c>=32 || c=='\n' || c=='\t' || c=='\r') b.append(c);
        }
        return b.toString();
    }
    private static void entry(ZipOutputStream zip,String path,String data) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(data.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static void cell(StringBuilder s,String value) { s.append("<c t=\"inlineStr\"><is><t>").append(xml(value)).append("</t></is></c>"); }
    private static void number(StringBuilder s,int value) { s.append("<c><v>").append(value).append("</v></c>"); }
    public static void write(OutputStream output,List<ExamStore.ResultRow> rows) throws IOException {
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        sheet.append("<row>"); for(String h:new String[]{"Date UTC","Examiner name","Examiner ID","Test set","Score","Maximum","Percent"}) cell(sheet,h); sheet.append("</row>");
        for(ExamStore.ResultRow r:rows) {
            sheet.append("<row>"); cell(sheet,r.date); cell(sheet,r.name); cell(sheet,r.identifier); cell(sheet,r.set);
            number(sheet,r.score); number(sheet,r.total); number(sheet,r.total==0?0:(int)Math.round(100.0*r.score/r.total)); sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            entry(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Results\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entry(zip,"xl/worksheets/sheet1.xml",sheet.toString());
        }
    }
}
