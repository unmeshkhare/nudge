package com.graviton.nudge;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;

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
