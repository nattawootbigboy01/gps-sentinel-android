package com.gpsdefenseauditor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MahathepEvolutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        // One bounded, local-only cycle per WorkManager execution.
        EvolutionStore.runAndSave(applicationContext)
        Result.success()
    }.getOrElse {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
