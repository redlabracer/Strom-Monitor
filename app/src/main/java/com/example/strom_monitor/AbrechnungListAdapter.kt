package com.example.strom_monitor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class AbrechnungListAdapter(
    private val context: Context,
    private val onPaidStatusChanged: (Monatsabrechnung) -> Unit
) : RecyclerView.Adapter<AbrechnungListAdapter.AbrechnungViewHolder>() {

    private var abrechnungen = emptyList<Monatsabrechnung>()

    class AbrechnungViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monatJahrTextView: TextView = itemView.findViewById(R.id.textViewAbrechnungMonatJahr)
        val verbrauchTextView: TextView = itemView.findViewById(R.id.textViewAbrechnungVerbrauch)
        val gesamtkostenTextView: TextView = itemView.findViewById(R.id.textViewAbrechnungGesamtkosten)
        val bezahltCheckBox: CheckBox = itemView.findViewById(R.id.checkBoxBezahlt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbrechnungViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_abrechnung, parent, false)
        return AbrechnungViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AbrechnungViewHolder, position: Int) {
        val current = abrechnungen[position]

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preisProKwh = prefs.getString("preis_kwh", "0.35")?.toDoubleOrNull() ?: 0.35

        val monatsName = getMonthName(current.monat)
        holder.monatJahrTextView.text = "$monatsName ${current.jahr}"

        val verbrauchKosten = current.verbrauchteKwh * preisProKwh
        holder.verbrauchTextView.text = "Verbrauch: ${"%.2f".format(current.verbrauchteKwh)} kWh (${currencyFormat.format(verbrauchKosten)})"
        holder.gesamtkostenTextView.text = "Gesamtkosten: ${currencyFormat.format(current.gesamtkosten)}"

        holder.bezahltCheckBox.setOnCheckedChangeListener(null)
        holder.bezahltCheckBox.isChecked = current.bezahlt
        holder.bezahltCheckBox.setOnCheckedChangeListener { _, isChecked ->
            onPaidStatusChanged(current.copy(bezahlt = isChecked))
        }
    }

    override fun getItemCount() = abrechnungen.size

    fun setData(neueAbrechnungen: List<Monatsabrechnung>) {
        this.abrechnungen = neueAbrechnungen
        notifyDataSetChanged()
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Januar"; 2 -> "Februar"; 3 -> "März"; 4 -> "April"; 5 -> "Mai"; 6 -> "Juni";
            7 -> "Juli"; 8 -> "August"; 9 -> "September"; 10 -> "Oktober"; 11 -> "November"; 12 -> "Dezember";
            else -> "Unbekannt"
        }
    }
}