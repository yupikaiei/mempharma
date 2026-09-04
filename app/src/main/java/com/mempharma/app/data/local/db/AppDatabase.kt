package com.mempharma.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mempharma.app.data.local.dao.DoseEventDao
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication

/**
 * Room database. Version is bumped + a migration added every time the schema
 * changes so people never lose their medicine history on update.
 * (exportSchema = true writes schemas to app/schemas for migration tests.)
 */
@Database(
    entities = [Medication::class, DoseEvent::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseEventDao(): DoseEventDao
}
