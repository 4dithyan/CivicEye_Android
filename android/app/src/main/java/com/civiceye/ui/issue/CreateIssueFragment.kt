package com.civiceye.ui.issue

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import com.civiceye.R
import com.civiceye.databinding.FragmentCreateIssueBinding
import com.civiceye.util.GeminiHelper
import com.civiceye.util.LocationHelper
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val OTHER_CATEGORY = "Other"
private const val OTHER_LOCATION = "Other"

@AndroidEntryPoint
class CreateIssueFragment : Fragment() {
    
    // Use the standard layout binding
    private var _binding: FragmentCreateIssueBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CreateIssueViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                updateImagePreview()
                
                // Auto-start AI analysis and GPS capture
                viewModel.analyzeImageAndCaptureLocation(requireContext(), uri)
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            pickImage()
        } else {
            Toast.makeText(requireContext(), "Location permission needed for auto-tagging", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateIssueBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
        observeState()
        
        // Load locations and departments for smart linking
        viewModel.loadMasterData()
        
        // Check/request location permissions immediately
        checkLocationPermission()
    }
    
    private fun checkLocationPermission() {
        if (!LocationHelper.hasLocationPermission(requireContext())) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    private fun setupClickListeners() {
        binding.framePhotoContainer.setOnClickListener {
            checkLocationAndPickImage()
        }
        
        binding.btnSubmit.setOnClickListener {
            // Button Animation
            binding.btnSubmit.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.btnSubmit.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
                
            validateAndSubmit()
        }
    }
    
    private fun checkLocationAndPickImage() {
        if (LocationHelper.hasLocationPermission(requireContext())) {
            pickImage()
        } else {
            checkLocationPermission()
        }
    }
    
    private fun pickImage() {
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(1920, 1080)
            .createIntent { intent ->
                imagePickerLauncher.launch(intent)
            }
    }
    private fun updateImagePreview() {
        selectedImageUri?.let { uri ->
            binding.ivPhotoPreview.setImageURI(uri)
            binding.ivPhotoPreview.visibility = View.VISIBLE
            binding.layoutPlaceholder.visibility = View.GONE
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                
                // Loading State
                binding.cardLoading.visibility = if (state.isAnalyzing) View.VISIBLE else View.GONE
                
                // Visual readiness (Alpha) instead of disabling
                val canSubmit = !state.isAnalyzing && !state.isLoading && state.aiResult != null
                binding.btnSubmit.alpha = if (canSubmit) 1.0f else 0.5f
                
                // AI Result State (Merged Card)
                if (state.aiResult != null && !state.isAnalyzing) {
                    binding.cardAIResult.visibility = View.VISIBLE
                    
                    // 1. Issue
                    binding.tvAITitle.text = state.aiResult.title
                    binding.tvAIDescription.text = state.aiResult.description
                    
                    // 2. Department
                    binding.tvAICategory.text = "${state.aiResult.category}"
                    
                    // 3. Time (Current Time)
                    val dateFormat = java.text.SimpleDateFormat("hh:mm a, dd MMM yyyy", java.util.Locale.ENGLISH)
                    binding.tvIssueTime.text = dateFormat.format(java.util.Date())
                    
                    // 4. Place (GPS Address)
                    if (state.gpsAddress != null) {
                        binding.tvGPSAddress.text = state.gpsAddress
                        binding.tvGPSCoordinates.text = "Lat: %.4f, Long: %.4f".format(state.gpsLatitude ?: 0.0, state.gpsLongitude ?: 0.0)
                    } else {
                        binding.tvGPSAddress.text = "Fetching location..."
                        binding.tvGPSCoordinates.text = ""
                    }
                    
                    // 5. Intensity (Severity)
                    binding.tvAISeverity.text = state.aiResult.severity.uppercase()
                    binding.tvAIConfidence.text = "AI Confidence: ${(state.aiResult.confidence * 100).toInt()}%"
                    
                    // Set severity color
                    binding.tvAISeverity.backgroundTintList = when(state.aiResult.severity.uppercase()) {
                        "HIGH" -> ContextCompat.getColorStateList(requireContext(), R.color.brand_primary) // Red
                        "MEDIUM" -> ContextCompat.getColorStateList(requireContext(), android.R.color.holo_orange_dark)
                        else -> ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
                    }
                }
                
                // Note: GPS Card removed, merged into AI Result Card above
                
                // Submit Loading
                if (state.isLoading) {
                    binding.btnSubmit.text = "Submitting..."
                } else {
                    binding.btnSubmit.text = if (state.isSubmitted) "Submitted!" else "Submit Report"
                }
                
                if (state.isSubmitted) {
                    Toast.makeText(requireContext(), "Report submitted successfully!", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
                
                // Error - Show persistent Snackbar if analysis failed
                state.error?.let { error ->
                    android.util.Log.e("CreateIssueFragment", "Error state: $error")
                    com.google.android.material.snackbar.Snackbar.make(binding.root, error, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    private fun validateAndSubmit() {
        val state = viewModel.uiState.value
        
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "📸 Please take a photo first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (state.isAnalyzing) {
            Toast.makeText(requireContext(), "⏳ AI is analyzing... please wait", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (state.isLoading) {
            return // Already submitting
        }
        
        // If we have an image but no AI result yet (and not analyzing), something weird happened, but try to trigger submit which will fail safely or use fallback
        val comments = binding.etComments.text.toString().trim()
        viewModel.submitIssueWithAI(listOf(selectedImageUri!!), comments)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
