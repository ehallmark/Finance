package model.trade_policy;

import finance.Portfolio;
import finance.Trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 4/30/2017.
 */
public class MutlipleStockPolicy implements TradePolicy {
    protected Portfolio portfolio;
    protected String[] stocks;
    public MutlipleStockPolicy(Portfolio portfolio, String... stocks) {
        this.portfolio=portfolio;
        this.stocks=stocks;
    }
    @Override
    public List<Trade> getTrades(Map<String,Integer> assignment, int idx) {
        if(idx==0) {
            // buy all shares of dow jones industrial (dji)
            // To buy
            double availableCash = portfolio.getAvailableCash();
            List<Trade> trades = new ArrayList<>();
            if (availableCash > portfolio.getTransactionCost()*stocks.length) {
                for(String stock : stocks) {
                    double stockPrice = portfolio.stockPriceAtTime(stock, idx);
                    double shares = (availableCash-portfolio.getTransactionCost()) / (stockPrice*stocks.length);
                    Trade trade = new Trade(stock, shares);
                    if (portfolio.makeTrade(trade, stockPrice)) {
                        trades.add(trade);
                    }
                }
            }
            return trades;
        } else {
            // sit back
            return Collections.emptyList();
        }
    }
}
