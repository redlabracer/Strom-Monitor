package com.example.strom_monitor

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), MainFragment.OnPersonSelectedListener {

    private lateinit var personViewModel: PersonViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personViewModel = ViewModelProvider(this).get(PersonViewModel::class.java)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener(navListener)

        if (savedInstanceState == null) {
            loadFragment(MainFragment())
            bottomNav.selectedItemId = R.id.navigation_home
        }
    }

    private val navListener = NavigationBarView.OnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> loadFragment(MainFragment())
            R.id.navigation_statistics -> loadFragment(StatisticsFragment())
            R.id.navigation_settings -> loadFragment(SettingsFragment())
        }
        true
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val billingMenuItem = menu.findItem(R.id.action_create_billing)

        val isLocked = personViewModel.isBillingLocked.value ?: false

        billingMenuItem?.isVisible = currentFragment is MainFragment
        billingMenuItem?.isEnabled = !isLocked
        billingMenuItem?.icon?.alpha = if (isLocked) 130 else 255 // Visuelles Feedback

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_create_billing -> {
                if (personViewModel.isBillingLocked.value == true) {
                    Toast.makeText(this, "Abrechnung für diesen Monat bereits erfolgt.", Toast.LENGTH_SHORT).show()
                } else {
                    showCreateBillingConfirmationDialog()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCreateBillingConfirmationDialog() {
        val (year, month) = personViewModel.getCurrentBillingPeriod()
        val calendar = Calendar.getInstance().apply {
            set(year, month - 1, 1)
            add(Calendar.MONTH, -1)
        }
        val monthToBillStr = SimpleDateFormat("MMMM yyyy", Locale.GERMANY).format(calendar.time)

        AlertDialog.Builder(this)
            .setTitle("Abrechnung für $monthToBillStr erstellen?")
            .setMessage("Die Abrechnung für $monthToBillStr wird erstellt, Zählerstände zurückgesetzt und die Eingabe bis zum nächsten Monat gesperrt. Dieser Vorgang kann nicht rückgängig gemacht werden.")
            .setPositiveButton("Abrechnen") { _, _ ->
                personViewModel.erstelleMonatsabrechnung()
                Toast.makeText(this, "Monatsabrechnung wird erstellt...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onPersonSelected(personId: Int) {
        val fragment = StatisticsFragment()
        val bundle = Bundle()
        bundle.putInt("SELECTED_PERSON_ID", personId)
        fragment.arguments = bundle
        loadFragment(fragment)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.navigation_statistics
    }
}