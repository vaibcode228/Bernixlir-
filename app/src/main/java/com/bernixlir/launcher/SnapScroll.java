package com.bernixlir.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

public class SnapScroll extends HorizontalScrollView {
    public Runnable onScroll;
    public int startPage = 0;
    private boolean flung;

    public SnapScroll(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
    }

    int pages() {
        ViewGroup g = (ViewGroup) getChildAt(0);
        return g == null ? 1 : g.getChildCount();
    }

    public int page() {
        int w = getWidth();
        return w == 0 ? 0 : Math.round((float) getScrollX() / w);
    }

    @Override
    protected void onSizeChanged(final int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        ViewGroup g = (ViewGroup) getChildAt(0);
        if (g != null) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp.width != w) {
                    lp.width = w;
                    v.setLayoutParams(lp);
                }
            }
        }
        if (startPage > 0) {
            final int sp = startPage;
            startPage = 0;
            post(new Runnable() {
                public void run() {
                    scrollTo(sp * w, 0);
                }
            });
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (onScroll != null) onScroll.run();
    }

    @Override
    public void fling(int vx) {
        flung = true;
        int w = getWidth();
        if (w == 0) return;
        int cur = getScrollX();
        int pg;
        if (vx > 800) pg = (int) Math.floor((float) cur / w) + 1;
        else if (vx < -800) pg = (int) Math.ceil((float) cur / w) - 1;
        else pg = Math.round((float) cur / w);
        goTo(pg);
    }

    public void goTo(int pg) {
        pg = Math.max(0, Math.min(pages() - 1, pg));
        smoothScrollTo(pg * getWidth(), 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) flung = false;
        boolean r = super.onTouchEvent(e);
        int a = e.getActionMasked();
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !flung) goTo(page());
        return r;
    }
}
