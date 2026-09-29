package com.denberg28.exammaker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ExamStore extends SQLiteOpenHelper {
    public static final class SetRow {
        public final long id; public final String name; public final int count;
        SetRow(long id,String name,int count) { this.id=id; this.name=name; this.count=count; }
    }
    public static final class QuestionRow {
        public final long id; public final ExamEngine.Question question;
        QuestionRow(long id,ExamEngine.Question question) { this.id=id; this.question=question; }
    }
    public static final class ResultRow {
        public final String date,name,identifier,set; public final int score,total;
        ResultRow(String date,String name,String identifier,String set,int score,int total) {
            this.date=date; this.name=name; this.identifier=identifier; this.set=set; this.score=score; this.total=total;
        }
    }
    public ExamStore(Context context) { super(context,"exam_maker.db",null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sets (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
        db.execSQL("CREATE TABLE questions (id INTEGER PRIMARY KEY, set_id INTEGER NOT NULL REFERENCES sets(id) ON DELETE CASCADE, prompt TEXT NOT NULL, a TEXT NOT NULL, b TEXT NOT NULL, c TEXT NOT NULL, d TEXT NOT NULL, correct INTEGER NOT NULL CHECK(correct BETWEEN 0 AND 3), explanation TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE results (attempt_id TEXT PRIMARY KEY, created_at TEXT NOT NULL, examiner_name TEXT NOT NULL, examiner_id TEXT NOT NULL, set_name TEXT NOT NULL, score INTEGER NOT NULL, total INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Database migration required"); }
    public long addSet(String name) {
        ContentValues v=new ContentValues(); v.put("name",required(name,200)); return getWritableDatabase().insertOrThrow("sets",null,v);
    }
    public void renameSet(long id,String name) {
        ContentValues v=new ContentValues(); v.put("name",required(name,200));
        if(getWritableDatabase().update("sets",v,"id=?",new String[]{String.valueOf(id)})!=1) throw new IllegalArgumentException("Set not found");
    }
    public void deleteSet(long id) { getWritableDatabase().delete("sets","id=?",new String[]{String.valueOf(id)}); }
    public List<SetRow> sets() {
        List<SetRow> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.name,COUNT(q.id) FROM sets s LEFT JOIN questions q ON q.set_id=s.id GROUP BY s.id ORDER BY s.name COLLATE NOCASE",null)) {
            while(c.moveToNext()) rows.add(new SetRow(c.getLong(0),c.getString(1),c.getInt(2)));
        }
        return rows;
    }
    public String setName(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM sets WHERE id=?",new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Set not found"); return c.getString(0);
        }
    }
    public List<QuestionRow> questions(long setId) {
        List<QuestionRow> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,prompt,a,b,c,d,correct,explanation FROM questions WHERE set_id=? ORDER BY id",new String[]{String.valueOf(setId)})) {
            while(c.moveToNext()) rows.add(new QuestionRow(c.getLong(0),new ExamEngine.Question(String.valueOf(c.getLong(0)),c.getString(1),Arrays.asList(c.getString(2),c.getString(3),c.getString(4),c.getString(5)),c.getInt(6),c.getString(7))));
        }
        return rows;
    }
    public long saveQuestion(long setId,long id,String prompt,List<String> options,int correct,String explanation) {
        if(options==null || options.size()!=4 || options.stream().anyMatch(o->o==null || o.trim().isEmpty() || o.trim().length()>500))
            throw new IllegalArgumentException("Four options are required (max 500 characters each)");
        ExamEngine.Question q=new ExamEngine.Question(id>0?String.valueOf(id):"new",required(prompt,2000),options,correct,explanation==null?"":explanation.trim());
        if(q.explanation.length()>2000) throw new IllegalArgumentException("Explanation exceeds 2000 characters");
        ExamEngine.validate(java.util.Collections.singletonList(q));
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM questions WHERE set_id=? AND LOWER(TRIM(prompt))=LOWER(?) AND id<>? LIMIT 1",new String[]{String.valueOf(setId),q.prompt,String.valueOf(id)})) {
            if(c.moveToFirst()) throw new IllegalArgumentException("This question already exists in the set");
        }
        ContentValues v=new ContentValues(); v.put("set_id",setId); v.put("prompt",q.prompt);
        for(int i=0;i<4;i++) v.put(new String[]{"a","b","c","d"}[i],q.options.get(i).trim());
        v.put("correct",correct); v.put("explanation",q.explanation);
        if(id<=0) {
            try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM questions WHERE set_id=?",new String[]{String.valueOf(setId)})) {
                c.moveToFirst(); if(c.getInt(0)>=500) throw new IllegalArgumentException("Set cannot exceed 500 questions");
            }
            return getWritableDatabase().insertOrThrow("questions",null,v);
        }
        if(getWritableDatabase().update("questions",v,"id=? AND set_id=?",new String[]{String.valueOf(id),String.valueOf(setId)})!=1) throw new IllegalArgumentException("Question not found");
        return id;
    }
    public void deleteQuestion(long setId,long id) { getWritableDatabase().delete("questions","id=? AND set_id=?",new String[]{String.valueOf(id),String.valueOf(setId)}); }
    public void record(String attemptId,String date,String name,String identifier,String set,int score,int total) {
        ContentValues v=new ContentValues(); v.put("attempt_id",attemptId); v.put("created_at",date); v.put("examiner_name",required(name,200)); v.put("examiner_id",required(identifier,200));
        v.put("set_name",set); v.put("score",score); v.put("total",total);
        getWritableDatabase().insertWithOnConflict("results",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public List<ResultRow> results() {
        List<ResultRow> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT created_at,examiner_name,examiner_id,set_name,score,total FROM results ORDER BY created_at DESC",null)) {
            while(c.moveToNext()) rows.add(new ResultRow(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5)));
        }
        return rows;
    }
    public long importQuestions(Long existingSetId,String newName,List<CsvTemplate.Row> rows) {
        if(rows==null || rows.isEmpty() || rows.size()>CsvTemplate.MAX_QUESTIONS) throw new IllegalArgumentException("Import 1 to 500 questions");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            long setId;
            if(existingSetId==null) {
                ContentValues set=new ContentValues(); set.put("name",required(newName,200));
                setId=db.insertOrThrow("sets",null,set);
            } else {
                setId=existingSetId;
                try(Cursor c=db.rawQuery("SELECT id FROM sets WHERE id=?",new String[]{String.valueOf(setId)})) {
                    if(!c.moveToFirst()) throw new IllegalArgumentException("Target set no longer exists");
                }
            }
            Set<String> prompts=new HashSet<>();
            try(Cursor c=db.rawQuery("SELECT prompt FROM questions WHERE set_id=?",new String[]{String.valueOf(setId)})) {
                while(c.moveToNext()) prompts.add(c.getString(0).trim().toLowerCase(Locale.ROOT));
            }
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM questions WHERE set_id=?",new String[]{String.valueOf(setId)})) {
                c.moveToFirst(); if(c.getInt(0)+rows.size()>500) throw new IllegalArgumentException("Set cannot exceed 500 questions");
            }
            for(CsvTemplate.Row row:rows) {
                if(!prompts.add(row.prompt.trim().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate question: "+row.prompt);
                ContentValues values=new ContentValues(); values.put("set_id",setId); values.put("prompt",required(row.prompt,2000));
                for(int i=0;i<4;i++) values.put(new String[]{"a","b","c","d"}[i],required(row.options.get(i),500));
                values.put("correct",row.correct); values.put("explanation",row.explanation);
                db.insertOrThrow("questions",null,values);
            }
            db.setTransactionSuccessful(); return setId;
        } finally { db.endTransaction(); }
    }
    public int resultCount() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM results",null)) { c.moveToFirst(); return c.getInt(0); }
    }
    public List<ResultRow> recentResults(int limit) {
        List<ResultRow> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT created_at,examiner_name,examiner_id,set_name,score,total FROM results ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)})) {
            while(c.moveToNext()) rows.add(new ResultRow(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5)));
        }
        return rows;
    }
    private static String required(String s,int max) {
        if(s==null || s.trim().isEmpty() || s.trim().length()>max) throw new IllegalArgumentException("A value is required (maximum "+max+" characters)");
        return s.trim();
    }
}
