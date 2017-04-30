package model.trade_policy;

import finance.Portfolio;

/**
 * Created by Evan on 4/30/2017.
 */
public class MarketTradePolicy extends SingleStockPolicy {
   public MarketTradePolicy(Portfolio portfolio) {
       super("dji",portfolio);
   }
}
