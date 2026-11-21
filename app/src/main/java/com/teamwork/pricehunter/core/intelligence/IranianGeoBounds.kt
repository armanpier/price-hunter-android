package com.teamwork.pricehunter.core.intelligence

sealed class LocationValidationResult {
    data class Valid(val location: GeoLocation) : LocationValidationResult()
    data class OutOfBounds(val location: GeoLocation, val warningMessage: String) : LocationValidationResult()
}

object IranianGeoBounds {
    const val MIN_LAT = 25.0
    const val MAX_LAT = 40.0
    const val MIN_LON = 44.0
    const val MAX_LON = 64.0
    const val OUT_OF_BOUNDS_WARNING = "موقعیت انتخابی خارج از محدوده سرویس‌دهی است"

    fun isWithinIran(location: GeoLocation): Boolean {
        return location.latitude in MIN_LAT..MAX_LAT &&
               location.longitude in MIN_LON..MAX_LON
    }

    fun validateLocation(location: GeoLocation): LocationValidationResult {
        return if (isWithinIran(location)) {
            LocationValidationResult.Valid(location)
        } else {
            LocationValidationResult.OutOfBounds(
                location = location,
                warningMessage = OUT_OF_BOUNDS_WARNING
            )
        }
    }
}

sealed class LocationSource {
    data class Gps(val location: GeoLocation) : LocationSource()
    data class ManualMapPin(val location: GeoLocation) : LocationSource()
    object None : LocationSource()
}

class LocationSelectionManager(
    val defaultLocation: GeoLocation = GeoLocation(35.6892, 51.3890) // Tehran Center fallback
) {
    var activeLocation: GeoLocation = defaultLocation
        private set
    var locationSource: LocationSource = LocationSource.None
        private set
    var permissionDeniedBannerVisible: Boolean = false
        private set

    fun onGpsPermissionResult(isGranted: Boolean, gpsLocation: GeoLocation? = null): LocationSource {
        return if (isGranted && gpsLocation != null) {
            activeLocation = gpsLocation
            locationSource = LocationSource.Gps(gpsLocation)
            permissionDeniedBannerVisible = false
            locationSource
        } else {
            locationSource = LocationSource.ManualMapPin(activeLocation)
            permissionDeniedBannerVisible = true
            locationSource
        }
    }

    fun onManualPinDragged(pinLocation: GeoLocation) {
        activeLocation = pinLocation
        locationSource = LocationSource.ManualMapPin(pinLocation)
    }
}
