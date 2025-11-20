package com.example.strom_monitor

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var personViewModel: PersonViewModel

    // Launcher für den "Backup erstellen" Dialog
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
        uri?.let {
            try {
                val dbPath = requireContext().getDatabasePath("strom_monitor_database_final")
                requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                    FileInputStream(dbPath).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(requireContext(), "Backup erfolgreich erstellt.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Backup fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher für den "Wiederherstellen" Dialog
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                // WICHTIG: Datenbankverbindung schließen, bevor die Datei überschrieben wird
                AppDatabase.closeInstance()

                val dbPath = requireContext().getDatabasePath("strom_monitor_database_final")
                requireContext().contentResolver.openInputStream(it)?.use { inputStream ->
                    FileOutputStream(dbPath).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Dialog anzeigen, der den Benutzer zum Neustart der App auffordert
                AlertDialog.Builder(requireContext())
                    .setTitle("Wiederherstellung erfolgreich")
                    .setMessage("Die App muss neu gestartet werden, um die Daten zu laden. Bitte starten Sie die App jetzt neu.")
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Wiederherstellung fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personViewModel = ViewModelProvider(requireActivity()).get(PersonViewModel::class.java)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<Preference>("backup_data")?.setOnPreferenceClickListener {
            val calendar = Calendar.getInstance()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(calendar.time)
            val fileName = "strom_monitor_backup_$formattedDate.db"
            backupLauncher.launch(fileName)
            true
        }

        findPreference<Preference>("restore_data")?.setOnPreferenceClickListener {
            restoreLauncher.launch(arrayOf("application/vnd.sqlite3", "application/octet-stream"))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != null) {
            val personId = when (key) {
                "person_1_name" -> 1
                "person_2_name" -> 2
                "person_3_name" -> 3
                "person_4_name" -> 4
                "person_5_name" -> 5
                else -> -1
            }

            if (personId != -1) {
                val newName = sharedPreferences.getString(key, "") ?: ""
                if (newName.isNotEmpty()) {
                    personViewModel.updatePersonName(personId, newName)
                }
            }
        }
    }
}