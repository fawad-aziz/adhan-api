import React, { useState } from "react";

const DEFAULT_BASE_URL = "http://localhost:7071";

// If you deploy the function app, change this to your live URL, e.g.:
// const DEFAULT_BASE_URL = "https://<YOUR_FUNCTION_APP_NAME>.azurewebsites.net";

type AladhanTimingsResponse = {
  data?: {
    date?: {
      readable?: string;
      hijri?: {
        date?: string;
        month?: { en?: string };
        year?: string;
      };
    };
    timings?: Record<string, string>;
  };
};

const containerStyle: React.CSSProperties = {
  maxWidth: 600,
  margin: "2rem auto",
  padding: "1.5rem",
  borderRadius: 12,
  border: "1px solid #e5e7eb",
  boxShadow: "0 10px 25px rgba(15,23,42,0.08)",
  fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif",
};

const labelStyle: React.CSSProperties = {
  display: "block",
  fontWeight: 600,
  marginBottom: 4,
};

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "0.55rem 0.7rem",
  borderRadius: 8,
  border: "1px solid #d1d5db",
  fontSize: 14,
  boxSizing: "border-box",
};

const rowStyle: React.CSSProperties = {
  display: "grid",
  gridTemplateColumns: "1fr 1fr",
  gap: "1rem",
};

const buttonStyle: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  justifyContent: "center",
  padding: "0.6rem 1.4rem",
  borderRadius: 999,
  border: "none",
  background:
    "linear-gradient(135deg, rgb(59,130,246) 0%, rgb(37,99,235) 50%, rgb(147,51,234) 100%)",
  color: "white",
  fontWeight: 600,
  cursor: "pointer",
  fontSize: 14,
};

const PRAYER_ORDER: { key: string; label: string }[] = [
  { key: "Fajr", label: "Fajr" },
  { key: "Sunrise", label: "Sunrise" },
  { key: "Dhuhr", label: "Dhuhr" },
  { key: "Asr", label: "Asr" },
  { key: "Maghrib", label: "Maghrib" },
  { key: "Isha", label: "Isha" },
];

type PrayerCard = {
  key: string;
  label: string;
  time: string;
};

function buildPrayerCards(data?: AladhanTimingsResponse["data"]): PrayerCard[] {
  const timings = data?.timings ?? {};
  return PRAYER_ORDER.map((p) => ({
    key: p.key,
    label: p.label,
    time: timings[p.key] ?? "--:--",
  }));
}

function findNextPrayer(prayers: PrayerCard[]): PrayerCard | null {
  const now = new Date();
  const nowMinutes = now.getHours() * 60 + now.getMinutes();

  let best: { minutes: number; card: PrayerCard } | null = null;

  for (const p of prayers) {
    const [hStr, mStr] = p.time.split(":");
    const h = Number.parseInt(hStr, 10);
    const m = Number.parseInt(mStr, 10);
    if (Number.isNaN(h) || Number.isNaN(m)) continue;

    const mins = h * 60 + m;
    if (mins >= nowMinutes && (!best || mins < best.minutes)) {
      best = { minutes: mins, card: p };
    }
  }

  return best?.card ?? null;
}

