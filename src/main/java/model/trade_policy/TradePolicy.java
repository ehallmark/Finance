package model.trade_policy;

import finance.Trade;

import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 4/30/2017.
 */
public interface TradePolicy {
    List<Trade> getTrades(Map<String,Integer> assignment, int idx);
}
