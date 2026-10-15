package com.graviton.nudge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Random;

/**
 * Home-screen widget: the companion (with a mood and a simple 2-frame animation),
 * a random message, streak/level/coins and the next task.
 * The app writes the data (JSON) through NudgeNative.setWidget().
 */
public class MsgWidget extends AppWidgetProvider {
    static final String REFRESH = "com.graviton.nudge.REFRESH";
    static final String PREFS = "nudge_widget";
    static final String[] MOODS = {"happy", "excited", "love", "sleepy", "focus", "worried", "sad", "surprised"};

    static final String[] FALLBACK = {
        "Tiny steps count. Pick one 25-min thing.",
        "Future you says thanks in advance.",
        "Just 25 minutes. Then snacks.",
        "One task. That's the whole plan.",
        "Lazy is fine. Starting is the only hard part.",
        "Press start. I'll cheer.",
        "Your streak is watching 👀",
        "Small win now, big smile later."
    };

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (REFRESH.equals(i.getAction())) refreshAll(c);
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, MsgWidget.class));
        for (int id : ids) update(c, m, id);
    }

    static int moodIndex(String mood) {
        for (int i = 0; i < MOODS.length; i++) if (MOODS[i].equals(mood)) return i;
        return 0;
    }

    static String pick(JSONObject msgs, String mood) {
        Random rnd = new Random();
        try {
            if (msgs != null) {
                JSONArray a = msgs.optJSONArray(mood);
                if (a == null || a.length() == 0) a = msgs.optJSONArray("happy");
                if (a != null && a.length() > 0) return a.getString(rnd.nextInt(a.length()));
            }
        } catch (Exception ignored) { }
        return FALLBACK[rnd.nextInt(FALLBACK.length)];
    }

    static void setFrames(Context c, RemoteViews v, String ch, int mood) {
        try {
            String name = "char_" + (ch != null && ch.matches("[a-z]+") ? ch : "pip");
            int res = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
            if (res == 0) res = R.drawable.char_pip;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap sheet = BitmapFactory.decodeResource(c.getResources(), res, o);
            if (sheet == null) return;
            int cell = sheet.getWidth() / 8;
            v.setImageViewBitmap(R.id.f1, Bitmap.createBitmap(sheet, mood * cell, 0, cell, cell));
            v.setImageViewBitmap(R.id.f2, Bitmap.createBitmap(sheet, mood * cell, cell, cell, cell));
        } catch (Throwable ignored) { }
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_msg);
        String stats = "Open Nudge to get started";
        String next = "";
        String mood = "happy";
        String ch = "pip";
        JSONObject msgs = null;
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", null);
            if (raw != null && !raw.isEmpty()) {
                JSONObject o = new JSONObject(raw);
                int streak = o.optInt("streak");
                stats = "🔥 " + streak + (streak == 1 ? " day" : " days")
                        + "  ·  Lv " + o.optInt("lvl")
                        + "  ·  🪙 " + o.optInt("coins");
                int open = o.optInt("open");
                String n = o.optString("next");
                if (open > 0) {
                    next = "Next: " + n + (open > 1 ? "  (+" + (open - 1) + " more)" : "");
                } else if (o.optInt("done") > 0) {
                    next = "All done today 🎉";
                } else {
                    next = "No tasks yet — add one!";
                }
                mood = o.optString("mood", "happy");
                ch = o.optString("char", "pip");
                msgs = o.optJSONObject("msgs");
            }
        } catch (Exception ignored) { }

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if ((hour >= 22 || hour < 5) && "happy".equals(mood)) mood = "sleepy";

        // a running timer takes over the widget text
        JSONObject tm = null;
        try {
            String ts = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("timer", null);
            if (ts != null && !ts.isEmpty()) tm = new JSONObject(ts);
        } catch (Exception ignored) { }
        boolean chrono = false;
        String msgText = null;
        long base = 0L;
        if (tm != null) {
            String kind = tm.optString("kind", "focus");
            String label = tm.optString("label", "");
            boolean paused = tm.optBoolean("paused");
            boolean ringNow = tm.optBoolean("ring");
            long endAt = tm.optLong("endAt");
            long remain = tm.optLong("remainMs");
            String kn = "sprint".equals(kind) ? "SPRINT ⚡" : ("break".equals(kind) ? "BREAK ☕" : "FOCUS");
            stats = kn + (label.isEmpty() ? "" : "  ·  " + label);
            next = "Tap to open Nudge";
            if (ringNow) {
                mood = "excited";
                msgText = "⏰ Time's up!";
            } else if (paused) {
                mood = "focus";
                long sec = Math.max(0L, remain / 1000L);
                msgText = "⏸ " + (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
                next = "Paused · tap to open";
            } else {
                mood = "break".equals(kind) ? "happy" : ("sprint".equals(kind) ? "excited" : "focus");
                chrono = true;
                base = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis());
            }
        }

        v.setTextViewText(R.id.stats, stats);
        if (chrono) {
            v.setViewVisibility(R.id.msg, View.GONE);
            v.setViewVisibility(R.id.chrono, View.VISIBLE);
            v.setChronometerCountDown(R.id.chrono, true);
            v.setChronometer(R.id.chrono, base, null, true);
        } else {
            v.setViewVisibility(R.id.chrono, View.GONE);
            v.setViewVisibility(R.id.msg, View.VISIBLE);
            v.setTextViewText(R.id.msg, msgText != null ? msgText : pick(msgs, mood));
        }
        v.setTextViewText(R.id.next, next);
        setFrames(c, v, ch, moodIndex(mood));

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.root,
                PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        Intent r = new Intent(c, MsgWidget.class);
        r.setAction(REFRESH);
        v.setOnClickPendingIntent(R.id.refresh,
                PendingIntent.getBroadcast(c, 1, r, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        m.updateAppWidget(id, v);
    }
}
