package com.example.strom_monitor

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.strom_monitor.databinding.FragmentStatisticsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import java.text.NumberFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var personViewModel: PersonViewModel
    private lateinit var abrechnungAdapter: AbrechnungListAdapter
    private var allPersonsAndSolar: List<Person> = emptyList()

    private var selectedPerson: Person? = null
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var availableYears: List<Int> = emptyList()

    private val monthLabels = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

    private var solar1Data: List<Monatsabrechnung>? = null
    private var solar2Data: List<Monatsabrechnung>? = null

    private var spinnersInitialized = false

    private val csvExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            exportDataToUri(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        personViewModel = ViewModelProvider(this).get(PersonViewModel::class.java)

        setupRecyclerView()
        setupChart()
        setupSpinners()
        setupExportButton()
        observeData()
    }

    private fun setupChart() {
        val nightModeFlags = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val textColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        binding.barChart.description.isEnabled = false
        binding.barChart.setDrawGridBackground(false)
        binding.barChart.setDrawValueAboveBar(true)

        val xAxis = binding.barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        xAxis.textColor = textColor

        // ***** KORREKTUREN FÜR DIE ACHSE *****
        // Stellt sicher, dass die Labels zentriert unter den Balkengruppen angezeigt werden.
        xAxis.setCenterAxisLabels(true)
        // Setzt den Startpunkt der Achse explizit auf 0.
        xAxis.axisMinimum = 0f
        // Stellt sicher, dass genügend Platz für alle 12 Monate vorhanden ist.
        xAxis.axisMaximum = 12f
        // Verhindert, dass die erste und letzte Beschriftung abgeschnitten werden.
        xAxis.setAvoidFirstLastClipping(true)


        val leftAxis = binding.barChart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = textColor

        binding.barChart.axisRight.isEnabled = false
        binding.barChart.legend.textColor = textColor
    }

    private fun updatePersonChartData(year: Int) {
        val nightModeFlags = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val textColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK

        val chartPersons = allPersonsAndSolar.filter { p -> p.id != 5 && !p.name.contains("Solaranlage") }
        if (chartPersons.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }

        val dataSets = arrayOfNulls<IBarDataSet>(chartPersons.size)
        var dataLoadedCounter = 0
        val colors = listOf(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN)

        chartPersons.forEachIndexed { index, person ->
            val liveData = personViewModel.getAbrechnungenFuerPerson(person.id)
            liveData.observe(viewLifecycleOwner, object : Observer<List<Monatsabrechnung>> {
                override fun onChanged(abrechnungen: List<Monatsabrechnung>) {
                    liveData.removeObserver(this)
                    val entries = ArrayList<BarEntry>()
                    for (month in 1..12) {
                        val abrechnung = abrechnungen.find { it.jahr == year && it.monat == month }
                        val kosten = abrechnung?.gesamtkosten?.toFloat() ?: 0f
                        entries.add(BarEntry((month - 1).toFloat(), kosten))
                    }
                    dataSets[index] = BarDataSet(entries, person.name).apply {
                        color = colors[index % colors.size]
                        setValueTextColor(textColor)
                    }
                    dataLoadedCounter++
                    if (dataLoadedCounter == chartPersons.size) {
                        displayChartData(dataSets.filterNotNull().toMutableList(), 0.15f, 0.3f, 0.05f)
                    }
                }
            })
        }
    }

    private fun updateMietChartData(year: Int) {
        val nightModeFlags = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val textColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK

        personViewModel.getAbrechnungenFuerPerson(5).observe(viewLifecycleOwner) { abrechnungen ->
            val entries = ArrayList<BarEntry>()
            for (month in 1..12) {
                val abrechnung = abrechnungen.find { it.jahr == year && it.monat == month }
                val bezahlt = abrechnung?.bezahlterBetrag?.toFloat() ?: 0f
                val offen = (abrechnung?.gesamtkosten?.toFloat() ?: 120f) - bezahlt
                entries.add(BarEntry((month - 1).toFloat(), floatArrayOf(bezahlt, if (offen > 0) offen else 0f)))
            }

            val dataSet = BarDataSet(entries, "").apply {
                colors = listOf(Color.rgb(76, 175, 80), Color.rgb(244, 67, 54))
                stackLabels = arrayOf("Bezahlt", "Offen")
                setValueTextColor(textColor)
            }

            val barData = BarData(dataSet)
            barData.barWidth = 0.5f
            binding.barChart.data = barData
            binding.barChart.invalidate()
        }
    }

    private fun updateSolarChartData(year: Int) {
        val nightModeFlags = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val textColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK

        val solaranlagen = allPersonsAndSolar.filter { it.name.contains("Solaranlage") }.sortedBy { it.id }
        if (solaranlagen.size < 2) return

        val dataSets = arrayOfNulls<IBarDataSet>(solaranlagen.size)
        var dataLoadedCounter = 0
        val colors = listOf(Color.YELLOW, Color.rgb(255, 165, 0))

        solaranlagen.forEachIndexed { index, solaranlage ->
            val liveData = personViewModel.getAbrechnungenFuerPerson(solaranlage.id)
            liveData.observe(viewLifecycleOwner, object: Observer<List<Monatsabrechnung>>{
                override fun onChanged(abrechnungen: List<Monatsabrechnung>) {
                    liveData.removeObserver(this)
                    val entries = ArrayList<BarEntry>()
                    for (month in 1..12) {
                        val abrechnung = abrechnungen.find { it.jahr == year && it.monat == month }
                        val kwhValue = abrechnung?.verbrauchteKwh?.toFloat() ?: 0f
                        entries.add(BarEntry((month - 1).toFloat(), kwhValue))
                    }
                    dataSets[index] = BarDataSet(entries, solaranlage.name).apply {
                        color = colors[index % colors.size]
                        setValueTextColor(textColor)
                    }
                    dataLoadedCounter++
                    if (dataLoadedCounter >= solaranlagen.size) {
                        displayChartData(dataSets.filterNotNull().toMutableList(), 0.4f, 0.2f, 0.05f)
                    }
                }
            })
        }
    }

    private fun displayChartData(dataSets: MutableList<IBarDataSet>, barWidth: Float, groupSpace: Float, barSpace: Float) {
        if (dataSets.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }
        val barData = BarData(dataSets)
        barData.barWidth = barWidth
        binding.barChart.data = barData

        if (dataSets.size > 1) {
            // ***** KORREKTUR FÜR DIE GRUPPIERUNG *****
            // Der Startpunkt für die Gruppierung wird auf 0 gesetzt, damit es mit der Achse übereinstimmt.
            binding.barChart.groupBars(0f, groupSpace, barSpace)
        }

        binding.barChart.invalidate()
    }

    // ... Der Rest der Datei bleibt unverändert und kann aus Ihrer Vorlage übernommen werden.
    private fun setupExportButton() {
        binding.buttonExport.setOnClickListener {
            triggerCsvExport()
        }
    }

    private fun setupRecyclerView() {
        abrechnungAdapter = AbrechnungListAdapter(
            context = requireContext(),
            onAddPaymentClicked = { abrechnung -> showPaymentDialog(abrechnung) },
            onCheckboxChanged = { abrechnung, isChecked -> personViewModel.updateBillingStatus(abrechnung, isChecked) }
        )
        binding.recyclerViewAbrechnungen.adapter = abrechnungAdapter
        binding.recyclerViewAbrechnungen.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSpinners() {
        binding.spinnerPersons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (allPersonsAndSolar.isNotEmpty()) {
                    selectedPerson = allPersonsAndSolar[position]
                    updateDataViews()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(availableYears.isNotEmpty()){
                    selectedYear = availableYears[position]
                    updateDataViews()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeData() {
        personViewModel.allePersonenUndAnlagen.observe(viewLifecycleOwner) { personen ->
            if (personen.isEmpty()) return@observe

            allPersonsAndSolar = personen.sortedBy { it.id }

            if (!spinnersInitialized) {
                val personNames = allPersonsAndSolar.map { it.name }
                val personAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, personNames)
                personAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerPersons.adapter = personAdapter

                extractAvailableYears()

                val preselectPersonId = arguments?.getInt("SELECTED_PERSON_ID", -1) ?: -1
                if (preselectPersonId != -1) {
                    val position = allPersonsAndSolar.indexOfFirst { it.id == preselectPersonId }
                    if (position >= 0) {
                        binding.spinnerPersons.setSelection(position)
                    }
                }
                spinnersInitialized = true
            }

            val updatedSelectedPerson = allPersonsAndSolar.find { it.id == selectedPerson?.id }
            if (updatedSelectedPerson != null && !updatedSelectedPerson.name.contains("Solaranlage") && updatedSelectedPerson.id != 5) {
                val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
                binding.textViewDetail1.text = "Aktuelles Guthaben: ${currencyFormat.format(updatedSelectedPerson.guthaben)}"
            }
        }
    }

    private fun extractAvailableYears() {
        // Sicherstellen, dass die Liste nicht leer ist, bevor auf das erste Element zugegriffen wird
        if (allPersonsAndSolar.isEmpty()) return

        personViewModel.getAbrechnungenFuerPerson(allPersonsAndSolar.first().id).observe(viewLifecycleOwner) { abrechnungen ->
            val yearsFromDb = abrechnungen.map { it.jahr }.distinct().sortedDescending()

            if (yearsFromDb.isEmpty()) {
                availableYears = listOf(Calendar.getInstance().get(Calendar.YEAR))
            } else {
                availableYears = yearsFromDb
            }

            val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableYears)
            yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerYear.adapter = yearAdapter

            if (availableYears.isNotEmpty()) {
                val currentSelectionIndex = availableYears.indexOf(selectedYear)
                binding.spinnerYear.setSelection(if(currentSelectionIndex != -1) currentSelectionIndex else 0)
            }
        }
    }

    private fun updateDataViews() {
        if (selectedPerson == null) return

        if (selectedPerson!!.name.contains("Solaranlage")) {
            showSolarView()
        } else {
            showPersonView(selectedPerson!!)
        }
    }

    private fun showPersonView(person: Person) {
        binding.recyclerViewAbrechnungen.visibility = View.VISIBLE
        binding.layoutDetails.visibility = View.VISIBLE
        binding.textViewDetail1.visibility = View.VISIBLE
        binding.textViewDetail2.visibility = View.GONE
        binding.barChart.visibility = View.VISIBLE

        if (person.id == 5) {
            binding.buttonEditGuthaben.visibility = View.GONE
            binding.textViewDetail1.text = "Übersicht der Mietzahlungen"
            updateMietChartData(selectedYear)
        } else {
            binding.buttonEditGuthaben.visibility = View.VISIBLE
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
            binding.textViewDetail1.text = "Aktuelles Guthaben: ${currencyFormat.format(person.guthaben)}"
            binding.buttonEditGuthaben.setOnClickListener { showEditGuthabenDialog(person) }
            updatePersonChartData(selectedYear)
        }

        personViewModel.getAbrechnungenFuerPerson(person.id).distinctUntilChanged().observe(viewLifecycleOwner) { abrechnungen ->
            val filtered = abrechnungen.filter { it.jahr == selectedYear }
            abrechnungAdapter.setData(filtered)
        }
    }

    private fun showSolarView() {
        binding.recyclerViewAbrechnungen.visibility = View.VISIBLE
        binding.layoutDetails.visibility = View.VISIBLE
        binding.textViewDetail1.visibility = View.VISIBLE
        binding.textViewDetail2.visibility = View.VISIBLE
        binding.buttonEditGuthaben.visibility = View.GONE
        binding.barChart.visibility = View.VISIBLE

        loadAndProcessAllSolarData()
        updateSolarChartData(selectedYear)
    }

    private fun loadAndProcessAllSolarData() {
        val solaranlagen = allPersonsAndSolar.filter { it.name.contains("Solaranlage") }.sortedBy { it.id }
        if (solaranlagen.size < 2) return

        solar1Data = null
        solar2Data = null

        val liveData1 = personViewModel.getAbrechnungenFuerPerson(solaranlagen[0].id)
        liveData1.observe(viewLifecycleOwner, object : Observer<List<Monatsabrechnung>> {
            override fun onChanged(data: List<Monatsabrechnung>) {
                liveData1.removeObserver(this)
                solar1Data = data
                processSolarDataIfReady()
            }
        })

        val liveData2 = personViewModel.getAbrechnungenFuerPerson(solaranlagen[1].id)
        liveData2.observe(viewLifecycleOwner, object : Observer<List<Monatsabrechnung>> {
            override fun onChanged(data: List<Monatsabrechnung>) {
                liveData2.removeObserver(this)
                solar2Data = data
                processSolarDataIfReady()
            }
        })
    }

    private fun processSolarDataIfReady() {
        if (solar1Data != null && solar2Data != null) {
            val totalKwhYear = (solar1Data!!.filter { it.jahr == selectedYear }.sumOf { it.verbrauchteKwh }) +
                    (solar2Data!!.filter { it.jahr == selectedYear }.sumOf { it.verbrauchteKwh })
            val totalKwhAllTime = solar1Data!!.sumOf { it.verbrauchteKwh } + solar2Data!!.sumOf { it.verbrauchteKwh }

            binding.textViewDetail1.text = "Gesamteinspeisung ($selectedYear): ${"%.2f".format(totalKwhYear)} kWh"
            binding.textViewDetail2.text = "Gesamteinspeisung (Total): ${"%.2f".format(totalKwhAllTime)} kWh"

            val (currentData, otherData) = if (selectedPerson?.id == solar1Data?.firstOrNull()?.personId) {
                Pair(solar1Data!!, solar2Data!!)
            } else {
                Pair(solar2Data!!, solar1Data!!)
            }
            abrechnungAdapter.setData(
                neueAbrechnungen = currentData.filter { it.jahr == selectedYear },
                isSolar = true,
                otherSolar = otherData.filter { it.jahr == selectedYear }
            )
        }
    }

    private fun showEditGuthabenDialog(person: Person) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${person.name}: Guthaben bearbeiten")
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
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
        builder.setNegativeButton("Abbrechen", null)
        builder.show()
    }

    private fun showPaymentDialog(abrechnung: Monatsabrechnung) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Zahlung für ${getMonthName(abrechnung.monat)} erfassen")

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Betrag in €"
        }
        builder.setView(input)

        builder.setPositiveButton("Buchen") { _, _ ->
            val betrag = input.text.toString().toDoubleOrNull()
            if (betrag != null && betrag > 0) {
                personViewModel.recordPayment(abrechnung, betrag)
                Toast.makeText(context, "Zahlung verbucht", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Ungültiger Betrag", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Abbrechen", null)
        builder.show()
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Januar"; 2 -> "Februar"; 3 -> "März"; 4 -> "April"; 5 -> "Mai"; 6 -> "Juni"
            7 -> "Juli"; 8 -> "August"; 9 -> "September"; 10 -> "Oktober"; 11 -> "November"; 12 -> "Dezember"
            else -> "Unbekannt"
        }
    }

    private fun triggerCsvExport() {
        selectedPerson?.let { person ->
            val year = selectedYear
            val personName = person.name.replace(Regex("[^A-Za-z0-9]"), "_")
            val fileName = "Abrechnung_${personName}_$year.csv"
            csvExportLauncher.launch(fileName)
        } ?: run {
            Toast.makeText(requireContext(), "Bitte eine Person oder Solaranlage auswählen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportDataToUri(uri: Uri) {
        val person = selectedPerson ?: return
        val year = selectedYear

        val liveData = personViewModel.getAbrechnungenFuerPerson(person.id)
        liveData.observe(viewLifecycleOwner, object : Observer<List<Monatsabrechnung>> {
            override fun onChanged(allAbrechnungen: List<Monatsabrechnung>) {
                liveData.removeObserver(this)

                val yearAbrechnungen = allAbrechnungen.filter { it.jahr == year }
                if (yearAbrechnungen.isEmpty()) {
                    Toast.makeText(requireContext(), "Keine Daten für den Export vorhanden.", Toast.LENGTH_SHORT).show()
                    return
                }

                val csvContent = if (person.name.contains("Solaranlage")) {
                    generateSolarCsvContent(yearAbrechnungen, person, year)
                } else {
                    generatePersonCsvContent(yearAbrechnungen, person, year)
                }

                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        outputStream.write(csvContent.toByteArray())
                    }
                    Toast.makeText(requireContext(), "CSV erfolgreich exportiert", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Fehler beim Export: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun generatePersonCsvContent(abrechnungen: List<Monatsabrechnung>, person: Person, year: Int): String {
        val sb = StringBuilder()
        NumberFormat.getCurrencyInstance(Locale.GERMANY)

        sb.append("Jahresabrechnung für ${person.name} - $year\n\n")
        sb.append("Monat;Verbrauch (kWh);Gesamtkosten (€);Bezahlter Betrag (€);Status\n")

        val sortedAbrechnungen = abrechnungen.sortedBy { it.monat }

        sortedAbrechnungen.forEach {
            val monthName = getMonthName(it.monat)
            val verbrauch = "%.2f".format(Locale.GERMANY, it.verbrauchteKwh)
            val kosten = "%.2f".format(Locale.GERMANY, it.gesamtkosten)
            val bezahltBetrag = "%.2f".format(Locale.GERMANY, it.bezahlterBetrag)
            val status = if (it.bezahlt) "Vollständig bezahlt" else "Offen"
            sb.append("$monthName;$verbrauch;$kosten;$bezahltBetrag;$status\n")
        }

        sb.append("\nZusammenfassung\n")
        val totalKwh = sortedAbrechnungen.sumOf { it.verbrauchteKwh }
        val totalCost = sortedAbrechnungen.sumOf { it.gesamtkosten }
        val totalPaid = sortedAbrechnungen.sumOf { it.bezahlterBetrag }
        val totalOpen = totalCost - totalPaid

        sb.append("Gesamtverbrauch (kWh);${"%.2f".format(Locale.GERMANY, totalKwh)}\n")
        sb.append("Gesamtkosten (€);${"%.2f".format(Locale.GERMANY, totalCost)}\n")
        sb.append("Insgesamt bezahlt (€);${"%.2f".format(Locale.GERMANY, totalPaid)}\n")
        sb.append("Offener Betrag (€);${"%.2f".format(Locale.GERMANY, totalOpen)}\n")

        return sb.toString()
    }

    private fun generateSolarCsvContent(abrechnungen: List<Monatsabrechnung>, person: Person, year: Int): String {
        val sb = StringBuilder()
        sb.append("Jahreseinspeisung für ${person.name} - $year\n\n")
        sb.append("Monat;Eingespeist (kWh)\n")

        val sortedAbrechnungen = abrechnungen.sortedBy { it.monat }

        sortedAbrechnungen.forEach {
            val monthName = getMonthName(it.monat)
            val einspeisung = "%.2f".format(Locale.GERMANY, it.verbrauchteKwh)
            sb.append("$monthName;$einspeisung\n")
        }

        sb.append("\nZusammenfassung\n")
        val totalKwh = sortedAbrechnungen.sumOf { it.verbrauchteKwh }
        sb.append("Gesamteinspeisung (kWh);${"%.2f".format(Locale.GERMANY, totalKwh)}\n")

        return sb.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}