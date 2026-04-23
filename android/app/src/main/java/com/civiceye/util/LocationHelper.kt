package com.civiceye.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class for GPS location operations
 */
object LocationHelper {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    /**
     * Initialize location client
     */
    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get current location using high accuracy GPS
     * @return Location object with latitude and longitude, or null if failed
     */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            return null
        }
        
        return try {
            // Try getting fresh location with high accuracy
            val cancellationTokenSource = CancellationTokenSource()
            val freshLocation = withTimeoutOrNull(5000L) { // 5s timeout for fresh fix
                 fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()
            }

            if (freshLocation != null) {
                return freshLocation
            }
            
            // Fallback: Last known location
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            // Last resort: try last location if fresh failed with exception
            try {
                fusedLocationClient.lastLocation.await()
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * Convert GPS coordinates to human-readable address
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Address string, or null if geocoding failed
     */
    suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): Address? = suspendCancellableCoroutine { continuation ->
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1
                ) { addresses ->
                    if (addresses.isNotEmpty()) {
                        continuation.resume(addresses[0])
                    } else {
                        continuation.resume(null)
                    }
                }
            } else {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    continuation.resume(addresses[0])
                } else {
                    continuation.resume(null)
                }
            }
        } catch (e: IOException) {
            continuation.resumeWithException(e)
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
    
    /**
     * Format address object to readable string
     */
    fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()

        // User requested Estimate Area (Main City) only.
        // We prioritize Locality (City) and AdminArea (State).
        // If Locality is missing, we try SubAdminArea (District).

        // Primary: City / Town
        if (!address.locality.isNullOrEmpty()) {
            parts.add(address.locality)
        } else if (!address.subAdminArea.isNullOrEmpty()) {
            // Fallback to District if City is null
            parts.add(address.subAdminArea)
        }

        // Secondary: State
        if (!address.adminArea.isNullOrEmpty()) {
            parts.add(address.adminArea)
        }

        // Fallback: If both City and District are missing, try SubLocality (Neighborhood)
        if (parts.isEmpty() && !address.subLocality.isNullOrEmpty()) {
            parts.add(address.subLocality)
        }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Unknown Location"
    }
    
    /**
     * Get last known location (faster but potentially less accurate)
     */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            return null
        }
        
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    /**
     * Format distance in human-readable format
     */
    fun formatDistance(meters: Float): String {
        return when {
            meters < 1000 -> "${meters.toInt()}m"
            meters < 10000 -> String.format(Locale.getDefault(), "%.1fkm", meters / 1000)
            else -> "${(meters / 1000).toInt()}km"
        }
    }
}
