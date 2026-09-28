package com.bopis.associate.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OrderSummary(
    val id: String,
    val customer_name: String,
    val status: String,
    val pending_count: String,
)

@Serializable
data class OrderItem(
    val id: String,
    val order_id: String,
    val product_id: String,
    val status: String,
    // Null when this comes from /sync/pull, which returns raw order_items rows without the
    // product join that GET /orders/:id provides.
    val product_name: String? = null,
    val sku: String? = null,
    val substituted_with_product_id: String? = null,
    val substituted_with_product_name: String? = null,
)

@Serializable
data class OrderDetail(
    val id: String,
    val customer_id: String,
    val store_id: String,
    val status: String,
    val items: List<OrderItem> = emptyList(),
)

@Serializable
data class Shelf(
    val id: String,
    val store_id: String,
    val product_id: String,
    val qr_code: String,
    val aisle: String? = null,
    val product_name: String,
    val sku: String,
    val qty: Int,
    val inventory_status: String,
) {
    companion object {
        // Returned by the API for an unknown QR code (see backend shelves.ts).
        const val NOT_FOUND_MARKER = "error"
    }
}

@Serializable
data class SubstituteCandidate(
    val product_id: String,
    val name: String,
    val sku: String,
    val score: Double,
)

@Serializable
data class ShelfReportRequest(val status: String)

@Serializable
data class SubstituteRequest(val substituteProductId: String)

@Serializable
data class AppNotification(
    val id: String,
    val role: String,
    val type: String,
    // JsonElement, not String: rule action payloads mix strings and numbers (e.g. qty),
    // and a plain Map<String, String> throws on any non-string value (caused a crash).
    val payload: Map<String, JsonElement> = emptyMap(),
    val read_at: String? = null,
)

@Serializable
data class CreateNotificationRequest(
    val role: String,
    val type: String,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable
data class SyncChanges(
    val inventory: List<InventoryRow> = emptyList(),
    val order_items: List<OrderItem> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
)

@Serializable
data class InventoryRow(
    val shelf_id: String,
    val qty: Int,
    val status: String,
    val version: String,
)

@Serializable
data class SyncPullResponse(
    val changes: SyncChanges,
    val version: Long,
)

@Serializable
data class AssistantQueryRequest(val question: String, val storeId: String)

@Serializable
data class AssistantResponse(
    val answer: String,
    val question: String? = null,
    val audioBase64: String? = null,
)

@Serializable
data class SpeakRequest(val text: String)

@Serializable
data class SpeakResponse(val audioBase64: String? = null)
