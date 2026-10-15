package com.graviton.nudge;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Keeps a focus / sprint / break timer alive with the screen off:
 *  - foreground service with a live countdown notification
 *  - generated ambient loops (rain, wind, chimes) played gaplessly
 *  - rings a real alarm when time is up, until the user stops it
 *  - feeds the home-screen widget with the running timer
 */
public class TimerService extends Service {
    static final String ACT_SET = "com.graviton.nudge.TIMER_SET";
    static final String ACT_CLEAR = "com.graviton.nudge.TIMER_CLEAR";
    static final String ACT_END = "com.graviton.nudge.TIMER_END";
    static final String ACT_STOP_ALARM = "com.graviton.nudge.ALARM_STOP";

    static final String PREFS = "nudge_timer";
    static final String CH_RUN = "nudge_timer_run";
    static final String CH_ALARM = "nudge_timer_alarm";
    static final int NOTIF_ID = 4101;
    static final long RING_MAX_MS = 180000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable endTask;
    private Runnable ringStopTask;
    private AudioTrack amb;
    private MediaPlayer ring;
    private PowerManager.WakeLock wl;
    private boolean ringing = false;

    private String kind = "focus";
    private long endAt = 0L;
    private boolean paused = false;
    private long remainMs = 0L;
    private String label = "";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel run = new NotificationChannel(CH_RUN, "Running timer", NotificationManager.IMPORTANCE_LOW);
            run.setSound(null, null);
            run.enableVibration(false);
            nm.createNotificationChannel(run);
            NotificationChannel al = new NotificationChannel(CH_ALARM, "Timer alarm", NotificationManager.IMPORTANCE_HIGH);
            al.setSound(null, null);
            al.enableVibration(false);
            nm.createNotificationChannel(al);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        String a = in == null ? null : in.getAction();
        if (a == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACT_SET.equals(a)) {
            handleSet(in);
        } else if (ACT_END.equals(a)) {
            handleEnd();
        } else if (ACT_STOP_ALARM.equals(a)) {
            stopAlarm();
        } else if (ACT_CLEAR.equals(a)) {
            if (!ringing) clearAll();
        }
        return START_NOT_STICKY;
    }

    /* ---------- commands ---------- */

    private void handleSet(Intent in) {
        stopRingSound();
        ringing = false;
        kind = in.getStringExtra("kind");
        if (kind == null) kind = "focus";
        endAt = in.getLongExtra("endAt", 0L);
        paused = in.getBooleanExtra("paused", false);
        remainMs = in.getLongExtra("remainMs", 0L);
        label = in.getStringExtra("label");
        if (label == null) label = "";
        String snd = in.getStringExtra("snd");
        if (snd == null) snd = "off";
        int vol = in.getIntExtra("vol", 60);

        goForeground(runNotification());
        cancelEnd();
        stopAmb();

        long now = System.currentTimeMillis();
        if (!paused && endAt <= now + 800L) {
            clearAll();
            return;
        }
        saveState(true);
        if (paused) {
            releaseWl();
        } else {
            scheduleEnd();
            acquireWl(endAt - now + 20000L);
            if (!"off".equals(snd) && !"break".equals(kind)) startAmb(snd, vol);
        }
        writeWidget(false);
    }

    private void handleEnd() {
        if (ringing) return;
        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("active", false) || p.getBoolean("paused", false)) {
            goForeground(runNotification());
            clearAll();
            return;
        }
        kind = p.getString("kind", "focus");
        endAt = p.getLong("endAt", 0L);
        label = p.getString("label", "");
        paused = false;
        startRinging();
    }

    private void startRinging() {
        if (ringing) return;
        ringing = true;
        cancelEnd();
        stopAmb();
        acquireWl(RING_MAX_MS + 10000L);
        goForeground(alarmNotification());
        startRingSound();
        ringStopTask = new Runnable() {
            @Override
            public void run() {
                stopAlarm();
            }
        };
        h.postDelayed(ringStopTask, RING_MAX_MS);
        writeWidget(true);
    }

    private void stopAlarm() {
        stopRingSound();
        ringing = false;
        clearAll();
    }

    private void clearAll() {
        cancelEnd();
        stopAmb();
        stopRingSound();
        releaseWl();
        saveState(false);
        try {
            getSharedPreferences(MsgWidget.PREFS, Context.MODE_PRIVATE).edit().remove("timer").apply();
        } catch (Exception ignored) { }
        MsgWidget.refreshAll(this);
        try {
            stopForeground(true);
        } catch (Exception ignored) { }
        stopSelf();
    }

    /* ---------- state + widget ---------- */

    private void saveState(boolean active) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("active", active)
                .putString("kind", kind)
                .putLong("endAt", endAt)
                .putBoolean("paused", paused)
                .putString("label", label)
                .apply();
    }

    private void writeWidget(boolean ringNow) {
        try {
            JSONObject o = new JSONObject();
            o.put("kind", kind);
            o.put("endAt", endAt);
            o.put("paused", paused);
            o.put("remainMs", remainMs);
            o.put("label", label);
            o.put("ring", ringNow);
            getSharedPreferences(MsgWidget.PREFS, Context.MODE_PRIVATE).edit().putString("timer", o.toString()).apply();
        } catch (Exception ignored) { }
        MsgWidget.refreshAll(this);
    }

    /* ---------- scheduling ---------- */

    private PendingIntent endPendingIntent() {
        Intent i = new Intent(this, TimerService.class);
        i.setAction(ACT_END);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) return PendingIntent.getForegroundService(this, 77, i, fl);
        return PendingIntent.getService(this, 77, i, fl);
    }

    private void scheduleEnd() {
        long delay = Math.max(0L, endAt - System.currentTimeMillis());
        endTask = new Runnable() {
            @Override
            public void run() {
                startRinging();
            }
        };
        h.postDelayed(endTask, delay);
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, endPendingIntent());
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, endPendingIntent());
            }
        } catch (Throwable ignored) { }
    }

    private void cancelEnd() {
        if (endTask != null) h.removeCallbacks(endTask);
        endTask = null;
        if (ringStopTask != null) h.removeCallbacks(ringStopTask);
        ringStopTask = null;
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(endPendingIntent());
        } catch (Throwable ignored) { }
    }

    private void acquireWl(long ms) {
        try {
            if (wl == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nudge:timer");
                wl.setReferenceCounted(false);
            }
            wl.acquire(Math.max(5000L, ms));
        } catch (Throwable ignored) { }
    }

    private void releaseWl() {
        try {
            if (wl != null && wl.isHeld()) wl.release();
        } catch (Throwable ignored) { }
    }

    /* ---------- notifications ---------- */

    private Notification.Builder builder(String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(this, channel);
        return new Notification.Builder(this);
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private String kindName() {
        if ("sprint".equals(kind)) return "Sprint";
        if ("break".equals(kind)) return "Break";
        return "Focus";
    }

    private Notification runNotification() {
        String title = kindName() + (label.isEmpty() || label.equals(kindName()) ? "" : " · " + label);
        Notification.Builder b = builder(CH_RUN)
                .setSmallIcon(R.drawable.ic_stat_nudge)
                .setContentTitle(title)
                .setOngoing(true)
                .setContentIntent(openAppIntent());
        if (paused) {
            long s = Math.max(0L, remainMs / 1000L);
            b.setContentText("Paused · " + (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60) + " left");
            b.setShowWhen(false);
        } else {
            b.setContentText("Counting down. Tap to open Nudge.");
            b.setUsesChronometer(true);
            b.setChronometerCountDown(true);
            b.setWhen(endAt > 0 ? endAt : System.currentTimeMillis());
        }
        return b.build();
    }

    private Notification alarmNotification() {
        Intent s = new Intent(this, TimerService.class);
        s.setAction(ACT_STOP_ALARM);
        PendingIntent stop = PendingIntent.getService(this, 78, s, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = label.isEmpty() ? kindName() + " finished" : label;
        Notification.Builder b = builder(CH_ALARM)
                .setSmallIcon(R.drawable.ic_stat_nudge)
                .setContentTitle("⏰ Time's up!")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(openAppIntent())
                .addAction(0, "STOP ALARM", stop);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        return b.build();
    }

    private void goForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    /* ---------- ambient sound ---------- */

    private void startAmb(String which, int vol) {
        stopAmb();
        int res = getResources().getIdentifier("amb_" + which, "raw", getPackageName());
        if (res == 0) res = R.raw.amb_chimes;
        try {
            InputStream in = getResources().openRawResource(res);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            byte[] all = bo.toByteArray();
            int len = all.length - 44;
            if (len <= 0) return;
            byte[] pcm = new byte[len];
            System.arraycopy(all, 44, pcm, 0, len);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(22050)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(len)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(pcm, 0, len);
            t.setLoopPoints(0, len / 2, -1);
            float v = Math.max(0f, Math.min(1f, (vol / 100f) * 0.85f));
            t.setVolume(v);
            t.play();
            amb = t;
        } catch (Throwable e) {
            amb = null;
        }
    }

    private void stopAmb() {
        AudioTrack t = amb;
        amb = null;
        if (t == null) return;
        try {
            t.stop();
        } catch (Throwable ignored) { }
        try {
            t.release();
        } catch (Throwable ignored) { }
    }

    /* ---------- alarm sound ---------- */

    private void startRingSound() {
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (u != null) {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(this, u);
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                ring = mp;
            }
        } catch (Throwable e) {
            ring = null;
        }
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = new long[]{0, 700, 500, 700, 500, 700, 1500};
            if (v != null) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0));
                else v.vibrate(pattern, 0);
            }
        } catch (Throwable ignored) { }
    }

    private void stopRingSound() {
        MediaPlayer mp = ring;
        ring = null;
        if (mp != null) {
            try {
                mp.stop();
            } catch (Throwable ignored) { }
            try {
                mp.release();
            } catch (Throwable ignored) { }
        }
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.cancel();
        } catch (Throwable ignored) { }
    }

    @Override
    public void onDestroy() {
        cancelEnd();
        stopAmb();
        stopRingSound();
        releaseWl();
        super.onDestroy();
    }
}
