package com.example.strom_monitor

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.strom_monitor.databinding.FragmentStatisticsBinding
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.github.mikephil.charting.components.XAxis

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var personViewModel: PersonViewModel
    private lateinit var abrechnungAdapter: AbrechnungListAdapter
    private var allPersons: List<Person> = emptyList()
    private val chartObservers = mutableListOf<LiveData<List<Monatsabrechnung>>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        personViewModel = ViewModelProvider(this).get(PersonViewModel::class.java)
        val selectedPersonId = arguments?.getInt("SELECTED_PERSON_ID", -1) ?: -1
        setupRecyclerView()
        observePersonsForSpinner(selectedPersonId)
        setupChart()
    }

    private fun setupRecyclerView() {
        abrechnungAdapter = AbrechnungListAdapter(requireContext()) { abrechnung ->
            personViewModel.updateAbrechnung(abrechnung)
        }
        binding.recyclerViewAbrechnungen.adapter = abrechnungAdapter
        binding.recyclerViewAbrechnungen.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observePersonsForSpinner(preselectPersonId: Int) {
        personViewModel.allePersonen.observe(viewLifecycleOwner) { personen ->
            if (personen.isNotEmpty()) {
                allPersons = personen
                val personNames = personen.map { it.name }
                val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, personNames)
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerPersons.adapter = spinnerAdapter

                if (preselectPersonId != -1) {
                    val personToSelect = allPersons.find { it.id == preselectPersonId }
                    val position = allPersons.indexOf(personToSelect)
                    if (position >= 0) {
                        binding.spinnerPersons.setSelection(position)
                    }
                }

                binding.spinnerPersons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedPerson = allPersons[position]
                        observeAbrechnungen(selectedPerson.id)
                        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
                        binding.textViewCurrentGuthaben.text = "Aktuelles Guthaben: ${currencyFormat.format(selectedPerson.guthaben)}"
                        binding.buttonEditGuthaben.setOnClickListener {
                            showEditGuthabenDialog(selectedPerson)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                updateChartData()
            }
        }
    }

    private fun observeAbrechnungen(personId: Int) {
        personViewModel.getAbrechnungenFuerPerson(personId).distinctUntilChanged().observe(viewLifecycleOwner) { abrechnungen ->
            abrechnungAdapter.setData(abrechnungen)
        }
    }

    private fun showEditGuthabenDialog(person: Person) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${person.name}: Guthaben bearbeiten")
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(person.guthaben.toString())
        builder.setView(input)
        builder.setPositiveButton("OK") { _, _ ->
            val newValue = input.text.toString().toDoubleOrNull()
            if (newValue != null) {
                personViewModel.updateGuthaben(person.id, newValue)
            } else {
                Toast.makeText(requireContext(), "Ungültige Eingabe", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Abbrechen") { dialog, _ -> dialog.cancel() }
        builder.show()
    }
    private fun setupChart() {
        binding.barChart.description.isEnabled = false
        binding.barChart.setDrawGridBackground(false)
        binding.barChart.setDrawValueAboveBar(true)

        val xAxis = binding.barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        binding.barChart.axisLeft.axisMinimum = 0f
        binding.barChart.axisRight.isEnabled = false
    }

    private fun updateChartData() {

        chartObservers.forEach { it.removeObservers(viewLifecycleOwner) }
        chartObservers.clear()

        val chartPersons = allPersons.filter { !it.name.contains("Person 5") }
        if (chartPersons.isEmpty()) return

        val colors = listOf(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN)
        val monthLabels = (1..6).map { // Labels für die letzten 6 Monate
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -it + 1)
            SimpleDateFormat("MMM", Locale.GERMANY).format(cal.time)
        }.reversed()

        val resultsMap = mutableMapOf<Int, List<Monatsabrechnung>>()


        chartPersons.forEach { person ->
            val liveData = personViewModel.getAbrechnungenFuerPerson(person.id)
            chartObservers.add(liveData)

            liveData.observe(viewLifecycleOwner) { abrechnungen ->
                resultsMap[person.id] = abrechnungen

                if (resultsMap.size == chartPersons.size) {

                    val dataSets = mutableListOf<IBarDataSet>()

                    chartPersons.forEachIndexed { index, p ->
                        val personAbrechnungen = resultsMap[p.id] ?: emptyList()
                        val entries = ArrayList<BarEntry>()

                        monthLabels.forEachIndexed { monthIndex, _ ->
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.MONTH, -(monthLabels.size - 1 - monthIndex))
                            val targetMonth = cal.get(Calendar.MONTH) + 1
                            val targetYear = cal.get(Calendar.YEAR)

                            val abrechnung = personAbrechnungen.find { it.monat == targetMonth && it.jahr == targetYear }
                            val kosten = abrechnung?.gesamtkosten?.toFloat() ?: 0f
                            entries.add(BarEntry(monthIndex.toFloat(), kosten))
                        }

                        val dataSet = BarDataSet(entries, p.name)
                        dataSet.color = colors[index % colors.size]
                        dataSets.add(dataSet)
                    }


                    val barData = BarData(dataSets)
                    barData.barWidth = 0.15f
                    binding.barChart.data = barData
                    binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
                    binding.barChart.xAxis.labelCount = monthLabels.size
                    binding.barChart.groupBars(0f, 0.3f, 0.05f)
                    binding.barChart.invalidate()
                }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        chartObservers.forEach { it.removeObservers(viewLifecycleOwner) }
        _binding = null
    }
}