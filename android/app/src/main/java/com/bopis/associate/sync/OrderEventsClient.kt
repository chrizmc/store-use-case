package com.bopis.associate.sync

import com.bopis.associate.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

// Live push for the Orders list: the backend relays Postgres NOTIFY(data_change) for
// orders/order_items over SSE, so an order placed via the web Customer Order Simulator
// appears without the associate pulling to refresh. okhttp-sse has no built-in
// auto-reconnect, so failures/closes are retried with a fixed delay.
class OrderEventsClient(
    private val scope: CoroutineScope,
    private val onOrderChanged: () -> Unit,
) {
    private var eventSource: EventSource? = null
    private var reconnectJob: Job? = null

    fun start() {
        eventSource?.cancel()
        val request = Request.Builder().url(NetworkModule.BASE_URL + "events/orders").build()
        eventSource = EventSources.createFactory(NetworkModule.sseClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    onOrderChanged()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    reconnect()
                }

                override fun onClosed(eventSource: EventSource) {
                    reconnect()
                }
            },
        )
    }

    private fun reconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            start()
        }
    }

    fun stop() {
        reconnectJob?.cancel()
        eventSource?.cancel()
        eventSource = null
    }
}
