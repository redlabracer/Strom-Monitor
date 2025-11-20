package com.example.strom_monitor

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.strom_monitor.databinding.FragmentMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var personViewModel: PersonViewModel
    private lateinit var adapter: PersonListAdapter

    interface OnPersonSelectedListener {
        fun onPersonSelected(personId: Int)
    }
    private var listener: OnPersonSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPersonSelectedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnPersonSelectedListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        personViewModel = ViewModelProvider(requireActivity()).get(PersonViewModel::class.java)

        // Immer den neusten Sperrstatus vom ViewModel abfragen
        personViewModel.checkBillingLock()

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        adapter = PersonListAdapter(
            onUpdate = { person -> personViewModel.updatePerson(person) },
            onEdit = { person -> showEditKwhDialog(person) },
            onPersonClick = { person -> listener?.onPersonSelected(person.id) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun updateTitle() {
        val (year, month) = personViewModel.getCurrentBillingPeriod()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            add(Calendar.MONTH, -1)
        }
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
        binding.textViewMonth.text = "Abrechnung für: ${monthFormat.format(calendar.time)}"
    }

    private fun updateTotals(personen: List<Person>) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val preisProKwh = prefs.getString("preis_kwh", "0.35")?.toDoubleOrNull() ?: 0.35
        val grundgebuehrDefault = prefs.getString("grundgebuehr", "15.00")?.toDoubleOrNull() ?: 15.00

        var totalKwh = 0.0
        var totalCost = 0.0

        for (person in personen) {
            if (person.name.contains("Solaranlage")) continue

            totalKwh += person.verbrauchteKwh

            // Logic must match ViewModel to be accurate
            val grundgebuehr = when (person.id) {
                2 -> 0.0
                5 -> 120.0
                else -> grundgebuehrDefault
            }
            
            val cost = if (person.id == 5) {
                grundgebuehr
            } else {
                (person.verbrauchteKwh * preisProKwh) + grundgebuehr
            }
            totalCost += cost
        }

        val kwhFormat = java.text.DecimalFormat("#,##0.##")
        val currencyFormat = java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY)

        binding.textViewTotalKwh.text = "${kwhFormat.format(totalKwh)} kWh"
        binding.textViewTotalCost.text = currencyFormat.format(totalCost)
    }

    private fun observeViewModel() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE

        // Auf Änderungen des Sperr-Status reagieren
        personViewModel.isBillingLocked.observe(viewLifecycleOwner) { isLocked ->
            adapter.setLocked(isLocked)
            requireActivity().invalidateOptionsMenu() // Menü neu zeichnen (Button an/aus)
        }

        personViewModel.allePersonenUndAnlagen.observe(viewLifecycleOwner) { personen ->
            binding.progressBar.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE

            if (personen != null) {
                updateTotals(personen)

                val verbraucher = personen.filter { !it.name.contains("Solaranlage") }
                val solaranlagen = personen.filter { it.name.contains("Solaranlage") }

                val listItems = mutableListOf<ListItem>()
                verbraucher.forEach { listItems.add(ListItem.PersonItem(it)) }

                if (solaranlagen.isNotEmpty()) {
                    listItems.add(ListItem.SolarItem(solaranlagen))
                }
                adapter.setData(listItems)
                updateTitle() // Titel aktualisieren, sobald Daten da sind
            }
        }
    }

    private fun showEditKwhDialog(person: Person) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${person.name}: kWh-Stand bearbeiten")
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(person.verbrauchteKwh.toString())
        builder.setView(input)
        builder.setPositiveButton("Speichern") { _, _ ->
            val neuerWertStr = input.text.toString()
            val neuerWert = neuerWertStr.toDoubleOrNull()
            if (neuerWert != null) {
                val updatedPerson = person.copy(verbrauchteKwh = neuerWert)
                personViewModel.updatePerson(updatedPerson)
                Toast.makeText(context, "Wert für ${person.name} aktualisiert", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Ungültige Eingabe", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Abbrechen") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}