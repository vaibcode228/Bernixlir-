package com.bernixlir.launcher;

import android.graphics.*;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import java.util.HashMap;

public class IconFactory {
    static final HashMap<String, Bitmap> cache = new HashMap<>();

    public static Bitmap get(AppInfo a, Prefs p, int size) {
        int shape = p.i("shape"), scale = p.i("iconScale");
        boolean unify = p.b("unify");
        int plate = p.i("plateColor");
        String k = a.key + "|" + shape + "|" + size + "|" + scale + "|" + unify + "|" + plate;
        Bitmap b = cache.get(k);
        if (b != null) return b;
        if (cache.size() > 800) cache.clear();
        b = render(a.icon, shape, scale, unify, plate, size);
        cache.put(k, b);
        return b;
    }

    static Bitmap render(Drawable d, int shape, int scale, boolean unify, int plate, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = scale / 100f;
        try {
            if (d instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
                int ext = (int) (size * 1.5f * s);
                int off = (size - ext) / 2;
                Drawable bg = ad.getBackground();
                Drawable fg = ad.getForeground();
                if (bg != null) {
                    bg.setBounds(off, off, off + ext, off + ext);
                    bg.draw(c);
                }
                if (fg != null) {
                    fg.setBounds(off, off, off + ext, off + ext);
                    fg.draw(c);
                }
            } else {
                int in;
                if (unify) {
                    c.drawColor(plate);
                    in = (int) (size * 0.66f * s);
                } else {
                    in = (int) (size * s);
                }
                int off = (size - in) / 2;
                d.setBounds(off, off, off + in, off + in);
                d.draw(c);
            }
        } catch (Exception ignored) {
        }
        Path inv = shapePath(shape, size);
        inv.setFillType(Path.FillType.INVERSE_WINDING);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawPath(inv, pt);
        return bmp;
    }

    static Path shapePath(int shape, float s) {
        Path p = new Path();
        float r = s / 2f;
        switch (shape) {
            case 0:
                p.addCircle(r, r, r, Path.Direction.CW);
                break;
            case 1: {
                int n = 120;
                for (int i = 0; i <= n; i++) {
                    double t = 2 * Math.PI * i / n;
                    double ct = Math.cos(t), st = Math.sin(t);
                    float x = (float) (r + r * Math.signum(ct) * Math.pow(Math.abs(ct), 0.5));
                    float y = (float) (r + r * Math.signum(st) * Math.pow(Math.abs(st), 0.5));
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
                break;
            }
            case 2:
                p.addRoundRect(new RectF(0, 0, s, s), s * 0.28f, s * 0.28f, Path.Direction.CW);
                break;
            case 3:
                p.addRoundRect(new RectF(0, 0, s, s), s * 0.08f, s * 0.08f, Path.Direction.CW);
                break;
            case 4: {
                float big = s * 0.5f, small = s * 0.08f;
                p.addRoundRect(new RectF(0, 0, s, s),
                        new float[]{big, big, big, big, small, small, big, big}, Path.Direction.CW);
                break;
            }
            default: {
                for (int i = 0; i < 6; i++) {
                    double ang = Math.toRadians(60 * i - 90);
                    float x = (float) (r + r * Math.cos(ang));
                    float y = (float) (r + r * Math.sin(ang));
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
            }
        }
        return p;
    }
}
