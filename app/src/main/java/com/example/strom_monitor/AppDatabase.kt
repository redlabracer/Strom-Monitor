package com.example.strom_monitor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Version auf 5 erhöhen, um die Änderungen sicher zu übernehmen
@Database(entities = [Person::class, Monatsabrechnung::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun monatsabrechnungDao(): MonatsabrechnungDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "strom_monitor_database_final"
                )
                    // Der Callback, der Probleme gemacht hat, wird entfernt
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
        fun closeInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }

    // Die gesamte "DatabaseCallback" Klasse wurde entfernt, da sie nicht mehr benötigt wird.
}