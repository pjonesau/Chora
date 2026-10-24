package com.craftworks.music.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DataRefreshManager {
    private val _dataSourceChangedEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val dataSourceChangedEvent = _dataSourceChangedEvent.asSharedFlow()

    @Volatile
    private var loadFailedOffline = false

    fun notifyDataSourcesChanged() {
        _dataSourceChangedEvent.tryEmit(Unit)
    }

    /** A request could not reach the server; the next network to come up reloads the screens. */
    fun notifyLoadFailedOffline() {
        loadFailedOffline = true
    }

    /**
     * The screens load once and then only on [dataSourceChangedEvent], so a start made before the
     * network was up - a TV that woke before its Wi-Fi did - would stay empty until the app was
     * killed. When a network becomes available after such a failure, send that event so every
     * screen tries again.
     */
    fun watchNetwork(context: Context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (loadFailedOffline) {
                    loadFailedOffline = false
                    notifyDataSourcesChanged()
                }
            }
        })
    }
}
