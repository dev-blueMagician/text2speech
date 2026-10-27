package com.ember.reader;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative punctuation/grammar rules; this is not semantic emotion analysis. */
final class QuestionProsody {
    private static final String AUX = "am|is|are|was|were|do|does|did|have|has|had|can|could|will|would|shall|should|may|might|must|isn't|aren't|wasn't|weren't|don't|doesn't|didn't|haven't|hasn't|hadn't|can't|couldn't|won't|wouldn't|shan't|shouldn't|mightn't|mustn't";
    private static final Pattern POLAR = Pattern.compile("^(?:" + AUX + ")\\b");
    private static final Pattern INTRO = Pattern.compile("^(?:(?:well|so|okay|ok|hey|please|then)[, ]+)+");
    private static final Pattern TAG = Pattern.compile(",\\s*(?:" + AUX + ")\\s+(?:i|you|he|she|it|we|they|there)\\s*$");
    private static final Pattern OR = Pattern.compile("\\bor\\b");
    private static final String OPEN = "\"'\u201c\u2018([{ ";
    private static final String CLOSE = "\"'\u201d\u2019)]} ";

    static boolean shouldRise(String text) {
        if (text == null) return false;
        String s = text.trim().toLowerCase(Locale.ROOT).replace('\u2019', '\'').replace('\uff1f', '?');
        while (!s.isEmpty() && CLOSE.indexOf(s.charAt(s.length()-1)) >= 0) s = s.substring(0, s.length()-1).trim();
        boolean question = false;
        while (!s.isEmpty() && (s.endsWith("?") || s.endsWith("!"))) {
            question |= s.endsWith("?"); s = s.substring(0, s.length()-1).trim();
        }
        if (!question) return false;
        while (!s.isEmpty() && OPEN.indexOf(s.charAt(0)) >= 0) s = s.substring(1).trim();
        s = INTRO.matcher(s).replaceFirst("");
        // Explicit alternative questions normally fall on the last choice.
        if (TAG.matcher(s).find()) return true;
        if (OR.matcher(s).find() && !s.endsWith("or not")) return false;
        return POLAR.matcher(s).find();
    }
}
