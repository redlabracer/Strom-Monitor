package com.example.strom_monitor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class PersonViewModel(application: Application) : AndroidViewModel(application) {

    private val personDao: PersonDao
    private val monatsabrechnungDao: MonatsabrechnungDao
    val allePersonen: LiveData<List<Person>>

    init {
        val database = AppDatabase.getDatabase(application)
        personDao = database.personDao()
        monatsabrechnungDao = database.monatsabrechnungDao()
        allePersonen = personDao.getAllPersonen()
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch(Dispatchers.IO) {
            personDao.update(person)
        }
    }

    fun updatePersonName(personId: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            personDao.updateName(personId, newName)
        }
    }

    fun resetAllPersonsKwh() {
        viewModelScope.launch(Dispatchers.IO) {
            val personen = allePersonen.value ?: return@launch
            val updatedPersonen = personen.map { it.copy(verbrauchteKwh = 0.0) }
            updatedPersonen.forEach { personDao.update(it) }
        }
    }
    fun updateGuthaben(personId: Int, neuesGuthaben: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            personDao.updateGuthaben(personId, neuesGuthaben)
        }
    }

    fun checkForMonthlyReset() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastResetMonth = prefs.getInt("last_reset_month", -1)
            val lastResetYear = prefs.getInt("last_reset_year", -1)

            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            if (currentMonth != lastResetMonth || currentYear != lastResetYear) {

                if (lastResetMonth != -1) {
                    archiveLastMonthData()
                }


                prefs.edit()
                    .putInt("last_reset_month", currentMonth)
                    .putInt("last_reset_year", currentYear)
                    .apply()
            }
        }
    }

    private suspend fun archiveLastMonthData() {

        val personen = personDao.getAllPersonen().value ?: return


        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val monthToArchive = calendar.get(Calendar.MONTH) + 1
        val yearToArchive = calendar.get(Calendar.YEAR)


        val context = getApplication<Application>().applicationContext
        val settingsPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preisProKwh = settingsPrefs.getString("preis_kwh", "0.35")?.toDoubleOrNull() ?: 0.35
        val grundgebuehrNormal = settingsPrefs.getString("grundgebuehr", "15.00")?.toDoubleOrNull() ?: 15.00

        personen.forEach { person ->
            var grundgebuehr = grundgebuehrNormal
            var verbrauch = person.verbrauchteKwh


            if (person.name.contains("Person 5")) {
                grundgebuehr = 120.0
                verbrauch = 0.0
            }

            val gesamtkosten = (verbrauch * preisProKwh) + grundgebuehr
            val abrechnung = Monatsabrechnung(
                personId = person.id,
                monat = monthToArchive,
                jahr = yearToArchive,
                verbrauchteKwh = verbrauch,
                gesamtkosten = gesamtkosten,
                bezahlt = false
            )
            monatsabrechnungDao.insert(abrechnung)


            val updatedPerson = person.copy(verbrauchteKwh = 0.0)
            personDao.update(updatedPerson)
        }
    }



    fun getAbrechnungenFuerPerson(personId: Int): LiveData<List<Monatsabrechnung>> {
        return monatsabrechnungDao.getAbrechnungenFuerPerson(personId)
    }

    fun updateAbrechnung(abrechnung: Monatsabrechnung) {
        viewModelScope.launch(Dispatchers.IO) {
            monatsabrechnungDao.update(abrechnung)
        }
    }
}