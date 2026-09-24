package com.bopis.associate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// A queued write made while offline; replayed by SyncWorker once connectivity returns.
// `id` doubles as the Idempotency-Key sent to the backend, so replays are safe no-ops.
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey val id: String,
    val actionType: String, // PICK, SUBSTITUTE, REPORT
    val orderId: String? = null,
    val itemId: String? = null,
    val qrCode: String? = null,
    val status: String? = null,
    val substituteProductId: String? = null,
    val createdAt: Long,
)
