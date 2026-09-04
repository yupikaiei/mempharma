package com.mempharma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mempharma.app.data.local.entity.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM medications ORDER BY lower(name)")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY lower(name)")
    fun observeActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun observe(id: Long): Flow<Medication?>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: Long): Medication?

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<Medication>

    @Query("UPDATE medications SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Int)
}
