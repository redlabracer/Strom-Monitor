package com.example.strom_monitor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class PersonViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val PREFS_NAME = "billing_prefs"
        const val KEY_LOCKED_UNTIL = "locked_until_timestamp"
        const val KEY_BILLING_YEAR = "current_billing_year"
        const val KEY_BILLING_MONTH = "current_billing_month"
    }

    private val personDao: PersonDao
    private val monatsabrechnungDao: MonatsabrechnungDao
    val allePersonenUndAnlagen: LiveData<List<Person>>

    private val _isBillingLocked = MutableLiveData<Boolean>()
    val isBillingLocked: LiveData<Boolean> get() = _isBillingLocked

    private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val database = AppDatabase.getDatabase(application)
        personDao = database.personDao()
        monatsabrechnungDao = database.monatsabrechnungDao()
        allePersonenUndAnlagen = personDao.getAllPersonenUndAnlagen()

        checkBillingLock()
        initializeBillingPeriod()

        viewModelScope.launch(Dispatchers.IO) {
            if (personDao.getCount() == 0) {
                val anfangsPersonen = listOf(
                    Person(id = 1, name = "Person 1", verbrauchteKwh = 0.0),
                    Person(id = 2, name = "Person 2", verbrauchteKwh = 0.0),
                    Person(id = 3, name = "Person 3", verbrauchteKwh = 0.0),
                    Person(id = 4, name = "Person 4", verbrauchteKwh = 0.0),
                    Person(id = 5, name = "Person 5 (Fixkosten)", verbrauchteKwh = 0.0),
                    Person(id = 6, name = "Solaranlage 1", verbrauchteKwh = 0.0),
                    Person(id = 7, name = "Solaranlage 2", verbrauchteKwh = 0.0)
                )
                personDao.insertAll(anfangsPersonen)
            }
        }
    }

    fun checkBillingLock() {
        val lockedUntil = sharedPreferences.getLong(KEY_LOCKED_UNTIL, 0)
        val isLocked = System.currentTimeMillis() < lockedUntil
        _isBillingLocked.postValue(isLocked)
    }

    private fun initializeBillingPeriod() {
        if (!sharedPreferences.contains(KEY_BILLING_YEAR)) {
            val calendar = Calendar.getInstance()
            with(sharedPreferences.edit()) {
                putInt(KEY_BILLING_YEAR, calendar.get(Calendar.YEAR))
                putInt(KEY_BILLING_MONTH, calendar.get(Calendar.MONTH) + 1)
                apply()
            }
        }
    }

    fun getCurrentBillingPeriod(): Pair<Int, Int> {
        val year = sharedPreferences.getInt(KEY_BILLING_YEAR, Calendar.getInstance().get(Calendar.YEAR))
        val month = sharedPreferences.getInt(KEY_BILLING_MONTH, Calendar.getInstance().get(Calendar.MONTH) + 1)
        return Pair(year, month)
    }

    fun erstelleMonatsabrechnung() = viewModelScope.launch(Dispatchers.IO) {
        if (_isBillingLocked.value == true) return@launch

        val billingYear = sharedPreferences.getInt(KEY_BILLING_YEAR, -1)
        val billingMonth = sharedPreferences.getInt(KEY_BILLING_MONTH, -1)
        if (billingYear == -1 || billingMonth == -1) return@launch

        val calendar = Calendar.getInstance().apply {
            set(billingYear, billingMonth - 1, 1)
            add(Calendar.MONTH, -1)
        }
        val abrechnungsJahr = calendar.get(Calendar.YEAR)
        val abrechnungsMonat = calendar.get(Calendar.MONTH) + 1

        val existingBills = monatsabrechnungDao.getAbrechnungenFuerMonat(abrechnungsJahr, abrechnungsMonat)
        if (existingBills.isNotEmpty()) return@launch

        val application = getApplication<Application>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(application)
        val preisProKwh = prefs.getString("preis_kwh", "0.35")?.toDoubleOrNull() ?: 0.35
        val grundgebuehrDefault = prefs.getString("grundgebuehr", "15.00")?.toDoubleOrNull() ?: 15.00
        val allPersons = personDao.getAllPersonsAsList()

        for (person in allPersons) {
            if (person.name.contains("Solaranlage")) {
                val solaranlageAbrechnung = Monatsabrechnung(personId = person.id, monat = abrechnungsMonat, jahr = abrechnungsJahr, verbrauchteKwh = person.verbrauchteKwh, gesamtkosten = 0.0, bezahlt = true)
                monatsabrechnungDao.insert(solaranlageAbrechnung)
                personDao.update(person.copy(verbrauchteKwh = 0.0))
                continue
            }
            val grundgebuehr = when (person.id) {
                2 -> 0.0
                5 -> 120.0
                else -> grundgebuehrDefault
            }
            val kostenVerbrauch = person.verbrauchteKwh * preisProKwh
            val gesamtkosten = if (person.id == 5) grundgebuehr else kostenVerbrauch + grundgebuehr
            val neuesGuthaben = person.guthaben - gesamtkosten
            val verbrauchZuruecksetzen = if (person.id != 5) 0.0 else person.verbrauchteKwh
            personDao.update(person.copy(guthaben = neuesGuthaben, verbrauchteKwh = verbrauchZuruecksetzen))
            val neueAbrechnung = Monatsabrechnung(personId = person.id, monat = abrechnungsMonat, jahr = abrechnungsJahr, verbrauchteKwh = person.verbrauchteKwh, gesamtkosten = gesamtkosten, bezahlt = false, bezahlterBetrag = 0.0)
            monatsabrechnungDao.insert(neueAbrechnung)
        }

        val nextBillingCalendar = Calendar.getInstance().apply {
            set(billingYear, billingMonth - 1, 1)
            add(Calendar.MONTH, 1)
        }

        // KORREKTUR: App bis zum 1. des NÄCHSTEN Monats sperren.
        // Wenn der nächste Abrechnungszeitraum August ist, muss die Sperre bis zum 1. August gelten.
        val lockCalendar = Calendar.getInstance().apply {
            set(billingYear, billingMonth - 1, 1) // Startet im aktuellen Abrechnungsmonat (z.B. Juli)
            add(Calendar.MONTH, 1)            // Geht zum nächsten Monat (z.B. August)
            set(Calendar.DAY_OF_MONTH, 1);
            set(Calendar.HOUR_OF_DAY, 0);
            set(Calendar.MINUTE, 0);
            set(Calendar.SECOND, 0);
        }

        with(sharedPreferences.edit()) {
            putInt(KEY_BILLING_YEAR, nextBillingCalendar.get(Calendar.YEAR))
            putInt(KEY_BILLING_MONTH, nextBillingCalendar.get(Calendar.MONTH) + 1)
            putLong(KEY_LOCKED_UNTIL, lockCalendar.timeInMillis)
            apply()
        }

        checkBillingLock()
    }

    fun updatePerson(person: Person) = viewModelScope.launch(Dispatchers.IO) { personDao.update(person) }
    fun getAbrechnungenFuerPerson(personId: Int): LiveData<List<Monatsabrechnung>> = monatsabrechnungDao.getAbrechnungenFuerPerson(personId)
    fun updateBillingStatus(abrechnung: Monatsabrechnung, isChecked: Boolean) = viewModelScope.launch(Dispatchers.IO) { monatsabrechnungDao.update(abrechnung.copy(bezahlt = isChecked)) }
    fun updateGuthaben(personId: Int, guthaben: Double) = viewModelScope.launch(Dispatchers.IO) { personDao.updateGuthaben(personId, guthaben) }
    fun updatePersonName(personId: Int, newName: String) = viewModelScope.launch(Dispatchers.IO) { personDao.updateName(personId, newName) }
    fun recordPayment(abrechnung: Monatsabrechnung, zahlungsbetrag: Double) = viewModelScope.launch(Dispatchers.IO) {
        val neuerBezahlterBetrag = abrechnung.bezahlterBetrag + zahlungsbetrag
        val updatedAbrechnung = abrechnung.copy(bezahlterBetrag = neuerBezahlterBetrag, bezahlt = neuerBezahlterBetrag >= abrechnung.gesamtkosten)
        monatsabrechnungDao.update(updatedAbrechnung)
    }
}