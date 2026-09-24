package com.bopis.associate.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bopis.associate.data.local.AppDatabase
import com.bopis.associate.data.local.PendingActionEntity
import com.bopis.associate.data.remote.NetworkModule
import com.bopis.associate.data.remote.ShelfReportRequest
import com.bopis.associate.data.remote.SubstituteRequest
import java.io.IOException

// Replays queued offline actions against the backend using their original idempotency
// key, so retries (including partially-applied ones) are safe no-ops server-side.
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val api = NetworkModule.api
        val dao = db.pendingActionDao()

        val pending = dao.getAll()
        var allSucceeded = true

        for (action: PendingActionEntity in pending) {
            try {
                when (action.actionType) {
                    "PICK" -> api.pickItem(action.orderId!!, action.itemId!!, action.id)
                    "MARK_UNAVAILABLE" -> api.markUnavailable(action.orderId!!, action.itemId!!, action.id)
                    "SUBSTITUTE" -> api.substituteItem(
                        action.orderId!!,
                        action.itemId!!,
                        action.id,
                        SubstituteRequest(action.substituteProductId!!),
                    )
                    "REPORT" -> api.reportShelf(action.qrCode!!, action.id, ShelfReportRequest(action.status!!))
                }
                dao.delete(action)
            } catch (e: IOException) {
                // Still offline; leave it queued and retry on the next sync pass.
                allSucceeded = false
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }
}
