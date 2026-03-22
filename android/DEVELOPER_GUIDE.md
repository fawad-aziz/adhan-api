# Android app — code flow & debugging

This document explains how the Prayer Times Android app is structured, the basic execution flow, and how to debug common issues. Keep it for future review.

## Big picture

1. **`MainActivity`** starts the app, wires **Jetpack Compose**, and handles **location permission**.
2. **`PrayerTimesViewModel`** holds **all screen state** and performs **network + location** work (off the main thread where appropriate).
3. **`PrayerTimesScreen`** is **UI only**: it reads state and invokes callbacks (no HTTP inside the composable).
4. **`AladhanJsonParser`** and **`PrayerModels`** turn the JSON **`data`** object into what the UI shows (prayer cards + “next” prayer).

---

## 1. Entry: `MainActivity.kt`

- **`onCreate`** → `setContent { PrayerTimesTheme { … } }` builds the UI.
- **`viewModel()`** provides a single **`PrayerTimesViewModel`** tied to the activity (survives configuration changes like rotation).
- **`PrayerTimesScreen`** receives:
  - **`state`** from `viewModel.uiState` (a `StateFlow` collected as Compose state).
  - **Callbacks** that forward to the ViewModel (`setZip`, `fetchPrayerTimes`, etc.).
- **Location**: **`onDetectLocation`** either:
  - Calls **`viewModel.detectLocation(this)`** if permission is already granted, or
  - Launches **`ActivityResultContracts.RequestMultiplePermissions`**; when granted, calls **`detectLocation`** again.

**Debugging:** Use breakpoints in `onCreate` and in the permission launcher lambda to see whether failures are permission-related vs. network-related.

---

## 2. State & logic: `PrayerTimesViewModel.kt`

### State (`PrayerModels.kt` — `PrayerTimesUiState`)

- Form: `baseUrl`, `zip`, `country`, `date`, `method`
- Flags: `loading`, `locationLoading`
- Messages: `error`, `locationError`
- Result: `panel` (`PrayerPanelUi` with cards + `nextKey`)

### `fetchPrayerTimes()`

1. Validates that `zip` is not empty.
2. Sets `loading = true`, clears `error` and `panel`.
3. **`viewModelScope.launch`** → **`withContext(Dispatchers.IO)`** runs **`doHttpGet(buildRequestUrl(state))`** using **OkHttp**.
4. On success, **`AladhanJsonParser.parsePanel(body)`** builds **`PrayerPanelUi`**; on failure, sets **`error`**.

### `detectLocation(activity)`

1. Sets `locationLoading = true`.
2. On **`Dispatchers.Main`**, uses **Fused Location Provider** (`getCurrentLocation`, then **`lastLocation`** as fallback) with **`.await()`** (Play Services + Kotlin coroutines).
3. On **`Dispatchers.IO`**, **`Geocoder.getFromLocation`** → postal code + country → updates **`zip`** / **`country`**.

### `buildRequestUrl`

- Uses OkHttp **`HttpUrl`** to append **`/api/prayertimes`** and query parameters (`zip`, optional `country`, `date`, `method`).

### Debugging the ViewModel

- Breakpoints in **`fetchPrayerTimes`** and **`doHttpGet`**: inspect **`url`**, response **code**, and **body**.
- Temporary **`Log.d("Prayer", url)`** before the request.
- Optional: **Charles / mitmproxy**, or Android Studio **Network Inspector** (when enabled for the app process).

---

## 3. UI: `PrayerTimesScreen.kt`

- **`PrayerTimesScreen`** is a **stateless** composable: it displays **`state`** and calls **`onFetch`**, **`onDetectLocation`**, etc.
- **`PrayerPanel`** draws the dark gradient block and **`FlowRow`** of prayer cards. **“Upcoming”** is when **`card.key == panel.nextKey`**.

**Debugging:** If the UI is wrong but the API JSON looks correct, breakpoint **`AladhanJsonParser.parsePanel`** and the helpers in **`PrayerModels.kt`** (`buildPrayerCards`, **`findNextPrayer`**).

---

## 4. Parsing & “next prayer”: `AladhanJsonParser.kt` + `PrayerModels.kt`

- **`AladhanJsonParser.parsePanel`**: reads **`data.timings`**, **`data.date.readable`**, Hijri fields → builds **`PrayerPanelUi`**.
- **`buildPrayerCards`**: fixed order **Fajr → Sunrise → Dhuhr → Asr → Maghrib → Isha** (aligned with the web app).
- **`findNextPrayer`**: compares the **current device time** to parsed times (simple **HH:mm** style parsing).

**Debugging:** If times look wrong, log raw timing strings from JSON — AlAdhan may include extra text; **`parseTimeToMinutes`** may need tuning for some formats.

---

## 5. Theme: `ui/theme/Theme.kt`

- Wraps **`MaterialTheme`** (light/dark). Visual only.

---

## Symptom → where to look

| Symptom | Where to look |
|--------|----------------|
| Permission dialog never appears / location errors | `MainActivity` permission launcher + `detectLocation` |
| “Could not get your position” | Fused Location (emulator: set mock location; prefer an AVD **with Google Play**) |
| Geocoder returns nothing | Emulator without Play Services / network; try a physical device or different AVD |
| HTTP errors / 401 / connection refused | `buildRequestUrl`, `doHttpGet`, base URL (`10.0.2.2` vs PC LAN IP), firewall, Azure Function **`code=`** auth |
| JSON / empty panel | `AladhanJsonParser.parsePanel`; log **`body`** (or a substring) |
| Wrong “Upcoming” prayer | `findNextPrayer` + 12h vs 24h / extra characters in time strings |

---

## Android Studio debugging basics

1. **Breakpoints** in the gutter in `ViewModel` / `MainActivity`.
2. **Run → Debug ‘app’** (bug icon).
3. **Logcat**: filter by package **`com.adhan.prayertimes`** or a custom **`Log.d`** tag.
4. In the debugger, when stopped inside the ViewModel, inspect **`_uiState`** / current **`uiState.value`** in the **Variables** pane (depending on visibility).

---

## Related docs

- Run instructions, emulator URL, and JDK/Gradle notes: **`README.md`** in this `android/` folder.
