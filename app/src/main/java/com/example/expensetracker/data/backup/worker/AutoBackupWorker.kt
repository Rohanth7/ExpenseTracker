package com.example.expensetracker.data.backup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.db.AppDatabase
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val gson = Gson()

            val cats = db.categoryDao().getAll().first()
            val exps = db.expenseDao().getAll().first()
            val templates = db.recurringTemplateDao().getAll().first()

            val data = BackupData(categories = cats, expenses = exps, recurringTemplates = templates)
            val json = gson.toJson(data)

            val file = File(applicationContext.filesDir, "auto_backup.json")
            file.writeText(json)

            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            if (account?.account != null) {
                try {
                    val scope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
                    val token = GoogleAuthUtil.getToken(applicationContext, account.account!!, scope)
                    uploadToDrive(token, json)
                } catch (_: UserRecoverableAuthException) {
                    // User needs to re-grant permissions interactively — skip Drive upload this cycle
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun uploadToDrive(token: String, json: String) {
        val existingFileId = findExistingBackupFile(token)

        val boundary = "backup_boundary_${System.currentTimeMillis()}"
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            if (existingFileId == null) {
                append("""{"name":"auto_backup.json","parents":["appDataFolder"]}""")
            } else {
                append("""{"name":"auto_backup.json"}""")
            }
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(json)
            append("\r\n--$boundary--")
        }

        val urlStr = if (existingFileId != null) {
            "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        }

        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = if (existingFileId != null) "PATCH" else "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.doOutput = true

        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val responseCode = conn.responseCode
        conn.disconnect()

        if (responseCode !in 200..299) {
            throw IOException("Drive upload failed: HTTP $responseCode")
        }
    }

    private fun findExistingBackupFile(token: String): String? {
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%3D'auto_backup.json'&fields=files(id)")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")

            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val files = JsonParser.parseString(response).asJsonObject.getAsJsonArray("files")
            if (files.size() > 0) files[0].asJsonObject.get("id").asString else null
        } catch (_: Exception) {
            null
        }
    }
}
