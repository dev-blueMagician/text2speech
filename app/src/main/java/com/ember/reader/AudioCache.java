package com.ember.reader;

import java.util.LinkedHashMap;
import java.util.Map;

/** Worker-owned LRU of immutable PCM passages for the single standard voice. */
final class AudioCache {
    private final long maxSamples;
    private final LinkedHashMap<String, float[]> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long samples;

    AudioCache(long maxSamples) {
        if (maxSamples <= 0) throw new IllegalArgumentException("Cache size must be positive.");
        this.maxSamples = maxSamples;
    }
    float[] get(String text) { return entries.get(text); }
    void put(String text, float[] audio) {
        if (audio == null || audio.length == 0 || audio.length > maxSamples) return;
        float[] previous = entries.put(text, audio);
        if (previous != null) samples -= previous.length;
        samples += audio.length;
        while (samples > maxSamples || entries.size() > 64) {
            Map.Entry<String, float[]> oldest = entries.entrySet().iterator().next();
            samples -= oldest.getValue().length;
            entries.remove(oldest.getKey());
        }
    }
    void clear() { entries.clear(); samples = 0; }
}
