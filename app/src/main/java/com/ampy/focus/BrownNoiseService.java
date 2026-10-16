package com.ampy.focus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.IBinder;
import java.util.Random;

/** Locally synthesized, nonlooping noise; plays as a user-started media FGS. */
public final class BrownNoiseService extends Service {
    private static final String CHANNEL = "ampyfocus_audio";
    public static volatile boolean isPlaying;
    public static volatile int selectedColor = AudioSynthesis.BROWN;
    private volatile boolean running;
    private Thread audioThread;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            selectedColor = Math.max(AudioSynthesis.BROWN, Math.min(AudioSynthesis.WHITE,
                    intent.getIntExtra("color", AudioSynthesis.BROWN)));
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "AmpyFocus Audio", NotificationManager.IMPORTANCE_LOW));
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AmpyFocus · Focus Sound")
                .setContentText("Audio offline per la concentrazione")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(210, notification);
        if (!running) {
            running = true;
            isPlaying = true;
            audioThread = new Thread(this::playLoop, "AmpyFocus-BrownNoise");
            audioThread.start();
        }
        return START_NOT_STICKY;
    }

    private void playLoop() {
        final int rate = 48_000;
        int minBytes = AudioTrack.getMinBufferSize(rate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(8192, minBytes);
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
            AudioSynthesis synth = new AudioSynthesis(System.nanoTime());
            short[] pcm = new short[2048];
            track.play();
            while (running && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < pcm.length; i++) {
                    pcm[i] = (short) Math.round(synth.next(selectedColor) * 32767d);
                }
                if (track.write(pcm, 0, pcm.length) < 0) break;
            }
        } catch (RuntimeException ignored) {
            // A missing/busy audio output must never crash policy enforcement.
        } finally {
            if (track != null) {
                try { track.pause(); track.flush(); } catch (RuntimeException ignored) { }
                track.release();
            }
            running = false;
            isPlaying = false;
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        isPlaying = false;
        if (audioThread != null) audioThread.interrupt();
        super.onDestroy();
    }
}
