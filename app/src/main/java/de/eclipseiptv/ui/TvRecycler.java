package de.eclipseiptv.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Liste fuer die Bedienung mit der Fernbedienung: merkt sich die zuletzt gewaehlte
 * Zeile, haelt den Fokus beim schnellen Blaettern in der Liste und rueckt die
 * gewaehlte Zeile in die Mitte.
 */
public class TvRecycler extends RecyclerView {
    public interface OnSelect {
        void onSelect(int position);
    }

    private int selected = 0;
    private boolean lockAxis;
    private boolean vertical = true;
    private OnSelect onSelect;

    public TvRecycler(Context c) {
        super(c);
        setItemAnimator(null);
        setHasFixedSize(true);
        setClipChildren(false);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(false);
    }

    public TvRecycler vertical() {
        vertical = true;
        setLayoutManager(new Linear(getContext(), VERTICAL));
        return this;
    }

    public TvRecycler horizontal() {
        vertical = false;
        setLayoutManager(new Linear(getContext(), HORIZONTAL));
        return this;
    }

    public TvRecycler grid(int columns) {
        vertical = true;
        setLayoutManager(new Grid(getContext(), columns));
        return this;
    }

    /** Fokus verlaesst die Liste nicht in Blaetter-Richtung (oben/unten bei senkrechten Listen). */
    public TvRecycler lockAxis(boolean lock) {
        lockAxis = lock;
        return this;
    }

    public void onSelect(OnSelect l) {
        onSelect = l;
    }

    public int selected() {
        return selected;
    }

    public void select(int position) {
        selected = Math.max(0, position);
    }

    /** Springt zu einer Zeile und gibt ihr, falls gewuenscht, den Fokus. */
    public void jumpTo(final int position, final boolean focus) {
        selected = Math.max(0, position);
        LayoutManager lm = getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            int size = vertical ? getHeight() : getWidth();
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(selected, size > 0 ? size / 3 : 0);
        } else {
            scrollToPosition(selected);
        }
        if (focus) {
            post(new Runnable() {
                @Override
                public void run() {
                    ViewHolder h = findViewHolderForAdapterPosition(selected);
                    if (h != null) h.itemView.requestFocus();
                }
            });
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int pos = getChildAdapterPosition(child);
        if (pos != NO_POSITION && pos != selected) {
            selected = pos;
            if (onSelect != null) onSelect.onSelect(pos);
        } else if (pos != NO_POSITION && onSelect != null) {
            onSelect.onSelect(pos);
        }
    }

    /** Kommt der Fokus von aussen, landet er auf der gemerkten Zeile statt auf der naechstgelegenen. */
    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (!hasFocus()) {
            ViewHolder h = findViewHolderForAdapterPosition(selected);
            if (h != null && h.itemView.isFocusable() && h.itemView.getVisibility() == VISIBLE) {
                views.add(h.itemView);
                return;
            }
        }
        super.addFocusables(views, direction, focusableMode);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previous) {
        ViewHolder h = findViewHolderForAdapterPosition(selected);
        if (h != null && h.itemView.requestFocus()) return true;
        return super.onRequestFocusInDescendants(direction, previous);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        View next = super.focusSearch(focused, direction);
        boolean along = vertical ? (direction == FOCUS_UP || direction == FOCUS_DOWN)
                : (direction == FOCUS_LEFT || direction == FOCUS_RIGHT);
        if (!along || next == null || isInside(next)) return next;
        int dir = (direction == FOCUS_UP || direction == FOCUS_LEFT) ? -1 : 1;
        boolean canScroll = vertical ? canScrollVertically(dir) : canScrollHorizontally(dir);
        // Beim schnellen Blaettern ist die naechste Zeile evtl. noch nicht aufgebaut: Fokus halten.
        if (canScroll || lockAxis) return focused;
        return next;
    }

    private boolean isInside(View v) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == this) return true;
            p = p.getParent();
        }
        return false;
    }

    static boolean center(RecyclerView parent, View child, boolean vertical, boolean immediate) {
        int delta;
        if (vertical) {
            delta = child.getTop() + child.getHeight() / 2 - (parent.getHeight() / 2);
        } else {
            delta = child.getLeft() + child.getWidth() / 2 - (parent.getWidth() / 2);
        }
        if (delta == 0) return false;
        if (immediate) parent.scrollBy(vertical ? 0 : delta, vertical ? delta : 0);
        else parent.smoothScrollBy(vertical ? 0 : delta, vertical ? delta : 0);
        return true;
    }

    static final class Linear extends LinearLayoutManager {
        Linear(Context c, int orientation) {
            super(c, orientation, false);
        }

        @Override
        public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect, boolean immediate, boolean focusedChildVisible) {
            return center(parent, child, getOrientation() == VERTICAL, immediate);
        }
    }

    static final class Grid extends GridLayoutManager {
        Grid(Context c, int columns) {
            super(c, columns);
        }

        @Override
        public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect, boolean immediate, boolean focusedChildVisible) {
            return center(parent, child, true, immediate);
        }
    }
}
