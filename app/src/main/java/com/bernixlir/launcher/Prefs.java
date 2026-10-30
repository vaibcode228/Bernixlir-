package com.bernixlir.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class Prefs {
    public static boolean dirty = false;
    static final HashMap<String, Object> DEF = new HashMap<>();

    static {
        // home
        DEF.put("cols", 4);
        DEF.put("rows", 5);
        DEF.put("iconSize", 56);
        DEF.put("iconScale", 100);
        DEF.put("shape", 1);
        DEF.put("unify", true);
        DEF.put("plateColor", 0xFFFFFFFF);
        DEF.put("showLabels", true);
        DEF.put("labelSize", 12);
        DEF.put("labelColor", 0xFFFFFFFF);
        DEF.put("labelShadow", true);
        DEF.put("hPad", 8);
        DEF.put("dim", 20);
        DEF.put("indicator", true);
        DEF.put("hideStatus", false);
        DEF.put("homeSearch", false);
        // clock
        DEF.put("clock", true);
        DEF.put("clock24", true);
        DEF.put("clockDate", true);
        DEF.put("clockSize", 56);
        DEF.put("clockAlign", 1);
        DEF.put("clockColor", 0xFFFFFFFF);
        // dock
        DEF.put("dock", true);
        DEF.put("dockCount", 4);
        DEF.put("dockAlpha", 40);
        DEF.put("dockRadius", 28);
        DEF.put("dockColor", 0xFF000000);
        // drawer
        DEF.put("drawerCols", 4);
        DEF.put("drawerList", false);
        DEF.put("sort", 0);
        DEF.put("drawerSearch", true);
        DEF.put("drawerKeyboard", false);
        DEF.put("drawerBg", 0xFF101014);
        DEF.put("drawerAlpha", 92);
        // gestures: 0 nothing, 1 drawer, 2 notifications, 3 settings
        DEF.put("swipeUp", 1);
        DEF.put("swipeDown", 2);
        DEF.put("doubleTap", 0);
        DEF.put("longPress", 3);
        // anim
        DEF.put("animMs", 250);
        DEF.put("pressAnim", true);
        // colors
        DEF.put("accent", 0xFF4C8DFF);
    }

    final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    public int i(String k) {
        return sp.getInt(k, (Integer) DEF.get(k));
    }

    public boolean b(String k) {
        return sp.getBoolean(k, (Boolean) DEF.get(k));
    }

    public void put(String k, int v) {
        sp.edit().putInt(k, v).apply();
        dirty = true;
    }

    public void put(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
        dirty = true;
    }

    public List<String> list(String k) {
        String raw = sp.getString(k, "");
        List<String> l = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return l;
        for (String s : raw.split("\n")) if (!s.isEmpty()) l.add(s);
        return l;
    }

    public void putList(String k, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(s).append('\n');
        sp.edit().putString(k, sb.toString()).apply();
        dirty = true;
    }

    public void reset() {
        sp.edit().clear().apply();
        dirty = true;
    }
}
