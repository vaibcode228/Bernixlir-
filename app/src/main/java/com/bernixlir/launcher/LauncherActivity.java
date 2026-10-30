package com.bernixlir.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.view.GestureDetector;
import java.util.*;

public class LauncherActivity extends Activity {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    Prefs p;
    Root root;
    GestureDetector gd;
    SnapScroll pager;
    LinearLayout dots, drawer;
    GridView grid;
    EditText search;
    DrawerAdapter adapter;
    boolean drawerOpen;
    int insTop, insBottom, lastDot = -1;
    BroadcastReceiver pkgReceiver;
    final List<AppInfo> shown = new ArrayList<>();

    class Root extends FrameLayout {
        Root(Context c) {
            super(c);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            gd.onTouchEvent(e);
            return super.dispatchTouchEvent(e);
        }
    }

    int dp(float v) {
        return U.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        AppRepo.load(this);
        ensureInit();

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY(), dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < dp(80) || Math.abs(dy) < Math.abs(dx) * 1.5f) return false;
                if (drawerOpen) {
                    if (dy > 0 && grid != null && !grid.canScrollVertically(-1)) closeDrawer();
                    return false;
                }
                if (dy < 0) doAction(p.i("swipeUp"));
                else doAction(p.i("swipeDown"));
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!drawerOpen) doAction(p.i("doubleTap"));
                return false;
            }
        });
        gd.setIsLongpressEnabled(false);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);

        root = new Root(this);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                int t = ins.getStableInsetTop(), bt = ins.getStableInsetBottom();
                if (t != insTop || bt != insBottom) {
                    insTop = t;
                    insBottom = bt;
                    rebuild();
                }
                return ins;
            }
        });
        setContentView(root);
        rebuild();

        pkgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                AppRepo.load(c);
                prune();
                rebuildKeepPage();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.dirty) {
            Prefs.dirty = false;
            ensureInit();
            rebuild();
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (drawerOpen) closeDrawer();
        else if (pager != null) pager.goTo(0);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        rebuild();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
    }

    // ---------- first run ----------
    void ensureInit() {
        if (p.sp.getBoolean("inited", false) || AppRepo.all.isEmpty()) return;
        List<String> dock = new ArrayList<>(), home = new ArrayList<>();
        Intent[] probes = {
                new Intent(Intent.ACTION_DIAL),
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        };
        PackageManager pm = getPackageManager();
        for (Intent i : probes) {
            ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) continue;
            for (AppInfo a : AppRepo.all) {
                if (a.pkg.equals(ri.activityInfo.packageName)) {
                    if (!dock.contains(a.key)) dock.add(a.key);
                    break;
                }
            }
        }
        for (AppInfo a : AppRepo.all) {
            if (home.size() >= 20) break;
            if (!dock.contains(a.key)) home.add(a.key);
        }
        p.putList("dock", dock);
        p.putList("home", home);
        p.sp.edit().putBoolean("inited", true).apply();
    }

    void prune() {
        for (String k : new String[]{"home", "dock"}) {
            List<String> l = p.list(k);
            List<String> out = new ArrayList<>();
            for (String s : l) if (AppRepo.byKey.containsKey(s)) out.add(s);
            if (out.size() != l.size()) p.putList(k, out);
        }
        Prefs.dirty = false;
    }

    List<AppInfo> resolve(List<String> keys) {
        List<AppInfo> out = new ArrayList<>();
        for (String k : keys) {
            AppInfo a = AppRepo.byKey.get(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    // ---------- build UI ----------
    void rebuildKeepPage() {
        int pg = pager != null ? pager.page() : 0;
        rebuild();
        if (pager != null) pager.startPage = pg;
    }

    void rebuild() {
        if (root == null) return;
        drawerOpen = false;
        lastDot = -1;
        root.removeAllViews();

        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(p.i("dim") * 255 / 100, 0, 0, 0));
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int hp = dp(p.i("hPad"));
        content.setPadding(hp, insTop, hp, insBottom + dp(10));
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));

        if (p.b("clock")) content.addView(buildClock());

        buildPager();
        content.addView(pager, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        int pages = ((ViewGroup) pager.getChildAt(0)).getChildCount();
        buildDots(pages);
        dots.setVisibility(p.b("indicator") ? View.VISIBLE : View.GONE);
        content.addView(dots, new LinearLayout.LayoutParams(MATCH, WRAP));

        if (p.b("homeSearch")) content.addView(buildSearchPill());
        if (p.b("dock")) content.addView(buildDock());

        buildDrawer();
        root.addView(drawer, new FrameLayout.LayoutParams(MATCH, MATCH));
        drawer.setVisibility(View.GONE);

        int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (p.b("hideStatus")) f |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        root.setSystemUiVisibility(f);
    }

    View buildClock() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int al = p.i("clockAlign");
        l.setGravity(al == 0 ? Gravity.START : al == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(16), dp(24), dp(16), dp(8));
        int col = p.i("clockColor");

        TextClock t = new TextClock(this);
        String fmt = p.b("clock24") ? "HH:mm" : "h:mm";
        t.setFormat24Hour(fmt);
        t.setFormat12Hour(fmt);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.i("clockSize"));
        t.setTextColor(col);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setShadowLayer(8, 0, 2, 0x66000000);
        l.addView(t, new LinearLayout.LayoutParams(WRAP, WRAP));

        if (p.b("clockDate")) {
            TextClock d = new TextClock(this);
            d.setFormat24Hour("EEEE, d MMMM");
            d.setFormat12Hour("EEEE, d MMMM");
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            d.setTextColor(col);
            d.setShadowLayer(6, 0, 1, 0x66000000);
            l.addView(d, new LinearLayout.LayoutParams(WRAP, WRAP));
        }
        return l;
    }

    void bgLong(View v) {
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View x) {
                x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                doAction(p.i("longPress"));
                return true;
            }
        });
    }

    void buildPager() {
        int cols = p.i("cols"), rows = p.i("rows"), cap = cols * rows;
        List<AppInfo> items = resolve(p.list("home"));
        int pages = Math.max(1, (items.size() + cap - 1) / cap);
        pager = new SnapScroll(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(inner, new FrameLayout.LayoutParams(WRAP, MATCH));
        for (int pg = 0; pg < pages; pg++) {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            bgLong(page);
            for (int r = 0; r < rows; r++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int c = 0; c < cols; c++) {
                    int idx = pg * cap + r * cols + c;
                    View cell;
                    if (idx < items.size()) {
                        cell = makeCell(items.get(idx), true, p.i("iconSize"));
                    } else {
                        cell = new View(this);
                        bgLong(cell);
                    }
                    row.addView(cell, new LinearLayout.LayoutParams(0, MATCH, 1f));
                }
                page.addView(row, new LinearLayout.LayoutParams(MATCH, 0, 1f));
            }
            inner.addView(page, new LinearLayout.LayoutParams(dp(300), MATCH));
        }
        pager.onScroll = new Runnable() {
            public void run() {
                updateDots();
            }
        };
    }

    void buildDots(int pages) {
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, dp(4), 0, dp(8));
        for (int i = 0; i < pages; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(7), dp(7));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(d, lp);
        }
        updateDots();
    }

    void updateDots() {
        if (dots == null || pager == null) return;
        int cur = pager.page();
        if (cur == lastDot) return;
        lastDot = cur;
        for (int i = 0; i < dots.getChildCount(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == cur ? p.i("accent") : 0x55FFFFFF);
            dots.getChildAt(i).setBackground(g);
        }
    }

    View buildSearchPill() {
        TextView t = new TextView(this);
        t.setText("Поиск приложений");
        t.setTextColor(0xCCFFFFFF);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(20), 0, dp(20), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(26));
        g.setColor(0x44000000);
        t.setBackground(g);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openDrawer(true);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(48));
        lp.setMargins(dp(8), dp(4), dp(8), dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    View buildDock() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        d.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor((p.i("dockColor") & 0x00FFFFFF) | ((p.i("dockAlpha") * 255 / 100) << 24));
        g.setCornerRadius(dp(p.i("dockRadius")));
        d.setBackground(g);
        d.setPadding(dp(8), dp(10), dp(8), dp(10));
        List<AppInfo> items = resolve(p.list("dock"));
        int n = Math.min(items.size(), p.i("dockCount"));
        for (int i = 0; i < n; i++) {
            d.addView(makeCell(items.get(i), false, p.i("iconSize")), new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(4);
        d.setLayoutParams(lp);
        return d;
    }

    // ---------- cells ----------
    View makeCell(final AppInfo a, boolean showLabel, int iconDp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(IconFactory.get(a, p, dp(iconDp)));
        l.addView(iv, new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        if (showLabel && p.b("showLabels")) {
            TextView t = new TextView(this);
            t.setText(a.label);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.i("labelSize"));
            t.setTextColor(p.i("labelColor"));
            t.setMaxLines(1);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            if (p.b("labelShadow")) t.setShadowLayer(4, 0, 1, 0xAA000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.topMargin = dp(4);
            l.addView(t, lp);
        }
        attach(l, a);
        return l;
    }

    View makeListCell(AppInfo a) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(12), dp(6), dp(12), dp(6));
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(IconFactory.get(a, p, dp(44)));
        l.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = new TextView(this);
        t.setText(a.label);
        t.setTextSize(16);
        t.setTextColor(Color.WHITE);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp.leftMargin = dp(16);
        l.addView(t, lp);
        attach(l, a);
        return l;
    }

    void attach(final View v, final AppInfo a) {
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                launch(a, x);
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View x) {
                x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showMenu(a);
                return true;
            }
        });
        if (p.b("pressAnim")) {
            v.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View x, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                    else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
                        x.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    return false;
                }
            });
        }
    }

    void launch(AppInfo a, View v) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(a.pkg, a.cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Bundle o = null;
            if (v != null)
                o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, Math.max(1, v.getWidth()), Math.max(1, v.getHeight())).toBundle();
            startActivity(i, o);
            if (drawerOpen) {
                root.postDelayed(new Runnable() {
                    public void run() {
                        closeDrawerNow();
                    }
                }, 350);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- menu ----------
    void showMenu(final AppInfo a) {
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        final String k = a.key;
        final boolean inHome = p.list("home").contains(k), inDock = p.list("dock").contains(k);

        if (!inHome) {
            names.add("На рабочий стол");
            acts.add(new Runnable() {
                public void run() {
                    List<String> h = p.list("home");
                    h.add(k);
                    p.putList("home", h);
                    refresh();
                }
            });
        } else {
            names.add("Убрать с рабочего стола");
            acts.add(new Runnable() {
                public void run() {
                    List<String> h = p.list("home");
                    h.remove(k);
                    p.putList("home", h);
                    refresh();
                }
            });
            names.add("Сдвинуть назад");
            acts.add(new Runnable() {
                public void run() {
                    move(k, -1);
                }
            });
            names.add("Сдвинуть вперёд");
            acts.add(new Runnable() {
                public void run() {
                    move(k, 1);
                }
            });
        }
        if (!inDock) {
            names.add("В док");
            acts.add(new Runnable() {
                public void run() {
                    List<String> d = p.list("dock");
                    if (d.size() >= p.i("dockCount")) {
                        Toast.makeText(LauncherActivity.this, "Док заполнен", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    d.add(k);
                    p.putList("dock", d);
                    refresh();
                }
            });
        } else {
            names.add("Убрать из дока");
            acts.add(new Runnable() {
                public void run() {
                    List<String> d = p.list("dock");
                    d.remove(k);
                    p.putList("dock", d);
                    refresh();
                }
            });
        }
        names.add("Скрыть приложение");
        acts.add(new Runnable() {
            public void run() {
                List<String> hid = p.list("hidden");
                if (!hid.contains(k)) hid.add(k);
                p.putList("hidden", hid);
                List<String> h = p.list("home");
                h.remove(k);
                p.putList("home", h);
                List<String> d = p.list("dock");
                d.remove(k);
                p.putList("dock", d);
                refresh();
            }
        });
        names.add("О приложении");
        acts.add(new Runnable() {
            public void run() {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
            }
        });
        names.add("Удалить");
        acts.add(new Runnable() {
            public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                } catch (Exception ignored) {
                }
            }
        });
        names.add("Настройки лаунчера");
        acts.add(new Runnable() {
            public void run() {
                openSettings();
            }
        });

        new AlertDialog.Builder(this).setTitle(a.label)
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        acts.get(w).run();
                    }
                }).show();
    }

    void move(String k, int dir) {
        List<String> h = p.list("home");
        int i = h.indexOf(k), j = i + dir;
        if (i < 0 || j < 0 || j >= h.size()) return;
        Collections.swap(h, i, j);
        p.putList("home", h);
        refresh();
    }

    void refresh() {
        Prefs.dirty = false;
        boolean open = drawerOpen;
        rebuildKeepPage();
        if (open) {
            // keep drawer closed after rebuild; data refresh happens on next open
        }
    }

    // ---------- actions ----------
    void doAction(int a) {
        switch (a) {
            case 1:
                openDrawer(false);
                break;
            case 2:
                expandNotifications();
                break;
            case 3:
                openSettings();
                break;
            default:
                break;
        }
    }

    void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class<?> c = Class.forName("android.app.StatusBarManager");
            c.getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть шторку", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- drawer ----------
    void buildDrawer() {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor((p.i("drawerBg") & 0x00FFFFFF) | ((p.i("drawerAlpha") * 255 / 100) << 24));
        drawer.setPadding(dp(8), insTop + dp(12), dp(8), insBottom);
        drawer.setClickable(true);

        search = null;
        if (p.b("drawerSearch")) {
            search = new EditText(this);
            search.setHint("Поиск приложений");
            search.setHintTextColor(0x99FFFFFF);
            search.setTextColor(Color.WHITE);
            search.setSingleLine(true);
            search.setTextSize(16);
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            search.setPadding(dp(20), 0, dp(20), 0);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(26));
            g.setColor(0x33FFFFFF);
            search.setBackground(g);
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void afterTextChanged(Editable s) {
                    refreshDrawerData(s.toString());
                }
            });
            search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView v, int id, android.view.KeyEvent e) {
                    if (!shown.isEmpty()) launch(shown.get(0), null);
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(48));
            lp.setMargins(dp(8), 0, dp(8), dp(8));
            drawer.addView(search, lp);
        }

        grid = new GridView(this);
        boolean list = p.b("drawerList");
        grid.setNumColumns(list ? 1 : p.i("drawerCols"));
        grid.setVerticalSpacing(dp(10));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new DrawerAdapter();
        grid.setAdapter(adapter);
        drawer.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        refreshDrawerData("");
    }

    void refreshDrawerData(String q) {
        shown.clear();
        Set<String> hid = new HashSet<>(p.list("hidden"));
        String ql = q == null ? "" : q.toLowerCase(Locale.getDefault()).trim();
        List<AppInfo> src = new ArrayList<>(AppRepo.all);
        final int sort = p.i("sort");
        if (sort == 1) {
            Collections.reverse(src);
        } else if (sort == 2) {
            Collections.sort(src, new Comparator<AppInfo>() {
                public int compare(AppInfo x, AppInfo y) {
                    return Long.compare(y.installTime, x.installTime);
                }
            });
        }
        for (AppInfo a : src) {
            if (hid.contains(a.key)) continue;
            if (!ql.isEmpty() && !a.label.toLowerCase(Locale.getDefault()).contains(ql)) continue;
            shown.add(a);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    class DrawerAdapter extends BaseAdapter {
        public int getCount() {
            return shown.size();
        }

        public Object getItem(int i) {
            return shown.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int pos, View cv, ViewGroup parent) {
            AppInfo a = shown.get(pos);
            if (p.b("drawerList")) return makeListCell(a);
            View v = makeCell(a, true, p.i("iconSize"));
            v.setPadding(0, dp(6), 0, dp(6));
            return v;
        }
    }

    void openDrawer(boolean focus) {
        if (drawerOpen || drawer == null) return;
        drawerOpen = true;
        if (search != null) search.setText("");
        refreshDrawerData("");
        grid.setSelection(0);
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(root.getHeight());
        drawer.setAlpha(0f);
        drawer.animate().translationY(0).alpha(1f).setDuration(p.i("animMs"))
                .setInterpolator(new DecelerateInterpolator()).setListener(null).start();
        if (search != null && (focus || p.b("drawerKeyboard"))) {
            search.requestFocus();
            search.postDelayed(new Runnable() {
                public void run() {
                    InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (im != null) im.showSoftInput(search, 0);
                }
            }, 250);
        }
    }

    void hideIme() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null && root != null) im.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    void closeDrawer() {
        if (!drawerOpen || drawer == null) return;
        drawerOpen = false;
        hideIme();
        drawer.animate().translationY(root.getHeight()).alpha(0f).setDuration(p.i("animMs"))
                .withEndAction(new Runnable() {
                    public void run() {
                        if (!drawerOpen) drawer.setVisibility(View.GONE);
                    }
                }).start();
    }

    void closeDrawerNow() {
        if (drawer == null) return;
        drawerOpen = false;
        hideIme();
        drawer.animate().cancel();
        drawer.setVisibility(View.GONE);
    }
}
