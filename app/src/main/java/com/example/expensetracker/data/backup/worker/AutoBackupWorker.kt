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
            backUp(BackupManager(applicationContext), File(applicationContext.filesDir, FILE_NAME), DriveBackupClient(applicationContext))
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val FILE_NAME = "auto_backup.json"

        /**
         * Writes [manager]'s snapshot to [file], then to Drive if [drive] has an account. A
         * snapshot with no records (a phone that was reset) never replaces a backup that has some
         * ([BackupCodec.mayReplace]); Drive's copy is only downloaded to check in that case.
         */
        suspend fun backUp(manager: BackupManager, file: File, drive: DriveBackupClient?) {
            val snapshot = manager.snapshot()
            val json = BackupCodec.toJson(snapshot)
            // The existing file is only read when it matters, as Drive's is only downloaded then.
            if (snapshot.hasRecords || BackupCodec.mayReplace(file.takeIf { it.exists() }?.readText(), snapshot)) file.writeText(json)
            else Log.w(TAG, "Kept the local backup: this phone has no records")

            try {
                val token = drive?.token() ?: return
                val remote = if (snapshot.hasRecords) null else drive.find(token)?.let { drive.download(token, it.id) }
                if (BackupCodec.mayReplace(remote, snapshot)) drive.upload(token, json)
                else Log.w(TAG, "Kept the Drive backup: this phone has no records")
            } catch (_: UserRecoverableAuthException) {
                // The user has to grant access again from the app; skip Drive this time.
                Log.w(TAG, "Drive access needs to be granted again")
            }
        }
    }
}
