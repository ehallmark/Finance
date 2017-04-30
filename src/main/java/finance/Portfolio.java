package finance;

import lombok.Getter;
import lombok.Setter;
import model.trade_policy.TradePolicy;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/30/2017.
 */
public class Portfolio {
    protected List<Map<String,Double>> stockToPricesList;
    @Getter
    protected double transactionCost;

    @Getter
    protected Map<String,Double> currentProfile;
    @Getter
    protected Map<String,Double> lastProfile;

    @Getter @Setter
    protected double availableCash;

    @Setter @Getter
    protected double previouslyAvailableCash;

    @Getter
    protected double rateOfReturn;

    @Getter
    protected final double startingCash;

    @Getter @Setter
    protected List<List<Trade>> tradesOverTime;

    public Portfolio(List<Map<String,Double>> stockToPricesList, double startingCash, double transactionCost) {
        this.availableCash=startingCash;
        this.startingCash=startingCash;
        this.previouslyAvailableCash=startingCash;
        this.stockToPricesList=stockToPricesList;
        this.currentProfile=new HashMap<>();
        this.lastProfile=new HashMap<>();
        this.tradesOverTime=new ArrayList<>();
        this.transactionCost = transactionCost;
    }

    public boolean makeTrade(Trade trade, double stockPrice) {
        double newAmount = trade.getAmount();
        if(!(newAmount>0&&stockPrice*newAmount+transactionCost>availableCash)) {
            availableCash-=newAmount*stockPrice+transactionCost;
            return true;
        }
        return false;
    }

    public double computeRateOfReturnBetweenTimeSteps(int lastIdx, int currentIdx) {
        double moneyInTime1 = computeMoney(lastProfile,lastIdx)+previouslyAvailableCash;
        double moneyInTime2 = computeMoney(currentProfile,currentIdx)+availableCash;
        if(moneyInTime1>0) return (moneyInTime2-moneyInTime1)/moneyInTime1;
        else return 0d;
    }

    public double computeMoney(Map<String,Double> map, int idx) {
        return map.entrySet().stream().collect(Collectors.summingDouble(e->{
            return stockToPricesList.get(idx).get(e.getKey())*e.getValue();
        }));
    }

    public double stockPriceAtTime(String stock, int idx) {
        return stockToPricesList.get(idx).get(stock);
    }

    public double determineTrades(TradingAlgorithm algorithm, TradePolicy tradePolicy) {
        rateOfReturn=algorithm.getFunction(tradePolicy).apply(this);
        return rateOfReturn;
    }

}
