package com.teamwork.pricehunter.ui.food

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.intelligence.IranianGeoBounds
import com.teamwork.pricehunter.core.state.LocationPickerState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

@Composable
fun LocationPickerDialog(
    initialLocation: GeoLocation = LocationPickerState.DEFAULT_TEHRAN_LOCATION,
    onLocationConfirmed: (GeoLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pickerState = remember { LocationPickerState(initialLocation) }
    var currentLocation by remember { mutableStateOf(initialLocation) }
    var isWithinBounds by remember { mutableStateOf(IranianGeoBounds.isWithinIran(initialLocation)) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Initialize OSMDroid configuration with custom User-Agent
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Permission launcher for GPS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchGpsLocation(context, pickerState) { newLoc ->
                currentLocation = newLoc
                isWithinBounds = IranianGeoBounds.isWithinIran(newLoc)
                mapViewInstance?.controller?.animateTo(GeoPoint(newLoc.latitude, newLoc.longitude))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("location_picker_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Interactive OSMDroid MapView
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                            addMapListener(object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    val center = mapCenter
                                    val newLoc = GeoLocation(center.latitude, center.longitude)
                                    pickerState.onManualMapPinDragged(newLoc)
                                    currentLocation = newLoc
                                    isWithinBounds = IranianGeoBounds.isWithinIran(newLoc)
                                    return true
                                }
                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    val center = mapCenter
                                    val newLoc = GeoLocation(center.latitude, center.longitude)
                                    pickerState.onManualMapPinDragged(newLoc)
                                    currentLocation = newLoc
                                    isWithinBounds = IranianGeoBounds.isWithinIran(newLoc)
                                    return true
                                }
                            })
                            mapViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Stationary Center Pin Marker
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "انتخاب موقعیت",
                    tint = if (isWithinBounds) Color(0xFFD32F2F) else Color.Gray,
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                        .padding(bottom = 22.dp)
                        .testTag("map_center_pin")
                )

                // Top Header with Close Button and Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "انتخاب موقعیت تحویل سفارش",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("location_picker_close_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                // Out-of-Bounds Warning Banner
                if (!isWithinBounds) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 64.dp)
                            .align(Alignment.TopCenter)
                            .testTag("out_of_bounds_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = IranianGeoBounds.OUT_OF_BOUNDS_WARNING,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // GPS My Location Floating Button
                FloatingActionButton(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasFine || hasCoarse) {
                            fetchGpsLocation(context, pickerState) { newLoc ->
                                currentLocation = newLoc
                                isWithinBounds = IranianGeoBounds.isWithinIran(newLoc)
                                mapViewInstance?.controller?.animateTo(GeoPoint(newLoc.latitude, newLoc.longitude))
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 96.dp, end = 16.dp)
                        .testTag("gps_my_location_button"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = Icons.Default.Place, contentDescription = "موقعیت من (GPS)")
                }

                // Bottom Confirmation & Coordinates Panel
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "مختصات: ${String.format(Locale.US, "%.4f, %.4f", currentLocation.latitude, currentLocation.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("location_coordinates_text")
                            )
                            Text(
                                text = if (isWithinBounds) "محدوده مجاز" else "خارج از محدوده",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWithinBounds) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onLocationConfirmed(currentLocation)
                                onDismiss()
                            },
                            enabled = isWithinBounds,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_location_button")
                        ) {
                            Text(
                                text = "تأیید موقعیت تحویل",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchGpsLocation(
    context: Context,
    pickerState: LocationPickerState,
    onLocation: (GeoLocation) -> Unit
) {
    try {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val geo = GeoLocation(loc.latitude, loc.longitude)
                pickerState.onGpsLocationReceived(geo)
                onLocation(geo)
            }
        }
    } catch (_: Exception) {}
}
