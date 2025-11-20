package com.example.strom_monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import java.text.NumberFormat
import java.util.*

class PersonListAdapter(
    private val onUpdate: (Person) -> Unit,
    private val onEdit: (Person) -> Unit,
    private val onPersonClick: (Person) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = emptyList<ListItem>()
    private var isLocked = false // Neuer Status für die Sperre

    /**
     * Setzt den Sperr-Status von außen und aktualisiert die Liste.
     */
    fun setLocked(locked: Boolean) {
        isLocked = locked
        notifyDataSetChanged()
    }

    // ... (ViewHolder-Klassen bleiben unverändert)
    companion object {
        private const val VIEW_TYPE_PERSON = 1
        private const val VIEW_TYPE_SOLAR = 2
    }

    class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewName)
        val kwhTextView: TextView = itemView.findViewById(R.id.textViewKwh)
        val zuZahlenTextView: TextView = itemView.findViewById(R.id.textViewZuZahlen)
        val addKwhEditText: EditText = itemView.findViewById(R.id.editTextAddKwh)
        val addKwhButton: Button = itemView.findViewById(R.id.buttonAddKwh)
        val editKwhButton: ImageButton = itemView.findViewById(R.id.buttonEditKwh)
        val eingabeLayout: LinearLayout = itemView.findViewById(R.id.layout_eingabe)
    }

    class SolarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val inputLayoutSolar1: TextInputLayout = itemView.findViewById(R.id.inputLayoutSolar1)
        val editTextSolar1Kwh: EditText = itemView.findViewById(R.id.editTextSolar1Kwh)
        val inputLayoutSolar2: TextInputLayout = itemView.findViewById(R.id.inputLayoutSolar2)
        val editTextSolar2Kwh: EditText = itemView.findViewById(R.id.editTextSolar2Kwh)
        val buttonSaveSolar: Button = itemView.findViewById(R.id.buttonSaveSolar)
        val textViewSolarTotal: TextView = itemView.findViewById(R.id.textViewSolarTotal)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.PersonItem -> VIEW_TYPE_PERSON
            is ListItem.SolarItem -> VIEW_TYPE_SOLAR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_PERSON) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_person, parent, false)
            PersonViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_solar, parent, false)
            SolarViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = items[position]) {
            is ListItem.PersonItem -> bindPersonViewHolder(holder as PersonViewHolder, currentItem.person)
            is ListItem.SolarItem -> bindSolarViewHolder(holder as SolarViewHolder, currentItem.solaranlagen)
        }
    }

    private fun bindPersonViewHolder(holder: PersonViewHolder, person: Person) {
        // Deaktivieren der Eingabefelder, wenn die App gesperrt ist
        holder.addKwhEditText.isEnabled = !isLocked
        holder.addKwhButton.isEnabled = !isLocked
        holder.editKwhButton.isEnabled = !isLocked
        holder.eingabeLayout.alpha = if (isLocked) 0.5f else 1.0f // Visuelles Feedback

        // ... (Rest der Methode bleibt unverändert)
        val context = holder.itemView.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preisProKwh = prefs.getString("preis_kwh", "0.35")?.toDoubleOrNull() ?: 0.35
        var grundgebuehr = prefs.getString("grundgebuehr", "15.00")?.toDoubleOrNull() ?: 15.00

        if (person.id == 2) {
            grundgebuehr = 0.0
        }

        if (person.id == 5) {
            holder.eingabeLayout.visibility = View.GONE
            holder.editKwhButton.visibility = View.GONE
            holder.kwhTextView.text = "Feste Monatsmiete"
            holder.zuZahlenTextView.text = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(120.0)
            holder.zuZahlenTextView.setTextColor(ContextCompat.getColor(context, R.color.red_text))
        } else {
            holder.eingabeLayout.visibility = View.VISIBLE
            holder.editKwhButton.visibility = View.VISIBLE
            if (person.name.contains("Fixkosten")) {
                grundgebuehr = 120.0
            }
            val kostenVerbrauch = person.verbrauchteKwh * preisProKwh
            val gesamtkostenDiesenMonat = kostenVerbrauch + grundgebuehr
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
            val numberFormat = NumberFormat.getNumberInstance(Locale.GERMANY).apply { maximumFractionDigits = 2 }
            holder.kwhTextView.text = "Verbrauch: ${numberFormat.format(person.verbrauchteKwh)} kWh (${currencyFormat.format(kostenVerbrauch)})"
            holder.zuZahlenTextView.text = currencyFormat.format(gesamtkostenDiesenMonat)
            holder.zuZahlenTextView.setTextColor(ContextCompat.getColor(context, R.color.red_text))
            holder.addKwhButton.setOnClickListener {
                val kwhText = holder.addKwhEditText.text.toString()
                if (kwhText.isNotEmpty()) {
                    try {
                        val kwhToAdd = kwhText.toDouble()
                        val updatedPerson = person.copy(verbrauchteKwh = person.verbrauchteKwh + kwhToAdd)
                        onUpdate(updatedPerson)
                        holder.addKwhEditText.text.clear()
                    } catch (e: NumberFormatException) {
                    }
                }
            }
        }
        holder.nameTextView.text = person.name
        holder.nameTextView.setOnClickListener { onPersonClick(person) }
        holder.editKwhButton.setOnClickListener { onEdit(person) }
    }

    private fun bindSolarViewHolder(holder: SolarViewHolder, solaranlagen: List<Person>) {
        // Deaktivieren der Eingabefelder, wenn die App gesperrt ist
        holder.editTextSolar1Kwh.isEnabled = !isLocked
        holder.editTextSolar2Kwh.isEnabled = !isLocked
        holder.buttonSaveSolar.isEnabled = !isLocked
        holder.inputLayoutSolar1.alpha = if(isLocked) 0.5f else 1.0f
        holder.inputLayoutSolar2.alpha = if(isLocked) 0.5f else 1.0f

        // ... (Rest der Methode bleibt unverändert)
        if (solaranlagen.size < 2) return
        val solar1 = solaranlagen[0]
        val solar2 = solaranlagen[1]
        holder.inputLayoutSolar1.hint = solar1.name
        holder.editTextSolar1Kwh.setText(if(solar1.verbrauchteKwh > 0) solar1.verbrauchteKwh.toString() else "")
        holder.inputLayoutSolar2.hint = solar2.name
        holder.editTextSolar2Kwh.setText(if(solar2.verbrauchteKwh > 0) solar2.verbrauchteKwh.toString() else "")
        val totalKwh = solar1.verbrauchteKwh + solar2.verbrauchteKwh
        holder.textViewSolarTotal.text = "Gesamteinspeisung: %.2f kWh".format(totalKwh)
        holder.buttonSaveSolar.setOnClickListener {
            val kwh1 = holder.editTextSolar1Kwh.text.toString().toDoubleOrNull() ?: 0.0
            val kwh2 = holder.editTextSolar2Kwh.text.toString().toDoubleOrNull() ?: 0.0
            onUpdate(solar1.copy(verbrauchteKwh = kwh1))
            onUpdate(solar2.copy(verbrauchteKwh = kwh2))
            Toast.makeText(holder.itemView.context, "Einspeisung gespeichert", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = items.size

    fun setData(neueItems: List<ListItem>) {
        this.items = neueItems
        notifyDataSetChanged()
    }
}