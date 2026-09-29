package com.denberg28.exammaker;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class CsvTemplateTest {
    @Test public void parsesQuotedCommaNewlineAndEscapedQuote() {
        String data=CsvTemplate.HEADER+"\"What, is \"\"lift\"\"?\",A,B,C,D,B,\"First line\nSecond line\"\r\n";
        List<CsvTemplate.Row> rows=CsvTemplate.parse("\uFEFF"+data);
        assertEquals(1,rows.size()); assertEquals("What, is \"lift\"?",rows.get(0).prompt);
        assertEquals(1,rows.get(0).correct); assertEquals("First line\nSecond line",rows.get(0).explanation);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsWrongHeader() { CsvTemplate.parse("wrong,header\nQ,A,B,C,D,A,\n"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsDuplicateQuestion() {
        CsvTemplate.parse(CsvTemplate.HEADER+"Q,A,B,C,D,A,\nq,A,B,C,D,A,\n");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidCorrectChoice() { CsvTemplate.parse(CsvTemplate.HEADER+"Q,A,B,C,D,E,\n"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnclosedQuote() { CsvTemplate.parse(CsvTemplate.HEADER+"\"Q,A,B,C,D,A,\n"); }
}
