package com.github.tvbox.osc.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.fragment.PlayFragment;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.LOG;

import java.lang.ref.WeakReference;

public class MusicPlaybackService extends Service {
    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_UPDATE = "com.github.tvbox.osc.music.UPDATE";
    private static final String ACTION_PLAY = "com.github.tvbox.osc.music.PLAY";
    private static final String ACTION_PAUSE = "com.github.tvbox.osc.music.PAUSE";
    private static final String ACTION_PREVIOUS = "com.github.tvbox.osc.music.PREVIOUS";
    private static final String ACTION_NEXT = "com.github.tvbox.osc.music.NEXT";
    private static final String ACTION_PLACEHOLDER = "com.github.tvbox.osc.music.PLACEHOLDER";
    private static final String ACTION_STOP = "com.github.tvbox.osc.music.STOP";
    private static final String ACTION_SEEK = "com.github.tvbox.osc.music.SEEK";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_SUBTITLE = "subtitle";
    private static final String EXTRA_ARTWORK = "artwork";
    private static final String EXTRA_POSITION = "position";
    private static final String EXTRA_DURATION = "duration";
    private static final String EXTRA_PLAYING = "playing";
    private static final String EXTRA_SEEK = "seek";

    private static MusicPlaybackService instance;
    private static WeakReference<PlayFragment> owner;

    private MediaSessionCompat mediaSession;
    private PendingIntent sessionActivity;
    private String title = "TVBox";
    private String subtitle = "";
    private String artworkUrl = "";
    private Bitmap artwork;
    private long position;
    private long duration;
    private boolean playing;
    private CustomTarget<Bitmap> artworkTarget;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public static boolean isSupported(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        return !ScreenUtils.isTv(context);
    }

