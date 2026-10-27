package com.ember.reader;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioCacheTest {
    @Test public void repeatedPassageReusesAudioAndEditedTextMisses() {
        AudioCache cache = new AudioCache(8);
        float[] audio = {0.1f, 0.2f};
        cache.put("Hello.", audio);
        assertSame(audio, cache.get("Hello."));
        assertNull(cache.get("Hello!"));
    }
    @Test public void leastRecentlyUsedPassageIsEvictedWithinMemoryBound() {
        AudioCache cache = new AudioCache(4);
        cache.put("first", new float[2]); cache.put("second", new float[2]);
        cache.get("first");
        cache.put("third", new float[2]);
        assertNull(cache.get("second"));
        assertNotNull(cache.get("first")); assertNotNull(cache.get("third"));
    }
    @Test public void replacingAnEntryDoesNotCountOldSamplesTwice() {
        AudioCache cache = new AudioCache(4);
        cache.put("first", new float[3]); cache.put("first", new float[1]);
        cache.put("second", new float[3]);
        assertNotNull(cache.get("first")); assertNotNull(cache.get("second"));
    }
    @Test public void oversizedAudioIsNotRetained() {
        AudioCache cache = new AudioCache(2);
        cache.put("large", new float[3]);
        assertNull(cache.get("large"));
    }
    @Test public void clearReleasesCachedAudio() {
        AudioCache cache = new AudioCache(4);
        cache.put("first", new float[2]); cache.clear();
        assertNull(cache.get("first"));
    }
}
