package model.trade_policy;

import finance.Portfolio;
import finance.Trade;
import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.graphs.CliqueTree;
import model.graphs.Graph;
import model.nodes.FactorNode;
import util.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/30/2017.
 */
public class InferenceTradePolicy implements TradePolicy {
    protected BayesianNet originalNet;
    protected CliqueTree cliqueTree;
    protected Portfolio portfolio;
    public InferenceTradePolicy(BayesianNet originalNet, CliqueTree cliqueTree, Portfolio portfolio) {
        this.originalNet = originalNet;
        this.portfolio=portfolio;
        this.cliqueTree = cliqueTree;
    }
    @Override
    public List<Trade> getTrades(Map<String,int[]> assignment, int idx) {
        cliqueTree.setCurrentAssignment(assignment.entrySet().stream().map(e->{
            if(!e.getKey().endsWith("future")) return new Pair<>(e.getKey(),e.getValue()[0]);
            else return null;
        }).filter(a->a!=null).collect(Collectors.toList()));
        List<Pair<String,Double>> all = cliqueTree.getFactorNodes().stream().map(factor->{
            Optional<String> option = Arrays.stream(factor.getVarLabels()).filter(label->label.endsWith("future")).findFirst();
            if(!option.isPresent()) return null;
            List<String> labelsToSum = Arrays.stream(factor.getVarLabels()).filter(label->!label.endsWith("future")).collect(Collectors.toList());
            FactorNode f = factor;
            for(String label : labelsToSum) {
                f=f.multiply(Graph.givenValueFactor(originalNet.findNode(label),assignment.get(label)[0]));
            }
            FactorNode result = f.sumOut(labelsToSum.toArray(new String[labelsToSum.size()]));
            result.reNormalize(new DivideByPartition());
            double prediction = result.getWeights()[1];
            return new Pair<>(option.get(),prediction);
        }).filter(p->p!=null).map(pair->new Pair<>(pair._1.replace("_future",""),pair._2)).sorted((p1,p2)->p2._2.compareTo(p1._2)).collect(Collectors.toList());

        List<Trade> trades = new ArrayList<>();
        // To buy
        double availableCash = portfolio.getAvailableCash();
        if(availableCash> portfolio.getTransactionCost()) {
            all.subList(0, all.size() / 2).forEach(pair -> {
                double stockPrice = portfolio.stockPriceAtTime(pair._1, idx);
                double shares = (pair._2 * availableCash) / (stockPrice * all.size());
                Trade trade = new Trade(pair._1, shares);
                if(portfolio.makeTrade(trade, stockPrice)) {
                    trades.add(trade);
                }
            });
            // To sell
            all.subList(all.size() / 2, all.size()).forEach(pair -> {
                Double currentShare = portfolio.getLastProfile().get(pair._1);
                if (currentShare != null && currentShare > 0) {
                    double stockPrice = portfolio.stockPriceAtTime(pair._1, idx);
                    double shares = -(1d - pair._2) * currentShare;
                    Trade trade = new Trade(pair._1, shares);
                    if(portfolio.makeTrade(trade, stockPrice)) {
                        trades.add(trade);
                    }
                }
            });
        }
        return trades;
    }
}
