package com.civiceye.ui.staff

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.civiceye.data.model.Issue
import com.civiceye.databinding.ItemStaffTaskBinding

class StaffIssueAdapter(
    private val onStartClick: (Issue) -> Unit,
    private val onCompleteClick: (Issue) -> Unit,
    private val onDirectionClick: (Issue) -> Unit
) : ListAdapter<Issue, StaffIssueAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStaffTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemStaffTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(issue: Issue) {
            binding.tvTitle.text = issue.title
            binding.tvAddress.text = issue.address
            // binding.tvDescription.text = issue.description // Not used in card summary
            
            binding.tvCategory.text = issue.category
            
            // Load image
            if (issue.images.isNotEmpty()) {
                binding.ivIssueImage.load(issue.images[0]) {
                    crossfade(true)
                    placeholder(com.civiceye.R.drawable.ic_camera)
                    error(com.civiceye.R.drawable.ic_camera)
                }
            } else {
                 binding.ivIssueImage.setImageResource(com.civiceye.R.drawable.ic_camera)
            }

            // Status & Action Logic
            when (issue.status) {
                Issue.STATUS_PENDING -> {
                    // Status Badge
                    binding.tvStatus.text = "PENDING START"
                    binding.tvStatus.setTextColor(Color.parseColor("#92400E")) // Yellow text
                    binding.cardStatus.setCardBackgroundColor(Color.parseColor("#FEF3C7")) // Yellow bg
                    
                    // Button
                    binding.btnAction.text = "Start Task"
                    binding.btnAction.setBackgroundColor(Color.parseColor("#3B82F6")) // Blue
                    binding.btnAction.isEnabled = true
                    binding.btnAction.setOnClickListener { onStartClick(issue) }
                }
                Issue.STATUS_IN_PROGRESS -> {
                    // Status Badge
                    binding.tvStatus.text = "IN PROGRESS"
                    binding.tvStatus.setTextColor(Color.parseColor("#1E40AF")) // Blue text
                    binding.cardStatus.setCardBackgroundColor(Color.parseColor("#DBEAFE")) // Blue bg
                    
                    // Button
                    binding.btnAction.text = "Complete Task"
                    binding.btnAction.setBackgroundColor(Color.parseColor("#10B981")) // Green
                    binding.btnAction.isEnabled = true
                    binding.btnAction.setOnClickListener { onCompleteClick(issue) }
                }
                Issue.STATUS_RESOLVED -> {
                    // Status Badge
                    binding.tvStatus.text = "COMPLETED"
                    binding.tvStatus.setTextColor(Color.parseColor("#166534")) // Green text
                    binding.cardStatus.setCardBackgroundColor(Color.parseColor("#DCFCE7")) // Green bg
                    
                    // Button
                    binding.btnAction.text = "Submitted"
                    binding.btnAction.setBackgroundColor(Color.parseColor("#94A3B8")) // Gray
                    binding.btnAction.isEnabled = false
                }
                else -> {
                    binding.tvStatus.text = issue.status.uppercase()
                }
            }
            
            // Directions
            binding.btnDirections.setOnClickListener { onDirectionClick(issue) }
            binding.btnDirections.visibility = if (issue.latitude != null && issue.longitude != null) View.VISIBLE else View.GONE
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Issue>() {
        override fun areItemsTheSame(oldItem: Issue, newItem: Issue) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Issue, newItem: Issue) = oldItem == newItem
    }
}
