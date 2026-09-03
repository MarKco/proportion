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

    override fun schedule(intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours.toLong(), TimeUnit.HOURS).build()
        // UPDATE, not KEEP: the user can change the interval from settings and expects it to take
        // effect without waiting for the currently-running period to elapse. WorkManager applies
        // the new period without dropping the job's identity, so flipping the toggle off and back
        // on with the same interval already in effect still doesn't reset anything meaningful.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "proportion-folder-sync"
    }
}
