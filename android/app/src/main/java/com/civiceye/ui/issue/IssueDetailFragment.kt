package com.civiceye.ui.issue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.civiceye.R
import com.civiceye.databinding.FragmentIssueDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IssueDetailFragment : Fragment() {
    
    private var _binding: FragmentIssueDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: IssueDetailViewModel by viewModels()
    
    private val issueId: String by lazy {
        arguments?.getString("issueId") ?: ""
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIssueDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
        observeState()
        viewModel.loadIssue(issueId)
    }
    
    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnLike.setOnClickListener {
            viewModel.toggleLike()
        }
        
        binding.btnSupport.setOnClickListener {
            viewModel.toggleSupport()
        }
        
        binding.btnShare.setOnClickListener {
            viewModel.uiState.value.issue?.let { issue ->
                shareIssue(issue)
            }
        }
        
        // Comment listener removed
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                
                state.issue?.let { issue ->
                    // User info
                    binding.tvUserName.text = issue.reporterName
                    binding.tvAddress.text = issue.address
                    binding.tvTimeAgo.text = issue.getTimeAgo()
                    
                    // Profile image - Always generic
                    binding.ivUserAvatar.setImageResource(R.drawable.ic_profile)
                    
                    // Images
                    if (issue.images.isNotEmpty()) {
                        binding.flImageContainer.visibility = View.VISIBLE
                        binding.ivIssueImage.load(issue.images.first()) {
                            crossfade(true)
                        }
                    } else {
                        binding.flImageContainer.visibility = View.GONE
                    }
                    
                    // Status
                    binding.tvStatus.text = issue.status.uppercase().replace("_", " ")
                    
                    // Like state
                    binding.ivLike.setImageResource(
                        if (issue.isLikedBy(viewModel.currentUserId)) 
                            R.drawable.ic_like_filled 
                        else R.drawable.ic_like_outline
                    )
                    binding.tvLikeCount.text = "${issue.likeCount} likes"
                    
                    // Title & Description
                    binding.tvTitle.text = issue.title
                    binding.tvCategory.text = issue.category
                    binding.tvDescription.text = issue.description
                    
                    // Support
                    binding.tvSupportCount.text = "${issue.supportCount} people support this"
                    
                    if (issue.reporterId == viewModel.currentUserId) {
                        binding.btnSupport.visibility = View.GONE
                    } else {
                        binding.btnSupport.visibility = View.VISIBLE
                        binding.btnSupport.text = if (issue.isSupportedBy(viewModel.currentUserId)) 
                            "Supported" else "Support this issue"
                    }
                }
                
                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    private fun shareIssue(issue: com.civiceye.data.model.Issue) {
        val shareText = """
            🚨 ${issue.title}
            
            📍 ${issue.address}
            📝 ${issue.description}
            
            Report issues with CivicEye!
        """.trimIndent()
        
        val intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share Issue"))
        viewModel.incrementShare()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
