package com.denberg28.exammaker;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
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
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String PREFS="exam_state";
    private static final int INK=Color.rgb(23,35,52), MUTED=Color.rgb(95,108,124);
    private static final int ACCENT=Color.rgb(36,91,198), SURFACE=Color.WHITE;
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
            try(java.io.InputStream stream=getAssets().open("questions.json")) {
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
                while((n=stream.read(buffer))!=-1) out.write(buffer,0,n);
                bytes=out.toByteArray();
            }
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
        } catch(Exception ex) { screen(); heading("Question bank unavailable"); label("The installed bank could not be validated. Reinstall a verified build.",16,MUTED); }
    }
    private void restore() {
        try {
            seed=prefs.getLong("seed",0); int count=prefs.getInt("count",0);
            if(count<1) return;
            exam=new ExamEngine(bank,seed,count);
            JSONArray answers=new JSONArray(prefs.getString("answers","[]"));
            if(answers.length()!=count) throw new IllegalArgumentException();
            for(int i=0;i<count;i++) { int a=answers.getInt(i); if(a < -1 || a>=4) throw new IllegalArgumentException(); exam.items.get(i).selected=a; }
            int p=prefs.getInt("position",0); if(p<0 || p>=count) throw new IllegalArgumentException(); exam.position=p;
        } catch(Exception ex) { exam=null; prefs.edit().clear().apply(); }
    }
    private void save() {
        if(exam==null) return;
        JSONArray answers=new JSONArray(); for(ExamEngine.Item item:exam.items) answers.put(item.selected);
        prefs.edit().putString("bankId",bankId).putLong("seed",seed).putInt("count",exam.items.size()).putInt("position",exam.position).putString("answers",answers.toString()).apply();
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int fill,int stroke) {
        GradientDrawable bg=new GradientDrawable(); bg.setColor(fill); bg.setCornerRadius(dp(16));
        if(stroke!=0) bg.setStroke(dp(1),stroke);
        return bg;
    }
    private void screen() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(247,249,252));
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(36),dp(22),dp(28)); scroll.addView(content); setContentView(scroll);
    }
    private TextView label(String text,int size,int color) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3),1f); view.setPadding(0,0,0,dp(15)); content.addView(view); return view;
    }
    private void heading(String text) { TextView v=label(text,29,INK); v.setTypeface(null,Typeface.BOLD); }
    private void action(String text,boolean primary,Runnable click) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(text); b.setTextSize(16); b.setTypeface(null,Typeface.BOLD);
        b.setTextColor(primary?Color.WHITE:INK); b.setBackgroundTintList(null);
        b.setBackground(shape(primary?ACCENT:SURFACE,primary?0:Color.rgb(218,226,237)));
        b.setPadding(dp(20),dp(12),dp(20),dp(12));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(58)); params.bottomMargin=dp(12);
        content.addView(b,params); b.setOnClickListener(v->click.run());
    }
    private void choice(String text,boolean selected,Runnable click) {
        TextView v=new TextView(this); v.setText(text); v.setTextSize(16); v.setTextColor(INK); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setBackground(shape(selected?Color.rgb(232,239,255):SURFACE,selected?ACCENT:Color.rgb(218,226,237)));
        v.setPadding(dp(20),dp(16),dp(20),dp(16));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(12);
        content.addView(v,params); v.setMinHeight(dp(62));
        if(click!=null) v.setOnClickListener(w->click.run());
    }
    private void home() {
        screen(); heading("Exam Maker"); label(title,21,INK);
        label(bank.size()+" sample questions  •  4 choices each",15,MUTED);
        label("Begin with "+bank.size()+" points. Each incorrect answer reduces the score by one. Your choice is locked when tapped.",16,INK);
        if(exam!=null) {
            action(exam.finished()?"View saved result":"Resume exam",true,()->{ if(exam.finished()) results(); else question(); });
            action("Start a new exam",false,()->start());
        } else action("Start exam",true,()->start());
        label("Sample content only. Not an official CAAP exam or approved study bank.",14,MUTED);
    }
    private void start() { seed=new SecureRandom().nextLong(); exam=new ExamEngine(bank,seed,bank.size()); save(); question(); }
    private void question() {
        if(exam==null) { home(); return; }
        screen(); ExamEngine.Item item=exam.items.get(exam.position);
        label("QUESTION "+(exam.position+1)+" OF "+exam.items.size(),14,ACCENT);
        TextView score=label("Current score  "+exam.score()+" / "+exam.items.size(),18,INK); score.setTypeface(null,Typeface.BOLD);
        TextView prompt=label(item.question.prompt,23,INK); prompt.setTypeface(null,Typeface.BOLD);
        for(int i=0;i<item.order.size();i++) {
            final int selected=i;
            String text=(char)('A'+i)+"    "+item.question.options.get(item.order.get(i));
            choice(text,item.selected==i,item.selected<0?()->{ if(exam.answer(selected)) { save(); question(); } }:null);
        }
        if(item.selected>=0) {
            label("Answer submitted. Your current score is "+exam.score()+" / "+exam.items.size()+".",15,MUTED);
            action(exam.position+1==exam.items.size()?"See final result":"Next question",true,()->{
                if(exam.position+1<exam.items.size()) { exam.position++; save(); question(); } else results();
            });
        }
        action("Back to home",false,()->home());
    }
    private void results() {
        if(exam==null || !exam.finished()) { question(); return; }
        screen(); heading("Exam complete");
        label("Final score",16,MUTED);
        TextView result=label(exam.score()+" / "+exam.items.size(),36,ACCENT); result.setTypeface(null,Typeface.BOLD);
        label(String.format(Locale.US,"%.0f%%",100.0*exam.score()/exam.items.size()),21,INK);
        label("Starting a new exam replaces this saved attempt.",16,MUTED);
        action("Start new exam",true,()->start()); action("Home",false,()->home());
    }
    @Override public void onBackPressed() { home(); }
}
