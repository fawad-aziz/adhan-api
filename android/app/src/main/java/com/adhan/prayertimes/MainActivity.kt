package com.adhan.prayertimes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.adhan.prayertimes.ui.theme.PrayerTimesTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerTimesViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.detectLocation(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrayerTimesTheme {
                val state by viewModel.uiState.collectAsState()
                PrayerTimesScreen(
                    state = state,
                    onBaseUrlChange = viewModel::setBaseUrl,
                    onZipChange = viewModel::setZip,
                    onCountryChange = viewModel::setCountry,
                    onDateChange = viewModel::setDate,
                    onMethodChange = viewModel::setMethod,
                    onFetch = { viewModel.fetchPrayerTimes() },
                    onDetectLocation = {
                        if (hasLocationPermission()) {
                            viewModel.detectLocation(this)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
