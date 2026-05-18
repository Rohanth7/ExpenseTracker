package com.example.expensetracker.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.expensetracker.data.backup.worker.AutoBackupWorker
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<Category>,
    val expenses: List<Expense>,
    val recurringTemplates: List<RecurringTemplate>? = null
)

class BackupManager(private val context: Context) {
    private val gson = Gson()

    fun scheduleAutoBackup() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately on app open
            .addTag("auto_backup")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAutoBackup() {
        WorkManager.getInstance(context).cancelUniqueWork("daily_backup")
    }

    suspend fun exportToUri(uri: Uri, data: BackupData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(gson.toJson(data).toByteArray())
            }
        }.isSuccess
    }

    suspend fun importFromUri(uri: Uri): BackupData? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val data = gson.fromJson(stream.bufferedReader().readText(), BackupData::class.java)
                if (data?.categories == null || data.expenses == null) null else data
            }
        }.getOrNull()
    }
}
