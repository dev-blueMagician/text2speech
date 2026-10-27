package com.ember.reader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TextChunksTest {
    @Test public void oneShortSentenceIsPreserved() {
        assertEquals(List.of("You finally came."), TextChunks.split("You finally came."));
    }
    @Test public void splittingPreservesWordsAndPunctuation() {
        String text = "I’m so glad you’re here! We can begin again.\n\n".repeat(40);
        List<String> parts = TextChunks.split(text);
        assertEquals(text, String.join("", parts));
        assertTrue(parts.stream().allMatch(p -> p.length() <= TextChunks.MAX_CHUNK));
    }
    @Test public void longUnbrokenUnicodeTextDoesNotSplitSurrogatePairs() {
        String text = "a" + "📚".repeat(300);
        List<String> parts = TextChunks.split(text);
        assertEquals(text, String.join("", parts));
        for (String part : parts) assertFalse(Character.isHighSurrogate(part.charAt(part.length() - 1)));
    }
    @Test(expected = IllegalArgumentException.class) public void emptyTextRejected() { TextChunks.split(" \n\t "); }
    @Test(expected = IllegalArgumentException.class) public void oversizedTextRejected() { TextChunks.split("a".repeat(20001)); }
    @Test(expected = IllegalArgumentException.class) public void embeddedNullRejected() { TextChunks.split("Hello\0world"); }
    @Test public void firstSentenceCanPlayWithoutWaitingForFollowingSentences() {
        String text = "The door opened. I was glad to see you. We can start now.";
        List<String> parts = TextChunks.split(text);
        assertEquals("The door opened. ", parts.get(0));
        assertEquals(text, String.join("", parts));
    }
    @Test public void whitespaceAtChunkLimitDoesNotExceedTheLimit() {
        String text = "a".repeat(260) + " " + "b".repeat(50);
        List<String> parts = TextChunks.split(text);
        assertEquals(text, String.join("", parts));
        assertTrue(parts.stream().allMatch(p -> p.length() <= TextChunks.MAX_CHUNK));
    }
}
