package com.denberg28.exammaker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** UTF-8 RFC-4180 style question template. A-D marks the correct option. */
public final class CsvTemplate {
    public static final String HEADER="question,option_a,option_b,option_c,option_d,correct_option,explanation\r\n";
    public static final int MAX_BYTES=2_000_000, MAX_QUESTIONS=500;
    public static final class Row {
        public final String prompt,explanation;
        public final List<String> options;
        public final int correct;
        Row(String prompt,List<String> options,int correct,String explanation) {
            this.prompt=prompt; this.options=options; this.correct=correct; this.explanation=explanation;
        }
    }
    private CsvTemplate() {}
    public static List<Row> read(InputStream input) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
        while((n=input.read(buffer))!=-1) {
            if(out.size()+n>MAX_BYTES) throw new IllegalArgumentException("Template exceeds 2 MB");
            out.write(buffer,0,n);
        }
        return parse(new String(out.toByteArray(),StandardCharsets.UTF_8));
    }
    public static List<Row> parse(String text) {
        if(text.startsWith("\uFEFF")) text=text.substring(1);
        List<List<String>> records=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder cell=new StringBuilder();
        boolean quoted=false, afterQuote=false, atStart=true;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quoted) {
                if(c=='"') {
                    if(i+1<text.length() && text.charAt(i+1)=='"') { cell.append('"'); i++; }
                    else { quoted=false; afterQuote=true; }
                } else cell.append(c);
            } else if(c=='"') {
                if(!atStart || afterQuote) throw new IllegalArgumentException("Unexpected quote in CSV");
                quoted=true; atStart=false;
            } else if(c==',') {
                row.add(cell.toString()); cell.setLength(0); atStart=true; afterQuote=false;
            } else if(c=='\r' || c=='\n') {
                if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                row.add(cell.toString()); records.add(row);
                if(records.size()>MAX_QUESTIONS+2) throw new IllegalArgumentException("Template exceeds 500 questions");
                row=new ArrayList<>(); cell.setLength(0); atStart=true; afterQuote=false;
            } else {
                if(afterQuote) throw new IllegalArgumentException("Unexpected text after quoted cell");
                cell.append(c); atStart=false;
            }
        }
        if(quoted) throw new IllegalArgumentException("Unclosed quoted cell");
        if(!row.isEmpty() || cell.length()>0) { row.add(cell.toString()); records.add(row); }
        if(records.isEmpty() || !records.get(0).equals(Arrays.asList(HEADER.trim().split(","))))
            throw new IllegalArgumentException("Incorrect CSV headings. Download a fresh template.");
        List<Row> questions=new ArrayList<>(); Set<String> prompts=new HashSet<>();
        for(int i=1;i<records.size();i++) {
            List<String> cells=records.get(i);
            if(cells.stream().allMatch(String::isEmpty)) continue;
            if(cells.size()!=7) throw new IllegalArgumentException("Row "+(i+1)+": expected 7 columns");
            String prompt=cells.get(0).trim(), answer=cells.get(5).trim().toUpperCase(Locale.ROOT);
            List<String> options=new ArrayList<>(); for(int j=1;j<=4;j++) options.add(cells.get(j).trim());
            if(prompt.isEmpty() || prompt.length()>2000 || options.stream().anyMatch(x->x.isEmpty() || x.length()>500)
                || new HashSet<>(options).size()!=4 || !answer.matches("[A-D]") || cells.get(6).length()>2000)
                throw new IllegalArgumentException("Row "+(i+1)+": complete the question, four distinct options, and correct_option A-D");
            if(!prompts.add(prompt.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Row "+(i+1)+": duplicate question");
            questions.add(new Row(prompt,options,answer.charAt(0)-'A',cells.get(6).trim()));
        }
        if(questions.isEmpty() || questions.size()>MAX_QUESTIONS) throw new IllegalArgumentException("Add 1 to 500 completed question rows");
        return questions;
    }
}
