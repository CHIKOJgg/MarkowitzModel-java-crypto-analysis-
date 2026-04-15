package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds raw market data fetched from CoinGecko for a single coin.
 */
public class CoinData {

    private String coinName;

    // Deserialized by Gson from JSON response
    private List<List<Double>> prices;
    private List<List<Double>> total_volumes;
    private List<List<Double>> market_caps;

    private final Map<String, List<Double>> pricesMap = new HashMap<>();
    private final Map<String, List<Double>> returnMap  = new HashMap<>();

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getCoinName() { return coinName; }
    public void   setCoinName(String name) { this.coinName = name; }

    public List<List<Double>> getPrices()           { return prices; }
    public void setPrices(List<List<Double>> prices) { this.prices = prices; }

    public List<List<Double>> getMarketCap()               { return market_caps; }
    public void setMarketCap(List<List<Double>> marketCap) { this.market_caps = marketCap; }

    public List<List<Double>> getVolume()              { return total_volumes; }
    public void setVolume(List<List<Double>> volume)   { this.total_volumes = volume; }

    public Map<String, List<Double>> getPricesMap() { return pricesMap; }
    public Map<String, List<Double>> getReturnMap()  { return returnMap;  }

    // ── Derived data ─────────────────────────────────────────────────────────

    /** Extract closing prices (second element of each [timestamp, price] pair). */
    public List<Double> getClosePrices() {
        return prices.stream()
                .map(p -> p.get(1))
                .toList();
    }

    /** Populate pricesMap with { coinName -> closePrices }. Call after setCoinName + setPrices. */
    public void setPricesMap() {
        pricesMap.put(coinName, getClosePrices());
    }

    /** Populate returnMap with daily log-returns. Call after setPricesMap. */
    public void setReturnMap() {
        List<Double> px = pricesMap.get(coinName);
        List<Double> returns = new ArrayList<>(px.size() - 1);

        for (int i = 1; i < px.size(); i++) {
            returns.add((px.get(i) - px.get(i - 1)) / px.get(i - 1));
        }
        returnMap.put(coinName, returns);
    }
}
