package com.gpsdefenseauditor

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object EvolutionScheduler {
    private const val UNIQUE_PERIODIC = "mahathep_evolution_periodic"
    private const val UNIQUE_ONCE = "mahathep_evolution_once"

    fun start(context: Context) {
        context.getSharedPreferences("mahathep_evolution", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        val constraints = Constraints.Builder().build()
        val periodic = PeriodicWorkRequestBuilder<MahathepEvolutionWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic
        )
        runOnce(context)
    }

    fun stop(context: Context) {
        context.getSharedPreferences("mahathep_evolution", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ONCE)
    }

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences("mahathep_evolution", Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<MahathepEvolutionWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONCE, ExistingWorkPolicy.REPLACE, request
        )
    }
}
