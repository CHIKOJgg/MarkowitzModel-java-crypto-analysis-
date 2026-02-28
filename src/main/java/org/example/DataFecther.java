package org.example;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DataFecther {
    //private String currency = "bitcoin";

    Gson gson =new Gson();
    public HttpResponse<String> fetchData(String coin){
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0,20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String url = "https://api.coingecko.com/api/v3/coins/" + coin +
                "/market_chart?vs_currency=usd&days=365&interval=daily" +
                "&x_cg_demo_api_key=" + Config.get("api.key");
        try (HttpClient httpClient  = HttpClient.newHttpClient()){
            HttpRequest request  = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response;
        //TODO распарсть и вытянуть нужные данные
        //TODO сложть в array
        //TODO найти изменения

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
 //   public CompletableFuture<CoinData> future = new CompletableFuture<>();
//    public void setFuture(){
//        s
//    }
//     public CompletableFuture<CoinData> coinDataCompletableFuture ( String coin){
//         return CompletableFuture
//                 .supplyAsync(
//                 ()-> parseCoin(fetchData(coin))
//         ).thenAccept(coinData -> coinData.setPrices(coinData.getPrices())
//                 ).
//
//
//     }
    public ArrayList<CoinData> fetchAllCoins( List<String> coinList){
        ArrayList<CompletableFuture<CoinData>> futures = coinList.stream().map(
                coin->CompletableFuture.supplyAsync(()->{
                    HttpResponse<String> response = fetchData(coin);
                    CoinData coinData = parseCoin(response);
                    coinData.setPrices(coinData.getPrices());
                    coinData.setCoinName(coin);
                    coinData.setPricesMap();
                    coinData.setReturnMap();
                    return coinData;
                })

        ).collect(Collectors.toCollection(ArrayList::new));

     List<CoinData> coinDataCollector = futures.stream().map(
             CompletableFuture::join
     ).toList();
       // for (String coin: coinList){
//            HttpResponse<String> response = fetchData(coin);
//            CoinData coinDataByDay =  parseCoin(response);
//
//            coinDataByDay.setPrices(coinDataByDay.getPrices());
//            coinDataByDay.setCoinName(coin);
//            coinDataByDay.setPricesMap();
//            coinDataByDay.setReturnMap();
//            AllCoinData.add(coinDataByDay);
//        }
        return new ArrayList<>(coinDataCollector);
    }


    public CoinData parseCoin(HttpResponse<String> response) {
        return gson.fromJson(response.body(), CoinData.class);
    }


}
