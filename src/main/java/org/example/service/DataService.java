package org.example.service;

import com.google.gson.Gson;
import org.example.model.CoinData;
import org.example.util.Config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Fetches 365-day daily OHLCV data from the CoinGecko public / demo API.
 * Requests are dispatched concurrently via CompletableFuture.
 */
public class DataService {
    private final CacheService cache = new CacheService();
    private static final String BASE_URL =
            "https://api.coingecko.com/api/v3/coins/%s/market_chart?vs_currency=usd&days=365&interval=daily";

    private final Gson gson = new Gson();

    /**
     * Fetch data for all coins in parallel.
     *
     * @param coins List of CoinGecko coin IDs (e.g. "bitcoin", "ethereum")
     * @return List of fully populated {@link CoinData} objects
     */
    public List<CoinData> fetchAll(List<String> coins) {
        List<CompletableFuture<CoinData>> futures = coins.stream()
                .map(coin -> CompletableFuture.supplyAsync(() -> fetchOne(coin)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private CoinData fetchOne(String coin) {

        String cacheKey = coin + "_365_daily";

        // ✅ 1. пробуем кеш
        String cached = cache.get(cacheKey);

        if (cached != null) {
            CoinData data = gson.fromJson(cached, CoinData.class);
            data.setCoinName(coin);
            data.setPricesMap();
            data.setReturnMap();
            return data;
        }

        // ❗ 2. если нет кеша → API
        jitter();

        String url = buildUrl(coin);

        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            HttpResponse<String> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode());
            }

            String body = resp.body();



            cache.put(cacheKey, body);

            CoinData data = gson.fromJson(body, CoinData.class);
            data.setCoinName(coin);
            data.setPricesMap();
            data.setReturnMap();

            return data;

        } catch (Exception e) {
            throw new RuntimeException("Fetch failed: " + coin, e);
        }
    }

    private String buildUrl(String coin) {
        String url = String.format(BASE_URL, coin);
        String key = Config.get("api.key");
        if (key != null && !key.isBlank() && !key.equals("YOUR_API_KEY_HERE")) {
            url += "&x_cg_demo_api_key=" + key;
        }
        return url;
    }

    private static void jitter() {
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(50, 250));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