    public static void update(Context context, PlayFragment fragment, String title, String subtitle,
                              String artwork, long position, long duration, boolean playing) {
        if (!isSupported(context)) return;
        owner = new WeakReference<>(fragment);
        Intent intent = new Intent(context, MusicPlaybackService.class).setAction(ACTION_UPDATE);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_ARTWORK, artwork);
        intent.putExtra(EXTRA_POSITION, position);
        intent.putExtra(EXTRA_DURATION, duration);
        intent.putExtra(EXTRA_PLAYING, playing);
        if (instance != null) {
            instance.handleIntent(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context, PlayFragment fragment) {
        PlayFragment current = owner == null ? null : owner.get();
        if (fragment != null && current != null && current != fragment) return;
        owner = null;
        if (instance != null) {
            instance.stopPlaybackService();
        } else if (context != null) {
            context.stopService(new Intent(context, MusicPlaybackService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(this, "TVBoxMusic");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                PlayFragment fragment = getOwner();
                if (fragment != null) fragment.resumeFromMediaSession();
            }

            @Override
            public void onPause() {
                PlayFragment fragment = getOwner();
                if (fragment != null) fragment.pauseFromMediaSession();
            }

            @Override
            public void onSkipToPrevious() {
                PlayFragment fragment = getOwner();
                if (fragment != null) {
                    fragment.playPrevious();
                }
            }

            @Override
            public void onSkipToNext() {
                PlayFragment fragment = getOwner();
                if (fragment != null) {
                    fragment.playNext(false);
                }
            }

            @Override
            public void onStop() {
                PlayFragment fragment = getOwner();
                if (fragment != null) fragment.stopFromMediaSession();
                stopPlaybackService();
            }

            @Override
            public void onSeekTo(long pos) {
                PlayFragment fragment = getOwner();
                if (fragment != null) fragment.seekFromMediaSession(pos);
            }
        }, new Handler(Looper.getMainLooper()));
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            sessionActivity = PendingIntent.getActivity(this, 0, launchIntent, flags);
            mediaSession.setSessionActivity(sessionActivity);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) handleIntent(intent);
        return START_NOT_STICKY;
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            PlayFragment fragment = getOwner();
            if (fragment != null) fragment.stopFromMediaSession();
            stopPlaybackService();
            return;
        }
        if (ACTION_PLAY.equals(action)) {
            PlayFragment fragment = getOwner();
            if (fragment != null) fragment.resumeFromMediaSession();
            return;
        }
        if (ACTION_PAUSE.equals(action)) {
            PlayFragment fragment = getOwner();
            if (fragment != null) fragment.pauseFromMediaSession();
            return;
        }
        if (ACTION_PREVIOUS.equals(action)) {
            PlayFragment fragment = getOwner();
            if (fragment != null) {
                fragment.playPrevious();
            }
            return;
        }
        if (ACTION_NEXT.equals(action)) {
            PlayFragment fragment = getOwner();
            if (fragment != null) {
                fragment.playNext(false);
            }
            return;
        }
        if (ACTION_SEEK.equals(action)) {
            PlayFragment fragment = getOwner();
            if (fragment != null) fragment.seekFromMediaSession(intent.getLongExtra(EXTRA_SEEK, 0));
            return;
        }
        if (ACTION_UPDATE.equals(action)) {
            acquirePlaybackLocks();
            title = intent.getStringExtra(EXTRA_TITLE);
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE);
            String newArtworkUrl = intent.getStringExtra(EXTRA_ARTWORK);
            position = intent.getLongExtra(EXTRA_POSITION, 0);
            duration = intent.getLongExtra(EXTRA_DURATION, 0);
            playing = intent.getBooleanExtra(EXTRA_PLAYING, false);
            updateArtwork(newArtworkUrl);
            updateSession();
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }

    private void updateArtwork(String url) {
        if (TextUtils.equals(artworkUrl, url)) return;
        artworkUrl = url == null ? "" : url;
        artwork = null;
        if (TextUtils.isEmpty(artworkUrl)) return;
        if (artworkTarget != null) Glide.with(this).clear(artworkTarget);
        artworkTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                artwork = resource;
                updateSession();
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
            }
        };
        Glide.with(this).asBitmap().load(ImgUtil.getImageModel(artworkUrl)).override(256, 256).into(artworkTarget);
    }

    private void updateSession() {
        if (mediaSession == null) return;
        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subtitle);
        if (duration > 0) metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        if (!TextUtils.isEmpty(artworkUrl)) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artworkUrl);
        }
        if (artwork != null) metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        mediaSession.setMetadata(metadata.build());
        long action = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_STOP;
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(action)
                .setState(state, position, playing ? 1f : 0f)
                .build());
        mediaSession.setActive(true);
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new NotificationCompat.Builder(this, CHANNEL_ID)
                : new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.app_icon)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(sessionActivity)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .setDeleteIntent(actionIntent(ACTION_STOP))
                .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2, 3));
        if (artwork != null) builder.setLargeIcon(artwork);
        builder.addAction(new NotificationCompat.Action(R.drawable.media_action_placeholder, "", actionIntent(ACTION_PLACEHOLDER)));
        builder.addAction(new NotificationCompat.Action(R.drawable.exo_icon_previous, "上一首", actionIntent(ACTION_PREVIOUS)));
        builder.addAction(new NotificationCompat.Action(playing ? R.drawable.exo_icon_pause : R.drawable.exo_icon_play,
                playing ? "暂停" : "播放", actionIntent(playing ? ACTION_PAUSE : ACTION_PLAY)));
        builder.addAction(new NotificationCompat.Action(R.drawable.exo_icon_next, "下一首", actionIntent(ACTION_NEXT)));
        return builder.build();
    }

    private PendingIntent actionIntent(String action) {
        Intent intent = new Intent(this, MusicPlaybackService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("音乐后台播放控制");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void acquirePlaybackLocks() {
        try {
            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TVBox:MusicPlayback");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
                LOG.i("echo-music wake lock acquired");
            }
        } catch (Throwable th) {
            LOG.i("echo-music wake lock acquire failed: " + th.getMessage());
        }
        try {
            if (wifiLock == null) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TVBox:MusicPlayback");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                LOG.i("echo-music wifi lock acquired");
            }
        } catch (Throwable th) {
            LOG.i("echo-music wifi lock acquire failed: " + th.getMessage());
        }
    }

    private void releasePlaybackLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                LOG.i("echo-music wifi lock released");
            }
        } catch (Throwable th) {
            LOG.i("echo-music wifi lock release failed: " + th.getMessage());
        } finally {
            wifiLock = null;
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                LOG.i("echo-music wake lock released");
            }
        } catch (Throwable th) {
            LOG.i("echo-music wake lock release failed: " + th.getMessage());
        } finally {
            wakeLock = null;
        }
    }

    private PlayFragment getOwner() {
        return owner == null ? null : owner.get();
    }

    private void stopPlaybackService() {
        playing = false;
        releasePlaybackLocks();
        if (artworkTarget != null) Glide.with(this).clear(artworkTarget);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopPlaybackService();
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
