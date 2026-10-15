package com.example.expensetracker.data.backup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.data.backup.BackupCodec
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.backup.DriveBackupClient
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Daily (or on-demand) backup: a local copy in app storage, then the same file to Drive. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val json = BackupCodec.toJson(BackupManager(applicationContext).snapshot())
            File(applicationContext.filesDir, "auto_backup.json").writeText(json)

            val drive = DriveBackupClient(applicationContext)
            try {
                drive.token()?.let { drive.upload(it, json) }
            } catch (_: UserRecoverableAuthException) {
                // The user has to grant access again from the app; skip Drive this time.
                Log.w(TAG, "Drive access needs to be granted again")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.retry()
        }
    }

    private companion object { const val TAG = "AutoBackupWorker" }
}
