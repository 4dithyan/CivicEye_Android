package com.civiceye.ui.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.civiceye.R
import com.civiceye.data.model.Issue
import com.civiceye.databinding.ItemIssueBinding
import android.widget.ImageView

class IssueAdapter(
    private val onItemClick: (Issue) -> Unit,
    private val onLikeClick: (Issue) -> Unit,
    private val onSupportClick: (Issue) -> Unit,
    private val onShareClick: (Issue) -> Unit,
    private val onCommentClick: (Issue) -> Unit
) : ListAdapter<Issue, IssueAdapter.IssueViewHolder>(IssueDiffCallback()) {
    
    private var currentUserId: String = ""
    
    fun submitList(list: List<Issue>, userId: String) {
        currentUserId = userId
        super.submitList(list)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IssueViewHolder {
        val binding = ItemIssueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return IssueViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: IssueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class IssueViewHolder(
        private val binding: ItemIssueBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(issue: Issue) {
            // User info - ANONYMOUS for public
            binding.tvUserName.text = "Anonymous Citizen"
            binding.tvAddress.text = issue.address
            binding.tvTimeAgo.text = issue.getTimeAgo()
            
            // Profile image - Always generic
            binding.ivUserAvatar.setImageResource(R.drawable.ic_profile)
            
            // Issue image (first image)
            if (issue.images.isNotEmpty()) {
                binding.flImageContainer.visibility = View.VISIBLE
                binding.vpImages.visibility = View.VISIBLE
                // For simplicity, show first image only
                // TODO: Implement ViewPager2 adapter for carousel
                val imageAdapter = IssueImageAdapter(issue.images)
                binding.vpImages.adapter = imageAdapter
                
                // Show indicator for multiple images
                if (issue.images.size > 1) {
                    binding.indicatorLayout.visibility = View.VISIBLE
                    setupIndicators(issue.images.size)
                } else {
                    binding.indicatorLayout.visibility = View.GONE
                }
            } else {
                binding.flImageContainer.visibility = View.GONE
                binding.vpImages.visibility = View.GONE
                binding.indicatorLayout.visibility = View.GONE
            }
            
            // Status badge
            binding.tvStatus.text = when (issue.status) {
                Issue.STATUS_PENDING -> "PENDING"
                Issue.STATUS_IN_PROGRESS -> "IN PROGRESS"
                Issue.STATUS_RESOLVED -> "RESOLVED"
                else -> issue.status.uppercase()
            }
            binding.tvStatus.setBackgroundResource(
                when (issue.status) {
                    Issue.STATUS_PENDING -> R.drawable.bg_status_pending
                    Issue.STATUS_IN_PROGRESS -> R.drawable.bg_status_progress
                    Issue.STATUS_RESOLVED -> R.drawable.bg_status_resolved
                    else -> R.drawable.bg_status_badge
                }
            )
            
            // Title & Description
            binding.tvTitle.text = "Department: ${issue.category}"
            binding.tvDescription.text = issue.description
            
            // Category (department name - will be fetched separately)
            binding.tvCategory.text = issue.category
            binding.tvCategory.visibility = if (issue.category.isNotEmpty()) View.VISIBLE else View.GONE
            
            // Support button state and count
            val isSupported = issue.isSupportedBy(currentUserId)
            
            // Change button text and color based on support state
            // Change button text and color based on support state
            if (isSupported) {
                binding.btnSupport.text = "Supported"
                binding.btnSupport.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.brand_primary))
                binding.btnSupport.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                binding.btnSupport.iconTint = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.white))
                binding.btnSupport.strokeWidth = 0
            } else {
                binding.btnSupport.text = "Support issue"
                binding.btnSupport.setBackgroundColor(ContextCompat.getColor(binding.root.context, android.R.color.transparent))
                binding.btnSupport.setTextColor(ContextCompat.getColor(binding.root.context, R.color.brand_primary))
                binding.btnSupport.iconTint = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.brand_primary))
                binding.btnSupport.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.brand_primary))
                binding.btnSupport.strokeWidth = 3
            }
            
            // Support count display
            val supportText = when {
                issue.supportCount == 0 -> ""
                issue.supportCount == 1 -> binding.root.context.getString(R.string.one_person_supported_this)
                else -> binding.root.context.getString(R.string.people_supported_this, issue.supportCount)
            }
            binding.tvSupportCount.text = supportText
            binding.tvSupportCount.visibility = if (issue.supportCount > 0) View.VISIBLE else View.GONE
            
            // Click listeners
            binding.root.setOnClickListener { onItemClick(issue) }
            binding.btnSupport.setOnClickListener { onSupportClick(issue) }
            binding.btnShare.setOnClickListener { onShareClick(issue) }
        }
        
        private fun setupIndicators(count: Int) {
            binding.indicatorLayout.removeAllViews()
            for (i in 0 until count) {
                val dot = View(itemView.context).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(8, 8).apply {
                        marginStart = 4
                        marginEnd = 4
                    }
                    setBackgroundResource(
                        if (i == 0) R.drawable.indicator_active else R.drawable.indicator_inactive
                    )
                }
                binding.indicatorLayout.addView(dot)
            }
        }
    }
    
    private class IssueDiffCallback : DiffUtil.ItemCallback<Issue>() {
        override fun areItemsTheSame(oldItem: Issue, newItem: Issue): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Issue, newItem: Issue): Boolean {
            return oldItem == newItem
        }
    }
}

// Simple Image Adapter for ViewPager2
class IssueImageAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<IssueImageAdapter.ImageViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return ImageViewHolder(imageView)
    }
    
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }
    
    override fun getItemCount() = images.size
    
    class ImageViewHolder(
        private val imageView: ImageView
    ) : RecyclerView.ViewHolder(imageView) {
        
        fun bind(imageUrl: String) {
            imageView.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.shimmer_box)
            }
        }
    }
}
