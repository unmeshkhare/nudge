package com.graviton.nudge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Random;

public class MsgWidget extends AppWidgetProvider {
    static final String REFRESH = "com.graviton.nudge.REFRESH";

    static final String[] MSGS = {
        "Tiny steps count. Pick one 25-min thing.",
        "Future you says thanks in advance.",
        "Just 25 minutes. Then snacks.",
        "One task. That's the whole plan.",
        "Lazy is fine. Starting is the only hard part.",
        "Press start. I'll cheer.",
        "Your streak is watching 👀",
        "Small win now, big smile later.",
        "You don't have to feel ready. Just begin.",
        "Do the tiny version first.",
        "Boop! Time for one little task?",
        "Pip believes in you. Mostly.",
        "A 25-minute sprint beats a 2-hour worry.",
        "Scratch card energy: go earn one 🎟️",
        "Your future self is peeking at today's list.",
        "Hydrate, then conquer.",
        "Done is better than perfect. Go!",
        "Five minutes in, it gets easy. Promise."
    };

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (REFRESH.equals(i.getAction())) {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, MsgWidget.class));
            onUpdate(c, m, ids);
        }
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_msg);
        String stats = "Open Nudge to get started";
        String next = "";
        try {
            String raw = c.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                    .getString("nudge.widget", null);
            if (raw != null) {
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
            }
        } catch (Exception ignored) { }

        v.setTextViewText(R.id.stats, stats);
        v.setTextViewText(R.id.msg, MSGS[new Random().nextInt(MSGS.length)]);
        v.setTextViewText(R.id.next, next);

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
