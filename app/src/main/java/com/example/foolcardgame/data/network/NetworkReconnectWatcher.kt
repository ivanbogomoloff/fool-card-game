package com.example.foolcardgame.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * При реальной потере/смене сети форсирует reconnect gRPC stream.
 * Не дергает reconnect на каждом [onCapabilitiesChanged] (иначе thrash на эмуляторе/Wi‑Fi).
 */
class NetworkReconnectWatcher(
    context: Context,
    private val onNetworkChanged: () -> Unit,
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val lost = AtomicBoolean(false)
    private val activeNetwork = AtomicReference<Network?>(null)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = activeNetwork.getAndSet(network)
            val wasLost = lost.getAndSet(false)
            // Первый callback при register — не reconnect; только после lost или смены Network.
            if (wasLost || (previous != null && previous != network)) {
                onNetworkChanged()
            }
        }

        override fun onLost(network: Network) {
            if (activeNetwork.compareAndSet(network, null)) {
                lost.set(true)
            }
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
