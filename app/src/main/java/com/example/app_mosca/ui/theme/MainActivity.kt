package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.FieldResponse
import com.example.app_mosca.repositories.AuthRepository
import com.example.app_mosca.repositories.FieldRepository
import com.example.app_mosca.ui.theme.EscanearPlagas
import com.example.app_mosca.utils.NetworkUtils
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.example.app_mosca.R
import com.example.app_mosca.models.FieldCreate
import com.example.app_mosca.ui.adapters.FieldsAdapter
import com.example.app_mosca.ui.theme.MainActivity.Companion.CREATE_FIELD_REQUEST_CODE

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val IMAGE_MIME_TYPE = "image/*"
        private const val TEMP_FILE_PREFIX = "temp_gallery_image_"
        private const val TEMP_FILE_EXTENSION = ".jpg"
        private const val BUFFER_SIZE = 8192

        const val EXTRA_IMAGE_PATH = "imagePath"
        const val EXTRA_IMAGE_URI = "imageUri"
        const val EXTRA_SELECTED_FIELD_ID = "selectedFieldId"
        const val EXTRA_SELECTED_FIELD_NAME = "selectedFieldName"

        const val CREATE_FIELD_REQUEST_CODE = 1001
    }

    // Componentes de autenticación
    private lateinit var tokenManager: TokenManager
    private lateinit var authRepository: AuthRepository
    private lateinit var fieldRepository: FieldRepository

    // UI Components
    private lateinit var rvFields: RecyclerView
    private lateinit var progressFields: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var btnCreateField: Button
    private lateinit var btnStartScan: Button
    private lateinit var fieldsAdapter: FieldsAdapter

    // Launchers
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>

    // Estado
    private var selectedField: FieldResponse? = null
    private var fieldsList = mutableListOf<FieldResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar componentes de autenticación
        initializeAuth()

        // Verificar autenticación antes de continuar
        if (!tokenManager.isLoggedIn()) {
            redirectToLogin()
            return
        }

        setContentView(R.layout.activity_main)

        // Inicializar UI
        initializeViews()
        initializeLaunchers()
        setupClickListeners()
        setupRecyclerView()
        requestPermissionsIfNeeded()

        // Verificar validez del token y cargar campos
        verifyTokenValidity()
        loadFields()
    }

    private fun initializeAuth() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        authRepository = AuthRepository(ApiClient.apiService, tokenManager)
        fieldRepository = FieldRepository(ApiClient.apiService, tokenManager)
    }

    private fun initializeViews() {
        rvFields = findViewById(R.id.rv_fields)
        progressFields = findViewById(R.id.progress_fields)
        emptyState = findViewById(R.id.empty_state)
        btnCreateField = findViewById(R.id.btn_create_field)
        btnStartScan = findViewById(R.id.btn_start_scan)
    }

    private fun setupRecyclerView() {
        fieldsAdapter = FieldsAdapter(fieldsList) { field ->
            selectField(field)
        }
        rvFields.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fieldsAdapter
        }
    }

    private fun loadFields() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("Sin conexión a internet")
            return
        }

        showFieldsLoading(true)

        lifecycleScope.launch {
            try {
                val result = fieldRepository.getFields()

                result.fold(
                    onSuccess = { fields ->
                        Log.d(TAG, "Campos cargados: ${fields.size}")
                        fieldsList.clear()
                        fieldsList.addAll(fields)
                        fieldsAdapter.notifyDataSetChanged()

                        updateFieldsVisibility()
                        showFieldsLoading(false)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error cargando campos: ${exception.message}")
                        showToast("Error cargando campos: ${exception.message}")
                        showFieldsLoading(false)
                        updateFieldsVisibility()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excepción cargando campos", e)
                showToast("Error inesperado al cargar campos")
                showFieldsLoading(false)
                updateFieldsVisibility()
            }
        }
    }

    private fun selectField(field: FieldResponse) {
        selectedField = field
        fieldsAdapter.setSelectedField(field)
        updateUI()

        showToast("Campo seleccionado: ${field.name}")
        Log.d(TAG, "Campo seleccionado: ${field.name} (ID: ${field.id})")
    }

    private fun showFieldsLoading(show: Boolean) {
        progressFields.visibility = if (show) View.VISIBLE else View.GONE
        rvFields.visibility = if (show) View.GONE else View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun updateFieldsVisibility() {
        if (fieldsList.isEmpty()) {
            rvFields.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            rvFields.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }

    private fun updateUI() {
        btnStartScan.visibility = if (selectedField != null) View.VISIBLE else View.GONE

        // Si hay un campo seleccionado, cambiar el texto del botón de crear
        btnCreateField.text = if (selectedField != null)
            "CREAR OTRO CAMPO" else "CREAR NUEVO CAMPO"
    }

    private fun verifyTokenValidity() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.w(TAG, "Sin conexión a internet, no se puede verificar el token")
            return
        }

        lifecycleScope.launch {
            val result = authRepository.getCurrentUser()
            result.fold(
                onSuccess = { user ->
                    Log.d(TAG, "Token válido, usuario: ${user.full_name} (${user.role})")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Token inválido: ${exception.message}")
                    showToast("Sesión expirada. Por favor inicia sesión nuevamente.")
                    redirectToLogin()
                }
            )
        }
    }

    private fun initializeLaunchers() {
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Permiso de cámara concedido")
                showToast("Permiso de cámara concedido")
            } else {
                Log.w(TAG, "Permiso de cámara denegado")
                showToast("Permiso de cámara requerido para usar esta función")
            }
        }

        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isGranted = permissions.values.all { it }
            if (isGranted) {
                Log.d(TAG, "Permisos de almacenamiento concedidos")
            } else {
                Log.w(TAG, "Permisos de almacenamiento denegados")
            }
        }

        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { handleGalleryResult(it) }
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (!isCameraPermissionGranted()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupClickListeners() {
        // Botón crear nuevo campo
        btnCreateField.setOnClickListener {
            navigateToCreateField()
        }

        // Botón comenzar escaneo (solo visible cuando hay campo seleccionado)
        btnStartScan.setOnClickListener {
            selectedField?.let { field ->
                if (isCameraPermissionGranted()) {
                    navigateToScanPlagasWithField(field)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        // Navegación inferior
        findViewById<LinearLayout>(R.id.nav_home)?.setOnClickListener {
            showToast("Ya estás en inicio")
        }

        findViewById<LinearLayout>(R.id.nav_history)?.setOnClickListener {
            showToast("Historial próximamente")
        }

        // Botón principal de escaneo - requiere selección de campo
        findViewById<androidx.cardview.widget.CardView>(R.id.nav_scan)?.setOnClickListener {
            if (selectedField == null) {
                showToast("Selecciona un campo primero o crea uno nuevo")
                return@setOnClickListener
            }

            if (isCameraPermissionGranted()) {
                selectedField?.let { field ->
                    navigateToScanPlagasWithField(field)
                }
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        findViewById<LinearLayout>(R.id.nav_perfil)?.setOnClickListener {
            showProfile()
        }

        findViewById<LinearLayout>(R.id.nav_logout)?.setOnClickListener {
            showLogoutDialog()
        }

        findViewById<LinearLayout>(R.id.nav_history)?.setOnClickListener {
            navigateToHistorial()
        }
    }

    private fun navigateToCreateField() {
        showCreateFieldDialog()
    }

    private fun showCreateFieldDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_field_data, null)

        val etFieldName = dialogView.findViewById<EditText>(R.id.et_field_name)
        val etSizeHectares = dialogView.findViewById<EditText>(R.id.et_size_hectares)
        val etCantPlants = dialogView.findViewById<EditText>(R.id.et_cant_plants)
        val etLocation = dialogView.findViewById<EditText>(R.id.et_location)
        val etDescription = dialogView.findViewById<EditText>(R.id.et_description)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Crear Nuevo Campo")
            .setMessage("Ingresa la información del campo")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Crear") { _, _ ->
                val name = etFieldName.text.toString().trim()
                val sizeStr = etSizeHectares.text.toString().trim()
                val cantPlantsStr = etCantPlants.text.toString().trim()
                val location = etLocation.text.toString().trim()
                val description = etDescription.text.toString().trim()

                if (name.isEmpty() || sizeStr.isEmpty() || cantPlantsStr.isEmpty() || location.isEmpty()) {
                    showToast("Por favor completa todos los campos obligatorios")
                    return@setPositiveButton
                }

                val size = sizeStr.toDoubleOrNull()
                if (size == null || size <= 0) {
                    showToast("Ingresa un tamaño válido")
                    return@setPositiveButton
                }

                val cantPlants = cantPlantsStr.toIntOrNull()
                if (cantPlants == null || cantPlants <= 0) {
                    showToast("Ingresa una cantidad de plantas válida")
                    return@setPositiveButton
                }

                createField(name, size, cantPlants, location, description)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createField(name: String, size: Double, cantPlants: Int, location: String, description: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("Sin conexión a internet")
            return
        }

        showFieldsLoading(true)

        lifecycleScope.launch {
            try {
                val fieldCreate = FieldCreate(
                    name = name,
                    size_hectares = size.toFloat(),
                    cant_plants = cantPlants,
                    location = location,
                    description = description.ifEmpty { "Campo para análisis de plagas" }
                )

                val result = fieldRepository.createField(fieldCreate)

                result.fold(
                    onSuccess = { fieldResponse ->
                        Log.d(TAG, "Campo creado exitosamente: ID=${fieldResponse.id}")
                        showToast("Campo '${fieldResponse.name}' creado exitosamente")

                        // Recargar la lista de campos
                        loadFields()

                        // Seleccionar automáticamente el nuevo campo
                        selectedField = fieldResponse
                        updateUI()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error creando campo", error)
                        showToast("Error: ${error.message}")
                        showFieldsLoading(false)
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Excepción creando campo", e)
                showToast("Error inesperado al crear campo")
                showFieldsLoading(false)
            }
        }
    }

    private fun navigateToScanPlagasWithField(field: FieldResponse) {
        try {
            val intent = Intent(this, EscanearPlagas::class.java).apply {
                putExtra(EXTRA_SELECTED_FIELD_ID, field.id)
                putExtra(EXTRA_SELECTED_FIELD_NAME, field.name)
            }
            startActivity(intent)
            Log.d(TAG, "Navegando a EscanearPlagas con campo: ${field.name} (ID: ${field.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir escáner de plagas", e)
            showToast("Error al abrir el escáner de plagas")
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handleGalleryResult(uri: Uri) {
        Log.d(TAG, "Imagen seleccionada: $uri")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempFile = copyImageToTempFile(uri)
                withContext(Dispatchers.Main) {
                    navigateToLoadingActivity(tempFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando imagen", e)
                withContext(Dispatchers.Main) {
                    showToast("Error procesando imagen")
                }
            }
        }
    }

    private suspend fun copyImageToTempFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se puede abrir URI: $uri")

        val tempFile = createTempFile()
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }

        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw IllegalStateException("Archivo temporal inválido")
        }

        tempFile
    }

    private fun createTempFile(): File {
        val timeStamp = System.currentTimeMillis()
        return File(cacheDir, "$TEMP_FILE_PREFIX$timeStamp$TEMP_FILE_EXTENSION")
    }

    private fun navigateToLoadingActivity(tempFile: File) {
        try {
            val fileUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )

            val intent = Intent(this, LoadingActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_PATH, tempFile.absolutePath)
                putExtra(EXTRA_IMAGE_URI, fileUri.toString())
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navegando a LoadingActivity", e)
            showToast("Error procesando imagen")
        }
    }

    private fun showProfile() {
        val userEmail = authRepository.getCurrentUserEmail()
        val userRole = authRepository.getCurrentUserRole()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Perfil de Usuario")
            .setMessage("Email: $userEmail\nRol: ${userRole?.uppercase()}")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro que deseas cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun logout() {
        authRepository.logout()
        showToast("Sesión cerrada exitosamente")
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FIELD_REQUEST_CODE && resultCode == RESULT_OK) {
            // Recargar la lista de campos después de crear uno nuevo
            loadFields()
            showToast("Campo creado exitosamente")
        }
    }

    private fun navigateToHistorial() {
        try {
            val intent = Intent(this, HistorialActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "Navegando a Historial")
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir historial", e)
            showToast("Error al abrir el historial")
        }
    }

}