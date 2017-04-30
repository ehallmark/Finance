package finance;

import model.trade_policy.TradePolicy;

import java.util.function.Function;

/**
 * Created by Evan on 4/30/2017.
 */
public interface TradingAlgorithm {
    Function<Portfolio,Double> getFunction(TradePolicy tradePolicy);
}
