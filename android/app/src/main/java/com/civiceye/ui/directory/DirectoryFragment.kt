package com.civiceye.ui.directory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.civiceye.databinding.FragmentDirectoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DirectoryFragment : Fragment() {

    private var _binding: FragmentDirectoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DirectoryViewModel by viewModels()
    private lateinit var adapter: DirectoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDirectoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = DirectoryAdapter { user ->
            makeCall(user.phone)
        }
        
        binding.rvDirectory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDirectory.adapter = adapter
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                
                // Populate Chips only once or when changed
                if (state.departmentNames.isNotEmpty() && binding.chipGroup.childCount <= 1) { // 1 is "All" chip
                    setupDepartmentChips(state.departmentNames)
                }

                adapter.departmentNames = state.departmentNames
                adapter.submitList(state.filteredStaff)
                
                binding.tvError.visibility = 
                    if (!state.isLoading && state.filteredStaff.isEmpty()) View.VISIBLE 
                    else View.GONE

                state.error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Handle "All" chip click
        binding.chipAll.setOnClickListener {
            viewModel.filterByDepartment(null)
        }
    }

    private fun setupDepartmentChips(departmentNames: Map<String, String>) {
        // Remove existing dynamic chips (keep "All")
        val viewCount = binding.chipGroup.childCount
        if (viewCount > 1) {
            binding.chipGroup.removeViews(1, viewCount - 1)
        }

        departmentNames.forEach { (id, name) ->
            val chip = layoutInflater.inflate(
                com.civiceye.R.layout.item_filter_chip, 
                binding.chipGroup, 
                false
            ) as com.google.android.material.chip.Chip
            
            chip.apply {
                text = name
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.filterByDepartment(id)
                    }
                }
            }
            binding.chipGroup.addView(chip)
        }
    }

    private fun makeCall(phoneNumber: String) {
        if (phoneNumber.isNotBlank()) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Phone number not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
