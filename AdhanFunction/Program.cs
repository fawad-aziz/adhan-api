using Azure.Data.Tables;
using AdhanFunction.Services;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services =>
    {
        services.AddHttpClient();

        services.AddSingleton(sp =>
        {
            var conn =
                Environment.GetEnvironmentVariable("CACHE_STORAGE_CONNECTION_STRING")
                ?? Environment.GetEnvironmentVariable("AzureWebJobsStorage");

            if (string.IsNullOrWhiteSpace(conn))
                throw new InvalidOperationException(
                    "Missing storage connection string. Set CACHE_STORAGE_CONNECTION_STRING or AzureWebJobsStorage.");

            return new TableServiceClient(conn);
        });

        services.AddSingleton<TableCache>();
        services.AddSingleton<ZipGeocoder>();
        services.AddSingleton<PrayerTimesService>();
    })
    .Build();

host.Run();

