package com.example.strom_monitor

sealed class ListItem {
    data class PersonItem(val person: Person) : ListItem()
    data class SolarItem(val solaranlagen: List<Person>) : ListItem()
}