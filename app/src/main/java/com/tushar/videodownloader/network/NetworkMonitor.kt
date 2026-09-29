package com.tushar.videodownloader.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Point-in-time connectivity check, used to fail fast with a clear offline message
 * instead of waiting out a socket timeout. Requires a *validated* route, so Wi-Fi
 * with no actual internet counts as offline.
 */
class NetworkMonitor(private val context: Context) {

    fun isOnline(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
