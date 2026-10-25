package com.skvod3400.superalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

/** Plays the alarm until the user solves the problem in [AlarmActivity]. */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volume = MIN_VOLUME
    private var savedAlarmVolume = -1
    private var alarmId = -1

    private val rampVolume = object : Runnable {
        override fun run() {
            volume = (volume + VOLUME_STEP).coerceAtMost(1f)
            player?.setVolume(volume, volume)
            if (volume < 1f) handler.postDelayed(this, VOLUME_STEP_MS)
        }
    }

    /** Brings the alarm screen back if the user tries to leave it. */
    private val keepActivityOnTop = object : Runnable {
        override fun run() {
            if (!isRinging) return
            if (!AlarmActivity.isOnScreen) {
                try {
                    startActivity(AlarmActivity.intent(this@AlarmService, alarmId))
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot bring alarm screen back", e)
                }
            }
            handler.postDelayed(this, KEEP_ON_TOP_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getIntExtra(EXTRA_ALARM_ID, -1)?.takeIf { it != -1 }?.let { alarmId = it }
        isRinging = true
        try {
            startForeground(
                NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null) startRinging()
        handler.removeCallbacks(keepActivityOnTop)
        handler.postDelayed(keepActivityOnTop, KEEP_ON_TOP_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        isRinging = false
        handler.removeCallbacksAndMessages(null)
        player?.run {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        if (savedAlarmVolume >= 0) {
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot restore alarm volume", e)
            }
        }
        super.onDestroy()
    }

    private fun startRinging() {
        val audio = getSystemService(AudioManager::class.java)
        try {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot raise alarm volume", e)
        }

        val alarm = AlarmRepository.get(this, alarmId)
        val candidates = listOfNotNull(
            alarm?.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(this, uri)
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.isLooping = true
                mp.setVolume(MIN_VOLUME, MIN_VOLUME)
                mp.prepare()
                mp.start()
                player = mp
                break
            } catch (e: Exception) {
                Log.w(TAG, "Cannot play $uri", e)
                mp.release()
            }
        }
        volume = MIN_VOLUME
        handler.postDelayed(rampVolume, VOLUME_STEP_MS)

        vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator?.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(
                pattern,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).run {
            // The first version used another channel id; its settings may have been lowered.
            deleteNotificationChannel(OLD_CHANNEL_ID)
            createNotificationChannel(channel)
        }

        val openAlarm = PendingIntent.getActivity(
            this, 0, AlarmActivity.intent(this, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.solve_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openAlarm)
            .setFullScreenIntent(openAlarm, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val CHANNEL_ID = "alarm_screen"
        private const val OLD_CHANNEL_ID = "alarm"
        private const val NOTIFICATION_ID = 1
        private const val MIN_VOLUME = 0.05f
        private const val VOLUME_STEP = 0.05f
        private const val VOLUME_STEP_MS = 1500L
        private const val KEEP_ON_TOP_MS = 2000L

        @Volatile
        var isRinging = false
            private set

        fun start(context: Context, alarmId: Int) {
            val intent = Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, alarmId)
            // Set right away: the ringing screen checks it before the service has started.
            isRinging = true
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                isRinging = false
                throw e
            }
        }

        fun stop(context: Context) {
            isRinging = false
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
