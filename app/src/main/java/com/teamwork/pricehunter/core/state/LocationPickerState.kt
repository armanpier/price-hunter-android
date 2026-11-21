package com.teamwork.pricehunter.core.state

import com.teamwork.pricehunter.core.intelligence.GeoLocation

class LocationPickerState(initialLocation: GeoLocation = DEFAULT_TEHRAN_LOCATION) {
    var currentLocation: GeoLocation = initialLocation
        private set

    var isGpsActive: Boolean = false
        private set

    var hasValidSelection: Boolean = false
        private set

    init {
        hasValidSelection = isInIranianBounds(initialLocation)
    }

    fun onGpsLocationReceived(location: GeoLocation) {
        currentLocation = location
        isGpsActive = true
        hasValidSelection = isInIranianBounds(location)
    }

    fun onManualMapPinDragged(location: GeoLocation) {
        currentLocation = location
        isGpsActive = false // Disengages GPS follow mode
        hasValidSelection = isInIranianBounds(location)
    }

    companion object {
        val DEFAULT_TEHRAN_LOCATION = GeoLocation(35.6892, 51.3890) // Tehran Center

        fun isInIranianBounds(loc: GeoLocation): Boolean {
            return (loc.latitude in 25.0..40.0) && (loc.longitude in 44.0..64.0)
        }
    }
}
