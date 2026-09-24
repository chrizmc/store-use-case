package com.bopis.associate

import android.app.Application
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.local.AppDatabase
import com.bopis.associate.data.remote.NetworkModule
import com.bopis.associate.sync.ConnectivityObserver
import com.bopis.associate.sync.SyncScheduler

// Deliberately a plain manual service locator instead of a DI framework (Hilt/Koin) --
// this is a small demo app with a handful of dependencies, so a framework would be
// pure ceremony (see project-wide "no schnick schnack" simplicity guideline).
class BopisApp : Application() {
    lateinit var repository: BopisRepository
        private set

    private lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = BopisRepository(NetworkModule.api, db)

        SyncScheduler.schedulePeriodic(this)
        connectivityObserver = ConnectivityObserver(this)
        connectivityObserver.start()
    }
}
