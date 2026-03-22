using System.Net;
using System.Net.Http.Json;
using System.Text.Json.Serialization;
using AdhanFunction.Models;
using Microsoft.Extensions.Logging;

namespace AdhanFunction.Services;

public sealed class ZipGeocoder
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<ZipGeocoder> _logger;
    private readonly TableCache _cache;

    public ZipGeocoder(IHttpClientFactory httpClientFactory, ILogger<ZipGeocoder> logger, TableCache cache)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _cache = cache;
    }

    public async Task<LatLong?> GetLatLongAsync(string country, string zip, DateTimeOffset nowUtc)
    {
        var cached = await _cache.TryGetZipLatLongAsync(country, zip, nowUtc);
        if (cached is not null)
            return cached;

        var baseUrl = Environment.GetEnvironmentVariable("ZIPPOPOTAM_BASE_URL")?.TrimEnd('/')
                      ?? "https://api.zippopotam.us";

        var url = $"{baseUrl}/{Uri.EscapeDataString(country)}/{Uri.EscapeDataString(zip)}";

        try
        {
            var http = _httpClientFactory.CreateClient();
            var resp = await http.GetAsync(url);
            if (resp.StatusCode == HttpStatusCode.NotFound)
                return null;

            resp.EnsureSuccessStatusCode();

            var payload = await resp.Content.ReadFromJsonAsync<ZippopotamResponse>();
            var place = payload?.Places?.FirstOrDefault();
            if (place is null)
                return null;

            if (!double.TryParse(place.Latitude, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var lat))
                return null;
            if (!double.TryParse(place.Longitude, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var lon))
                return null;

            var result = new LatLong(lat, lon);
            await _cache.PutZipLatLongAsync(country, zip, result, nowUtc);
            return result;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Zip geocoding failed for {Country}/{Zip}", country, zip);
            return null;
        }
    }

    private sealed class ZippopotamResponse
    {
        [JsonPropertyName("places")]
        public List<ZippopotamPlace>? Places { get; set; }
    }

    private sealed class ZippopotamPlace
    {
        [JsonPropertyName("latitude")]
        public string Latitude { get; set; } = default!;

        [JsonPropertyName("longitude")]
        public string Longitude { get; set; } = default!;
    }
}

