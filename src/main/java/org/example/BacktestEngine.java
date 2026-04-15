//package org.example;
//
//import org.ojalgo.matrix.MatrixR064;
//
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.example.Main.*;
//
//public class BacktestEngine {
//    public static void runBacktest(MatrixR064 returns) {
//
//        int window = 60;       // обучение
//        int horizon = 7;       // тест
//        double equity = 1.0;   // стартовый капитал
//
//        List<Double> equityCurve = new ArrayList<>();
//    List<BigDecimal> prevWeights = null;
//    double feeRate = 0.001; // 0.1%
//
//        for (int t = window; t < returns.countRows() - horizon; t++) {
//
//            MatrixR064 train = returns.rows(t - window, t);
//            MatrixR064 test = returns.rows(t, t + horizon);
//
//            // === СТРОИМ ПОРТФЕЛЬ ===
//            PortfolioResult result = buildPortfolio(train);
//
//            // === ТЕСТ ===
//            double pnl = simulate(test, result.weights);
//
//            equity *= (1.0 + pnl);
//            equityCurve.add(equity);
//
//            System.out.println("Step " + t + " | Equity: " + equity);
//        }
//
//        printStats(equityCurve);
//    }
//}