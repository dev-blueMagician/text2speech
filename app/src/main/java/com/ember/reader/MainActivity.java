package com.ember.reader;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int TEXT = 21;
    private ReaderController controller;
    private EditText text;
    private SeekBar speed;
    private Button read, pause, stop, importText;
    private TextView status, count, speedLabel;
    private ProgressBar progress;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshStatus = new Runnable() {
        @Override public void run() {
            if (controller != null && controller.busy) status.setText(controller.displayStatus());
            statusHandler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        try {
            ((TextView) findViewById(R.id.app_version)).setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { /* Own package is always present. */ }
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        text = findViewById(R.id.reader_text);
        speed = findViewById(R.id.speed); speedLabel = findViewById(R.id.speed_label);
        read = findViewById(R.id.read); pause = findViewById(R.id.pause); stop = findViewById(R.id.stop);
        importText = findViewById(R.id.import_text);
        status = findViewById(R.id.status);
        count = findViewById(R.id.character_count); progress = findViewById(R.id.progress);
        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        text.setText(settings.getString("text", getString(R.string.sample_text)));
        speed.setProgress(settings.getInt("speed", 2));
        updateCount(); updateSpeed();
        text.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateCount(); }
            @Override public void afterTextChanged(Editable editable) {}
        });
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) { updateSpeed(); }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        try {
            controller = ((ReaderApplication) getApplication()).reader();
        }
        catch (Exception e) {
            status.setText("Could not initialize the model catalog: " + e.getMessage());
            read.setEnabled(false); pause.setEnabled(false); stop.setEnabled(false); return;
        }
        controller.observer = this::render;
        read.setOnClickListener(v -> {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(text.getWindowToken(), 0);
            controller.read(text.getText().toString(), speedValue());
        });
        pause.setOnClickListener(v -> controller.togglePause());
        stop.setOnClickListener(v -> controller.stop());
        importText.setOnClickListener(v -> chooseFile(TEXT, "text/*"));
        acceptShared(getIntent());
        render();
    }
    private void chooseFile(int request, String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, request); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No file picker is available on this device.", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || controller == null) return;
        if (request == TEXT) controller.importText(data.getData());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptShared(intent); }
    private void acceptShared(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                if (shared.length() > TextChunks.MAX_INPUT) Toast.makeText(this, "Showing the first 20,000 characters.", Toast.LENGTH_LONG).show();
                text.setText(shared.substring(0, Math.min(shared.length(), TextChunks.MAX_INPUT)));
            }
        }
    }
    private float speedValue() { return 0.8f + speed.getProgress() * 0.1f; }
    private void updateSpeed() { speedLabel.setText(String.format(Locale.ROOT, "Reading speed · %.1f×", speedValue())); }
    private void updateCount() { count.setText(String.format(Locale.ROOT, "%,d / 20,000", text.length())); }
    private void render() {
        if (controller == null || isFinishing() || isDestroyed()) return;
        if (controller.importedText != null) { text.setText(controller.importedText); controller.importedText = null; }
        boolean busy = controller.busy;
        if (busy) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText(controller.displayStatus());
        read.setEnabled(!busy);
        pause.setEnabled(busy && controller.playing); pause.setText(controller.paused ? "Resume" : "Pause");
        stop.setEnabled(busy); importText.setEnabled(!busy);
        text.setEnabled(!busy);
        speed.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE); progress.setProgress(controller.progress);
        progress.setIndeterminate(busy && controller.progress == 0);
    }
    @Override protected void onResume() {
        super.onResume();
        statusHandler.removeCallbacks(refreshStatus);
        statusHandler.post(refreshStatus);
    }
    @Override protected void onPause() {
        super.onPause();
        statusHandler.removeCallbacks(refreshStatus);
        if (controller != null) controller.stop();
        if (text != null) getPreferences(MODE_PRIVATE).edit().putString("text", text.getText().toString())
                .putInt("speed", speed.getProgress()).apply();
    }
    @Override protected void onDestroy() {
        if (controller != null) {
            controller.observer = null;
        }
        super.onDestroy();
    }
}
