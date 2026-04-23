package com.civiceye.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.civiceye.R
import com.civiceye.data.model.Location
import com.civiceye.data.model.Department
import com.civiceye.databinding.FragmentHomeBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.R as MaterialR
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var issueAdapter: IssueAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupLocationSelector()
        setupSwipeRefresh()
        observeState()
        
        viewModel.loadData()
    }
    
    private fun setupRecyclerView() {
        issueAdapter = IssueAdapter(
            onItemClick = { _ ->
                // Do nothing (Detail view disabled as per user request)
            },
            onLikeClick = { issue ->
                viewModel.toggleLike(issue.id)
            },
            onSupportClick = { issue ->
                viewModel.toggleSupport(issue.id)
            },
            onShareClick = { issue ->
                shareIssue(issue)
            },
            onCommentClick = { _ ->
                // Do nothing (Detail view disabled)
            }
        )
        
        binding.rvIssues.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = issueAdapter
            setHasFixedSize(false)
        }
    }
    
    private fun setupLocationSelector() {
        binding.locationSelector.setOnClickListener {
            showLocationBottomSheet()
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.civilian_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Loading state
                binding.swipeRefresh.isRefreshing = state.isLoading && state.issues.isNotEmpty()
                binding.shimmerLayout.visibility = if (state.isLoading && state.issues.isEmpty()) View.VISIBLE else View.GONE
                
                if (state.isLoading && state.issues.isEmpty()) {
                    binding.shimmerLayout.startShimmer()
                } else {
                    binding.shimmerLayout.stopShimmer()
                }
                
                // Issues
                issueAdapter.submitList(state.issues, viewModel.currentUserId)
                
                // Empty state
                binding.emptyState.visibility = if (!state.isLoading && state.issues.isEmpty()) View.VISIBLE else View.GONE
                binding.rvIssues.visibility = if (state.issues.isNotEmpty()) View.VISIBLE else View.GONE
                
                // Location
                if (state.selectedLocation != null) {
                    binding.tvLocation.text = state.selectedLocation.name
                } else {
                    binding.tvLocation.text = getString(R.string.all_locations_display)
                }
                
                // Category chips
                if (state.departments.isNotEmpty()) {
                    setupCategoryChips(state.departments)
                }
                
                // Error
                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    private fun setupCategoryChips(departments: List<Department>) {
        // Keep the "All" chip, remove others
        val allChip = binding.chipGroup.findViewById<View>(R.id.chipAll)
        binding.chipGroup.removeAllViews()
        binding.chipGroup.addView(allChip)
        
        departments.forEach { department ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = department.name
                isCheckable = true
                tag = department.id // Use ID for filtering
                
                // Style to match "All" chip
                setChipBackgroundColorResource(MaterialR.color.mtrl_choice_chip_background_color)
                isClickable = true
                isFocusable = true
            }
            binding.chipGroup.addView(chip)
        }
        
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                viewModel.filterByCategory(null)
            } else {
                val chip = group.findViewById<Chip>(checkedIds.first())
                if (chip?.id == R.id.chipAll) {
                    viewModel.filterByCategory(null)
                } else {
                    viewModel.filterByCategory(chip?.tag as? String)
                }
            }
        }
    }
    
    private fun showLocationBottomSheet() {
        val locations = viewModel.uiState.value.locations
        if (locations.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_locations_available), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add "All Locations" option at the top
        val allOption = getString(R.string.all_locations_display)
        val locationNames = arrayOf(allOption) + locations.map { it.name }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_location_title))
            .setItems(locationNames) { dialog, which ->
                if (which == 0) {
                    // All Locations Selected
                    viewModel.selectLocation(null)
                    binding.tvLocation.text = getString(R.string.all_locations_display)
                } else {
                    // Specific Location Selected (index - 1)
                    val selectedLocation = locations[which - 1]
                    viewModel.selectLocation(selectedLocation)
                }
                dialog.dismiss()
            }
            .show()
    }
    
    private fun shareIssue(issue: com.civiceye.data.model.Issue) {
        val shareText = """
            🚨 ${issue.title}
            
            📍 ${issue.address}
            📝 ${issue.description}
            
            Report issues in your area with CivicEye!
            #CivicEye #CivicIssue
        """.trimIndent()
        
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        startActivity(Intent.createChooser(intent, getString(R.string.share_issue_title)))
        viewModel.incrementShare(issue.id)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
