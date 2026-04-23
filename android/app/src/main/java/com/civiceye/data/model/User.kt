package com.civiceye.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "civilian", // civilian, staff, admin
    val departmentId: String = "",
    val locationId: String = "",
    val issuesReported: Int = 0,
    val issuesSupported: Int = 0,
    val availabilityStatus: String = "Available", // Available, On Duty, Busy, Leave
    val createdAt: Timestamp? = null
) : Parcelable {
    
    fun isCivilian() = role == "civilian"
    fun isStaff() = role == "staff"
    fun isAdmin() = role == "admin"
    
    companion object {
        const val ROLE_CIVILIAN = "civilian"
        const val ROLE_STAFF = "staff"
        const val ROLE_ADMIN = "admin"
    }
}
