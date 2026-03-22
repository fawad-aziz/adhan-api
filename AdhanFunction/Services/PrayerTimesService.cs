using System.Net.Http.Headers;
using AdhanFunction.Models;
using Microsoft.Extensions.Logging;

namespace AdhanFunction.Services;

public sealed class PrayerTimesService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<PrayerTimesService> _logger;

    public PrayerTimesService(IHttpClientFactory httpClientFactory, ILogger<PrayerTimesService> logger)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    public async Task<string> GetTimingsAsync(string dateDdMmYyyy, LatLong latLong, int method)
    {
        var baseUrl = Environment.GetEnvironmentVariable("ALADHAN_BASE_URL")?.TrimEnd('/')
                      ?? "https://api.aladhan.com/v1";

        var url =
            $"{baseUrl}/timings/{Uri.EscapeDataString(dateDdMmYyyy)}" +
            $"?latitude={latLong.Latitude.ToString(System.Globalization.CultureInfo.InvariantCulture)}" +
            $"&longitude={latLong.Longitude.ToString(System.Globalization.CultureInfo.InvariantCulture)}" +
            $"&method={method}";

        var http = _httpClientFactory.CreateClient();
        http.DefaultRequestHeaders.Accept.Clear();
        http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        _logger.LogInformation("Calling AlAdhan timings API: {Url}", url);

        using var resp = await http.GetAsync(url);
        resp.EnsureSuccessStatusCode();
        return await resp.Content.ReadAsStringAsync();
    }
}

