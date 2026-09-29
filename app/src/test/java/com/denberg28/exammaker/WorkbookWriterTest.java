package com.denberg28.exammaker;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class WorkbookWriterTest {
    @Test public void workbookHasEscapedTextAndNumericScore() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        WorkbookWriter.write(output,Collections.singletonList(new ExamStore.ResultRow("2026-09-29 01:00:00","A & B <Test>","ID-1","Structures",7,10)));
        boolean worksheet=false,workbook=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[1024]; int n;
                while((n=zip.read(buffer))!=-1) bytes.write(buffer,0,n);
                if(entry.getName().endsWith("sheet1.xml")) {
                    worksheet=true; String xml=bytes.toString(StandardCharsets.UTF_8.name());
                    assertTrue(xml.contains("A &amp; B &lt;Test&gt;"));
                    assertTrue(xml.contains("<c><v>7</v></c>"));
                    assertTrue(xml.contains("<c><v>70</v></c>"));
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toByteArray()));
                }
                if(entry.getName().equals("xl/workbook.xml")) workbook=true;
            }
        }
        assertTrue(worksheet); assertTrue(workbook);
    }
}
