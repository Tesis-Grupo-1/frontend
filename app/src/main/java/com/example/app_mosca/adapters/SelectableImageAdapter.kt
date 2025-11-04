package com.example.app_mosca.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.app_mosca.R
import com.example.app_mosca.models.ImageData

class SelectableImageAdapter(
    private val images: List<ImageData>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<SelectableImageAdapter.ViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.card_selectable_image)
        val imageView: ImageView = view.findViewById(R.id.iv_selectable_image)
        val overlaySelected: View = view.findViewById(R.id.overlay_selected)
        val tvPercentage: TextView = view.findViewById(R.id.tv_selectable_percentage)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox_select)
        val tvImageId: TextView = view.findViewById(R.id.tv_image_id)
        val tvStatus: TextView = view.findViewById(R.id.tv_selection_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selectable_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = images[position]
        val isSelected = selectedPositions.contains(position)

        // Cargar imagen con Glide
        Glide.with(holder.imageView.context)
            .load(image.image_path)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .centerCrop()
            .into(holder.imageView)

        // Mostrar información
        holder.tvPercentage.text = String.format("%.1f%%", image.porcentaje_plaga)
        holder.tvImageId.text = "Imagen #${image.id_image}"

        // Estado de selección
        holder.checkbox.isChecked = isSelected
        holder.overlaySelected.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.tvStatus.text = if (isSelected) "✓ Seleccionada" else "Toca para seleccionar"

        val context = holder.itemView.context
        holder.tvStatus.setTextColor(
            if (isSelected) {
                // Usar color rojo si está disponible, sino usar uno por defecto
                try {
                    context.getColor(R.color.error_red)
                } catch (e: Exception) {
                    context.getColor(android.R.color.holo_red_dark)
                }
            } else {
                context.getColor(android.R.color.darker_gray)
            }
        )

        // Click en toda la card para seleccionar/deseleccionar
        holder.cardView.setOnClickListener {
            toggleSelection(position)
            onSelectionChanged(selectedPositions.size)
        }

        // Click en el checkbox
        holder.checkbox.setOnClickListener {
            toggleSelection(position)
            onSelectionChanged(selectedPositions.size)
        }
    }

    override fun getItemCount() = images.size

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
    }

    fun selectAll() {
        selectedPositions.clear()
        selectedPositions.addAll(images.indices)
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }

    fun deselectAll() {
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedImages(): List<ImageData> {
        return selectedPositions.map { images[it] }
    }

    fun hasSelections(): Boolean = selectedPositions.isNotEmpty()
}