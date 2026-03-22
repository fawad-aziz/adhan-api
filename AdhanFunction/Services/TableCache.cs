using AdhanFunction.Models;
using Azure;
using Azure.Data.Tables;
using Microsoft.Extensions.Logging;

namespace AdhanFunction.Services;

public sealed class TableCache
{
    private const string PrayerTimesTableName = "PrayerTimesCache";
    private const string ZipGeoTableName = "ZipGeoCache";

    private static readonly TimeSpan PrayerTimesTtl = TimeSpan.FromHours(24);
    private static readonly TimeSpan ZipGeoTtl = TimeSpan.FromDays(30);

    private readonly TableClient _prayerTimesTable;
    private readonly TableClient _zipGeoTable;
    private readonly ILogger<TableCache> _logger;

    public TableCache(TableServiceClient serviceClient, ILogger<TableCache> logger)
    {
        _logger = logger;

        _prayerTimesTable = serviceClient.GetTableClient(PrayerTimesTableName);
        _zipGeoTable = serviceClient.GetTableClient(ZipGeoTableName);

        _prayerTimesTable.CreateIfNotExists();
        _zipGeoTable.CreateIfNotExists();
    }

    public async Task<string?> TryGetPrayerTimesAsync(string country, string zip, string date, int method, DateTimeOffset nowUtc)
    {
        var pk = MakePartitionKey(country, zip);
        var rk = MakePrayerTimesRowKey(date, method);

        try
        {
            var resp = await _prayerTimesTable.GetEntityIfExistsAsync<PrayerTimesCacheEntity>(pk, rk);
            if (!resp.HasValue)
                return null;

            var entity = resp.Value;
            if (nowUtc - entity.FetchedAtUtc > PrayerTimesTtl)
                return null;

            return entity.ResponseJson;
        }
        catch (RequestFailedException ex)
        {
            _logger.LogWarning(ex, "Cache read failed for prayer times: {PartitionKey}/{RowKey}", pk, rk);
            return null;
        }
    }

    public async Task PutPrayerTimesAsync(string country, string zip, string date, int method, string responseJson, DateTimeOffset nowUtc)
    {
        var entity = new PrayerTimesCacheEntity
        {
            PartitionKey = MakePartitionKey(country, zip),
            RowKey = MakePrayerTimesRowKey(date, method),
            FetchedAtUtc = nowUtc,
            ResponseJson = responseJson
        };

        try
        {
            await _prayerTimesTable.UpsertEntityAsync(entity, TableUpdateMode.Replace);
        }
        catch (RequestFailedException ex)
        {
            _logger.LogWarning(ex, "Cache write failed for prayer times: {PartitionKey}/{RowKey}", entity.PartitionKey, entity.RowKey);
        }
    }

    public async Task<LatLong?> TryGetZipLatLongAsync(string country, string zip, DateTimeOffset nowUtc)
    {
        var pk = MakePartitionKey(country, zip);
        const string rk = "v1";

        try
        {
            var resp = await _zipGeoTable.GetEntityIfExistsAsync<ZipGeoCacheEntity>(pk, rk);
            if (!resp.HasValue)
                return null;

            var entity = resp.Value;
            if (nowUtc - entity.FetchedAtUtc > ZipGeoTtl)
                return null;

            return new LatLong(entity.Latitude, entity.Longitude);
        }
        catch (RequestFailedException ex)
        {
            _logger.LogWarning(ex, "Cache read failed for zip geo: {PartitionKey}/{RowKey}", pk, rk);
            return null;
        }
    }

    public async Task PutZipLatLongAsync(string country, string zip, LatLong latLong, DateTimeOffset nowUtc)
    {
        var entity = new ZipGeoCacheEntity
        {
            PartitionKey = MakePartitionKey(country, zip),
            RowKey = "v1",
            FetchedAtUtc = nowUtc,
            Latitude = latLong.Latitude,
            Longitude = latLong.Longitude
        };

        try
        {
            await _zipGeoTable.UpsertEntityAsync(entity, TableUpdateMode.Replace);
        }
        catch (RequestFailedException ex)
        {
            _logger.LogWarning(ex, "Cache write failed for zip geo: {PartitionKey}/{RowKey}", entity.PartitionKey, entity.RowKey);
        }
    }

    private static string MakePartitionKey(string country, string zip)
        => $"{country.Trim().ToLowerInvariant()}:{zip.Trim()}";

    private static string MakePrayerTimesRowKey(string date, int method)
        => $"{date}:{method}";
}

