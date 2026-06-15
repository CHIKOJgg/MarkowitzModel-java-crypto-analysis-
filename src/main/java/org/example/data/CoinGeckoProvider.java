package org.example.data;

import com.google.gson.Gson;
import org.example.Defaults;
import org.example.model.CoinData;
import org.example.util.Config;
import org.ojalgo.matrix.MatrixR064;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Fetches 365-day daily data from the CoinGecko API and caches results
 * in-memory so subsequent calls within the same session are instant.
 */
public class CoinGeckoProvider implements MarketDataProvider {

    private static final String BASE_URL = Defaults.API_BASE_URL;

    private final Gson gson = new Gson();

    /** Shared HTTP client for all requests (avoids per-request allocation). */
    private static final HttpClient SHARED_CLIENT = HttpClient.newHttpClient();

    /** In-memory cache: coinId → CoinData */
    private final Map<String, CoinData> cache = new ConcurrentHashMap<>();

    /** Observable progress for UI (optional) */
    private volatile String lastStatus = "";

    // ── MarketDataProvider ────────────────────────────────────────────────────

    @Override
    public MatrixR064 getReturns(List<String> assets) {
        List<CoinData> coins = fetchAll(assets);
        return buildReturnMatrix(coins, assets);
    }

    // ── Public extras ─────────────────────────────────────────────────────────

    /** Fetch raw CoinData objects (cached). */
    public List<CoinData> fetchAll(List<String> coins) {
        List<CompletableFuture<CoinData>> futures = coins.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> fetchOne(c)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    /** Status message for progress reporting. */
    public String getLastStatus() { return lastStatus; }

    /** Invalidate all cached entries (forces re-fetch). */
    public void clearCache() { cache.clear(); }

    // ── Private ───────────────────────────────────────────────────────────────

    private CoinData fetchOne(String coin) {
        return fetchOne(coin, 0);
    }

    private CoinData fetchOne(String coin, int attempt) {
        if (cache.containsKey(coin)) return cache.get(coin);

        if (attempt >= Defaults.MAX_RETRIES) {
            throw new RuntimeException("Failed to fetch " + coin + " after " + Defaults.MAX_RETRIES + " attempts (rate limited)");
        }

        jitter();
        lastStatus = "Fetching " + coin + " (attempt " + (attempt + 1) + ")...";

        String url = buildUrl(coin);
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> resp = SHARED_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 429) {
                long backoff = Defaults.RETRY_BACKOFF_MS * (attempt + 1);
                TimeUnit.MILLISECONDS.sleep(backoff);
                return fetchOne(coin, attempt + 1);
            }
            if (resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode() + " for " + coin);
            }

            CoinData data = gson.fromJson(resp.body(), CoinData.class);
            data.setCoinName(coin);
            data.setPricesMap();
            data.setReturnMap();

            cache.put(coin, data);
            return data;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch " + coin + ": " + e.getMessage(), e);
        }
    }

    private MatrixR064 buildReturnMatrix(List<CoinData> coins, List<String> order) {
        // Align by asset order given
        Map<String, CoinData> byName = new HashMap<>();
        coins.forEach(c -> byName.put(c.getCoinName(), c));

        int minLen = order.stream()
                .mapToInt(id -> byName.get(id).getReturnMap().get(id).size())
                .min().orElseThrow();

        MatrixR064.DenseReceiver builder =
                MatrixR064.FACTORY.makeDense(minLen, order.size());

        for (int col = 0; col < order.size(); col++) {
            List<Double> rets = byName.get(order.get(col))
                                      .getReturnMap().get(order.get(col));
            for (int row = 0; row < minLen; row++) {
                builder.set(row, col, rets.get(row));
            }
        }
        return builder.get();
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
        try { TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(Defaults.JITTER_MIN_MS, Defaults.JITTER_MAX_MS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
