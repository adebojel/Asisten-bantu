package com.asisten.bantu;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Daftar semua aplikasi yang bisa dibuka di HP (yang punya ikon peluncur), terurut menurut nama. */
public final class AppLauncher {
    private AppLauncher() {}

    public static List<AppMatcher.App> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, AppMatcher.App> byPkg = new LinkedHashMap<>();
        try {
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (byPkg.containsKey(pkg)) continue;
                CharSequence label = ri.loadLabel(pm);
                byPkg.put(pkg, new AppMatcher.App(label == null ? pkg : label.toString(), pkg));
            }
        } catch (Exception ignored) { }
        List<AppMatcher.App> out = new ArrayList<>(byPkg.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }
}
