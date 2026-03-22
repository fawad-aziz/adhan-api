package com.adhan.prayertimes

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

class PrayerTimesViewModel(application: Application) : AndroidViewModel(application) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()

    fun setBaseUrl(v: String) = _uiState.update { it.copy(baseUrl = v) }
    fun setZip(v: String) = _uiState.update { it.copy(zip = v) }
    fun setCountry(v: String) = _uiState.update { it.copy(country = v) }
    fun setDate(v: String) = _uiState.update { it.copy(date = v) }
    fun setMethod(v: String) = _uiState.update { it.copy(method = v) }

    fun fetchPrayerTimes() {
        val state = _uiState.value
        if (state.zip.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a ZIP / postal code.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, panel = null) }
            try {
                val body = withContext(Dispatchers.IO) { doHttpGet(buildRequestUrl(state)) }
                val panel = AladhanJsonParser.parsePanel(body)
                if (panel == null) {
                    _uiState.update {
                        it.copy(loading = false, error = "Unexpected response from server.")
                    }
                } else {
                    _uiState.update { it.copy(loading = false, panel = panel) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Request failed.")
                }
            }
        }
    }

    fun detectLocation(activity: ComponentActivity) {
        viewModelScope.launch {
            _uiState.update { it.copy(locationLoading = true, locationError = null) }
            try {
                val fused = LocationServices.getFusedLocationProviderClient(activity)
                val location = withContext(Dispatchers.Main) {
                    val token = CancellationTokenSource().token
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token).await()
                        ?: fused.lastLocation.await()
                } ?: throw IllegalStateException("Could not get your position. Try again outdoors or enable location.")

                withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(getApplication(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val addr = list?.firstOrNull()
                        ?: throw IllegalStateException("No address for this location.")
                    val postal = addr.postalCode?.trim()
                    val cc = addr.countryCode?.lowercase(Locale.US)?.trim().orEmpty()
                    if (postal.isNullOrBlank() && cc.isBlank()) {
                        throw IllegalStateException("No postal code found for this location.")
                    }
                    _uiState.update {
                        it.copy(
                            zip = postal ?: it.zip,
                            country = if (cc.isNotBlank()) cc else it.country,
                            locationLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        locationLoading = false,
                        locationError = e.message ?: "Location detection failed.",
                    )
                }
            }
        }
    }

    private fun buildRequestUrl(state: PrayerTimesUiState): String {
        val base = state.baseUrl.trimEnd('/')
        val httpUrl = base.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid base URL.")
        val b = httpUrl.newBuilder()
            .addPathSegment("api")
            .addPathSegment("prayertimes")
            .addQueryParameter("zip", state.zip.trim())
        if (state.country.isNotBlank()) b.addQueryParameter("country", state.country.trim())
        if (state.date.isNotBlank()) b.addQueryParameter("date", state.date.trim())
        if (state.method.isNotBlank()) b.addQueryParameter("method", state.method.trim())
        return b.build().toString()
    }

    private fun doHttpGet(url: String): String {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = AladhanJsonParser.parseErrorMessage(body)
                throw IllegalStateException(err ?: "HTTP ${resp.code}")
            }
            return body
        }
    }
}
