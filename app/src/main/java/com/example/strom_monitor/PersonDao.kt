package com.example.strom_monitor

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.*

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(personen: List<Person>)

    @Update
    suspend fun update(person: Person)

    @Query("SELECT * FROM personen_tabelle ORDER BY name ASC")
    fun getAllPersonen(): LiveData<List<Person>>

    @Query("SELECT COUNT(*) FROM personen_tabelle")
    suspend fun getCount(): Int

    @Query("UPDATE personen_tabelle SET guthaben = :neuesGuthaben WHERE id = :personId")
    suspend fun updateGuthaben(personId: Int, neuesGuthaben: Double)

    @Query("UPDATE personen_tabelle SET name = :newName WHERE id = :personId")
    suspend fun updateName(personId: Int, newName: String)

    @Query("SELECT * FROM personen_tabelle WHERE name NOT LIKE '%Solaranlage%' ORDER BY name ASC")
    fun getVerbraucher(): LiveData<List<Person>>

    @Query("SELECT * FROM personen_tabelle WHERE name LIKE '%Solaranlage%' ORDER BY name ASC")
    fun getSolaranlagen(): LiveData<List<Person>>

    @Query("SELECT * FROM personen_tabelle ORDER BY id ASC")
    fun getAllPersonenUndAnlagen(): LiveData<List<Person>>

    @Query("SELECT * FROM personen_tabelle ORDER BY id ASC")
    suspend fun getAllPersonsAsList(): List<Person>

    @Query("SELECT * FROM personen_tabelle WHERE id = :personId")
    suspend fun getPersonById(personId: Int): Person?
}