package com.graviton.nudge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.telecom.TelecomManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridge between the Nudge web app and Android:
 * installed-app list, Focus Shield (app blocking), home-screen widget data, sharing.
 */
@CapacitorPlugin(name = "NudgeNative")
public class NudgeNative extends Plugin {
    static final String PREFS = "nudge_block";

    private SharedPreferences blockPrefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @PluginMethod
    public void getApps(PluginCall call) {
        Context c = getContext();
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);

        String dialer = null;
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) dialer = tm.getDefaultDialerPackage();
        } catch (Exception ignored) { }

        Set<String> seen = new HashSet<>();
        List<String[]> out = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            if (pkg.equals(dialer)) continue;
            if (pkg.equals("com.android.settings")) continue;
            if (pkg.contains("emergency")) continue;
            if (!seen.add(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            out.add(new String[]{pkg, label == null ? pkg : label.toString()});
        }
        Collections.sort(out, (a, b) -> a[1].compareToIgnoreCase(b[1]));

        JSArray arr = new JSArray();
        for (String[] o : out) {
            JSObject ob = new JSObject();
            ob.put("pkg", o[0]);
            ob.put("label", o[1]);
            arr.put(ob);
        }
        JSObject ret = new JSObject();
        ret.put("apps", arr);
        call.resolve(ret);
    }

    @PluginMethod
    public void setBlock(PluginCall call) {
        JSONArray pk = call.getData().optJSONArray("packages");
        long until = call.getData().optLong("until", 0L);
        StringBuilder sb = new StringBuilder();
        if (pk != null) {
            for (int i = 0; i < pk.length(); i++) {
                String p = pk.optString(i, "");
                if (p.isEmpty()) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(p);
            }
        }
        blockPrefs().edit().putString("pkgs", sb.toString()).putLong("until", until).apply();
        call.resolve();
    }

    @PluginMethod
    public void clearBlock(PluginCall call) {
        blockPrefs().edit().putLong("until", 0L).apply();
        call.resolve();
    }

    @PluginMethod
    public void blockStatus(PluginCall call) {
        SharedPreferences p = blockPrefs();
        JSObject r = new JSObject();
        r.put("enabled", FocusGuardService.isEnabled(getContext()));
        r.put("active", p.getLong("until", 0L) > System.currentTimeMillis());
        r.put("count", p.getInt("count", 0));
        call.resolve(r);
    }

    @PluginMethod
    public void resetCount(PluginCall call) {
        blockPrefs().edit().putInt("count", 0).apply();
        call.resolve();
    }

    @PluginMethod
    public void openAccessibilitySettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openAppInfo(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void setWidget(PluginCall call) {
        String json = call.getString("json", "");
        getContext().getSharedPreferences(MsgWidget.PREFS, Context.MODE_PRIVATE)
                .edit().putString("json", json).apply();
        call.resolve();
    }

    @PluginMethod
    public void updateWidget(PluginCall call) {
        MsgWidget.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void shareText(PluginCall call) {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("text/plain");
        s.putExtra(Intent.EXTRA_TEXT, call.getString("text", ""));
        s.putExtra(Intent.EXTRA_SUBJECT, call.getString("title", "Nudge backup"));
        Intent chooser = Intent.createChooser(s, "Share backup");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(chooser);
        call.resolve();
    }

    @SuppressWarnings("deprecation")
    @PluginMethod
    public void getInfo(PluginCall call) {
        JSObject r = new JSObject();
        try {
            PackageInfo pi = getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0);
            r.put("version", pi.versionName);
            r.put("code", pi.versionCode);
        } catch (Exception e) {
            r.put("version", "?");
            r.put("code", 0);
        }
        call.resolve(r);
    }
}
