package com.civiceye.ui.track

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.civiceye.R
import com.civiceye.data.model.Issue
import com.civiceye.databinding.ItemTrackIssueBinding
import com.civiceye.ui.home.IssueImageAdapter

class TrackIssueAdapter(
    private val onItemClick: (Issue) -> Unit
) : ListAdapter<Issue, TrackIssueAdapter.TrackViewHolder>(IssueDiffCallback()) {

    private var currentUserId: String = ""

    fun submitList(list: List<Issue>, userId: String) {
        currentUserId = userId
        super.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackIssueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrackViewHolder(
        private val binding: ItemTrackIssueBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(issue: Issue) {
            // Show progress tracker
            binding.progressTracker.visibility = View.VISIBLE

            // Update progress based on status
            val progressStep = when (issue.status) {
                "pending" -> 0
                "verified" -> 1  // Viewed
                "in_progress" -> 2  // Processing
                "resolved" -> 3  // Solved
                else -> 0
            }

            // Update circles
            binding.step1Circle.setBackgroundResource(
                if (progressStep >= 0) R.drawable.circle_step_active else R.drawable.circle_step_inactive
            )
            binding.step2Circle.setBackgroundResource(
                if (progressStep >= 1) R.drawable.circle_step_active else R.drawable.circle_step_inactive
            )
            binding.step3Circle.setBackgroundResource(
                if (progressStep >= 2) R.drawable.circle_step_active else R.drawable.circle_step_inactive
            )
            binding.step4Circle.setBackgroundResource(
                if (progressStep >= 3) R.drawable.circle_step_active else R.drawable.circle_step_inactive
            )

            // Update progress line width
            binding.progressLineForeground.post {
                val layoutParams = binding.progressLineForeground.layoutParams
                val totalWidth = binding.progressLineBackground.width
                if (totalWidth > 0) {
                    layoutParams.width = (totalWidth * progressStep) / 3
                    binding.progressLineForeground.layoutParams = layoutParams
                }
            }

            // User info
            binding.tvUserName.text = issue.reporterName
            binding.tvAddress.text = issue.address
            binding.tvTimeAgo.text = issue.getTimeAgo()

            // Profile image
            // Profile image
            binding.ivUserAvatar.setImageResource(R.drawable.ic_profile)

            // Issue image
            if (issue.images.isNotEmpty()) {
                binding.flImageContainer.visibility = View.VISIBLE
                val imageAdapter = IssueImageAdapter(issue.images)
                binding.vpImages.adapter = imageAdapter
            } else {
                binding.flImageContainer.visibility = View.GONE
            }

            // Status badge
            binding.tvStatus.text = when (issue.status) {
                "pending" -> "PENDING"
                "verified" -> "VIEWED"
                "in_progress" -> "PROCESSING"
                "resolved" -> "SOLVED"
                else -> issue.status.uppercase()
            }

            // Title & Description
            binding.tvTitle.text = issue.title
            binding.tvDescription.text = issue.description
            binding.tvCategory.text = issue.category

            // Click listener
            itemView.setOnClickListener { onItemClick(issue) }
        }
    }

    class IssueDiffCallback : DiffUtil.ItemCallback<Issue>() {
        override fun areItemsTheSame(oldItem: Issue, newItem: Issue): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Issue, newItem: Issue): Boolean {
            return oldItem == newItem
        }
    }
}
