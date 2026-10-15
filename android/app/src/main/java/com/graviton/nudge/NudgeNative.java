package com.graviton.nudge;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;



/**
 * Bridge between the Nudge web app and Android:
 * home-screen widget data, sharing and version info.
 */
@CapacitorPlugin(name = "NudgeNative")
public class NudgeNative extends Plugin {
    @PluginMethod
    public void timerSet(PluginCall call) {
        Context c = getContext();
        String kind = call.getString("kind", "focus");
        long endAt = call.getData().optLong("endAt", 0L);
        boolean paused = call.getData().optBoolean("paused", false);
        long remainMs = call.getData().optLong("remainMs", 0L);
        String label = call.getString("label", "");
        String snd = call.getString("snd", "off");
        int vol = call.getData().optInt("vol", 60);
        Intent i = new Intent(c, TimerService.class);
        i.setAction(TimerService.ACT_SET);
        i.putExtra("kind", kind);
        i.putExtra("endAt", endAt);
        i.putExtra("paused", paused);
        i.putExtra("remainMs", remainMs);
        i.putExtra("label", label);
        i.putExtra("snd", snd);
        i.putExtra("vol", vol);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not start the timer service");
        }
    }

    @PluginMethod
    public void timerClear(PluginCall call) {
        Context c = getContext();
        try {
            Intent i = new Intent(c, TimerService.class);
            i.setAction(TimerService.ACT_CLEAR);
            c.startService(i);
        } catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void alarmStop(PluginCall call) {
        Context c = getContext();
        try {
            Intent i = new Intent(c, TimerService.class);
            i.setAction(TimerService.ACT_STOP_ALARM);
            c.startService(i);
        } catch (Exception ignored) { }
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
            r.put("timer", true);
            r.put("snd2", true);
        } catch (Exception e) {
            r.put("version", "?");
            r.put("code", 0);
        }
        call.resolve(r);
    }
}
