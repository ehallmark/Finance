package model;

import com.google.common.util.concurrent.AtomicDouble;
import finance.Portfolio;
import finance.Trade;
import finance.TradingAlgorithm;
import model.functions.normalization.DivideByPartition;
import model.graphs.CliqueTree;
import model.graphs.Graph;
import model.nodes.FactorNode;
import model.trade_policy.TradePolicy;
import util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/30/2017.
 */
public class TimeGraphAlgorithm implements TradingAlgorithm {
    private TimeGraph graph;
    private Collection<Map<String,int[]>> assignmentsOverTime;
    public TimeGraphAlgorithm(TimeGraph graph, Collection<Map<String,int[]>> assignmentsOverTime) {
        this.graph=graph;
        this.assignmentsOverTime=assignmentsOverTime;
    }
    @Override
    public Function<Portfolio, Double> getFunction(TradePolicy tradePolicy) {
        return (portfolio)->{
            AtomicInteger counter = new AtomicInteger(0);
            AtomicDouble totalRate = new AtomicDouble(0d);
            assignmentsOverTime.forEach(assignment->{
                portfolio.setPreviouslyAvailableCash(portfolio.getAvailableCash());
                System.out.println("Cash: "+(portfolio.getAvailableCash()+portfolio.computeMoney(portfolio.getLastProfile(),counter.get())));

                int idx = counter.getAndIncrement();
                List<Trade> trades = tradePolicy.getTrades(assignment,idx);

                // Update current amounts
                for(Trade tradeAtTimeT : trades) {
                    double newAmount = tradeAtTimeT.getAmount();
                    if(portfolio.getLastProfile().containsKey(tradeAtTimeT.getStock())) {
                        double currentAmount = portfolio.getLastProfile().get(tradeAtTimeT.getStock());
                        newAmount = currentAmount+newAmount;
                        if(newAmount<0) throw new RuntimeException("Negative new amount!");
                    }
                    portfolio.getCurrentProfile().put(tradeAtTimeT.getStock(),newAmount);
                    System.out.println("Current amount of stock "+tradeAtTimeT.getStock()+": "+newAmount);
                }
                // calculate current time step
                if(!portfolio.getLastProfile().isEmpty()) {
                    // add leftovers to current
                    portfolio.getLastProfile().forEach((k,v)->{
                        if(!portfolio.getCurrentProfile().containsKey(k)) {
                            portfolio.getCurrentProfile().put(k,v);
                        }
                    });

                    totalRate.getAndAdd(portfolio.computeRateOfReturnBetweenTimeSteps(idx-1,idx));
                }

                portfolio.getLastProfile().clear();
                portfolio.getLastProfile().putAll(portfolio.getCurrentProfile());
                portfolio.getCurrentProfile().clear();
                portfolio.getTradesOverTime().add(trades);

                if(counter.get()>0) {
                    System.out.println("Current Rate: "+totalRate.get()/counter.get());
                } else throw new RuntimeException("Error occured");
            });

            return (portfolio.getAvailableCash()+portfolio.computeMoney(portfolio.getLastProfile(),assignmentsOverTime.size()-1)-portfolio.getStartingCash())/portfolio.getStartingCash();
        };
    }
}
