package com.ember.reader;

import android.app.Application;

/** Own the expensive model for the app process, rather than for a single reader screen. */
public final class ReaderApplication extends Application {
    private ReaderController reader;

    public synchronized ReaderController reader() throws Exception {
        if (reader == null) reader = new ReaderController(this);
        return reader;
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (reader != null && (level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND))
            reader.releaseModelIfIdle();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (reader != null) reader.releaseModelIfIdle();
    }
}
