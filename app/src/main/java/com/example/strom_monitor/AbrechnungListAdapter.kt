package com.example.strom_monitor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class AbrechnungListAdapter(
    private val context: Context,
    private val onAddPaymentClicked: (Monatsabrechnung) -> Unit,
    private val onCheckboxChanged: (Monatsabrechnung, Boolean) -> Unit
) : RecyclerView.Adapter<AbrechnungListAdapter.AbrechnungViewHolder>() {

    // Diese Variablen werden jetzt wieder verwendet
    private var abrechnungen = emptyList<Monatsabrechnung>()
    private var otherSolarAbrechnungen = emptyList<Monatsabrechnung>()
    private var isSolarMode = false

    class AbrechnungViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monatJahrTextView: TextView = itemView.findViewById(R.id.textViewAbrechnungMonatJahr)
        val verbrauchTextView: TextView = itemView.findViewById(R.id.textViewAbrechnungVerbrauch)
        val gesamtkostenTextView: TextView = itemView.findViewById(R.id.textViewAbrechnungGesamtkosten)
        val bezahltCheckBox: CheckBox = itemView.findViewById(R.id.checkBoxBezahlt)
        val paymentLayout: LinearLayout = itemView.findViewById(R.id.layout_payment)
        val paymentStatusTextView: TextView = itemView.findViewById(R.id.textViewPaymentStatus)
        val addPaymentButton: Button = itemView.findViewById(R.id.buttonAddPayment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbrechnungViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_abrechnung, parent, false)
        return AbrechnungViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AbrechnungViewHolder, position: Int) {
        val current = abrechnungen[position]
        val monatsName = getMonthName(current.monat)
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        holder.monatJahrTextView.text = "$monatsName ${current.jahr}"

        if (isSolarMode) {
            // Ansicht für Solaranlagen
            holder.verbrauchTextView.text = "Erzeugt: ${"%.2f".format(current.verbrauchteKwh)} kWh"
            val otherAbrechnung = otherSolarAbrechnungen.find { it.monat == current.monat && it.jahr == current.jahr }
            val otherKwh = otherAbrechnung?.verbrauchteKwh ?: 0.0
            val totalMonthKwh = current.verbrauchteKwh + otherKwh
            holder.gesamtkostenTextView.text = "Gesamteinspeisung (Monat): ${"%.2f".format(totalMonthKwh)} kWh"

            holder.gesamtkostenTextView.visibility = View.VISIBLE
            holder.bezahltCheckBox.visibility = View.GONE
            holder.paymentLayout.visibility = View.GONE

        } else if (current.personId == 5) {
            // Ansicht für Person 5 (Mieter)
            holder.verbrauchTextView.text = "Miete"
            holder.gesamtkostenTextView.visibility = View.GONE
            holder.bezahltCheckBox.visibility = View.GONE
            holder.paymentLayout.visibility = View.VISIBLE

            val offenerBetrag = current.gesamtkosten - current.bezahlterBetrag
            if (offenerBetrag <= 0.01) {
                holder.paymentStatusTextView.text = "Vollständig bezahlt"
                holder.addPaymentButton.isEnabled = false
            } else {
                holder.paymentStatusTextView.text = "Offen: ${currencyFormat.format(offenerBetrag)} / ${currencyFormat.format(current.gesamtkosten)}"
                holder.addPaymentButton.isEnabled = true
            }
            holder.addPaymentButton.setOnClickListener { onAddPaymentClicked(current) }

        } else {
            // Ansicht für normale Stromkunden (Personen 1-4)
            holder.verbrauchTextView.text = "Verbrauch: ${"%.2f".format(current.verbrauchteKwh)} kWh"
            holder.gesamtkostenTextView.text = "Gesamtkosten: ${currencyFormat.format(current.gesamtkosten)}"
            holder.gesamtkostenTextView.visibility = View.VISIBLE
            holder.bezahltCheckBox.visibility = View.VISIBLE
            holder.paymentLayout.visibility = View.GONE

            holder.bezahltCheckBox.setOnCheckedChangeListener(null)
            holder.bezahltCheckBox.isChecked = current.bezahlt
            holder.bezahltCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckboxChanged(current, isChecked)
            }
        }
    }

    override fun getItemCount() = abrechnungen.size

    // ***** HIER IST DIE KORRIGIERTE FUNKTION *****
    // Sie akzeptiert jetzt wieder alle notwendigen Parameter.
    fun setData(neueAbrechnungen: List<Monatsabrechnung>, isSolar: Boolean = false, otherSolar: List<Monatsabrechnung> = emptyList()) {
        this.abrechnungen = neueAbrechnungen.sortedByDescending { it.jahr * 100 + it.monat }
        this.isSolarMode = isSolar
        this.otherSolarAbrechnungen = otherSolar
        notifyDataSetChanged()
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Januar"; 2 -> "Februar"; 3 -> "März"; 4 -> "April"; 5 -> "Mai"; 6 -> "Juni"
            7 -> "Juli"; 8 -> "August"; 9 -> "September"; 10 -> "Oktober"; 11 -> "November"; 12 -> "Dezember"
            else -> "Unbekannt"
        }
    }
}