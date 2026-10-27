package com.ember.reader;

import android.os.SystemClock;

/** One standard Kokoro voice with the model's natural prosody. */
public final class FastSpeech implements AutoCloseable {
    static { System.loadLibrary("ember_fast"); }
    private long context;
    public long lastGenerationMs;
    public void load(String directory) {
        if (context == 0) context = loadNative(directory, Math.min(4, Runtime.getRuntime().availableProcessors()));
        if (context == 0) throw new IllegalStateException("Could not load the natural-reading voice.");
    }
    public boolean isLoaded() { return context != 0; }
    public float[] generate(String text, boolean risingQuestion) {
        if (context == 0) throw new IllegalStateException("Load the natural-reading voice first.");
        long started = SystemClock.elapsedRealtime();
        // Original blended US English voice; playback speed never changes synthesis.
        float[] samples = synthesizeNative(context, text, 0, risingQuestion);
        lastGenerationMs = SystemClock.elapsedRealtime() - started;
        return samples;
    }
    public static native void cancel();
    public static native void resetCancellation();
    private static native long loadNative(String directory, int threads);
    private static native float[] synthesizeNative(long context, String text, int speaker, boolean risingQuestion);
    private static native void unloadNative(long context);
    @Override public void close() {
        if (context != 0) { unloadNative(context); context = 0; }
    }
}
