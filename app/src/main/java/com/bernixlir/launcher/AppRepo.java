package com.bernixlir.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.*;

public class AppRepo {
    public static List<AppInfo> all = new ArrayList<>();
    public static HashMap<String, AppInfo> byKey = new HashMap<>();

    public static void load(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
        List<AppInfo> out = new ArrayList<>();
        HashMap<String, AppInfo> map = new HashMap<>();
        for (ResolveInfo r : l) {
            try {
                AppInfo a = new AppInfo();
                a.pkg = r.activityInfo.packageName;
                a.cls = r.activityInfo.name;
                a.key = a.pkg + "/" + a.cls;
                CharSequence lb = r.loadLabel(pm);
                a.label = lb == null ? a.pkg : lb.toString();
                a.icon = r.loadIcon(pm);
                try {
                    a.installTime = pm.getPackageInfo(a.pkg, 0).firstInstallTime;
                } catch (Exception ignored) {
                }
                if (map.containsKey(a.key)) continue;
                out.add(a);
                map.put(a.key, a);
            } catch (Exception ignored) {
            }
        }
        Collections.sort(out, new Comparator<AppInfo>() {
            public int compare(AppInfo x, AppInfo y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        all = out;
        byKey = map;
    }
}
