package com.civiceye.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.civiceye.R
import com.civiceye.databinding.ActivityRegisterBinding
import com.civiceye.ui.main.MainActivity
import com.github.dhaval2404.imagepicker.ImagePicker
import com.civiceye.util.LocationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()
    
    // Image picker removed
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        observeState()
        viewModel.loadLocations()
    }
    
    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { finish() }
        
        // Profile photo listeners removed
        
        binding.btnRegister.setOnClickListener {
            if (validateInput()) {
                register()
            }
        }
        
        binding.tvLogin.setOnClickListener {
            finish()
        }
    }
    
    // pickImage function removed
    
    private fun checkLocationPermissionForAdd() {
        if (LocationHelper.hasLocationPermission(this)) {
            viewModel.createLocationFromGPS(this)
        } else {
            permissionLauncherForAdd.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private val permissionLauncherForAdd = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.createLocationFromGPS(this)
        } else {
            Toast.makeText(this, "Location permission needed to add new place", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressRegister.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.btnRegister.visibility = if (state.isLoading) View.INVISIBLE else View.VISIBLE
                
                // Setup location dropdown
                if (state.locations.isNotEmpty()) {
                    val locationNames = state.locations.map { it.name }
                    val adapter = ArrayAdapter(
                        this@RegisterActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        locationNames
                    )
                    binding.actvLocation.setAdapter(adapter)
                    
                    binding.actvLocation.setOnItemClickListener { _, _, position, _ ->
                        val selectedLocation = state.locations[position]
                        if (selectedLocation.id == "ADD_NEW_GPS") {
                            // Clear selection visually or keep it? 
                            // Better to trigger action and maybe show loading
                            binding.actvLocation.setText("", false) // Clear temp text
                            checkLocationPermissionForAdd()
                        }
                    }
                }
                
                state.error?.let { error ->
                    Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                if (state.isRegistered) {
                    Toast.makeText(this@RegisterActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                }

                // Handle Auto-Selection from GPS
                state.autoSelectedLocation?.let { location ->
                    binding.actvLocation.setText(location.name, false)
                    // binding.etAddress.setText(location.name) // Address field removed from UI requirement
                    binding.actvLocation.dismissDropDown()
                    viewModel.consumeAutoSelection()
                }
            }
        }
    }
    
    private fun validateInput(): Boolean {
        var isValid = true
        
        // Name removed
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        // Phone is optional for login but requested in Register page
        val location = binding.actvLocation.text.toString()
        
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_empty_email)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }
        
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_short_password)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }
        
        val confirmPassword = binding.etConfirmPassword.text.toString()
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }
        
        if (location.isEmpty()) {
            binding.tilLocation.error = "Please select a location"
            isValid = false
        } else {
            binding.tilLocation.error = null
        }
        
        return isValid
    }
    
    private fun register() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val locationName = binding.actvLocation.text.toString()
        
        val locationId = viewModel.uiState.value.locations
            .find { it.name == locationName }?.id ?: ""
        
        // Name is passed explicitly now
        viewModel.register(
            email = email,
            password = password,
            name = name,
            phone = phone,
            locationId = locationId
        )
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
