using System.Net;
using System.Text.Json;
using AdhanFunction.Services;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;

namespace AdhanFunction.Functions;

public sealed class GetPrayerTimes
{
    private readonly ILogger<GetPrayerTimes> _logger;
    private readonly TableCache _cache;
    private readonly ZipGeocoder _geocoder;
    private readonly PrayerTimesService _prayerTimes;

    public GetPrayerTimes(
        ILogger<GetPrayerTimes> logger,
        TableCache cache,
        ZipGeocoder geocoder,
        PrayerTimesService prayerTimes)
    {
        _logger = logger;
        _cache = cache;
        _geocoder = geocoder;
        _prayerTimes = prayerTimes;
    }

    [Function("GetPrayerTimes")]
    public async Task<HttpResponseData> Run(
        [HttpTrigger(AuthorizationLevel.Function, "get", "options", Route = "prayertimes")] HttpRequestData req)
    {
        if (string.Equals(req.Method, "OPTIONS", StringComparison.OrdinalIgnoreCase))
        {
            var preflight = req.CreateResponse(HttpStatusCode.NoContent);
            AddCorsHeaders(preflight);
            return preflight;
        }

        var query = ParseQuery(req.Url);

        var zip = (Get(query, "zip") ?? Get(query, "zipcode") ?? "").Trim();
        if (string.IsNullOrWhiteSpace(zip))
            return await BadRequest(req, "Missing required query parameter: zip");

        var country = (Get(query, "country") ?? Environment.GetEnvironmentVariable("DEFAULT_COUNTRY") ?? "us").Trim();
        if (string.IsNullOrWhiteSpace(country))
            country = "us";

        var method = 2;
        if (!string.IsNullOrWhiteSpace(Get(query, "method")) && !int.TryParse(Get(query, "method"), out method))
            return await BadRequest(req, "Invalid query parameter: method (must be an integer)");
        if (method <= 0)
            method = 2;

        var dateDdMmYyyy = NormalizeDateToDdMmYyyy(Get(query, "date"));
        if (dateDdMmYyyy is null)
            return await BadRequest(req, "Invalid query parameter: date (use yyyy-MM-dd or dd-MM-yyyy)");

        var nowUtc = DateTimeOffset.UtcNow;

        var cached = await _cache.TryGetPrayerTimesAsync(country, zip, dateDdMmYyyy, method, nowUtc);
        if (cached is not null)
        {
            _logger.LogInformation("Cache hit for {Country}/{Zip} {Date} method={Method}", country, zip, dateDdMmYyyy, method);
            return Json(req, cached, HttpStatusCode.OK);
        }

        var latLong = await _geocoder.GetLatLongAsync(country, zip, nowUtc);
        if (latLong is null)
            return await BadRequest(req, $"Could not resolve zip to latitude/longitude for country='{country}', zip='{zip}'.");

        try
        {
            var resultJson = await _prayerTimes.GetTimingsAsync(dateDdMmYyyy, latLong, method);
            await _cache.PutPrayerTimesAsync(country, zip, dateDdMmYyyy, method, resultJson, nowUtc);
            return Json(req, resultJson, HttpStatusCode.OK);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Upstream call failed (AlAdhan).");
            return await UpstreamError(req, "Upstream service error when calling AlAdhan.");
        }
    }

    private static Dictionary<string, string> ParseQuery(Uri url)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var q = url.Query;
        if (string.IsNullOrWhiteSpace(q))
            return result;

        var raw = q.StartsWith('?') ? q[1..] : q;
        foreach (var pair in raw.Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var idx = pair.IndexOf('=');
            if (idx < 0)
            {
                var k = Uri.UnescapeDataString(pair);
                if (!string.IsNullOrWhiteSpace(k))
                    result[k] = "";
                continue;
            }

            var key = Uri.UnescapeDataString(pair[..idx]);
            var val = Uri.UnescapeDataString(pair[(idx + 1)..]);
            if (!string.IsNullOrWhiteSpace(key))
                result[key] = val;
        }

        return result;
    }

    private static string? Get(Dictionary<string, string> query, string key)
        => query.TryGetValue(key, out var v) ? v : null;

    private static string? NormalizeDateToDdMmYyyy(string? date)
    {
        if (string.IsNullOrWhiteSpace(date))
            return DateTime.UtcNow.ToString("dd-MM-yyyy");

        var value = date.Trim();
        var formats = new[] { "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd" };
        if (DateTime.TryParseExact(
                value,
                formats,
                System.Globalization.CultureInfo.InvariantCulture,
                System.Globalization.DateTimeStyles.None,
                out var parsed))
        {
            return parsed.ToString("dd-MM-yyyy");
        }

        return null;
    }

    private static HttpResponseData Json(HttpRequestData req, string json, HttpStatusCode code)
    {
        var res = req.CreateResponse(code);
        res.Headers.Add("Content-Type", "application/json; charset=utf-8");
        AddCorsHeaders(res);
        res.WriteString(json);
        return res;
    }

    private static Task<HttpResponseData> BadRequest(HttpRequestData req, string message)
    {
        var payload = JsonSerializer.Serialize(new { error = message });
        return Task.FromResult(Json(req, payload, HttpStatusCode.BadRequest));
    }

    private static Task<HttpResponseData> UpstreamError(HttpRequestData req, string message)
    {
        var payload = JsonSerializer.Serialize(new { error = message });
        return Task.FromResult(Json(req, payload, HttpStatusCode.BadGateway));
    }

    private static void AddCorsHeaders(HttpResponseData res)
    {
        var origin = Environment.GetEnvironmentVariable("CORS_ALLOWED_ORIGIN")?.Trim();
        if (string.IsNullOrWhiteSpace(origin))
        {
            // Dev-friendly default; in production set CORS_ALLOWED_ORIGIN explicitly.
            origin = "*";
        }

        res.Headers.Add("Access-Control-Allow-Origin", origin);
        res.Headers.Add("Access-Control-Allow-Methods", "GET,OPTIONS");
        res.Headers.Add("Access-Control-Allow-Headers", "Content-Type,Authorization");
    }
}

