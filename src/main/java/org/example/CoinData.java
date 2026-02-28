package org.example;

import java.io.DataOutput;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class CoinData{
        private String coinName;
        private List<List<Double>> prices;
        private Map<String, List<Double>> pricesMap = new HashMap<>();
        private Map<String, List<Double>> returnMap = new HashMap<>();
        public void setPrices(List<List<Double>> prices) {
                this.prices = prices;
        }

        public Map<String, List<Double>> getPricesMap() {
                return pricesMap;

        }

        public String getCoinName() {
                return coinName;
        }

        public void setCoinName(String coinName) {
                this.coinName = coinName;
        }

        public void setPricesMap() {
                pricesMap.put(this.getCoinName(), this.getClosePrices());
        }
        public Double returnByDay(Double price1,Double price2){
                return  (price1-price2)/price2;
        }

        public void setReturnMap() {
                List<Double> currentPrices = pricesMap.get(coinName);
                returnMap.put(coinName, new ArrayList<>());
               for (int i = 1; i<pricesMap.get(coinName).size(); i++){
                       Double todayPrice  = currentPrices.get(i);
                       Double yesterdayPrice = currentPrices.get(i-1);
                       Double returnVal = (todayPrice - yesterdayPrice) / yesterdayPrice;
                       returnMap.get(coinName).add(returnVal);
               }

        }

        public Map<String, List<Double>> getReturnMap() {
                return returnMap;

        }

        public List<List<Double>> getPrices() {
                return prices;
        }

        public List<Double> getClosePrices() {
                return prices.stream()
                        .mapToDouble(p -> p.get(1))
                        .boxed()
                        .toList(); // берем только саму цену;
        }
}
