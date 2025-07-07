package com.example.strom_monitor

import android.app.AlertDialog
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
import java.util.Calendar
import java.util.Locale

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var personViewModel: PersonViewModel
    private lateinit var adapter: PersonListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        personViewModel = ViewModelProvider(this).get(PersonViewModel::class.java)
        personViewModel.checkForMonthlyReset()
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
        binding.textViewMonth.text = "Abrechnung für: ${monthFormat.format(calendar.time)}"

        adapter = PersonListAdapter(
            onUpdate = { person -> personViewModel.updatePerson(person) },
            onEdit = { person -> showEditKwhDialog(person) },
            onPersonClick = { person ->
                val fragment = StatisticsFragment()
                val bundle = Bundle()
                bundle.putInt("SELECTED_PERSON_ID", person.id)
                fragment.arguments = bundle
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.buttonResetMonth.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    private fun observeViewModel() {
        personViewModel.allePersonen.observe(viewLifecycleOwner) { personen ->
            personen?.let { adapter.setData(it) }
        }
    }

    private fun showEditKwhDialog(person: Person) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${person.name}: kWh-Stand bearbeiten")
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(person.verbrauchteKwh.toString())
        builder.setView(input)
        builder.setPositiveButton("OK") { _, _ ->
            val newValue = input.text.toString().toDoubleOrNull()
            if (newValue != null) {
                val updatedPerson = person.copy(verbrauchteKwh = newValue)
                personViewModel.updatePerson(updatedPerson)
            } else {
                Toast.makeText(requireContext(), "Ungültige Eingabe", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Abbrechen") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Monat zurücksetzen?")
            .setMessage("Möchtest du den Verbrauch aller Personen für diesen Monat wirklich auf 0 zurücksetzen?")
            .setPositiveButton("Ja, zurücksetzen") { _, _ ->
                personViewModel.resetAllPersonsKwh()
                Toast.makeText(requireContext(), "Verbrauch zurückgesetzt", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Nein", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}