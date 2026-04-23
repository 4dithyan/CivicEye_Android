package com.civiceye.ui.track

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.os.bundleOf
import com.civiceye.R
import com.civiceye.databinding.FragmentTrackBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackFragment : Fragment() {

    private var _binding: FragmentTrackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackViewModel by viewModels()

    private lateinit var issueAdapter: TrackIssueAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadUserIssues()
        }
    }

    private fun setupRecyclerView() {
        issueAdapter = TrackIssueAdapter(
            onItemClick = { issue ->
                val bundle = bundleOf("issueId" to issue.id)
                findNavController().navigate(R.id.action_trackFragment_to_issueDetailFragment, bundle)
            }
        )

        binding.recyclerView.apply {
            adapter = issueAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.issues.collect { issues ->
                        val userId = viewModel.currentUserId ?: ""
                        issueAdapter.submitList(issues, userId)
                        binding.emptyStateText.visibility = if (issues.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.swipeRefresh.isRefreshing = isLoading
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
