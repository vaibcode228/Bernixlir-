package com.bernixlir.launcher;

import android.content.Context;

public class U {
    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
