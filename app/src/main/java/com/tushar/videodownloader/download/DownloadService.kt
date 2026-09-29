package com.tushar.videodownloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.tushar.videodownloader.MainActivity
import com.tushar.videodownloader.R
import com.tushar.videodownloader.ServiceLocator
import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.core.toReadableSize
import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs downloads outside the Activity lifecycle so they survive backgrounding and
 * rotation, with a cancellable progress notification. Progress is published through
 * [progress] so the ViewModel observes without binding.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelDownload()
                return START_NOT_STICKY
            }
            ACTION_START -> startDownload()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startDownload() {
        val media = pendingMedia
        val quality = pendingQuality
        if (media == null || quality == null) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting download…", null))

        downloadJob = serviceScope.launch {
            ServiceLocator.downloader(applicationContext)
                .download(media, quality)
                .collect { update ->
                    _progress.value = update
                    when (update) {
                        is DownloadProgress.Running -> updateNotification(update)
                        is DownloadProgress.Completed,
                        is DownloadProgress.Failed,
                        -> finish()
                        DownloadProgress.Preparing -> Unit
                    }
                }
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        _progress.value = DownloadProgress.Failed(DownloadError.Cancelled)
        finish()
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(progress: DownloadProgress.Running) {
        val text = buildString {
            append(progress.bytesDownloaded.toReadableSize())
            progress.totalBytes?.let { append(" of ${it.toReadableSize()}") }
        }
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(text, progress.percent))
    }

    private fun buildNotification(text: String, percent: Int?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_downloading))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.action_cancel), cancelIntent)
            .setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager?.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager?
        get() = getSystemService()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.tushar.videodownloader.START"
        private const val ACTION_CANCEL = "com.tushar.videodownloader.CANCEL"

        private val _progress = MutableStateFlow<DownloadProgress?>(null)

        /** Latest state of the active download; null when nothing is running. */
        val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

        // Handed over in memory: the service is process-local and ResolvedMedia is a
        // rich model, so Intent-extra serialisation buys nothing.
        private var pendingMedia: ResolvedMedia? = null
        private var pendingQuality: VideoQuality? = null

        fun start(context: Context, media: ResolvedMedia, quality: VideoQuality) {
            pendingMedia = media
            pendingQuality = quality
            _progress.value = DownloadProgress.Preparing

            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }

        fun clear() {
            _progress.value = null
            pendingMedia = null
            pendingQuality = null
        }
    }
}
