package com.mempharma.app.di

import android.content.Context
import androidx.room.Room
import com.mempharma.app.data.local.dao.DoseEventDao
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mempharma.db")
            .fallbackToDestructiveMigration() // v1 only; replaced by real migrations later
            .build()

    @Provides
    fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideDoseEventDao(db: AppDatabase): DoseEventDao = db.doseEventDao()
}
