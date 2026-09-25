package com.thesis.bananaleaf.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "banana_leaf_detector.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Perform one-time migration from legacy SharedPreferences
                            CoroutineScope(Dispatchers.IO).launch {
                                migrateFromSharedPreferences(context.applicationContext, getDatabase(context))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun migrateFromSharedPreferences(context: Context, database: AppDatabase) {
            try {
                val prefs = context.getSharedPreferences("leafdetector_collection", Context.MODE_PRIVATE)
                val rawEntries = prefs.getStringSet("saved_scans", emptySet()) ?: emptySet()
                if (rawEntries.isEmpty()) return

                val dao = database.scanDao()
                for (raw in rawEntries) {
                    val parts = raw.split("|")
                    if (parts.isEmpty() || parts[0].isBlank()) continue
                    val imagePath = parts[0]
                    val entity = if (parts.size >= 5) {
                        val names = parts[1].split(",").filter { it.isNotBlank() }
                        val confidences = parts[2].split(",").mapNotNull { it.toFloatOrNull() }
                        val healthy = parts[3].toFloatOrNull() ?: 0f
                        val stamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                        ScanEntity.create(imagePath, names, confidences, healthy, stamp)
                    } else {
                        val legacyLabel = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                        val names = if (legacyLabel == listOf("Healthy Leaf")) emptyList() else legacyLabel
                        val stamp = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
                        ScanEntity.create(imagePath, names, emptyList(), 0f, stamp)
                    }
                    dao.insertScan(entity)
                }
                // Clear migrated entries so migration does not repeat
                prefs.edit().remove("saved_scans").apply()
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error migrating legacy SharedPreferences: ${e.message}")
            }
        }
    }
}
