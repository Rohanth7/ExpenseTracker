package com.example.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.expensetracker.data.db.dao.BillDao
import com.example.expensetracker.data.db.dao.CategoryDao
import com.example.expensetracker.data.db.dao.LoanDao
import com.example.expensetracker.data.db.dao.ExpenseDao
import com.example.expensetracker.data.db.dao.MerchantMappingDao
import com.example.expensetracker.data.db.dao.RecurringTemplateDao
import com.example.expensetracker.data.db.dao.SavingsGoalDao
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.db.entity.SavingsGoal

@Database(entities = [Category::class, Expense::class, RecurringTemplate::class, MerchantMapping::class, SavingsGoal::class, Bill::class, Loan::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun billDao(): BillDao
    abstract fun loanDao(): LoanDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS bills (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "dueDayOfMonth INTEGER NOT NULL, " +
                    "reminderDays INTEGER NOT NULL DEFAULT 3, " +
                    "categoryId INTEGER NOT NULL DEFAULT -1, " +
                    "isEnabled INTEGER NOT NULL DEFAULT 1)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS loans (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "emoji TEXT NOT NULL DEFAULT '🏦', " +
                    "totalAmount REAL NOT NULL, " +
                    "monthlyEmi REAL NOT NULL, " +
                    "tenureMonths INTEGER NOT NULL, " +
                    "startDate INTEGER NOT NULL, " +
                    "dueDayOfMonth INTEGER NOT NULL DEFAULT 5, " +
                    "isActive INTEGER NOT NULL DEFAULT 1)"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE bills ADD COLUMN autoLog INTEGER NOT NULL DEFAULT 0")
                // Migrate recurring templates into bills (autoLog=1, dueDayOfMonth=1, reminderDays=0)
                database.execSQL(
                    "INSERT INTO bills (name, amount, dueDayOfMonth, reminderDays, categoryId, isEnabled, autoLog) " +
                    "SELECT name, amount, 1, 0, categoryId, enabled, 1 FROM recurring_templates"
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
                .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
