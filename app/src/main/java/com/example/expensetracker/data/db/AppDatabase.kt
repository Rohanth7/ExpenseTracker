package com.example.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.expensetracker.data.db.dao.CategoryDao
import com.example.expensetracker.data.db.dao.ExpenseDao
import com.example.expensetracker.data.db.dao.MerchantMappingDao
import com.example.expensetracker.data.db.dao.RecurringTemplateDao
import com.example.expensetracker.data.db.dao.SavingsGoalDao
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.db.entity.SavingsGoal

@Database(entities = [Category::class, Expense::class, RecurringTemplate::class, MerchantMapping::class, SavingsGoal::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_templates (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "categoryId INTEGER NOT NULL DEFAULT -1, " +
                    "enabled INTEGER NOT NULL DEFAULT 1)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS merchant_mappings (" +
                    "merchantName TEXT PRIMARY KEY NOT NULL, " +
                    "categoryId INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_goals (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "targetAmount REAL NOT NULL, " +
                    "currentAmount REAL NOT NULL DEFAULT 0.0, " +
                    "colorHex TEXT NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
