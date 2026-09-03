package com.ilsecondodasinistra.proportion.core.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ilsecondodasinistra.proportion.core.domain.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS).build()
        // KEEP, not REPLACE: flipping the toggle off and back on must not reset the interval clock
        // or duplicate the job — the existing periodic work (if any) is left running as-is.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "proportion-folder-sync"
        const val SYNC_INTERVAL_HOURS = 4L
    }
}
