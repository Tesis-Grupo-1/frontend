package com.example.app_mosca.ui.theme

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.app_mosca.databinding.ActivityPrivacyNoticeBinding

class PrivacyNoticeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PrivacyNoticeActivity"
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_notice_accepted"

        /**
         * Método estático helper para verificar si el aviso de privacidad ha sido aceptado.
         * Puede ser usado desde otras actividades.
         */
        fun isPrivacyNoticeAccepted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
        }
    }

    private lateinit var binding: ActivityPrivacyNoticeBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPrivacyNoticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ocultar ActionBar
        supportActionBar?.hide()

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupUI()
    }

    private fun setupUI() {
        // Configurar el botón de aceptar
        binding.btnAcceptContinue.setOnClickListener {
            acceptPrivacyNotice()
        }

        // Cargar el texto del aviso de privacidad
        binding.tvPrivacyText.text = getPrivacyNoticePlaceholder()
    }

    private fun acceptPrivacyNotice() {
        // Guardar el flag de aceptación
        sharedPreferences.edit()
            .putBoolean(KEY_PRIVACY_ACCEPTED, true)
            .apply()

        Log.d(TAG, "Aviso de privacidad aceptado")

        // Navegar a LoginActivity
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Método helper para obtener el texto del aviso de privacidad.
     */
    private fun getPrivacyNoticePlaceholder(): String {
        return """
            📄 POLÍTICA DE PRIVACIDAD DE LA APLICACIÓN MÓVIL

            Última actualización: 2025

            La presente Política de Privacidad describe cómo la aplicación móvil "Minascan – Detección Temprana de la Mosca Minadora" (en adelante, "la Aplicación", "nosotros", "nuestro") recopila, utiliza, almacena y protege la información personal de los usuarios que emplean nuestro sistema de diagnóstico basado en visión por computadora.

            El uso de la Aplicación implica la aceptación expresa de esta Política. Si no está de acuerdo con sus términos, le recomendamos no utilizar el servicio.

            1. Información que recopilamos

            La Aplicación únicamente solicita y procesa datos mínimos y estrictamente necesarios para su funcionamiento, en cumplimiento del principio de minimización de datos.

            Los datos que podemos recopilar son:

            1.1. Datos proporcionados por el usuario

            • Nombre completo
            • Correo electrónico
            • Contraseña (almacenada en formato cifrado)

            1.2. Datos generados por el uso de la aplicación

            • Imágenes capturadas por el usuario para el diagnóstico
            • Resultado de la detección (plaga / no plaga)
            • Probabilidad estimada
            • Tiempo de inferencia
            • Historial de detecciones realizadas

            1.3. Datos no personales

            • Métricas de rendimiento del modelo (latencia, tiempo de respuesta)
            • Información técnica del dispositivo (versión de Android, resolución de pantalla)

            No recopilamos geolocalización, fotos ajenas al diagnóstico, ni información innecesaria.

            2. Finalidad del tratamiento de datos

            Sus datos se utilizan exclusivamente para:

            • Crear y gestionar la cuenta del usuario
            • Realizar diagnósticos mediante el modelo de IA
            • Generar el historial de detecciones
            • Sincronizar los registros con el servidor de respaldo
            • Mejorar la precisión del sistema
            • Garantizar la seguridad y auditoría del servicio

            En ningún caso se emplearán con fines comerciales, publicitarios o distintos a los señalados.

            3. Base legal del tratamiento

            El tratamiento de datos personales se realiza conforme a:

            • Ley N.º 29733 – Ley de Protección de Datos Personales (Perú)
            • Consentimiento informado del usuario otorgado en el registro
            • Principio de finalidad, proporcionalidad y seguridad

            4. Conservación y almacenamiento de la información

            Los datos se almacenan de manera cifrada en:

            • El dispositivo móvil del usuario (para consultas offline)
            • Una base de datos protegida en AWS (para respaldo y sincronización)

            Los datos serán conservados mientras el usuario mantenga su cuenta activa o mientras resulte necesario para el funcionamiento de la Aplicación. El usuario puede solicitar la eliminación total de su información en cualquier momento (ver Sección 9).

            5. Seguridad y protección de la información

            Hemos implementado medidas técnicas y organizativas de alta robustez para proteger la información del usuario:

            5.1. En tránsito

            • Cifrado HTTPS/TLS obligatorio entre la App y el servidor.

            5.2. En reposo

            • Contraseñas cifradas con bcrypt.
            • Tokens y credenciales almacenados en Android Keystore.
            • Datos personales e imágenes protegidos con cifrado AES-256 en la nube.

            5.3. Control de acceso

            • Autenticación basada en tokens JWT con expiración.
            • Validación por roles (empleado / jefe).
            • Restricción de endpoints mediante guardas de seguridad.

            5.4. Prevención de ataques

            • Protección ante fuerza bruta y DDoS.
            • Auditoría completa de modificaciones (logs de integridad).

            Estas medidas se evidencian en el cumplimiento del 100% de los requisitos Must Have de seguridad.

            6. Permisos solicitados

            La Aplicación solicita únicamente los permisos estrictamente necesarios:

            • Cámara: Captura de imágenes para el diagnóstico.
            • Almacenamiento: Guardado local de imágenes diagnósticas.
            • Internet: Sincronización de resultados y verificación del modelo.

            No solicitamos acceso a contactos, GPS, micrófono, historial u otros recursos innecesarios.

            7. Compartición de datos con terceros

            No compartimos, vendemos ni transferimos datos personales a terceros.

            Los únicos servicios externos utilizados son:

            • AWS (Amazon Web Services): Para almacenamiento seguro y backups.
            • Firebase/CloudWatch (opcional): Para monitoreo y telemetría técnica.

            En todos los casos, el tratamiento respeta medidas de seguridad y cláusulas de protección de datos.

            8. Derechos del usuario

            El usuario puede ejercer en cualquier momento sus derechos de:

            • Acceso: Solicitar los datos almacenados.
            • Rectificación: Actualizar información incorrecta.
            • Cancelación: Eliminar su cuenta y datos asociados.
            • Oposición: Retirar el consentimiento.

            Las solicitudes pueden realizarse por correo a:

            📧 soporte@minascan.app

            9. Eliminación de datos

            El usuario puede solicitar la eliminación total de:

            • Imágenes capturadas
            • Historial de detecciones
            • Información personal
            • Cuenta y credenciales

            Una vez eliminados los datos, no podrán recuperarse.

            10. Cambios en la Política de Privacidad

            Cualquier modificación futura será notificada dentro de la aplicación. Al continuar usando la App después de los cambios, se entiende que el usuario acepta los nuevos términos.

            11. Contacto

            Si tiene preguntas sobre esta Política de Privacidad o sobre el tratamiento de datos, puede contactarnos en:

            📧 soporte@minascan.app
        """.trimIndent()
    }

}

