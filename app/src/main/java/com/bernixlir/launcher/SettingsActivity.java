package com.bernixlir.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton;
import java.util.*;

public class SettingsActivity extends Activity {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final String[] SHAPES = {"Круг", "Сквиркл", "Скруглённый квадрат", "Квадрат", "Капля", "Шестиугольник"};
    static final String[] SORTS = {"А–Я", "Я–А", "Сначала новые"};
    static final String[] ALIGN = {"Слева", "По центру", "Справа"};
    static final String[] ACTIONS = {"Ничего", "Список приложений", "Шторка уведомлений", "Настройки лаунчера"};

    Prefs p;
    LinearLayout box;
    int accent;

    int dp(float v) {
        return U.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        if (AppRepo.all.isEmpty()) AppRepo.load(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF121214);
        sv.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(24), dp(16), dp(40));
        sv.addView(box, new ViewGroup.LayoutParams(MATCH, WRAP));
        setContentView(sv);
        build();
    }

    void build() {
        box.removeAllViews();
        accent = p.i("accent");

        TextView h = new TextView(this);
        h.setText("BernixLir Launcher");
        h.setTextColor(Color.WHITE);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        h.setPadding(dp(4), dp(8), 0, dp(8));
        box.addView(h);

        action("Сделать лаунчером по умолчанию", "Откроет системные настройки домашнего экрана", new Runnable() {
            public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
        });

        section("Рабочий стол");
        seek("Столбцы", "cols", 3, 7, "");
        seek("Строки", "rows", 3, 8, "");
        seek("Размер иконок", "iconSize", 40, 96, " dp");
        seek("Масштаб иконки внутри формы", "iconScale", 60, 140, "%");
        choice("Форма иконок", "shape", SHAPES);
        sw("Подложка для обычных (не адаптивных) иконок", "unify");
        color("Цвет подложки", "plateColor");
        sw("Подписи под иконками", "showLabels");
        seek("Размер подписей", "labelSize", 9, 18, " sp");
        color("Цвет подписей", "labelColor");
        sw("Тень у подписей", "labelShadow");
        seek("Боковые отступы", "hPad", 0, 32, " dp");
        seek("Затемнение обоев", "dim", 0, 80, "%");
        sw("Индикатор страниц", "indicator");
        sw("Скрыть строку состояния", "hideStatus");
        sw("Строка поиска на рабочем столе", "homeSearch");

        section("Часы");
        sw("Показывать часы", "clock");
        sw("24-часовой формат", "clock24");
        sw("Показывать дату", "clockDate");
        seek("Размер часов", "clockSize", 24, 96, " sp");
        choice("Выравнивание", "clockAlign", ALIGN);
        color("Цвет часов", "clockColor");

        section("Док");
        sw("Показывать док", "dock");
        seek("Иконок в доке", "dockCount", 3, 6, "");
        seek("Прозрачность фона", "dockAlpha", 0, 100, "%");
        seek("Скругление", "dockRadius", 0, 40, " dp");
        color("Цвет дока", "dockColor");

        section("Список приложений");
        seek("Столбцы", "drawerCols", 3, 6, "");
        sw("Режим списка (вместо сетки)", "drawerList");
        choice("Сортировка", "sort", SORTS);
        sw("Строка поиска", "drawerSearch");
        sw("Клавиатура при открытии", "drawerKeyboard");
        color("Цвет фона", "drawerBg");
        seek("Непрозрачность фона", "drawerAlpha", 50, 100, "%");

        section("Жесты");
        choice("Свайп вверх", "swipeUp", ACTIONS);
        choice("Свайп вниз", "swipeDown", ACTIONS);
        choice("Двойной тап", "doubleTap", ACTIONS);
        choice("Долгое нажатие на пустом месте", "longPress", ACTIONS);

        section("Анимации");
        seek("Скорость списка приложений", "animMs", 0, 600, " мс");
        sw("Сжатие иконки при нажатии", "pressAnim");

        section("Цвета");
        color("Акцентный цвет", "accent");

        section("Приложения");
        action("Скрытые приложения", "Выбрать, какие приложения скрыть из списка", new Runnable() {
            public void run() {
                hiddenDialog();
            }
        });
        action("Очистить рабочий стол", "Уберёт все иконки со страниц и из дока", new Runnable() {
            public void run() {
                p.putList("home", new ArrayList<String>());
                p.putList("dock", new ArrayList<String>());
                build();
            }
        });
        action("Сбросить все настройки", "Вернёт значения по умолчанию", new Runnable() {
            public void run() {
                new AlertDialog.Builder(SettingsActivity.this).setTitle("Сбросить всё?")
                        .setPositiveButton("Сбросить", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                p.reset();
                                build();
                            }
                        }).setNegativeButton("Отмена", null).show();
            }
        });
    }

    // ---------- row builders ----------
    TextView text(String s, float sp, int col) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(col);
        return t;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(8), dp(12), dp(8), dp(12));
        box.addView(l, new LinearLayout.LayoutParams(MATCH, WRAP));
        return l;
    }

    void section(String s) {
        TextView t = text(s.toUpperCase(Locale.getDefault()), 12, accent);
        t.setPadding(dp(8), dp(28), 0, dp(4));
        box.addView(t);
    }

    void action(String title, String sub, final Runnable r) {
        LinearLayout l = row();
        l.addView(text(title, 16, Color.WHITE));
        l.addView(text(sub, 13, 0x99FFFFFF));
        l.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                r.run();
            }
        });
    }

    void sw(String title, final String key) {
        LinearLayout l = row();
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 16, Color.WHITE);
        l.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final Switch s = new Switch(this);
        s.setChecked(p.b(key));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) {
                p.put(key, c);
            }
        });
        l.addView(s);
        l.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                s.toggle();
            }
        });
    }

    void seek(String title, final String key, final int min, int max, final String unit) {
        LinearLayout l = row();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(text(title, 16, Color.WHITE), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView val = text(p.i(key) + unit, 14, accent);
        top.addView(val);
        l.addView(top);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(p.i(key) - min);
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int pr, boolean user) {
                val.setText((pr + min) + unit);
                if (user) p.put(key, pr + min);
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        l.addView(sb);
    }

    void choice(String title, final String key, final String[] opts) {
        LinearLayout l = row();
        l.addView(text(title, 16, Color.WHITE));
        int cur = Math.max(0, Math.min(opts.length - 1, p.i(key)));
        l.addView(text(opts[cur], 13, 0x99FFFFFF));
        final String t = title;
        l.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this).setTitle(t)
                        .setSingleChoiceItems(opts, p.i(key), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                p.put(key, w);
                                d.dismiss();
                                build();
                            }
                        }).show();
            }
        });
    }

    void color(final String title, final String key) {
        LinearLayout l = row();
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(text(title, 16, Color.WHITE), new LinearLayout.LayoutParams(0, WRAP, 1f));
        View sw = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(p.i(key));
        g.setStroke(dp(2), 0x88FFFFFF);
        sw.setBackground(g);
        l.addView(sw, new LinearLayout.LayoutParams(dp(28), dp(28)));
        l.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                colorDialog(title, key);
            }
        });
    }

    void colorDialog(String title, final String key) {
        final int[] presets = {
                0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFF101014, 0xFFFF5252, 0xFFFF9800,
                0xFFFFEB3B, 0xFF4CAF50, 0xFF00BCD4, 0xFF4C8DFF, 0xFF7C4DFF, 0xFFE91E63,
                0xFFFFB4AB, 0xFFFFD8A8, 0xFFB9F6CA, 0xFFA7D8FF, 0xFFD1C4E9, 0xFFF48FB1
        };
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(12), dp(20), 0);
        final AlertDialog[] dlg = new AlertDialog[1];
        for (int r = 0; r < presets.length / 6; r++) {
            LinearLayout rw = new LinearLayout(this);
            rw.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 6; c++) {
                final int col = presets[r * 6 + c];
                View s = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(col);
                g.setStroke(dp(1), 0x88888888);
                s.setBackground(g);
                s.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        p.put(key, col);
                        dlg[0].dismiss();
                        build();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                rw.addView(s, lp);
            }
            wrap.addView(rw, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        final EditText hex = new EditText(this);
        hex.setHint("#RRGGBB или #AARRGGBB");
        hex.setSingleLine(true);
        hex.setText(String.format("#%08X", p.i(key)));
        wrap.addView(hex, new LinearLayout.LayoutParams(MATCH, WRAP));
        dlg[0] = new AlertDialog.Builder(this).setTitle(title).setView(wrap)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            p.put(key, Color.parseColor(hex.getText().toString().trim()));
                        } catch (Exception ignored) {
                        }
                        build();
                    }
                }).setNegativeButton("Отмена", null).create();
        dlg[0].show();
    }

    void hiddenDialog() {
        final List<AppInfo> all = AppRepo.all;
        final String[] names = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        Set<String> hid = new HashSet<>(p.list("hidden"));
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).label;
            checked[i] = hid.contains(all.get(i).key);
        }
        new AlertDialog.Builder(this).setTitle("Скрытые приложения")
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface d, int i, boolean c) {
                        checked[i] = c;
                    }
                })
                .setPositiveButton("Готово", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        List<String> out = new ArrayList<>();
                        List<String> home = p.list("home"), dock = p.list("dock");
                        for (int i = 0; i < all.size(); i++) {
                            if (checked[i]) {
                                String k = all.get(i).key;
                                out.add(k);
                                home.remove(k);
                                dock.remove(k);
                            }
                        }
                        p.putList("hidden", out);
                        p.putList("home", home);
                        p.putList("dock", dock);
                    }
                }).setNegativeButton("Отмена", null).show();
    }
}
