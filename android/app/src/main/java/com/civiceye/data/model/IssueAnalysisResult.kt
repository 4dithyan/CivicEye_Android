package com.civiceye.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Result of AI image analysis using Gemini
 */
@Parcelize
data class IssueAnalysisResult(
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val severity: String = "Medium",
    val confidence: Float = 0f,
    val detectedIssues: List<String> = emptyList()
) : Parcelable {
    
    fun isHighConfidence() = confidence >= 0.7f
    fun isMediumConfidence() = confidence >= 0.5f && confidence < 0.7f
    fun isLowConfidence() = confidence < 0.5f
    
    companion object {
        const val SEVERITY_HIGH = "High"
        const val SEVERITY_MEDIUM = "Medium"
        const val SEVERITY_LOW = "Low"
    }
}
