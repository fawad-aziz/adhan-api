## Overview

This repo contains an **Azure Function (C# / .NET isolated)** with an **HTTP trigger** that:

- Accepts **`zip`**, optional **`date`**, optional **`method`**.
- Defaults:
  - **`date`** defaults to **today (UTC)** if not passed.
  - **`method`** defaults to **2** if not passed.
- Translates **ZIP/postal code → latitude/longitude** via **Zippopotam** (`api.zippopotam.us`).
- Calls the AlAdhan **Prayer Times API** endpoint `GET /timings/{date}`.
- Caches the AlAdhan response in **Azure Table Storage** for **24 hours**.

AlAdhan endpoint reference: [Prayer Times API - Get timings by date](https://aladhan.com/prayer-times-api#get-/timings/-date-).

## Clients

- **Web**: Vite + React + TypeScript in `frontend/` (see that folder and `npm install` / `npm run dev`).
- **Android**: Kotlin + Jetpack Compose in `android/` — open the `android` directory in Android Studio; see [`android/README.md`](android/README.md) for emulator URL (`10.0.2.2`), device LAN IP, and permissions.

## API contract (your Azure Function)

### Endpoint

`GET /api/prayertimes`

### Query parameters

- **`zip`** (required): postal/zip code (ex: `10001`)
- **`country`** (optional, default `us`): needed by Zippopotam (`us`, `ca`, `gb`, etc.)
- **`date`** (optional): `yyyy-MM-dd` or `dd-MM-yyyy`
- **`method`** (optional, default `2`): AlAdhan calculation method integer

### Response

- **200**: raw JSON returned by AlAdhan
- **400**: `{ "error": "..." }` if input is invalid or ZIP can’t be geocoded

## Caching behavior

- **Prayer times cache key**: `(country, zip, date, method)`
- **TTL**: **24 hours**
- **Storage**: Azure Table Storage table `PrayerTimesCache`

> Note: The user requirement says “cache by zip code for 24 hours”. Caching only by zip would return the wrong result for different dates/methods, so the implementation caches by **zip+date+method** (still “by zip”, but correct).

ZIP geocoding results are cached separately in table `ZipGeoCache` for 30 days to reduce calls.

## Local run

### Prereqs

- Install **.NET 8 SDK**
- Install **Azure Functions Core Tools v4**

### Configure settings

Copy the example file:

```bash
cp AdhanFunction/local.settings.json.example AdhanFunction/local.settings.json
```

If you want to use a real Storage account locally, set one of:

- `CACHE_STORAGE_CONNECTION_STRING` (preferred)
- `AzureWebJobsStorage` (fallback)

### Run

```bash
cd AdhanFunction
func start
```

## Sample requests

### Default date (today UTC) and default method=2

```bash
curl "http://localhost:7071/api/prayertimes?zip=10001&country=us"
```

### Pass method

```bash
curl "http://localhost:7071/api/prayertimes?zip=10001&country=us&method=4"
```

### Pass date

```bash
curl "http://localhost:7071/api/prayertimes?zip=10001&country=us&date=2026-03-01&method=2"
```

## Deploy to Azure (GitHub → continuous deployment)

The recommended setup is **push to GitHub → automatic deploy** to your Function App.

### 1. Create Azure resources (Portal)

1. Create a **Storage Account** (used by Functions + cache tables).
2. Create a **Function App**:
   - **Runtime stack**: `.NET` (isolated)
   - **Version**: `.NET 8` (or latest available isolated runtime)
   - **Region**: choose your region
   - **Storage**: select the Storage Account from step 1

### 2. Configure app settings (Portal)

In Function App → **Settings** → **Configuration** → **Application settings**, verify:

- **`AzureWebJobsStorage`** is set (Azure sets this when you attach a storage account).
- **`FUNCTIONS_WORKER_RUNTIME`** = `dotnet-isolated`

Optional overrides:

- **`ALADHAN_BASE_URL`** (default `https://api.aladhan.com/v1`)
- **`ZIPPOPOTAM_BASE_URL`** (default `https://api.zippopotam.us`)
- **`DEFAULT_COUNTRY`** (default `us`)
- **`CACHE_STORAGE_CONNECTION_STRING`** (if you want cache to use a different Storage account than `AzureWebJobsStorage`)

### 3. Continuous deployment from GitHub (choose one)

#### Option A — GitHub Actions workflow in this repo (recommended)

This repo includes **`.github/workflows/azure-functions-deploy.yml`**, which:

- Runs on every **`push` to `main`** that touches **`AdhanFunction/**`**
- Runs **`dotnet publish`** on `AdhanFunction/AdhanFunction.csproj`
- Deploys the output with **`Azure/functions-action`**

**One-time setup:**

1. Push this repository to **GitHub** (if it is not already).
2. In **Azure Portal** → your Function App → **Get publish profile** (top toolbar) → download the `.PublishSettings` file.
3. In GitHub: **Repository → Settings → Secrets and variables → Actions → New repository secret**
   - **Name:** `AZURE_FUNCTIONAPP_PUBLISH_PROFILE`
   - **Value:** paste the **entire** contents of the publish profile file.
4. Add a second repository secret **`AZURE_FUNCTIONAPP_NAME`** with your Function App’s **name** (same as in Azure Portal, not the full URL).
5. Commit and push to **`main`**. Open **Actions** in GitHub to watch the run; each successful run updates the live Function App.

> **Monorepo note:** The workflow already publishes from the **`AdhanFunction/`** subfolder. If you use Deployment Center (Option B) and Azure generates its own workflow, you may need to add **`working-directory: AdhanFunction`** or adjust **`dotnet publish`** paths the same way.

#### Option B — Azure Portal **Deployment Center** (GitHub)

1. Put the code in a **GitHub** repository.
2. Azure Portal → your **Function App** → **Deployment Center**.
3. **Source**: **GitHub** → authorize with your GitHub account.
4. Select **Organization**, **Repository**, and **Branch** (e.g. `main`).
5. **Build provider**: **GitHub Actions** (or let Azure configure the pipeline it recommends for **.NET**).
6. Save. Azure will add or update a workflow under **`.github/workflows/`** in your repo.
7. **Important:** Because the .NET project lives in **`AdhanFunction/`**, open the generated YAML and ensure the build step publishes that project, for example:
   - `dotnet publish AdhanFunction/AdhanFunction.csproj -c Release -o ./publish`
   - Point the deploy action’s **`package`** / artifact path at that **`publish`** folder.

After setup, every push to the configured branch triggers a new deployment (continuous update).

### 4. Other publish methods (manual)

**CLI (one-off):**

```bash
az login
cd AdhanFunction
func azure functionapp publish <YOUR_FUNCTION_APP_NAME>
```

**Zip deploy:**

```bash
cd AdhanFunction
dotnet publish -c Release -o publish
cd publish
zip -r ../publish.zip .
cd ..
az functionapp deployment source config-zip \
  --resource-group <RG_NAME> \
  --name <YOUR_FUNCTION_APP_NAME> \
  --src publish.zip
```

---

After publishing, your function URL will look like:

`https://<YOUR_FUNCTION_APP_NAME>.azurewebsites.net/api/prayertimes?zip=10001&country=us`

If your function authorization level is **Function**, include the key:

```bash
curl "https://<YOUR_FUNCTION_APP_NAME>.azurewebsites.net/api/prayertimes?zip=10001&country=us&code=<FUNCTION_KEY>"
```

