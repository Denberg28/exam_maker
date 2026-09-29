package com.denberg28.exammaker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class MainActivity extends Activity {
    private static final int EXPORT_REQUEST=42, SET_EXPORT_REQUEST=43;
    private static final int INK=Color.rgb(23,35,52), MUTED=Color.rgb(95,108,124);
    private static final int ACCENT=Color.rgb(36,91,198), SURFACE=Color.WHITE;
    private SharedPreferences prefs,adminPrefs;
    private ExamStore store;
    private LinearLayout content;
    private ExamEngine exam;
    private List<ExamEngine.Question> bank;
    private String title,examinerName,examinerId,attemptId;
    private long seed;
    private boolean adminUnlocked;
    private int failedLogins;
    private long blockedUntil;
    private EditText nameInput,idInput;
    private long pendingSetExportId;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("exam_state",MODE_PRIVATE);
        adminPrefs=getSharedPreferences("admin_auth",MODE_PRIVATE);
        store=new ExamStore(this);
        try { seedSample(); restore(); home(); }
        catch(Exception ex) { screen(); heading("Unable to open exam data"); label("Restart the app or reinstall a verified build. Details: "+ex.getClass().getSimpleName(),16,MUTED); }
    }
    private void seedSample() throws Exception {
        if(prefs.getBoolean("sample_imported",false)) return;
        byte[] bytes;
        try(InputStream stream=getAssets().open("questions.json")) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
            while((n=stream.read(buffer))!=-1) out.write(buffer,0,n); bytes=out.toByteArray();
        }
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(root.getInt("schema")!=1) throw new IllegalArgumentException("Unsupported sample bank");
        JSONArray questions=root.getJSONArray("questions");
        List<ExamEngine.Question> sample=new ArrayList<>();
        for(int i=0;i<questions.length();i++) {
            JSONObject q=questions.getJSONObject(i); JSONArray opts=q.getJSONArray("options"); List<String> options=new ArrayList<>();
            for(int j=0;j<opts.length();j++) options.add(opts.getString(j));
            sample.add(new ExamEngine.Question(q.getString("id"),q.getString("prompt"),options,q.getInt("correct"),q.optString("explanation","")));
        }
        ExamEngine.validate(sample);
        if(store.sets().isEmpty()) {
            long set=store.addSet("Sample Practice Exam");
            for(ExamEngine.Question q:sample) store.saveQuestion(set,0,q.prompt,q.options,q.correct,q.explanation);
        }
        prefs.edit().putBoolean("sample_imported",true).apply();
    }
    private JSONArray snapshot(List<ExamEngine.Question> questions) {
        JSONArray all=new JSONArray();
        for(ExamEngine.Question q:questions) {
            JSONObject v=new JSONObject(); try {
                v.put("id",q.id); v.put("prompt",q.prompt); v.put("options",new JSONArray(q.options)); v.put("correct",q.correct); v.put("explanation",q.explanation);
            } catch(Exception ex) { throw new IllegalStateException(ex); }
            all.put(v);
        }
        return all;
    }
    private void restore() {
        try {
            JSONArray all=new JSONArray(prefs.getString("snapshot","[]")); if(all.length()==0) return;
            bank=new ArrayList<>();
            for(int i=0;i<all.length();i++) {
                JSONObject q=all.getJSONObject(i); JSONArray opts=q.getJSONArray("options"); List<String> options=new ArrayList<>();
                for(int j=0;j<opts.length();j++) options.add(opts.getString(j));
                bank.add(new ExamEngine.Question(q.getString("id"),q.getString("prompt"),options,q.getInt("correct"),q.optString("explanation","")));
            }
            seed=prefs.getLong("seed",0); exam=new ExamEngine(bank,seed,bank.size());
            JSONArray answers=new JSONArray(prefs.getString("answers","[]")); if(answers.length()!=bank.size()) throw new IllegalArgumentException();
            for(int i=0;i<answers.length();i++) {
                int a=answers.getInt(i); if(a < -1 || a>=4) throw new IllegalArgumentException(); exam.items.get(i).selected=a;
            }
            int p=prefs.getInt("position",0); if(p<0 || p>=bank.size()) throw new IllegalArgumentException(); exam.position=p;
            title=prefs.getString("title",""); examinerName=prefs.getString("examiner_name",""); examinerId=prefs.getString("examiner_id",""); attemptId=prefs.getString("attempt_id","");
            if(attemptId.isEmpty() || title.isEmpty() || examinerName.isEmpty() || examinerId.isEmpty()) throw new IllegalArgumentException();
        } catch(Exception ex) { exam=null; prefs.edit().remove("snapshot").remove("answers").apply(); }
    }
    private void save() {
        if(exam==null) return;
        JSONArray answers=new JSONArray(); for(ExamEngine.Item item:exam.items) answers.put(item.selected);
        prefs.edit().putString("snapshot",snapshot(bank).toString()).putString("answers",answers.toString())
            .putLong("seed",seed).putInt("position",exam.position).putString("title",title)
            .putString("examiner_name",examinerName).putString("examiner_id",examinerId).putString("attempt_id",attemptId).commit();
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int fill,int stroke) {
        GradientDrawable bg=new GradientDrawable(); bg.setColor(fill); bg.setCornerRadius(dp(16));
        if(stroke!=0) bg.setStroke(dp(1),stroke); return bg;
    }
    private void screen() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(247,249,252));
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(32),dp(22),dp(24)); scroll.addView(content); setContentView(scroll);
    }
    private TextView label(String text,int size,int color) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3),1f); view.setPadding(0,0,0,dp(15)); content.addView(view); return view;
    }
    private void heading(String text) { TextView v=label(text,28,INK); v.setTypeface(null,Typeface.BOLD); }
    private void action(String text,boolean primary,Runnable click) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(text); b.setTextSize(16); b.setTypeface(null,Typeface.BOLD);
        b.setTextColor(primary?Color.WHITE:INK); b.setBackgroundTintList(null);
        b.setBackground(shape(primary?ACCENT:SURFACE,primary?0:Color.rgb(218,226,237)));
        b.setPadding(dp(20),dp(12),dp(20),dp(12));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(58)); params.bottomMargin=dp(12);
        content.addView(b,params); b.setOnClickListener(v->click.run());
    }
    private EditText field(String hint,String value) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setTextSize(16); e.setHint(hint); e.setText(value);
        e.setPadding(dp(16),dp(12),dp(16),dp(12)); e.setBackground(shape(SURFACE,Color.rgb(218,226,237)));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(56)); params.bottomMargin=dp(12); content.addView(e,params); return e;
    }
    private void choice(String text,boolean selected,Runnable click) {
        TextView v=new TextView(this); v.setText(text); v.setTextSize(16); v.setTextColor(INK); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setBackground(shape(selected?Color.rgb(232,239,255):SURFACE,selected?ACCENT:Color.rgb(218,226,237)));
        v.setPadding(dp(20),dp(16),dp(20),dp(16));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(12);
        content.addView(v,params); v.setMinHeight(dp(62)); if(click!=null) v.setOnClickListener(w->click.run());
    }
    private void error(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private void home() {
        screen(); heading("Exam Maker");
        label("Enter examiner details, then choose a test set.",16,MUTED);
        nameInput=field("Examiner name",examinerName==null?"":examinerName);
        idInput=field("Examiner ID",examinerId==null?"":examinerId);
        if(exam!=null) action(exam.finished()?"View saved result":"Resume "+title,true,()->{ if(exam.finished()) results(); else question(); });
        List<ExamStore.SetRow> sets=store.sets();
        if(sets.isEmpty()) label("No test sets available. Open Admin to create one.",16,MUTED);
        for(ExamStore.SetRow s:sets) action(s.name+"  •  "+s.count+" questions",false,()->start(s));
        action("Admin",false,()->adminGate());
        label("Bundled sample questions are for testing only, not official CAAP content.",14,MUTED);
    }
    private void start(ExamStore.SetRow set) {
        String name=nameInput.getText().toString().trim(),id=idInput.getText().toString().trim();
        if(name.isEmpty() || id.isEmpty() || name.length()>200 || id.length()>200) { error("Enter examiner name and ID (up to 200 characters each)."); return; }
        List<ExamStore.QuestionRow> rows=store.questions(set.id);
        if(rows.isEmpty()) { error("This test set has no questions."); return; }
        List<ExamEngine.Question> questions=new ArrayList<>(); for(ExamStore.QuestionRow row:rows) questions.add(row.question);
        try { ExamEngine.validate(questions); }
        catch(Exception ex) { error("Test bank invalid. Ask the admin to review it."); return; }
        bank=questions; title=set.name; examinerName=name; examinerId=id; attemptId=UUID.randomUUID().toString();
        seed=new SecureRandom().nextLong(); exam=new ExamEngine(bank,seed,bank.size()); save(); question();
    }
    private void question() {
        if(exam==null) { home(); return; }
        screen(); ExamEngine.Item item=exam.items.get(exam.position);
        label(title+"  •  QUESTION "+(exam.position+1)+" OF "+exam.items.size(),14,ACCENT);
        TextView score=label("Current score  "+exam.score()+" / "+exam.items.size(),18,INK); score.setTypeface(null,Typeface.BOLD);
        TextView prompt=label(item.question.prompt,23,INK); prompt.setTypeface(null,Typeface.BOLD);
        for(int i=0;i<item.order.size();i++) {
            final int selected=i; String text=(char)('A'+i)+"    "+item.question.options.get(item.order.get(i));
            choice(text,item.selected==i,item.selected<0?()->{ if(exam.answer(selected)) { save(); question(); } }:null);
        }
        if(item.selected>=0) {
            label("Answer submitted. Current score: "+exam.score()+" / "+exam.items.size()+".",15,MUTED);
            action(exam.position+1==exam.items.size()?"See final result":"Next question",true,()->{
                if(exam.position+1<exam.items.size()) { exam.position++; save(); question(); } else results();
            });
        }
        action("Back to home",false,()->home());
    }
    private void results() {
        if(exam==null || !exam.finished()) { question(); return; }
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US); fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        store.record(attemptId,fmt.format(new java.util.Date()),examinerName,examinerId,title,exam.score(),exam.items.size());
        screen(); content.setGravity(Gravity.CENTER);
        TextView complete=label("Exam complete",28,INK); complete.setTypeface(null,Typeface.BOLD); complete.setGravity(Gravity.CENTER);
        label(title+"  •  "+examinerName+" ("+examinerId+")",16,MUTED).setGravity(Gravity.CENTER);
        label("Final score",16,MUTED).setGravity(Gravity.CENTER);
        TextView v=label(exam.score()+" / "+exam.items.size(),42,ACCENT); v.setTypeface(null,Typeface.BOLD); v.setGravity(Gravity.CENTER);
        label(String.format(Locale.US,"%.0f%%",100.0*exam.score()/exam.items.size()),21,INK).setGravity(Gravity.CENTER);
        label("The result was saved on this device. A new exam replaces the resumable attempt.",16,MUTED).setGravity(Gravity.CENTER);
        action("Home",true,()->home());
    }
    private byte[] derive(String password,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,150000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    private void adminGate() {
        if(adminUnlocked) { adminHome(); return; }
        screen(); heading(adminPrefs.contains("hash")?"Admin sign in":"Create admin account");
        label(adminPrefs.contains("hash")?"Enter your local admin password.":"Create a password of at least 8 characters. Keep it safe; there is no recovery service.",16,MUTED);
        EditText password=field("Admin password",""); password.setInputType(129);
        action(adminPrefs.contains("hash")?"Sign in":"Create account",true,()->{
            String value=password.getText().toString();
            if(System.currentTimeMillis()<blockedUntil) { error("Too many attempts. Try again later."); return; }
            if(!adminPrefs.contains("hash") && value.length()<8) { error("Use at least 8 characters."); return; }
            try {
                if(!adminPrefs.contains("hash")) {
                    byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
                    String saltText=android.util.Base64.encodeToString(salt,android.util.Base64.NO_WRAP);
                    String hash=android.util.Base64.encodeToString(derive(value,salt),android.util.Base64.NO_WRAP);
                    if(!adminPrefs.edit().putString("salt",saltText).putString("hash",hash).commit()) throw new IllegalStateException();
                    adminUnlocked=true; adminHome();
                } else {
                    byte[] salt=android.util.Base64.decode(adminPrefs.getString("salt",""),android.util.Base64.NO_WRAP);
                    byte[] expected=android.util.Base64.decode(adminPrefs.getString("hash",""),android.util.Base64.NO_WRAP);
                    if(MessageDigest.isEqual(expected,derive(value,salt))) { failedLogins=0; adminUnlocked=true; adminHome(); }
                    else { if(++failedLogins>=5) { blockedUntil=System.currentTimeMillis()+30000; failedLogins=0; } error("Incorrect password."); }
                }
            } catch(Exception ex) { error("Admin authentication failed."); }
        });
        action("Back",false,()->home());
    }
    private void adminHome() {
        if(!adminUnlocked) { adminGate(); return; }
        screen(); heading("Admin • Test sets");
        for(ExamStore.SetRow set:store.sets()) action(set.name+"  •  "+set.count+" questions",false,()->editSet(set.id));
        action("Add test set",true,()->{ screen(); heading("New test set"); EditText input=field("Test set name","");
            action("Create",true,()->{ try { editSet(store.addSet(input.getText().toString())); } catch(Exception ex) { error("Name is required and must be unique."); } });
            action("Cancel",false,()->adminHome());
        });
        action("Results and Excel export",false,()->adminResults());
        action("Lock admin",false,()->{ adminUnlocked=false; home(); });
    }
    private void editSet(long setId) {
        if(!adminUnlocked) { adminGate(); return; }
        String name; try { name=store.setName(setId); } catch(Exception ex) { adminHome(); return; }
        screen(); heading(name); EditText input=field("Test set name",name);
        action("Rename test set",false,()->{ try { store.renameSet(setId,input.getText().toString()); editSet(setId); } catch(Exception ex) { error("Name is required and must be unique."); } });
        for(ExamStore.QuestionRow row:store.questions(setId)) action(row.question.prompt,false,()->editQuestion(setId,row));
        action("Add question",true,()->editQuestion(setId,null));
        action("Export set for web QR session (.json)",false,()->{
            if(store.questions(setId).isEmpty()) { error("Add questions before exporting."); return; }
            pendingSetExportId=setId;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json"); intent.putExtra(Intent.EXTRA_TITLE,"exam-set-"+setId+".json");
            startActivityForResult(intent,SET_EXPORT_REQUEST);
        });
        action("Delete test set",false,()->confirm("Delete this test set and all its questions? Saved results remain.",()->{ store.deleteSet(setId); adminHome(); }));
        action("Back",false,()->adminHome());
    }
    private void editQuestion(long setId,ExamStore.QuestionRow row) {
        if(!adminUnlocked) { adminGate(); return; }
        screen(); heading(row==null?"Add question":"Edit question");
        EditText prompt=field("Question",row==null?"":row.question.prompt);
        EditText[] options=new EditText[4]; for(int i=0;i<4;i++) options[i]=field("Option "+(char)('A'+i),row==null?"":row.question.options.get(i));
        label("Correct option (admin only)",15,MUTED);
        Spinner correct=new Spinner(this); correct.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"A","B","C","D"}));
        correct.setSelection(row==null?0:row.question.correct); content.addView(correct);
        action("Save question",true,()->{
            try {
                List<String> choices=new ArrayList<>(); for(EditText e:options) choices.add(e.getText().toString().trim());
                store.saveQuestion(setId,row==null?0:row.id,prompt.getText().toString(),choices,correct.getSelectedItemPosition(),""); editSet(setId);
            } catch(Exception ex) { error("Complete the question and four distinct options."); }
        });
        if(row!=null) action("Delete question",false,()->confirm("Delete this question?",()->{ store.deleteQuestion(setId,row.id); editSet(setId); }));
        action("Back",false,()->editSet(setId));
    }
    private void confirm(String message,Runnable yes) {
        new AlertDialog.Builder(this).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->yes.run()).show();
    }
    private void adminResults() {
        if(!adminUnlocked) { adminGate(); return; }
        screen(); heading("Saved results"); List<ExamStore.ResultRow> rows=store.results();
        label(rows.size()+" completed attempts stored on this device.",16,MUTED);
        action("Export Excel (.xlsx)",true,()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_TITLE,"exam-maker-results.xlsx"); startActivityForResult(intent,EXPORT_REQUEST);
        });
        for(ExamStore.ResultRow r:rows) label(r.date+" UTC  •  "+r.name+" ("+r.identifier+")\n"+r.set+"  •  "+r.score+" / "+r.total,15,INK);
        action("Back",false,()->adminHome());
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null || !adminUnlocked) return;
        if(requestCode==EXPORT_REQUEST) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                if(out==null) throw new IllegalStateException(); WorkbookWriter.write(out,store.results()); error("Excel file saved.");
            } catch(Exception ex) { error("Export failed. Choose another location."); }
        } else if(requestCode==SET_EXPORT_REQUEST) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                if(out==null) throw new IllegalStateException();
                JSONObject payload=new JSONObject(); payload.put("schema",1); payload.put("title",store.setName(pendingSetExportId));
                List<ExamEngine.Question> questions=new ArrayList<>();
                for(ExamStore.QuestionRow row:store.questions(pendingSetExportId)) questions.add(row.question);
                payload.put("questions",snapshot(questions));
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8)); error("Set exported. Upload it in the Streamlit admin portal to create a QR session.");
            } catch(Exception ex) { error("Set export failed. Try another location."); }
        }
    }
    @Override public void onBackPressed() { if(adminUnlocked) adminHome(); else home(); }
}
