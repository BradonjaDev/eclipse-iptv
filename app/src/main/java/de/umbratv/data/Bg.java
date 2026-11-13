package de.umbratv.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hintergrundarbeit und Rueckkehr auf den Bedien-Thread. */
public final class Bg {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bg() {
    }

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    public static void ui(Runnable r) {
        MAIN.post(r);
    }

    public static void uiDelayed(Runnable r, long ms) {
        MAIN.postDelayed(r, ms);
    }

    public static void cancel(Runnable r) {
        MAIN.removeCallbacks(r);
    }
}
