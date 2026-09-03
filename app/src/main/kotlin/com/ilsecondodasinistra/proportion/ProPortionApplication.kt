package com.ilsecondodasinistra.proportion

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * [Configuration.Provider] is what lets the folder sync (phase 10) periodic job
 * (`:core:data`'s `SyncWorker`) be a `@HiltWorker` — `WorkManager` reads
 * [workManagerConfiguration] instead of its own default, no-DI worker factory.
 */
@HiltAndroidApp
class ProPortionApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
