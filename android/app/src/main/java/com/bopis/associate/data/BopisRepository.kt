package com.bopis.associate.data

import com.bopis.associate.data.local.AppDatabase
import com.bopis.associate.data.local.CachedResponseEntity
import com.bopis.associate.data.local.PendingActionEntity
import com.bopis.associate.data.remote.ApiService
import com.bopis.associate.data.remote.AssistantQueryRequest
import com.bopis.associate.data.remote.AssistantResponse
import com.bopis.associate.data.remote.CreateNotificationRequest
import com.bopis.associate.data.remote.OrderDetail
import com.bopis.associate.data.remote.OrderSummary
import com.bopis.associate.data.remote.Shelf
import com.bopis.associate.data.remote.ShelfReportRequest
import com.bopis.associate.data.remote.SpeakRequest
import com.bopis.associate.data.remote.SubstituteCandidate
import com.bopis.associate.data.remote.SubstituteRequest
import com.bopis.associate.data.remote.SyncPullResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

sealed interface ActionResult {
    data object Applied : ActionResult
    data object Queued : ActionResult
    data class Failed(val message: String) : ActionResult
}

// Central data access point: talks to the API first, falls back to the local Room cache
// for reads and to a pending-action outbox for writes when the backend is unreachable.
class BopisRepository(
    private val api: ApiService,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getOrders(storeId: String?): List<OrderSummary> = withCacheFallback(
        cacheKey = "orders",
        fetch = { api.getOrders(storeId) },
    )

    suspend fun getOrder(orderId: String): OrderDetail = withCacheFallback(
        cacheKey = "order:$orderId",
        fetch = { api.getOrder(orderId) },
    )

    suspend fun getShelf(qrCode: String): Shelf = withCacheFallback(
        cacheKey = "shelf:$qrCode",
        fetch = { api.getShelf(qrCode) },
    )

    // Manual fallback list for picking a shelf without scanning (see ShelfPickerDialog).
    suspend fun getShelves(storeId: String): List<Shelf> = withCacheFallback(
        cacheKey = "shelves:$storeId",
        fetch = { api.getShelves(storeId) },
    )

    suspend fun getSubstitutes(orderId: String, itemId: String): List<SubstituteCandidate> =
        try {
            api.getSubstitutes(orderId, itemId)
        } catch (e: IOException) {
            emptyList()
        }

    suspend fun pull(since: Long, storeId: String?): SyncPullResponse? =
        try {
            api.syncPull(since, storeId)
        } catch (e: IOException) {
            null
        }

    suspend fun pickItem(orderId: String, itemId: String): ActionResult {
        val key = UUID.randomUUID().toString()
        return try {
            api.pickItem(orderId, itemId, key)
            ActionResult.Applied
        } catch (e: IOException) {
            db.pendingActionDao().insert(
                PendingActionEntity(
                    id = key,
                    actionType = "PICK",
                    orderId = orderId,
                    itemId = itemId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            ActionResult.Queued
        }
    }

    suspend fun markUnavailable(orderId: String, itemId: String): ActionResult {
        val key = UUID.randomUUID().toString()
        return try {
            api.markUnavailable(orderId, itemId, key)
            ActionResult.Applied
        } catch (e: IOException) {
            db.pendingActionDao().insert(
                PendingActionEntity(
                    id = key,
                    actionType = "MARK_UNAVAILABLE",
                    orderId = orderId,
                    itemId = itemId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            ActionResult.Queued
        }
    }

    suspend fun substituteItem(orderId: String, itemId: String, substituteProductId: String): ActionResult {
        val key = UUID.randomUUID().toString()
        return try {
            api.substituteItem(orderId, itemId, key, SubstituteRequest(substituteProductId))
            ActionResult.Applied
        } catch (e: IOException) {
            db.pendingActionDao().insert(
                PendingActionEntity(
                    id = key,
                    actionType = "SUBSTITUTE",
                    orderId = orderId,
                    itemId = itemId,
                    substituteProductId = substituteProductId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            ActionResult.Queued
        }
    }

    suspend fun reportShelf(qrCode: String, status: String): ActionResult {
        val key = UUID.randomUUID().toString()
        return try {
            api.reportShelf(qrCode, key, ShelfReportRequest(status))
            ActionResult.Applied
        } catch (e: IOException) {
            db.pendingActionDao().insert(
                PendingActionEntity(
                    id = key,
                    actionType = "REPORT",
                    qrCode = qrCode,
                    status = status,
                    createdAt = System.currentTimeMillis(),
                )
            )
            ActionResult.Queued
        }
    }

    suspend fun pendingActionCount(): Int = db.pendingActionDao().count()

    suspend fun assistantQuery(question: String, storeId: String): AssistantResponse =
        api.assistantQuery(AssistantQueryRequest(question, storeId))

    // Fire-and-forget, associate-facing (shows up in the Alerts "Sent" tab); failure is silent
    // since it's just an informational note, not something worth queuing for offline retry.
    suspend fun notifyAssociate(type: String, message: String) {
        try {
            api.createNotification(CreateNotificationRequest(role = "associate", type = type, payload = mapOf("message" to message)))
        } catch (e: IOException) {
            // best-effort
        }
    }

    // Used only by the proactive agent to speak its Yes/No prompts; failures are silent
    // (falls back to text-only) since speech is a nice-to-have, not the source of truth.
    suspend fun assistantSpeak(text: String): String? =
        try {
            api.assistantSpeak(SpeakRequest(text)).audioBase64
        } catch (e: IOException) {
            null
        }

    suspend fun assistantVoice(audio: okhttp3.MultipartBody.Part, storeId: okhttp3.RequestBody): AssistantResponse =
        api.assistantVoice(audio, storeId)

    private suspend inline fun <reified T> withCacheFallback(
        cacheKey: String,
        fetch: () -> T,
    ): T {
        return try {
            val result = fetch()
            db.cachedResponseDao().put(
                CachedResponseEntity(cacheKey, json.encodeToString(result), System.currentTimeMillis())
            )
            result
        } catch (e: IOException) {
            val cached = db.cachedResponseDao().get(cacheKey)
                ?: throw IOException("Offline and no cached data for $cacheKey", e)
            json.decodeFromString(cached.json)
        }
    }
}
