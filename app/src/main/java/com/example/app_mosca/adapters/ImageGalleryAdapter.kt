package com.example.app_mosca.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.app_mosca.R
import com.example.app_mosca.models.ImageData

class ImageGalleryAdapter(
    private val onImageClick: (ImageData) -> Unit,
    private val onValidateClick: (ImageData) -> Unit
) : RecyclerView.Adapter<ImageGalleryAdapter.ImageViewHolder>() {

    private var images = listOf<ImageData>()
    var multiSelectMode = false
    private val selectedImages = mutableSetOf<ImageData>()

    fun toggleSelection(image: ImageData) {
        if (selectedImages.contains(image)) {
            selectedImages.remove(image)
        } else {
            selectedImages.add(image)
        }
        notifyDataSetChanged()
    }

    fun getSelectedImages(): List<ImageData> = selectedImages.toList()

    fun clearSelection() {
        selectedImages.clear()
        multiSelectMode = false
        notifyDataSetChanged()
    }

    fun submitList(newImages: List<ImageData>) {
        images = newImages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        holder.bind(image)

        // 🔹 Cambiar opacidad o color si está seleccionada
        if (selectedImages.contains(image)) {
            holder.itemView.alpha = 0.6f
            holder.cardView.setCardBackgroundColor(holder.itemView.context.getColor(R.color.selected_overlay))
        } else {
            holder.itemView.alpha = 1f
            holder.cardView.setCardBackgroundColor(holder.itemView.context.getColor(android.R.color.white))
        }
    }

    override fun getItemCount() = images.size

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.card_image)
        private val imageView: ImageView = itemView.findViewById(R.id.iv_detection_image)
        private val tvPercentage: TextView = itemView.findViewById(R.id.tv_percentage)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val btnValidate: Button = itemView.findViewById(R.id.btn_validate)
        private val iconValidated: ImageView = itemView.findViewById(R.id.icon_validated)
        private val iconFalsePositive: ImageView = itemView.findViewById(R.id.icon_false_positive)

        fun bind(image: ImageData) {
            // Cargar imagen
            Glide.with(itemView.context)
                .load(image.image_path)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .into(imageView)

            tvPercentage.text = String.format("%.1f%%", image.porcentaje_plaga)

            val percentageColor = when {
                image.porcentaje_plaga >= 50f -> R.color.error_red
                image.porcentaje_plaga >= 25f -> R.color.warning_orange
                image.porcentaje_plaga > 0f -> R.color.info_blue
                else -> R.color.success_green
            }
            tvPercentage.setTextColor(itemView.context.getColor(percentageColor))

            when {
                image.is_validated && image.is_false_positive -> {
                    tvStatus.text = "Falso Positivo"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.error_red))
                    iconFalsePositive.visibility = View.VISIBLE
                    iconValidated.visibility = View.GONE
                    btnValidate.visibility = View.GONE
                }
                image.is_validated -> {
                    tvStatus.text = "Validada ✓"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    iconValidated.visibility = View.VISIBLE
                    iconFalsePositive.visibility = View.GONE
                    btnValidate.visibility = View.GONE
                }
                else -> {
                    tvStatus.text = "Sin validar"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
                    iconValidated.visibility = View.GONE
                    iconFalsePositive.visibility = View.GONE
                    btnValidate.visibility = View.VISIBLE
                }
            }

            // ✅ Clics normales y de selección
            cardView.setOnClickListener {
                if (multiSelectMode) {
                    toggleSelection(image)
                } else {
                    onImageClick(image)
                }
            }

            // 🔥 Long click activa el modo multiselección
            cardView.setOnLongClickListener {
                multiSelectMode = true
                toggleSelection(image)
                true
            }

            btnValidate.setOnClickListener {
                onValidateClick(image)
            }
        }
    }
}
