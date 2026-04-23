package com.civiceye.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Issue(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "", // departmentId reference
    val locationId: String = "",
    val location: @RawValue GeoPoint? = null,
    val address: String = "",
    val images: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationType: String = "MANUAL", // GPS or MANUAL
    val gpsTimestamp: Timestamp? = null,
    
    // AI Fields
    val aiGenerated: Boolean = false,
    val aiConfidence: Float = 0f,
    val aiDetectedIssues: List<String> = emptyList(),
    val severity: String = "", // High, Medium, Low
    
    // User input
    val userComments: String = "",
    val userModifiedAI: Boolean = false,
    
    val reporterId: String = "",
    val reporterName: String = "",
    val status: String = STATUS_PENDING,
    
    // Social features
    val likeCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val supportCount: Int = 0,
    val supportedBy: List<String> = emptyList(),
    
    // Assignment
    val assignedTo: String = "",
    val assignedDepartment: String = "",
    
    // Resolution (Staff)
    val resolutionNotes: String = "",
    val proofImages: List<String> = emptyList(),
    val isVerified: Boolean = false,
    
    // Timestamps
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null,
    val scheduledDeletion: Timestamp? = null
) : Parcelable {
    
    fun isPending() = status == STATUS_PENDING
    fun isInProgress() = status == STATUS_IN_PROGRESS
    fun isResolved() = status == STATUS_RESOLVED // Staff marked done
    
    fun isLikedBy(userId: String) = likedBy.contains(userId)
    fun isSupportedBy(userId: String) = supportedBy.contains(userId)
    
    fun getTimeAgo(): String {
        val createdTime = createdAt?.toDate()?.time ?: return ""
        val now = System.currentTimeMillis()
        val diff = now - createdTime
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "now"
        }
    }
    
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_RESOLVED = "resolved"
        const val STATUS_VERIFIED = "verified" // Optional explicit state
    }
}
