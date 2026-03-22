# Prayer Times — Android app

Kotlin + **Jetpack Compose** + **Material 3** client for your Azure Function, matching the React web UI:

- Base URL, ZIP, country, optional date & method  
- **Use my location** (runtime permission → Fused Location → `Geocoder` for postal + country)  
- **Get prayer times** → OkHttp `GET` → parses `data` JSON → card layout with “Upcoming” highlight  

**Code flow & debugging (for future review):** see [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md).

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (latest stable).
2. **File → Open** and select the **`android`** folder (not the repo root).
3. Wait for Gradle sync. If Gradle Wrapper is missing, use **File → Settings → Build → Gradle** and let Studio use its bundled Gradle, or run **Gradle → Wrapper** from the IDE menu if offered.

### “Incompatible Gradle JVM version” (Java 21)

This project uses **Gradle 8.7+**, which can run on **JDK 21**. If Android Studio still complains:

1. **File → Settings** (macOS: **Android Studio → Settings**) → **Build, Execution, Deployment → Build Tools → Gradle**
2. Set **Gradle JDK** to **JDK 17** or **JDK 21** (with Gradle 8.7, both work for running Gradle).

If you prefer to stay on an older Gradle wrapper, you must point **Gradle JDK** to **17 or 20** (Gradle 8.4 does not support running on JVM 21).

## Default API URL (emulator vs device)

- **Android Emulator** → your computer’s `localhost` is **`10.0.2.2`**.  
  Default base URL in the app: `http://10.0.2.2:7071` (Azure Functions on host port `7071`).
- **Physical phone** → use your PC’s **LAN IP**, e.g. `http://192.168.1.10:7071`, and ensure the phone and PC are on the same Wi‑Fi. Firewall must allow inbound connections to that port.

## HTTP / HTTPS

- `usesCleartextTraffic="true"` is enabled for **local HTTP** during development.  
  For production, point the base URL to **HTTPS** and consider removing cleartext or using a [network security config](https://developer.android.com/privacy-and-security/security-config).

## Azure Function auth (`code=`)

If your function uses **Function** authorization, append the key to the base URL query in the app is awkward; easier options:

- Temporarily set the HTTP trigger to **Anonymous** for testing, or  
- Put the full URL including `?code=...` in “Function base URL” **without** extra path (OkHttp will merge query when building `/api/prayertimes` — actually our builder might drop existing query).  

**Recommended:** deploy with **Anonymous** for a public prayer widget, or add a small API gateway. If you need `code` in-app, we can extend the ViewModel to append `code` from a separate field.

## Run

1. Start the Azure Function on the host (`func start` in `AdhanFunction`).
2. Run the app from Android Studio on an emulator or device.
3. Optionally tap **Use my location**, then **Get prayer times**.

## Project layout

- `app/src/main/java/com/adhan/prayertimes/MainActivity.kt` — permissions + Compose entry  
- `PrayerTimesViewModel.kt` — HTTP + location  
- `PrayerTimesScreen.kt` — UI  
- `PrayerModels.kt` / `AladhanJsonParser.kt` — same prayer order & “next” logic as the React app  
