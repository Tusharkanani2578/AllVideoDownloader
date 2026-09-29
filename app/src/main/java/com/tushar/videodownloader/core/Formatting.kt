package com.tushar.videodownloader.core

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/** Formats a byte count as a short human-readable string, e.g. `12.4 MB`. */
fun Long.toReadableSize(): String {
    if (this <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (log10(toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = this / 1024.0.pow(digitGroup)
    return String.format(Locale.US, if (digitGroup == 0) "%.0f %s" else "%.1f %s", value, units[digitGroup])
}

fun Long.toReadableSpeed(): String = "${toReadableSize()}/s"
