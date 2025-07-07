package com.example.strom_monitor

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class PersonListAdapter(
    private val onUpdate: (Person) -> Unit,
    private val onEdit: (Person) -> Unit,
    private val onPersonClick: (Person) -> Unit // NEUER PARAMETER
) : RecyclerView.Adapter<PersonListAdapter.PersonViewHolder>() {

    private var personen = emptyList<Person>()

    class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewName)
        val kwhTextView: TextView = itemView.findViewById(R.id.textViewKwh)
        val kostenTextView: TextView = itemView.findViewById(R.id.textViewKosten)
        val gesamtkostenTextView: TextView = itemView.findViewById(R.id.textViewGesamtkosten)
        val guthabenTextView: TextView = itemView.findViewById(R.id.textViewGuthaben) // NEU
        val zuZahlenTextView: TextView = itemView.findViewById(R.id.textViewZuZahlen) // NEU
        val addKwhEditText: EditText = itemView.findViewById(R.id.editTextAddKwh)
        val addKwhButton: Button = itemView.findViewById(R.id.buttonAddKwh)
        val editKwhButton: View = itemView.findViewById(R.id.buttonEditKwh)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_person, parent, false)
        return PersonViewHolder(itemView)
    }

    override fun getItemCount() = personen.size

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val currentPerson = personen[position]
        val context = holder.itemView.context

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preisProKwh = prefs.getString("preis_kwh", "0.35")?.toDoubleOrNull() ?: 0.35
        var grundgebuehr = prefs.getString("grundgebuehr", "15.00")?.toDoubleOrNull() ?: 15.00

        if (currentPerson.name.contains("Person 5")) {
            grundgebuehr = 120.0
        }

        val kostenVerbrauch = currentPerson.verbrauchteKwh * preisProKwh
        val gesamtkosten = kostenVerbrauch + grundgebuehr
        val zuZahlen = gesamtkosten - currentPerson.guthaben

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val numberFormat = NumberFormat.getNumberInstance(Locale.GERMANY)
        numberFormat.maximumFractionDigits = 2

        holder.nameTextView.text = currentPerson.name
        holder.kwhTextView.text = "Verbrauch: ${numberFormat.format(currentPerson.verbrauchteKwh)} kWh"
        holder.kostenTextView.text = "Kosten Verbrauch: ${currencyFormat.format(kostenVerbrauch)}"
        holder.gesamtkostenTextView.text = "Gesamtkosten: ${currencyFormat.format(gesamtkosten)}"
        holder.guthabenTextView.text = "Guthaben: ${currencyFormat.format(currentPerson.guthaben)}"
        holder.zuZahlenTextView.text = "Noch zu zahlen: ${currencyFormat.format(zuZahlen)}"

        if (zuZahlen < 0) {
            holder.zuZahlenTextView.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            holder.zuZahlenTextView.setTextColor(Color.parseColor("#F44336"))
        }

        holder.nameTextView.setOnClickListener { onPersonClick(currentPerson) }
        holder.editKwhButton.setOnClickListener { onEdit(currentPerson) }

        holder.addKwhButton.setOnClickListener {
            val kwhToAddText = holder.addKwhEditText.text.toString()
            if (kwhToAddText.isNotEmpty()) {
                try {
                    val kwhToAdd = kwhToAddText.toDouble()
                    val updatedPerson = currentPerson.copy(verbrauchteKwh = currentPerson.verbrauchteKwh + kwhToAdd)
                    onUpdate(updatedPerson)
                    holder.addKwhEditText.text.clear()
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Bitte eine gültige Zahl eingeben", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Bitte einen Wert eingeben", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setData(neuePersonen: List<Person>) {
        this.personen = neuePersonen
        notifyDataSetChanged()
    }
}