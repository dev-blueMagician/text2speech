package com.ember.reader;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Buffers complete passages before playback; all model work stays on one worker. */
public final class ReaderController implements AutoCloseable {
    public interface Observer { void changed(); }
    private interface AudioSink { void onAudio(float[] samples) throws Exception; }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService playback = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Object audioLock = new Object();
    private final ModelFiles fastModels;
    private FastSpeech fastEngine;
    private boolean fastNativeReady;
    private final AudioCache cache = new AudioCache(24_000L * 120);
    private AudioTrack track;
    private boolean closed;
    private volatile boolean producerDone;
    public volatile boolean busy, paused, playing;
    public volatile int progress;
    public volatile String status;
    private volatile long statusSince = android.os.SystemClock.elapsedRealtime();
    public volatile String importedText;
    public Observer observer;

    public ReaderController(Context context) throws Exception {
        this.context = context.getApplicationContext();
        fastModels = new ModelFiles(context, "fast-model-manifest.json", "fast-model");
        status = "Ready to read.";
    }
    private void notifyChange() { main.post(() -> { if (observer != null) observer.changed(); }); }
    private void update(String text, int value) {
        if (!text.equals(status)) statusSince = android.os.SystemClock.elapsedRealtime();
        status = text; progress = value; notifyChange();
    }
    private static String duration(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m " + (seconds % 60) + "s";
    }
    public String displayStatus() {
        String current = status;
        return busy && !paused ? current + " · " + duration(android.os.SystemClock.elapsedRealtime() - statusSince) : current;
    }
    private boolean begin(String message) {
        if (busy || closed) return false;
        cancelled.set(false); busy = true; paused = false; playing = false;
        update(message, 0); return true;
    }
    private void finish() { busy = false; paused = false; playing = false; notifyChange(); }
    private void check() { if (cancelled.get()) throw new CancellationException(); }
    private void error(Throwable e) {
        if (cancelled.get() || e instanceof CancellationException) update("Stopped.", 0);
        else update(e.getMessage() == null ? "Something went wrong. Try again." : e.getMessage(), 0);
    }

