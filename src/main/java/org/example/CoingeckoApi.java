package org.example;

public enum CoingeckoApi {
    COINGECKO_API ("CG-7oycq4NxPYEmVt1V6mZdNDTJ");
    private final String  coingeckoApiKey;
    CoingeckoApi(String s) {
        this.coingeckoApiKey = s;
    }

    public String getCoingeckoApiKey() {
        return coingeckoApiKey;
    }
}
