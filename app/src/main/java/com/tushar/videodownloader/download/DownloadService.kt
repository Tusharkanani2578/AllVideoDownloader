package com.tushar.videodownloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.tushar.videodownloader.MainActivity
import com.tushar.videodownloader.R
import com.tushar.videodownloader.ServiceLocator
import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.core.toReadableSize
import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.MediaOption
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
        // onStartCommand is a re-entrant entry point; two coroutines would open two
        // append streams on the same .part file and interleave their writes.
        if (downloadJob?.isActive == true) return

        val media = pendingMedia
        val options = pendingOptions
        if (media == null || options.isEmpty()) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting download…", null))

        downloadJob = serviceScope.launch { runBatch(media, options) }
    }

    /**
     * Downloads the chosen items one after another.
     *
     * Sequential rather than parallel: the user is watching one progress bar, and three
     * downloads sharing the connection would each finish later than the first would have
     * alone. A failure stops the batch and is reported as it is — whatever already landed
     * stays saved, and retrying skips it, since the duplicate check runs before any bytes
     * are fetched.
     */
    private suspend fun runBatch(media: ResolvedMedia, options: List<MediaOption>) {
        var saved = 0
        var last: DownloadProgress.Completed? = null

        options.forEachIndexed { index, option ->
            var failed = false

            ServiceLocator.downloader(applicationContext)
                .download(media, option)
                .collect { update ->
                    when (update) {
                        is DownloadProgress.Running -> {
                            val positioned = update.copy(
                                itemNumber = index + 1,
                                itemCount = options.size,
                            )
                            _progress.value = positioned
                            updateNotification(positioned)
                        }
                        is DownloadProgress.Completed -> {
                            saved++
                            last = update
                        }
                        is DownloadProgress.Failed -> {
                            failed = true
                            _progress.value = update
                        }
                        DownloadProgress.Preparing ->
                            if (index == 0) _progress.value = update
                    }
                }

            if (failed) {
                finish()
                return
            }
        }

        last?.let { _progress.value = it.copy(savedCount = saved) }
        finish()
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
            if (progress.itemCount > 1) {
                append("${progress.itemNumber} of ${progress.itemCount} · ")
            }
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
        private var pendingOptions: List<MediaOption> = emptyList()

        fun start(context: Context, media: ResolvedMedia, options: List<MediaOption>) {
            pendingMedia = media
            pendingOptions = options
            _progress.value = DownloadProgress.Preparing

            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }

        fun clear() {
            _progress.value = null
            pendingMedia = null
            pendingOptions = emptyList()
        }
    }
}
