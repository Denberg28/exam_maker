package com.denberg28.exammaker;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String PREFS="exam_state";
    private final int ink=Color.rgb(28,39,58), accent=Color.rgb(20,96,144);
    private SharedPreferences prefs;
    private LinearLayout content;
    private ExamEngine exam;
    private List<ExamEngine.Question> bank;
    private String bankId, title;
    private long seed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        try {
            byte[] bytes;
            try(java.io.InputStream stream=getAssets().open("questions.json")) { java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n; while((n=stream.read(buffer))!=-1) out.write(buffer,0,n); bytes=out.toByteArray(); }
            JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(root.getInt("schema")!=1) throw new IllegalArgumentException("Unsupported bank schema");
            bankId=root.getString("bankId"); title=root.getString("title");
            JSONArray questions=root.getJSONArray("questions"); bank=new ArrayList<>();
            for(int i=0;i<questions.length();i++) {
                JSONObject q=questions.getJSONObject(i); JSONArray opts=q.getJSONArray("options"); List<String> options=new ArrayList<>();
                for(int j=0;j<opts.length();j++) options.add(opts.getString(j));
                bank.add(new ExamEngine.Question(q.getString("id"),q.getString("prompt"),options,q.getInt("correct"),q.getString("explanation")));
            }
            ExamEngine.validate(bank);
            if(bankId.equals(prefs.getString("bankId",""))) restore();
            home();
        } catch(Exception ex) { screen(); label("Question bank unavailable",24); label("The installed bank could not be validated. Reinstall a verified build.",16); }
    }
    private void restore() {
        try {
            seed=prefs.getLong("seed",0); int count=prefs.getInt("count",0);
            if(count<1) return;
            exam=new ExamEngine(bank,seed,count);
            JSONArray answers=new JSONArray(prefs.getString("answers","[]"));
            if(answers.length()!=count) throw new IllegalArgumentException();
            for(int i=0;i<count;i++) { int a=answers.getInt(i); if(a < -1 || a>=exam.items.get(i).order.size()) throw new IllegalArgumentException(); exam.items.get(i).selected=a; }
            int p=prefs.getInt("position",0); if(p<0 || p>=count) throw new IllegalArgumentException(); exam.position=p;
        } catch(Exception ex) { exam=null; prefs.edit().clear().apply(); }
    }
    private void save() {
        if(exam==null) return;
        JSONArray answers=new JSONArray(); for(ExamEngine.Item item:exam.items) answers.put(item.selected);
        prefs.edit().putString("bankId",bankId).putLong("seed",seed).putInt("count",exam.items.size()).putInt("position",exam.position).putString("answers",answers.toString()).apply();
    }
    private void screen() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(247,249,252));
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(36),dp(24),dp(28)); scroll.addView(content); setContentView(scroll);
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private TextView label(String text,int size) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(ink); view.setPadding(0,0,0,dp(18)); content.addView(view); return view;
    }
    private void button(String text,Runnable action) {
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(54)); params.bottomMargin=dp(12); content.addView(b,params); b.setOnClickListener(v->action.run());
    }
    private void home() {
        screen(); label("Exam Maker",30); label(title,21);
        label("Offline practice • "+bank.size()+" sample questions • one answer per question",16);
        label("Your score appears after each submitted choice. A summary appears after the last question.",16);
        if(exam!=null) {
            button(exam.finished()?"View saved result":"Resume exam",()->{ if(exam.finished()) results(); else question(); });
            button("Start a new exam",()->start());
        } else button("Start exam",()->start());
        label("Sample content only. Not an official CAAP exam or approved study bank.",14);
    }
    private void start() { seed=new SecureRandom().nextLong(); exam=new ExamEngine(bank,seed,bank.size()); save(); question(); }
    private void question() {
        if(exam==null) { home(); return; }
        screen(); ExamEngine.Item item=exam.items.get(exam.position);
        label("Question "+(exam.position+1)+" of "+exam.items.size(),16);
        label("Score: "+exam.score()+" / "+answered(),17);
        label(item.question.prompt,23);
        for(int i=0;i<item.order.size();i++) {
            final int choice=i; String option=item.question.options.get(item.order.get(i));
            if(item.selected<0) button(option,()->{ if(exam.answer(choice)) { save(); question(); } });
            else { TextView line=label((item.selected==i?"● ":"○ ")+option,17); if(item.order.get(i)==item.question.correct) line.setTextColor(Color.rgb(0,108,68)); }
        }
        if(item.selected>=0) {
            label(item.isCorrect()?"Correct • Score: "+exam.score():"Incorrect • Score: "+exam.score(),20);
            label(item.question.explanation,16);
            button(exam.position+1==exam.items.size()?"See final result":"Next question",()->{ if(exam.position+1<exam.items.size()) { exam.position++; save(); question(); } else results(); });
        }
        button("Back to home",()->home());
    }
    private int answered() { int n=0; for(ExamEngine.Item item:exam.items) if(item.selected>=0) n++; return n; }
    private void results() {
        if(exam==null || !exam.finished()) { question(); return; }
        screen(); label("Exam complete",30);
        label(exam.score()+" / "+exam.items.size()+" correct",26);
        label(String.format(java.util.Locale.US,"%.0f%%",100.0*exam.score()/exam.items.size()),21);
        label("Results are stored on this device. Starting a new exam replaces this attempt.",16);
        button("Start new exam",()->start()); button("Home",()->home());
    }
    @Override public void onBackPressed() { home(); }
}
