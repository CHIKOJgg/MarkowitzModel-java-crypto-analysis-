package org.example;


import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class FileWriterOutput {
    public void writeOutputData(List<String> coins,List<BigDecimal> weights){

        try(FileWriter writer = new FileWriter("modelOutput.txt", true))
        {
          for (String coin:coins){
              writer.write(coin);
              writer.write(" ");
          }
            writer.write("\n");
          for (int i = 0; i < weights.size(); i++) {
              BigDecimal percent = weights.get(i)
                      .multiply(new BigDecimal("100"))
                      .setScale(2, RoundingMode.HALF_UP);
             writer.write(coins.get(i).toUpperCase() + ": " + percent + "%");
             writer.write("\n");
          }

        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

}
