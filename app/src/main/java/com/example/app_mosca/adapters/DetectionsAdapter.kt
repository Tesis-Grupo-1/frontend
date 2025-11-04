package com.example.app_mosca.adapters

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mosca.R
import com.example.app_mosca.models.DetectionResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetectionsAdapter(
    private val detections: List<DetectionResponse>,
    private val onDetectionClick: (DetectionResponse) -> Unit
) : RecyclerView.Adapter<DetectionsAdapter.DetectionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection, parent, false)
        return DetectionViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        holder.bind(detections[position])
    }

    override fun getItemCount(): Int = detections.size

    inner class DetectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_detection)
        private val tvFieldName: TextView = itemView.findViewById(R.id.tv_field_name)
        private val tvDetectionDate: TextView = itemView.findViewById(R.id.tv_detection_date)
        private val tvDetectionTime: TextView = itemView.findViewById(R.id.tv_detection_time)
        private val tvAffectedPercentage: TextView = itemView.findViewById(R.id.tv_affected_percentage)
        private val tvPlantCount: TextView = itemView.findViewById(R.id.tv_plant_count)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.iv_status_icon)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)

        @RequiresApi(Build.VERSION_CODES.O)
        fun bind(detection: DetectionResponse) {
            // ✅ Nombre del campo
            tvFieldName.text = detection.field_name ?: "Campo desconocido"

            // ✅ Fecha de detección
            try {
                val date = LocalDate.parse(
                    detection.detection_date ,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
                )
                val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                tvDetectionDate.text = date.format(dateFormatter)
            } catch (e: Exception) {
                tvDetectionDate.text = "Fecha no disponible"
            }

            // ✅ Hora de detección
            tvDetectionTime.text = "${detection.time_initial} - ${detection.time_final}"

            // ✅ Porcentaje afectado
            tvAffectedPercentage.text = String.format("%.1f%%", detection.affected_percentage)

            // ✅ Colorear según gravedad
            val percentageColor = when {
                detection.affected_percentage >= 50f -> R.color.error_red
                detection.affected_percentage >= 25f -> R.color.warning_orange
                else -> R.color.success_green
            }
            tvAffectedPercentage.setTextColor(ContextCompat.getColor(itemView.context, percentageColor))

            // ✅ Cantidad de plantas (no llega desde el backend)
            tvPlantCount.text = detection.plant_count.toString()

            // ✅ Estado visual
            updateStatus(detection)

            // ✅ Evento de click
            cardView.setOnClickListener { onDetectionClick(detection) }
        }

        private fun updateStatus(detection: DetectionResponse) {
            when {
                detection.affected_percentage >= 50f -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_error)
                    ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.error_red))
                    tvStatus.text = "Atención urgente requerida"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_red))
                }
                detection.affected_percentage >= 25f -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_warning)
                    ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.warning_orange))
                    tvStatus.text = "Requiere monitoreo"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_orange))
                }
                detection.affected_percentage > 0f -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_info)
                    ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.info_blue))
                    tvStatus.text = "Detección leve"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.info_blue))
                }
                else -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                    ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.success_green))
                    tvStatus.text = "Sin plagas detectadas"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.success_green))
                }
            }
        }
    }
}
