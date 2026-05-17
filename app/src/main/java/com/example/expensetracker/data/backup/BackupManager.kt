package com.example.expensetracker.data.backup

import android.content.Context
import android.net.Uri
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<Category>,
    val expenses: List<Expense>
)

class BackupManager(private val context: Context) {
    private val gson = Gson()

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
                // Gson can deserialize missing fields as null even for non-nullable types
                if (data?.categories == null || data.expenses == null) null else data
            }
        }.getOrNull()
    }
}
