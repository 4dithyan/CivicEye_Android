package com.civiceye.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Department(
    val id: String = "",
    val name: String = "",
    val isActive: Boolean = true,
    val createdAt: Timestamp? = null
) : Parcelable
