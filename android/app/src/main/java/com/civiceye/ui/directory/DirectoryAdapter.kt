package com.civiceye.ui.directory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.civiceye.data.model.User
import com.civiceye.databinding.ItemStaffDirectoryBinding

class DirectoryAdapter(
    private val onCallClick: (User) -> Unit
) : ListAdapter<User, DirectoryAdapter.StaffViewHolder>(StaffDiffCallback()) {

    var departmentNames: Map<String, String> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val binding = ItemStaffDirectoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StaffViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StaffViewHolder(private val binding: ItemStaffDirectoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvName.text = user.name
            
            val deptName = departmentNames[user.departmentId] ?: "Unknown Department"
            binding.tvDepartment.text = deptName
            
            binding.btnCall.setOnClickListener {
                onCallClick(user)
            }
        }
    }

    class StaffDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
