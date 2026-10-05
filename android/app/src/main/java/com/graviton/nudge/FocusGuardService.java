package com.graviton.nudge;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Focus Shield. While a focus session is running (the web app stores the blocked
 * package list and an end time), opening one of those apps sends the user back home.
 * It only looks at which app came to the front. It never reads screen content.
 */
public class FocusGuardService extends AccessibilityService {
    private long lastAction = 0;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = e.getPackageName();
        if (cs == null) return;
        final String pkg = cs.toString();
        if (pkg.equals(getPackageName())) return;

        SharedPreferences p = getSharedPreferences(NudgeNative.PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (p.getLong("until", 0L) <= now) return;

        String raw = p.getString("pkgs", "");
        if (raw == null || raw.isEmpty()) return;
        Set<String> blocked = new HashSet<>(Arrays.asList(raw.split(",")));
        if (!blocked.contains(pkg)) return;

        if (now - lastAction < 400) return;
        lastAction = now;

        performGlobalAction(GLOBAL_ACTION_HOME);
        p.edit().putInt("count", p.getInt("count", 0) + 1).apply();

        String label = "That app";
        try {
            PackageManager pm = getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) { }
        final String msg = "🔒 " + label + " is blocked until your focus session ends";
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onInterrupt() { }

    static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(s)) return false;
        ComponentName me = new ComponentName(c, FocusGuardService.class);
        TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
        sp.setString(s);
        while (sp.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(sp.next());
            if (me.equals(cn)) return true;
        }
        return false;
    }
}