export const PrayerTimesApp: React.FC = () => {
  const [baseUrl, setBaseUrl] = useState(DEFAULT_BASE_URL);
  const [zip, setZip] = useState("");
  const [country, setCountry] = useState("us");
  const [date, setDate] = useState(""); // HTML date input (yyyy-MM-dd)
  const [method, setMethod] = useState("2");
  const [loading, setLoading] = useState(false);
  const [locationLoading, setLocationLoading] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AladhanTimingsResponse | null>(null);

  const detectLocation = React.useCallback(async () => {
    if (!navigator.geolocation) {
      setLocationError("Geolocation is not supported by your browser.");
      return;
    }
    setLocationError(null);
    setLocationLoading(true);
    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 300000,
        });
      });
      const { latitude, longitude } = position.coords;
      const res = await fetch(
        `https://nominatim.openstreetmap.org/reverse?lat=${latitude}&lon=${longitude}&format=json`,
        { headers: { "Accept-Language": "en", "User-Agent": "PrayerTimesApp/1.0" } }
      );
      if (!res.ok) throw new Error("Could not get address from location.");
      const data = (await res.json()) as {
        address?: { postcode?: string; country_code?: string };
      };
      const postcode = data.address?.postcode;
      const countryCode = (data.address?.country_code ?? "").toLowerCase();
      if (postcode) setZip(String(postcode).trim());
      if (countryCode) setCountry(countryCode);
      if (!postcode && !countryCode) {
        setLocationError("No postal code found for this location.");
      }
    } catch (e: unknown) {
      const err = e as { code?: number };
      const msg =
        err?.code === 1
          ? "Location permission denied."
          : e instanceof Error
            ? e.message
            : "Location detection failed.";
      setLocationError(msg);
    } finally {
      setLocationLoading(false);
    }
  }, []);

  const onSubmit: React.FormEventHandler<HTMLFormElement> = async (e) => {
    e.preventDefault();
    setError(null);
    setResult(null);

    if (!zip.trim()) {
      setError("Please enter a ZIP / postal code.");
      return;
    }

    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set("zip", zip.trim());
      if (country.trim()) params.set("country", country.trim());
      if (date.trim()) params.set("date", date.trim());
      if (method.trim()) params.set("method", method.trim());

      const url = `${baseUrl.replace(/\/+$/, "")}/api/prayertimes?${params.toString()}`;
      const resp = await fetch(url);
      const text = await resp.text();

      let parsed: AladhanTimingsResponse | null;
      try {
        parsed = text ? (JSON.parse(text) as AladhanTimingsResponse) : null;
      } catch {
        parsed = null;
      }

      if (!resp.ok) {
        const message =
          parsed &&
          typeof parsed === "object" &&
          "error" in (parsed as any)
            ? (parsed as any).error
            : `Request failed with status ${resp.status}`;
        throw new Error(String(message));
      }

      setResult(parsed);
    } catch (err: any) {
      setError(err?.message ?? "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={containerStyle}>
      <h1 style={{ fontSize: 22, marginBottom: 4 }}>Prayer Times</h1>
      <p style={{ marginBottom: 16, color: "#4b5563", fontSize: 14 }}>
        Enter your ZIP / postal code to fetch daily prayer times from your Azure Function.
      </p>

      <form onSubmit={onSubmit} style={{ display: "grid", gap: "1rem" }}>
        <div>
          <label style={labelStyle} htmlFor="baseUrl">
            Function base URL
          </label>
          <input
            id="baseUrl"
            type="text"
            style={inputStyle}
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="http://localhost:7071 or https://<app>.azurewebsites.net"
          />
        </div>

        <div style={rowStyle}>
          <div>
            <label style={labelStyle} htmlFor="zip">
              ZIP / Postal code
            </label>
            <input
              id="zip"
              type="text"
              style={inputStyle}
              value={zip}
              onChange={(e) => setZip(e.target.value)}
              placeholder="10001"
            />
          </div>

          <div>
            <label style={labelStyle} htmlFor="country">
              Country code
            </label>
            <input
              id="country"
              type="text"
              style={inputStyle}
              value={country}
              onChange={(e) => setCountry(e.target.value)}
              placeholder="us"
            />
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginTop: -4 }}>
          <button
            type="button"
            onClick={detectLocation}
            disabled={locationLoading}
            style={{
              ...buttonStyle,
              background: "transparent",
              color: "#0ea5e9",
              border: "1px solid #0ea5e9",
              padding: "0.45rem 0.9rem",
              fontSize: 13,
            }}
          >
            {locationLoading ? "Detecting…" : "Use my location"}
          </button>
          <span style={{ fontSize: 12, color: "#6b7280" }}>
            Fills ZIP and country from your browser location
          </span>
        </div>
        {locationError && (
          <div style={{ fontSize: 13, color: "#b91c1c", marginTop: -4 }}>
            {locationError}
          </div>
        )}

        <div style={rowStyle}>
          <div>
            <label style={labelStyle} htmlFor="date">
              Date (optional)
            </label>
            <input
              id="date"
              type="date"
              style={inputStyle}
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
            <div style={{ fontSize: 12, color: "#6b7280", marginTop: 4 }}>
              Leave empty to use today (UTC).
            </div>
          </div>

          <div>
            <label style={labelStyle} htmlFor="method">
              Method (optional)
            </label>
            <input
              id="method"
              type="number"
              style={inputStyle}
              value={method}
              onChange={(e) => setMethod(e.target.value)}
            />
            <div style={{ fontSize: 12, color: "#6b7280", marginTop: 4 }}>
              Defaults to 2 if empty.
            </div>
          </div>
        </div>

        <div>
          <button type="submit" style={buttonStyle} disabled={loading}>
            {loading ? "Loading..." : "Get prayer times"}
          </button>
        </div>
      </form>

      {error && (
        <div
          style={{
            marginTop: "1rem",
            padding: "0.75rem 0.9rem",
            borderRadius: 8,
            background: "#fef2f2",
            color: "#b91c1c",
            fontSize: 14,
          }}
        >
          {error}
        </div>
      )}

      {result && <PrayerTimesPanel data={result.data} />}
    </div>
  );
};

type PrayerTimesPanelProps = {
  data?: AladhanTimingsResponse["data"];
};

const PrayerTimesPanel: React.FC<PrayerTimesPanelProps> = ({ data }) => {
  const prayers = buildPrayerCards(data);
  const next = findNextPrayer(prayers);

  const readableDate = data?.date?.readable ?? "";
  const hijriDate =
    data?.date?.hijri?.date ??
    (data?.date?.hijri?.month?.en && data?.date?.hijri?.year
      ? `${data.date.hijri.month.en} ${data.date.hijri.year}`
      : "");

  return (
    <div
      style={{
        marginTop: "1.5rem",
        padding: "1.25rem 1.4rem",
        borderRadius: 16,
        background: "linear-gradient(135deg, #0f172a 0%, #1f2937 50%, #111827 100%)",
        color: "#e5e7eb",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "baseline",
          marginBottom: "1rem",
        }}
      >
        <div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>Today&apos;s Prayer Times</div>
          <div style={{ fontSize: 13, color: "#9ca3af", marginTop: 2 }}>
            {readableDate}
            {hijriDate ? ` · ${hijriDate}` : ""}
          </div>
        </div>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(5, minmax(0, 1fr))",
          gap: "0.75rem",
        }}
      >
        {prayers.map((p) => {
          const isNext = next && next.key === p.key;
          return (
            <div
              key={p.key}
              style={{
                padding: "0.75rem 0.6rem",
                borderRadius: 12,
                background: isNext ? "#f97316" : "rgba(15,23,42,0.9)",
                color: isNext ? "#111827" : "#e5e7eb",
                boxShadow: isNext
                  ? "0 12px 24px rgba(249,115,22,0.35)"
                  : "0 6px 16px rgba(15,23,42,0.5)",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
              }}
            >
              <div
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  opacity: isNext ? 0.95 : 0.8,
                  marginBottom: 6,
                }}
              >
                {p.label}
              </div>
              <div
                style={{
                  fontSize: 18,
                  fontWeight: 700,
                  letterSpacing: 0.2,
                }}
              >
                {p.time}
              </div>
              {isNext && (
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: 0.8,
                    marginTop: 4,
                  }}
                >
                  Upcoming
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

