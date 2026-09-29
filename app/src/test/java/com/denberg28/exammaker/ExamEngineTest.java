package com.denberg28.exammaker;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
public class ExamEngineTest {
    private ExamEngine.Question q(String id) { return new ExamEngine.Question(id,"Prompt",Arrays.asList("A","B","C","D"),1,"Because B"); }
    @Test public void shufflePreservesCorrectAnswerAndRejectsResubmission() {
        ExamEngine e=new ExamEngine(Arrays.asList(q("1"),q("2"),q("3")),42,3);
        for(ExamEngine.Item item:e.items) {
            int selected=item.order.indexOf(item.question.correct);
            assertTrue(e.answer(selected)); assertTrue(item.isCorrect()); assertFalse(e.answer(0)); e.position++;
        }
        assertEquals(3,e.score()); assertTrue(e.finished());
    }
    @Test public void repeatSeedPreservesOrderForRestore() {
        ExamEngine a=new ExamEngine(Arrays.asList(q("1"),q("2"),q("3")),192,3);
        ExamEngine b=new ExamEngine(Arrays.asList(q("1"),q("2"),q("3")),192,3);
        for(int i=0;i<3;i++) { assertEquals(a.items.get(i).question.id,b.items.get(i).question.id); assertEquals(a.items.get(i).order,b.items.get(i).order); }
    }
    @Test public void fixedScoreDecreasesOnlyForWrongAnswers() {
        ExamEngine e=new ExamEngine(Arrays.asList(q("1"),q("2"),q("3")),11,3);
        assertEquals(3,e.score());
        int wrong=(e.items.get(0).order.indexOf(e.items.get(0).question.correct)+1)%4;
        assertTrue(e.answer(wrong)); assertEquals(2,e.score());
        assertFalse(e.answer(e.items.get(0).order.indexOf(e.items.get(0).question.correct)));
        assertEquals(2,e.score());
        e.position=1;
        assertTrue(e.answer(e.items.get(1).order.indexOf(e.items.get(1).question.correct)));
        assertEquals(2,e.score());
    }
    @Test(expected=IllegalArgumentException.class) public void threeOptionsFail() {
        ExamEngine.validate(Collections.singletonList(new ExamEngine.Question("bad","Prompt",Arrays.asList("A","B","C"),1,"Explanation")));
    }
    @Test(expected=IllegalArgumentException.class) public void duplicateIdFails() { ExamEngine.validate(Arrays.asList(q("1"),q("1"))); }
    @Test(expected=IllegalArgumentException.class) public void emptyBankFails() { ExamEngine.validate(Collections.emptyList()); }
}
