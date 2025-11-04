package com.example.app_mosca.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mosca.R
import com.example.app_mosca.models.FieldResponse

class FieldsAdapter(
    private val fields: List<FieldResponse>,
    private val onFieldClick: (FieldResponse) -> Unit
) : RecyclerView.Adapter<FieldsAdapter.FieldViewHolder>() {

    private var selectedFieldId: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_field, parent, false)
        return FieldViewHolder(view)
    }

    override fun onBindViewHolder(holder: FieldViewHolder, position: Int) {
        val field = fields[position]
        holder.bind(field, field.id == selectedFieldId)
    }

    override fun getItemCount(): Int = fields.size

    fun setSelectedField(field: FieldResponse) {
        val previousSelected = selectedFieldId
        selectedFieldId = field.id

        // Actualizar las vistas
        fields.forEachIndexed { index, f ->
            if (f.id == previousSelected || f.id == selectedFieldId) {
                notifyItemChanged(index)
            }
        }
    }

    inner class FieldViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_field)
        private val tvFieldName: TextView = itemView.findViewById(R.id.tv_field_name)
        private val tvFieldSize: TextView = itemView.findViewById(R.id.tv_field_size)
        private val tvFieldLocation: TextView = itemView.findViewById(R.id.tv_field_location)
        private val tvFieldDescription: TextView = itemView.findViewById(R.id.tv_field_description)
        private val tvSelectedIndicator: TextView = itemView.findViewById(R.id.tv_selected_indicator)
        private val tvPlantCount: TextView = itemView.findViewById(R.id.tv_plant_count)

        fun bind(field: FieldResponse, isSelected: Boolean) {
            tvFieldName.text = field.name
            tvFieldSize.text = "${field.size_hectares} hectáreas"
            tvPlantCount.text = "${field.cant_plants} plantas" // Añadir esta línea
            tvFieldLocation.text = field.location

            // Mostrar descripción solo si no está vacía
            if (field.description?.isNotEmpty() == true && field.description != "Campo para análisis de plagas") {
                tvFieldDescription.text = field.description
                tvFieldDescription.visibility = View.VISIBLE
            } else {
                tvFieldDescription.visibility = View.GONE
            }

            // Indicador de selección
            if (isSelected) {
                tvSelectedIndicator.visibility = View.VISIBLE
                cardView.setCardBackgroundColor(
                    itemView.context.getColor(R.color.selected_field_background)
                )
                cardView.background = ContextCompat.getDrawable(itemView.context, R.drawable.selected_field_border)
            } else {
                tvSelectedIndicator.visibility = View.GONE
                cardView.setCardBackgroundColor(
                    itemView.context.getColor(R.color.white)
                )
                cardView.background = ContextCompat.getDrawable(itemView.context, R.drawable.normal_field_border)
            }

            // Click listener
            cardView.setOnClickListener {
                onFieldClick(field)
            }
        }
    }
}