    public void importText(Uri uri) {
        if (!begin("Opening text…")) return;
        worker.submit(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Could not open this text file.");
                // Bound bytes as well as characters, even for a provider with unknown file size.
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    check();
                    if (bytes.size() + count > 80000) throw new IOException("Choose a UTF-8 text file with at most 20,000 characters.");
                    bytes.write(buffer, 0, count);
                }
                String text = bytes.toString(StandardCharsets.UTF_8.name());
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                if (text.length() > TextChunks.MAX_INPUT) throw new IOException("Use up to 20,000 characters at a time.");
                importedText = text;
                update("Text opened. Press Read aloud.", 0);
            } catch (Exception e) { error(e); }
            finally { finish(); }
        });
    }

    public void read(String text, float speed) {
        final List<TextChunks.Passage> chunks;
        try {
            chunks = TextChunks.prepare(text);
        } catch (Exception e) { update(e.getMessage(), 0); return; }
        if (!begin("Preparing voice…")) return;
        // Clear native cancellation before queueing work, so Stop can never be lost during load.
        try {
            FastSpeech.resetCancellation(); fastNativeReady = true;
        } catch (LinkageError e) {
            update("Could not open the speech engine on this device.", 0);
            finish(); return;
        }
        worker.submit(() -> {
            Future<?> audio = null;
            long setupMs = 0, modelLoadMs = 0, generationMs = 0;
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes())
                    .setOnAudioFocusChangeListener(change -> { if (change < 0) stop(); }, main).build();
            try {
                check();
                boolean allCached = true;
                for (TextChunks.Passage chunk : chunks) if (cache.get(chunk.cacheKey()) == null) { allCached = false; break; }
                if (!allCached) {
                    if (!fastModels.installed()) {
                        update("Setting up the included voice…", 0);
                        long started = android.os.SystemClock.elapsedRealtime();
                        fastModels.installBundled(cancelled, this::update);
                        setupMs = android.os.SystemClock.elapsedRealtime() - started;
                    }
                    check();
                    if (fastEngine == null) fastEngine = new FastSpeech();
                    if (!fastEngine.isLoaded()) {
                        update("Preparing the voice…", 0);
                        long started = android.os.SystemClock.elapsedRealtime();
                        fastEngine.load(fastModels.directory().getAbsolutePath());
                        modelLoadMs = android.os.SystemClock.elapsedRealtime() - started;
                    }
                }
                check();
                if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    throw new IllegalStateException("Audio is busy. Try again when the other audio has stopped.");
                BlockingQueue<float[]> queue = new ArrayBlockingQueue<>(2);
                producerDone = false;
                Future<?> consumer = playback.submit(() -> play(queue, speed));
                audio = consumer;
                AudioSink sink = samples -> {
                    check();
                    if (consumer.isDone()) {
                        consumer.get();
                        throw new IllegalStateException("Speech playback ended unexpectedly.");
                    }
                    while (!queue.offer(samples, 100, TimeUnit.MILLISECONDS)) {
                        check();
                        if (consumer.isDone()) {
                            consumer.get();
                            throw new IllegalStateException("Speech playback ended unexpectedly.");
                        }
                    }
                };
                for (int i = 0; i < chunks.size(); i++) {
                    check();
                    if (audio.isDone()) audio.get();
                    update("Preparing passage " + (i + 1) + " of " + chunks.size() + "…", i * 100 / chunks.size());
                    TextChunks.Passage passage = chunks.get(i);
                    float[] samples = cache.get(passage.cacheKey());
                    if (samples == null) {
                        samples = fastEngine.generate(passage.text, passage.risingQuestion);
                        generationMs += fastEngine.lastGenerationMs;
                        check();
                        if (samples == null) throw new IllegalStateException("The voice did not produce audio. Try again.");
                        cache.put(passage.cacheKey(), samples);
                    }
                    sink.onAudio(samples);
                    check();
                }
                producerDone = true;
                update(paused ? "Paused." : "Reading…", 100);
                audio.get();
                check();
                update("Finished reading. " + (setupMs > 0 ? "First-use setup: " + duration(setupMs) + ". " : "")
                        + "Voice preparation: " + duration(modelLoadMs)
                        + ". Speech creation: " + duration(generationMs) + ".", 100);
            } catch (Exception | LinkageError | OutOfMemoryError e) {
                cancelled.set(true);
                cancelNative();
                Throwable reason = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                if (reason instanceof OutOfMemoryError) update("Not enough memory for this model. Close other apps and try again.", 0);
                else if (e instanceof CancellationException || "Stopping…".equals(status)) update("Stopped.", 0);
                else update(reason.getMessage() == null ? "Speech generation failed." : reason.getMessage(), 0);
            } finally {
                producerDone = true;
                synchronized (audioLock) { if (track != null) { track.pause(); track.flush(); } audioLock.notifyAll(); }
                if (audio != null) {
                    try { audio.get(); } catch (Exception ignored) { /* The error is shown above. */ }
                }
                manager.abandonAudioFocusRequest(focus);
                finish();
            }
        });
    }
    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }
    private void play(BlockingQueue<float[]> queue, float speed) {
        AudioTrack local = null;
        try {
            int min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
            if (min <= 0) throw new IllegalStateException("This device could not initialize speech playback.");
            local = new AudioTrack.Builder().setAudioAttributes(attributes())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(24000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                    .setBufferSizeInBytes(Math.max(min * 2, 24000)).setTransferMode(AudioTrack.MODE_STREAM).build();
            local.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1.0f));
            synchronized (audioLock) { track = local; }
            long written = 0;
            while (!producerDone || !queue.isEmpty()) {
                check();
                float[] samples = queue.poll(100, TimeUnit.MILLISECONDS);
                if (samples == null) continue;
                playing = true; notifyChange();
                int offset = 0;
                while (offset < samples.length) {
                    awaitResume();
                    synchronized (audioLock) { check(); if (local.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) local.play(); }
                    int count = local.write(samples, offset, Math.min(2048, samples.length - offset), AudioTrack.WRITE_BLOCKING);
                    if (count <= 0) { check(); throw new IllegalStateException("Speech playback was interrupted."); }
                    offset += count; written += count;
                }
            }
            while (Integer.toUnsignedLong(local.getPlaybackHeadPosition()) < written) {
                awaitResume(); check(); Thread.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new CancellationException();
        } finally {
            synchronized (audioLock) {
                track = null;
                if (local != null) { local.pause(); local.flush(); local.release(); }
                playing = false;
            }
        }
    }
    private void awaitResume() throws InterruptedException {
        check();
        synchronized (audioLock) { while (paused && !cancelled.get()) audioLock.wait(100); }
        check();
    }
    public void togglePause() {
        synchronized (audioLock) {
            if (!busy || !playing || cancelled.get()) return;
            paused = !paused;
            if (track != null) { if (paused) track.pause(); else track.play(); }
            audioLock.notifyAll();
        }
        update(paused ? "Paused." : "Reading…", progress);
    }
    public void stop() {
        if (!busy) return;
        cancelled.set(true); cancelNative();
        synchronized (audioLock) {
            paused = false;
            if (track != null) { track.pause(); track.flush(); }
            audioLock.notifyAll();
        }
        update("Stopping…", progress);
    }
    public void releaseModelIfIdle() {
        if (closed || busy) return;
        worker.submit(() -> {
            if (!busy) {
                if (fastEngine != null) { fastEngine.close(); fastEngine = null; }
                cache.clear();
            }
        });
    }
    private void cancelNative() {
        if (fastNativeReady) FastSpeech.cancel();
    }
    @Override public void close() {
        if (closed) return;
        closed = true; observer = null; stop();
        worker.submit(() -> {
            if (fastEngine != null) { fastEngine.close(); fastEngine = null; }
            cache.clear();
        });
        worker.shutdown(); playback.shutdown();
    }
}
