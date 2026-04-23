package com.civiceye.ui.staff

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.viewModels
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.civiceye.databinding.ActivityStaffBinding
import com.civiceye.ui.auth.AuthActivity
import com.civiceye.data.model.Issue
import com.civiceye.R
import dagger.hilt.android.AndroidEntryPoint
import coil.load
import kotlinx.coroutines.launch
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast

@AndroidEntryPoint
class StaffActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityStaffBinding
    private val viewModel: StaffViewModel by viewModels()
    private val adapter = StaffIssueAdapter(
        onStartClick = { issue -> viewModel.startIssue(issue.id) },
        onCompleteClick = { issue -> showCompleteDialog(issue) },
        onDirectionClick = { issue -> openGoogleMaps(issue) }
    )
    
    private var selectedIssueIdForCompletion: String? = null
    private var selectedProofUri: Uri? = null
    private var currentFilterMode = "dashboard" // dashboard (active), recent (resolved)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeState()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        
        // Navigation Drawer Config (If needed, or just remove if we use dashboard only)
        // For now, keeping drawer logic if layout has it, otherwise ignore
        binding.toolbar.setNavigationOnClickListener {
             binding.drawerLayout.open()
        }
        
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.close()
            when (menuItem.itemId) {
                com.civiceye.R.id.nav_dashboard -> {
                    currentFilterMode = "dashboard"
                    updateIssueList()
                    true
                }
                com.civiceye.R.id.nav_recent -> {
                    currentFilterMode = "recent"
                    updateIssueList()
                    true
                }
                com.civiceye.R.id.nav_profile -> {
                    showProfileDialog()
                    true
                }
                com.civiceye.R.id.nav_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
        
        // RecyclerView Setup
        binding.rvAssignedIssues.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvAssignedIssues.adapter = adapter
        
        // Stats Click Listeners (Filter Toggle)
        binding.cardPending.setOnClickListener {
            if (currentFilterMode != "dashboard") {
                currentFilterMode = "dashboard"
                updateIssueList()
            }
        }
        
        binding.cardCompleted.setOnClickListener {
            if (currentFilterMode != "recent") {
                currentFilterMode = "recent"
                updateIssueList()
            }
        }
        
        // Availability Toggle Listener
        binding.statusChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val status = when (checkedId) {
                R.id.chipAvailable -> "Available"
                R.id.chipOnDuty -> "On Duty"
                R.id.chipBusy -> "Busy"
                R.id.chipLeave -> "Leave"
                else -> return@setOnCheckedStateChangeListener
            }
            viewModel.updateAvailability(status)
        }
    }
    
    // Note: updateFilterMode is removed as we now show dashboard by default
    
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                
                    // 1. Update Profile Header
                    if (state.userProfile != null) {
                        binding.tvStaffName.text = state.userProfile.name

                        // Update Chip Selection safely
                        val chipId = when(state.userProfile.availabilityStatus) {
                            "Available" -> R.id.chipAvailable
                            "On Duty" -> R.id.chipOnDuty
                            "Busy" -> R.id.chipBusy
                            "Leave" -> R.id.chipLeave
                            else -> R.id.chipAvailable
                        }
                        
                        if (binding.statusChipGroup.checkedChipId != chipId) {
                             binding.statusChipGroup.check(chipId)
                        }
                    }
                    
                    // 2. Filter Issues & Update List
                    updateIssueList()
                    
                    // 3. Update Stats
                    val pending = state.assignedIssues.count { !it.isResolved() }
                    val completed = state.assignedIssues.count { it.isResolved() }
                    binding.tvPendingCount.text = pending.toString()
                    binding.tvCompletedCount.text = completed.toString()
                    
                    // Messages
                    if (state.error != null) {
                        Toast.makeText(this@StaffActivity, state.error, Toast.LENGTH_LONG).show()
                        viewModel.clearMessages()
                    }
                    
                    if (state.successMessage != null) {
                        Toast.makeText(this@StaffActivity, state.successMessage, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun updateIssueList() {
        val state = viewModel.uiState.value
        val allIssues = state.assignedIssues
        
        val filteredList = if (currentFilterMode == "recent") {
            binding.tvSectionTitle.text = getString(R.string.staff_recent_works)
            // Show only resolved
            allIssues.filter { it.isResolved() }
        } else {
            binding.tvSectionTitle.text = getString(R.string.staff_my_assignments)
            // Show only active (not resolved)
            allIssues.filter { !it.isResolved() }
        }
        
        adapter.submitList(filteredList)
        
        if (filteredList.isEmpty()) {
            binding.rvAssignedIssues.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvAssignedIssues.visibility = View.VISIBLE
            binding.llEmptyState.visibility = View.GONE
        }
    }
    
    private fun openGoogleMaps(issue: Issue) {
        if (issue.latitude != null && issue.longitude != null) {
            val gmmIntentUri = "google.navigation:q=${issue.latitude},${issue.longitude}".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            
            try {
                startActivity(mapIntent)
            } catch (_: Exception) {
                // If Google Maps not installed, try generalized intent
                val webIntent = Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/dir/?api=1&destination=${issue.latitude},${issue.longitude}".toUri())
                startActivity(webIntent)
            }
        } else {
            Toast.makeText(this, getString(R.string.location_coords_missing), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showProfileDialog() {
        // Mock Profile for now (or fetch from Auth)
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email ?: "Unknown"
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(com.civiceye.R.string.staff_profile_title))
            .setMessage(getString(com.civiceye.R.string.staff_logged_in_as, email))
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
    }
    
    private fun showCompleteDialog(issue: Issue) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_complete_issue, null)
        val etNotes = dialogView.findViewById<EditText>(R.id.etNotes)
        val attachArea = dialogView.findViewById<View>(R.id.layoutAttachPhoto)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivProofPreview)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val notes = etNotes.text.toString()
                if (notes.isBlank()) {
                    Toast.makeText(this, getString(R.string.describe_work_done), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (selectedProofUri == null) {
                     Toast.makeText(this, getString(R.string.attach_proof), Toast.LENGTH_SHORT).show()
                     return@setPositiveButton
                }
                
                val images = listOf(selectedProofUri!!)
                viewModel.resolveIssue(issue.id, notes, images)
                selectedProofUri = null
            }
            .setNegativeButton("Cancel") { _, _ -> selectedProofUri = null }
            .create()
            
        attachArea.setOnClickListener {
            selectedIssueIdForCompletion = issue.id
            currentProofImageView = ivPreview 
            pickImageLauncher.launch("image/*")
        }
        
        dialog.show()
    }
    
    private var currentProofImageView: ImageView? = null
    
    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedProofUri = uri
            currentProofImageView?.let { iv ->
                iv.visibility = View.VISIBLE
                iv.load(uri) {
                    crossfade(true)
                }
            }
        }
    }
    
    private fun logout() {
        viewModel.logout()
        startActivity(Intent(this, AuthActivity::class.java))
        finishAffinity()
    }
}
