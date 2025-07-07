package com.example.strom_monitor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monatsabrechnung_tabelle")
data class Monatsabrechnung(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val personId: Int,
    val monat: Int, // 1-12
    val jahr: Int,
    val verbrauchteKwh: Double,
    val gesamtkosten: Double,
    var bezahlt: Boolean = false
)