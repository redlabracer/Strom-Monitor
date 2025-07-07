package com.example.strom_monitor

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Person::class, Monatsabrechnung::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun monatsabrechnungDao(): MonatsabrechnungDao

    companion object {

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL("ALTER TABLE personen_tabelle ADD COLUMN guthaben REAL NOT NULL DEFAULT 0.0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "strom_database"
                )

                    .addMigrations(MIGRATION_2_3)
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }



    private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {

                    val personDao = database.personDao()
                    if (personDao.getCount() == 0) {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                        val p1Name = prefs.getString("person_1_name", "Person 1")!!
                        val p2Name = prefs.getString("person_2_name", "Person 2")!!
                        val p3Name = prefs.getString("person_3_name", "Person 3")!!
                        val p4Name = prefs.getString("person_4_name", "Person 4")!!
                        val p5Name = prefs.getString("person_5_name", "Person 5 (Fixkosten)")!!

                        val anfangsPersonen = listOf(
                            Person(name = p1Name, verbrauchteKwh = 0.0),
                            Person(name = p2Name, verbrauchteKwh = 0.0),
                            Person(name = p3Name, verbrauchteKwh = 0.0),
                            Person(name = p4Name, verbrauchteKwh = 0.0),
                            Person(name = p5Name, verbrauchteKwh = 0.0)
                        )
                        personDao.insertAll(anfangsPersonen)
                    }

                // --- CODE ZUM EINFÜGEN VON TESTDATEN ---
                // val abrechnungDao = database.monatsabrechnungDao()
                // Testdaten für Mai 2025
                //  abrechnungDao.insert(Monatsabrechnung(personId = 1, monat = 5, jahr = 2025, verbrauchteKwh = 150.5, gesamtkosten = 67.67, bezahlt = true))
                // abrechnungDao.insert(Monatsabrechnung(personId = 2, monat = 5, jahr = 2025, verbrauchteKwh = 120.0, gesamtkosten = 57.0, bezahlt = false))
                // abrechnungDao.insert(Monatsabrechnung(personId = 3, monat = 5, jahr = 2025, verbrauchteKwh = 210.2, gesamtkosten = 88.57, bezahlt = true))
                // abrechnungDao.insert(Monatsabrechnung(personId = 4, monat = 5, jahr = 2025, verbrauchteKwh = 95.0, gesamtkosten = 48.25, bezahlt = false))

                // Testdaten für Juni 2025
                // abrechnungDao.insert(Monatsabrechnung(personId = 1, monat = 6, jahr = 2025, verbrauchteKwh = 160.0, gesamtkosten = 71.0, bezahlt = false))
                // abrechnungDao.insert(Monatsabrechnung(personId = 2, monat = 6, jahr = 2025, verbrauchteKwh = 130.0, gesamtkosten = 60.5, bezahlt = true))
                // abrechnungDao.insert(Monatsabrechnung(personId = 3, monat = 6, jahr = 2025, verbrauchteKwh = 190.0, gesamtkosten = 81.5, bezahlt = false))
                //   abrechnungDao.insert(Monatsabrechnung(personId = 4, monat = 6, jahr = 2025, verbrauchteKwh = 110.0, gesamtkosten = 53.5, bezahlt = true))
                }
            }
        }
    }
}