package com.denberg28.exammaker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class ExamEngine {
    public static final class Question {
        public final String id, prompt, explanation;
        public final List<String> options;
        public final int correct;
        public Question(String id, String prompt, List<String> options, int correct, String explanation) {
            this.id=id; this.prompt=prompt; this.options=Collections.unmodifiableList(new ArrayList<>(options)); this.correct=correct; this.explanation=explanation;
        }
    }
    public static final class Item {
        public final Question question;
        public final List<Integer> order;
        public int selected=-1;
        private Item(Question question, Random random) {
            this.question=question;
            List<Integer> indices=new ArrayList<>();
            for(int i=0;i<question.options.size();i++) indices.add(i);
            Collections.shuffle(indices, random);
            order=Collections.unmodifiableList(indices);
        }
        public boolean isCorrect() { return selected >= 0 && order.get(selected)==question.correct; }
    }
    public final List<Item> items;
    public int position;
    public ExamEngine(List<Question> bank, long seed, int count) {
        validate(bank);
        if(count<1 || count>bank.size()) throw new IllegalArgumentException("Invalid exam length");
        Random random=new Random(seed);
        List<Question> shuffled=new ArrayList<>(bank);
        Collections.shuffle(shuffled,random);
        List<Item> result=new ArrayList<>();
        for(int i=0;i<count;i++) result.add(new Item(shuffled.get(i),random));
        items=Collections.unmodifiableList(result);
    }
    public static void validate(List<Question> bank) {
        if(bank==null || bank.isEmpty()) throw new IllegalArgumentException("Empty bank");
        Set<String> ids=new HashSet<>();
        for(Question q:bank) {
            if(q==null || blank(q.id) || !ids.add(q.id) || blank(q.prompt) || blank(q.explanation) || q.options==null || q.options.size()<2 || q.options.size()>6 || q.correct<0 || q.correct>=q.options.size()) throw new IllegalArgumentException("Invalid question");
            Set<String> options=new HashSet<>();
            for(String option:q.options) if(blank(option) || !options.add(option.trim())) throw new IllegalArgumentException("Invalid option");
        }
    }
    private static boolean blank(String s) { return s==null || s.trim().isEmpty(); }
    public boolean answer(int selection) {
        Item item=items.get(position);
        if(item.selected!=-1 || selection<0 || selection>=item.order.size()) return false;
        item.selected=selection;
        return true;
    }
    public int score() { int n=0; for(Item item:items) if(item.isCorrect()) n++; return n; }
    public boolean finished() { for(Item item:items) if(item.selected<0) return false; return true; }
}
