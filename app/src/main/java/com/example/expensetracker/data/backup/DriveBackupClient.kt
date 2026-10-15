package com.example.expensetracker.data.backup

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Reads and writes the single backup file in the app's private Drive "appDataFolder".
 *
 * All Google Sign-In use is kept in [token] so the Phase 1 move to the new authorization API only
 * has to change one function. Call everything off the main thread.
 */
class DriveBackupClient(private val context: Context) {
    data class RemoteBackup(val id: String, val modifiedTime: String?)

    /**
     * An access token for the drive.appdata scope, or null if no Google account is connected.
     * Throws UserRecoverableAuthException when the user has to grant access again; its intent
     * must be launched from the UI.
     */
    fun token(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return null
        return GoogleAuthUtil.getToken(context, account, "oauth2:$SCOPE")
    }

    fun find(token: String): RemoteBackup? {
        val q = URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val conn = open("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc", token)
        try {
            if (conn.responseCode != 200) throw IOException("Drive search failed: HTTP ${conn.responseCode}")
            val files = JsonParser.parseString(conn.inputStream.bufferedReader().readText())
                .asJsonObject.getAsJsonArray("files")
            if (files == null || files.size() == 0) return null
            val f = files[0].asJsonObject
            return RemoteBackup(f.get("id").asString, f.get("modifiedTime")?.asString)
        } finally {
            conn.disconnect()
        }
    }

    fun download(token: String, fileId: String): String {
        val conn = open("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token)
        try {
            if (conn.responseCode != 200) throw IOException("Drive download failed: HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    fun upload(token: String, json: String) {
        val existing = find(token)
        val boundary = "backup_boundary_${System.currentTimeMillis()}"
        val metadata = if (existing == null) """{"name":"$FILE_NAME","parents":["appDataFolder"]}""" else """{"name":"$FILE_NAME"}"""
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n$json\r\n--$boundary--"
        val url = if (existing != null) "https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=multipart"
                  else "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val conn = open(url, token).apply {
            // HttpURLConnection doesn't support PATCH; Drive accepts the override header.
            requestMethod = "POST"
            if (existing != null) setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IOException("Drive upload failed: HTTP ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, token: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        setRequestProperty("Authorization", "Bearer $token")
        connectTimeout = 20_000
        readTimeout = 30_000
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_NAME = "auto_backup.json"
    }
}
