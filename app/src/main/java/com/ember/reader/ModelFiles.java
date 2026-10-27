package com.ember.reader;

import android.content.Context;
import android.os.StatFs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports only the pinned, hash-verified model. Unknown archive paths are never extracted. */
public final class ModelFiles {
    public interface Progress { void update(String message, int percent); }
    private final Context context;
    private final String folder;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final String revision;
    private long total;
    private static final class Entry {
        final long size;
        final String sha256;
        Entry(long size, String sha256) { this.size = size; this.sha256 = sha256; }
    }

    public ModelFiles(Context context, String catalog, String folder) throws Exception {
        this.context = context.getApplicationContext();
        this.folder = folder;
        JSONObject manifest;
        try (InputStream source = context.getAssets().open(catalog)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = source.read(buffer)) != -1) bytes.write(buffer, 0, count);
            manifest = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
        revision = manifest.getString("revision");
        JSONArray files = manifest.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            long size = file.getLong("size");
            entries.put(file.getString("path"), new Entry(size, file.getString("sha256")));
            total += size;
        }
        File previous = new File(context.getFilesDir(), folder + "-previous");
        if (!directory().exists() && previous.exists() && !previous.renameTo(directory()))
            throw new IOException("Could not restore the previous voice model.");
    }
    public File directory() { return new File(context.getFilesDir(), folder); }
    public boolean installed() {
        File root = directory();
        try {
            if (!revision.equals(new String(java.nio.file.Files.readAllBytes(new File(root, ".verified").toPath()), StandardCharsets.UTF_8))) return false;
            for (Map.Entry<String, Entry> item : entries.entrySet()) {
                File f = new File(root, item.getKey());
                if (!f.isFile() || f.length() != item.getValue().size) return false;
            }
            return true;
        } catch (IOException e) { return false; }
    }
    public void installBundled(AtomicBoolean cancelled, Progress progress) throws Exception {
        try (InputStream input = context.getAssets().open("fast-voice.zip")) { install(input, cancelled, progress); }
    }
    private void install(InputStream input, AtomicBoolean cancelled, Progress progress) throws Exception {
        File stage = new File(context.getFilesDir(), folder + "-import");
        delete(stage);
        if (new StatFs(context.getFilesDir().getAbsolutePath()).getAvailableBytes() < total + 256L * 1024 * 1024)
            throw new IOException("Free at least " + ((total + 256L * 1024 * 1024) / (1024 * 1024) + 1) + " MB of internal storage to set up this voice.");
        if (!stage.mkdirs()) throw new IOException("Could not prepare model storage.");
        Set<String> seen = new HashSet<>();
        long copied = 0;
        long lastUpdate = 0;
        byte[] buffer = new byte[1024 * 1024];
        try {
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input, buffer.length))) {
                ZipEntry file;
                while ((file = zip.getNextEntry()) != null) {
                    check(cancelled);
                    String name = file.getName();
                    Entry expected = entries.get(name);
                    if (expected == null || file.isDirectory() || !seen.add(name))
                        throw new IOException("This is not the supported Ember model pack: " + name);
                    File output = new File(stage, name);
                    if (!output.getCanonicalPath().startsWith(stage.getCanonicalPath() + File.separator))
                        throw new IOException("Invalid model path.");
                    File parent = output.getParentFile();
                    if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IOException("Cannot create model folder.");
                    MessageDigest hash = MessageDigest.getInstance("SHA-256");
                    long count = 0;
                    try (OutputStream destination = new BufferedOutputStream(new FileOutputStream(output), buffer.length)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            check(cancelled);
                            count += n;
                            if (count > expected.size) throw new IOException("A model file is too large: " + name);
                            hash.update(buffer, 0, n);
                            destination.write(buffer, 0, n);
                            copied += n;
                            long now = android.os.SystemClock.elapsedRealtime();
                            if (now - lastUpdate > 250) {
                                progress.update("Importing and checking model…", (int) (copied * 100 / total));
                                lastUpdate = now;
                            }
                        }
                    }
                    if (count != expected.size || !hex(hash.digest()).equals(expected.sha256))
                        throw new IOException("Model verification failed: " + name + ". Copy the original model pack again.");
                }
            }
            if (seen.size() != entries.size()) throw new IOException("The model pack is incomplete.");
            check(cancelled);
            java.nio.file.Files.write(new File(stage, ".verified").toPath(), revision.getBytes(StandardCharsets.UTF_8));
            File old = new File(context.getFilesDir(), folder + "-previous");
            delete(old);
            File current = directory();
            if (current.exists() && !current.renameTo(old)) throw new IOException("Could not replace the previous model.");
            if (!stage.renameTo(current)) {
                if (old.exists() && !old.renameTo(current)) throw new IOException("Could not restore the previous model.");
                throw new IOException("Could not finish model import.");
            }
            delete(old);
            progress.update("Voice installed. Ready to read.", 100);
        } finally { delete(stage); }
    }
    private static void check(AtomicBoolean cancelled) { if (cancelled.get()) throw new CancellationException(); }
    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
    private static void delete(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (!file.delete()) throw new IOException("Cannot clear temporary model data.");
    }
}
