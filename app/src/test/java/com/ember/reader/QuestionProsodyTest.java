package com.ember.reader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class QuestionProsodyTest {
    @Test public void commonPolarQuestionsRise() {
        for (String text : new String[]{"Are you ready?", "Do you want tea?", "Can we go now?", "Will it rain?", "Have you eaten?", "Isn't it lovely?", "Won't you stay?", "So, are you ready?", "\u201cDon\u2019t you agree?\u201d"})
            assertTrue(text, QuestionProsody.shouldRise(text));
    }
    @Test public void statementsWhAndAlternativeQuestionsDoNotReceiveTheRise() {
        for (String text : new String[]{"You are ready.", "Where are you going?", "Why did you leave?", "How are you?", "What can we do?", "Can we choose red or blue?", "Do not go.", "Are you ready"})
            assertFalse(text, QuestionProsody.shouldRise(text));
    }
    @Test public void tagQuestionsRise() {
        assertTrue(QuestionProsody.shouldRise("It's cold, isn't it?"));
        assertTrue(QuestionProsody.shouldRise("You agree, don't you?"));
        assertFalse(QuestionProsody.shouldRise("I wonder if it is cold."));
    }
    @Test public void reportedMountainsQuestionIsRecognizedAndIsolated() {
        List<TextChunks.Passage> passages = TextChunks.prepare("Do you like mountains?");
        assertEquals(1, passages.size());
        assertEquals("Do you like mountains?", passages.get(0).text);
        assertTrue(passages.get(0).risingQuestion);
    }
    @Test public void questionInParagraphIsIsolatedWithoutChangingWords() {
        String text = "I am here. Are you ready? Then we can go. Where is your bag?";
        List<TextChunks.Passage> passages = TextChunks.prepare(text);
        assertEquals(text, String.join("", TextChunks.split(text)));
        assertEquals(1, passages.stream().filter(p -> p.risingQuestion).count());
        assertEquals("Are you ready? ", passages.stream().filter(p -> p.risingQuestion).findFirst().get().text);
    }
    @Test public void longQuestionRisesOnlyAtItsEnd() {
        String text = "Can you " + "please review this information ".repeat(20) + "before tomorrow?";
        List<TextChunks.Passage> passages = TextChunks.prepare(text);
        assertEquals(text, String.join("", TextChunks.split(text)));
        assertTrue(passages.get(passages.size()-1).risingQuestion);
        assertEquals(1, passages.stream().filter(p -> p.risingQuestion).count());
        assertTrue(passages.stream().allMatch(p -> p.text.length() <= TextChunks.MAX_CHUNK));
    }
    @Test public void cacheDistinguishesContextForAnIdenticalFinalFragment() {
        assertNotEquals(new TextChunks.Passage("tomorrow?", true).cacheKey(),
                new TextChunks.Passage("tomorrow?", false).cacheKey());
    }
}
