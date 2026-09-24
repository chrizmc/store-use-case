package com.bopis.associate.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Two triggers keep the offline outbox drained: an immediate one-off run whenever
// connectivity returns (registered by ConnectivityObserver), and a periodic backstop.
object SyncScheduler {
    private const val ONE_TIME_WORK_NAME = "bopis-sync-now"
    private const val PERIODIC_WORK_NAME = "bopis-sync-periodic"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
