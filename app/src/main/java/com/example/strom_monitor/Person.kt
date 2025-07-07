package com.example.strom_monitor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personen_tabelle")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    var name: String,
    var verbrauchteKwh: Double,
    var guthaben: Double = 0.0
)