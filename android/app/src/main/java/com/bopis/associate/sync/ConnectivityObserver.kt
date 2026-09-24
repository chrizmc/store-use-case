package com.bopis.associate.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

// Fires an immediate sync attempt the moment the device regains connectivity, instead
// of waiting for the periodic WorkManager job (which has a minimum 15-minute interval).
class ConnectivityObserver(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            SyncScheduler.syncNow(context)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}
