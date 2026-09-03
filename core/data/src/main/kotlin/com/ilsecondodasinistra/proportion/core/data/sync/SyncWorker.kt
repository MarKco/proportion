package com.ilsecondodasinistra.proportion.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * The periodic folder sync (phase 10) job — every ~4h, no foreground service, per Marco's
 * explicit call: there is no push notification from one device to the other anyway, only files
 * Syncthing (or equivalent) moves on its own schedule, so a period this coarse loses nothing a
 * tighter one would have caught, while costing a lot less battery. See the spec.
 *
 * Never throws: [SyncRepository.syncNow] already turns every failure into a log entry rather than
 * an exception, but a worker must not crash the process regardless — a genuinely unexpected
 * failure here asks WorkManager to retry rather than propagating.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferencesRepository.observePreferences().first().syncEnabled) return Result.success()
        return runCatching { syncRepository.syncNow() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
