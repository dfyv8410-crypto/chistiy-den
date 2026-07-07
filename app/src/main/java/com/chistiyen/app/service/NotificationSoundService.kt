package com.chistiyen.app.service

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import com.chistiyen.app.R

/**
 * Plays a custom notification sound. The actual sound file
 * should be placed at res/raw/notification_sound.ogg
 */
class NotificationSoundService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val soundResId = R.raw.notification_sound
            player = MediaPlayer.create(this, soundResId).apply {
                setVolume(1.0f, 1.0f)
                setOnCompletionListener { stopSelf() }
                start()
            }
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        player?.apply {
            if (isPlaying) stop()
            release()
        }
        player = null
        super.onDestroy()
    }
}
