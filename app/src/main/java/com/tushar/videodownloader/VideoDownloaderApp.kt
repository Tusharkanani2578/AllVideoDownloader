package com.tushar.videodownloader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * Application entry point; dependency wiring lives in [ServiceLocator].
 *
 * Supplies the app-wide Coil loader so a status tile can show a video's first frame.
 * Without the video decoder those tiles render as empty placeholders, which reads as a
 * broken thumbnail rather than a video.
 */
class VideoDownloaderApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
