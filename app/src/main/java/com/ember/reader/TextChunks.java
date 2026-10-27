package com.ember.reader;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded sentence groups keep both the native context and cancellation latency manageable. */
public final class TextChunks {
    public static final int MAX_INPUT = 20000;
    public static final int MAX_CHUNK = 260;
    private TextChunks() {}

    public static final class Passage {
        public final String text;
        public final boolean risingQuestion;
        Passage(String text, boolean risingQuestion) { this.text = text; this.risingQuestion = risingQuestion; }
        String cacheKey() { return (risingQuestion ? "rise:" : "plain:") + text; }
    }

    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        for (Passage passage : prepare(text)) result.add(passage.text);
        return result;
    }

    public static List<Passage> prepare(String text) {
        validate(text);
        List<Passage> result = new ArrayList<>();
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ROOT);
        sentences.setText(text);
        int spanStart = 0, start = sentences.first();
        for (int end = sentences.next(); end != BreakIterator.DONE; start = end, end = sentences.next()) {
            String sentence = text.substring(start, end);
            if (!QuestionProsody.shouldRise(sentence)) continue;
            String previous = text.substring(spanStart, start);
            if (!previous.trim().isEmpty()) addPassages(result, previous, false);
            else sentence = previous + sentence;
            addPassages(result, sentence, true);
            spanStart = end;
        }
        String remaining = text.substring(spanStart);
        if (!remaining.trim().isEmpty()) addPassages(result, remaining, false);
        else if (!remaining.isEmpty() && !result.isEmpty()) {
            Passage last = result.remove(result.size()-1);
            if (last.text.length() + remaining.length() <= MAX_CHUNK)
                result.add(new Passage(last.text + remaining, last.risingQuestion));
            else result.add(last);
        }
        return result;
    }

    private static void addPassages(List<Passage> output, String text, boolean question) {
        List<String> pieces = splitPlain(text);
        for (int i = 0; i < pieces.size(); ++i)
            output.add(new Passage(pieces.get(i), question && i == pieces.size()-1));
    }

    private static void validate(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Enter some text first.");
        if (text.length() > MAX_INPUT) throw new IllegalArgumentException("Use up to 20,000 characters at a time.");
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("This text contains a null character. Choose a plain text file.");
    }

    private static List<String> splitPlain(String text) {
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ROOT);
        sentences.setText(text);
        List<String> result = new ArrayList<>();
        StringBuilder group = new StringBuilder();
        int start = sentences.first();
        for (int end = sentences.next(); end != BreakIterator.DONE; start = end, end = sentences.next()) {
            String sentence = text.substring(start, end);
            if (group.length() + sentence.length() > MAX_CHUNK && group.length() > 0) {
                add(result, group.toString()); group.setLength(0);
            }
            while (sentence.length() > MAX_CHUNK) {
                int cut = MAX_CHUNK;
                for (int i = MAX_CHUNK - 1; i >= MAX_CHUNK / 2; i--) {
                    if (Character.isWhitespace(sentence.charAt(i))) { cut = i + 1; break; }
                }
                if (Character.isHighSurrogate(sentence.charAt(cut - 1))) cut--;
                add(result, sentence.substring(0, cut));
                sentence = sentence.substring(cut);
            }
            group.append(sentence);
            // Start with one complete sentence rather than waiting for a full group.
            if (result.isEmpty() && !group.toString().trim().isEmpty()) {
                add(result, group.toString()); group.setLength(0);
            }
        }
        add(result, group.toString());
        return result;
    }

    private static void add(List<String> result, String chunk) {
        if (!chunk.trim().isEmpty()) result.add(chunk);
    }
}
