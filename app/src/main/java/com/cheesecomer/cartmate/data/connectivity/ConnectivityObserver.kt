package com.cheesecomer.cartmate.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.cheesecomer.cartmate.domain.repository.OnlineStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * オンラインかどうか（docs/requirements.md §3.3）。インターネットに接続できるネットワークが1つでもあればオンラインとする。
 * Firestore への接続そのものは確かめないため、電波が弱い場所では、オンラインと判定しても書き込みが届くまで時間がかかることがある
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnlineStatus {
    val online: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val validated = mutableSetOf<Network>()
        // コールバックは別のスレッドから呼ばれる
        fun update(change: MutableSet<Network>.() -> Unit) {
            trySend(synchronized(validated) { validated.change(); validated.isNotEmpty() })
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update { if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add(network) else remove(network) }
            }

            override fun onLost(network: Network) {
                update { remove(network) }
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        // 最初の値は今のネットワークから決める（起動時にオフラインのバナーがちらつかないように）。
        // ネットワークがなければ onCapabilitiesChanged が呼ばれないので、ここでオフラインが流れる
        update {
            val active = manager.activeNetwork
            val capabilities = active?.let(manager::getNetworkCapabilities)
            if (active != null && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) add(active)
        }
        manager.registerNetworkCallback(request, callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override fun observeOnline(): Flow<Boolean> = online
}
