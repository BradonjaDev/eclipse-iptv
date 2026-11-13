package de.umbratv.ui;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

/** Laedt Bilder (Logos, Poster) mit Zwischenspeicher. */
public final class Img {
    private Img() {
    }

    public static void load(ImageView v, String url, int w, int h, boolean crop) {
        if (url == null || url.trim().isEmpty() || !url.startsWith("http")) {
            clear(v);
            return;
        }
        try {
            RequestBuilder<Drawable> r = Glide.with(v.getContext().getApplicationContext()).load(url.trim())
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE).override(Math.max(1, w), Math.max(1, h));
            r = crop ? r.centerCrop() : r.fitCenter();
            r.into(v);
        } catch (RuntimeException e) {
            v.setImageDrawable(null);
        }
    }

    public static void clear(ImageView v) {
        try {
            Glide.with(v.getContext().getApplicationContext()).clear(v);
        } catch (RuntimeException ignored) {
        }
        v.setImageDrawable(null);
    }

    public static Drawable placeholder() {
        return new ColorDrawable(Ui.SURFACE2);
    }
}
