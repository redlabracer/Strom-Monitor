package com.example.strom_monitor

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MonatsabrechnungDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(abrechnung: Monatsabrechnung)

    @Update
    suspend fun update(abrechnung: Monatsabrechnung)

    @Query("SELECT * FROM monatsabrechnung_tabelle WHERE personId = :personId ORDER BY jahr DESC, monat DESC")
    fun getAbrechnungenFuerPerson(personId: Int): LiveData<List<Monatsabrechnung>>

    @Query("SELECT * FROM monatsabrechnung_tabelle WHERE jahr = :jahr AND monat = :monat")
    suspend fun getAbrechnungenFuerMonat(jahr: Int, monat: Int): List<Monatsabrechnung>
}