using Azure;
using Azure.Data.Tables;

namespace AdhanFunction.Models;

public sealed class PrayerTimesCacheEntity : ITableEntity
{
    public string PartitionKey { get; set; } = default!;
    public string RowKey { get; set; } = default!;
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public DateTimeOffset FetchedAtUtc { get; set; }
    public string ResponseJson { get; set; } = default!;
}

public sealed class ZipGeoCacheEntity : ITableEntity
{
    public string PartitionKey { get; set; } = default!;
    public string RowKey { get; set; } = default!;
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public DateTimeOffset FetchedAtUtc { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
}

