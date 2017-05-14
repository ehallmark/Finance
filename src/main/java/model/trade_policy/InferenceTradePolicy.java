package model.trade_policy;

import finance.Portfolio;
import finance.Trade;
import model.functions.inference_methods.BeliefPropagation;
import model.functions.inference_methods.SamplingMethod;
import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.graphs.CliqueTree;
import model.graphs.Graph;
import model.graphs.MetropolisHastingsChain;
import model.learning.algorithms.LearningAlgorithm;
import model.learning.algorithms.MarkovLearningAlgorithm;
import model.nodes.FactorNode;
import util.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/30/2017.
 */
public class InferenceTradePolicy implements TradePolicy {
    protected Graph originalNet;
    protected Portfolio portfolio;
    //protected MetropolisHastingsChain chain;
    protected CliqueTree cliqueTree;
    protected int burnIn = 20;
    protected int epochs = 1;
    protected double buyPercent = 1d;
    protected double sellPercent = 1d;
    protected List<Map<String,Integer>> assignmentList;
    protected LearningAlgorithm learningAlgorithm;
    protected double stopLoss;
    protected double takeProfit;
    public InferenceTradePolicy(Graph originalNet, Portfolio portfolio,LearningAlgorithm learningAlgorithm, double stopLoss, double takeProfit) {
        this.originalNet = originalNet;
        this.learningAlgorithm=learningAlgorithm;
        this.portfolio=portfolio;
        this.takeProfit=takeProfit;
        this.stopLoss=stopLoss;
        this.assignmentList=new ArrayList<>();
        //chain = new MetropolisHastingsChain(originalNet,new HashMap<>());
        //for(int i = 0; i < burnIn*100 && chain.hasNext(); i++) chain.next();
    }
    @Override
    public List<Trade> getTrades(Map<String,Integer> assignment, int idx) {
        Map<String,Integer> currentAssignments = assignment.entrySet().stream().filter(e->!e.getKey().endsWith("_future")).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        // train
        assignmentList.add(assignment);
        if(assignmentList.size()>7)assignmentList=assignmentList.subList(assignmentList.size()-7,assignmentList.size());
        originalNet.setTrainingData(assignmentList);
        originalNet.applyLearningAlgorithm(learningAlgorithm,epochs);

        //originalNet.setCurrentAssignment(currentAssignments);
        //chain.setPermanentAssignments(assignment);
        //for(int i = 0; i < burnIn; i++) chain.next();
        //Map<String,FactorNode> nextFactors = chain.next();
        cliqueTree=originalNet.createCliqueTree();
        cliqueTree.setCurrentAssignment(currentAssignments);
        Map<String,FactorNode> nextFactors = cliqueTree.runBeliefPropagation(originalNet.getAllNodesList().stream().map(n->n.getLabel()).filter(label->label.endsWith("_future")).collect(Collectors.toList()));

        List<Pair<String,Double>> all = nextFactors.entrySet().stream().filter(e->e.getKey().endsWith("_future")).map(e->{
            return new Pair<>(e.getKey().replace("_future",""),e.getValue().getWeights()[1]);
        }).sorted((p1,p2)->p2._2.compareTo(p1._2)).collect(Collectors.toList());

        List<Trade> trades = new ArrayList<>();
        // To buy
        double availableCash = portfolio.getAvailableCash();
        if(availableCash> portfolio.getTransactionCost()) {
            all.subList(0, all.size()/2).forEach(pair -> {
                double stockPrice = portfolio.stockPriceAtTime(pair._1, idx);
                double shares = ((pair._2 * (availableCash-portfolio.getTransactionCost()) / (stockPrice))) * buyPercent;
                Trade trade = new Trade(pair._1, shares);
                if (portfolio.makeTrade(trade, stockPrice)) {
                    trades.add(trade);
                }
            });
        }
        // To sell
        all.subList(all.size()/2, all.size()).forEach(pair -> {
            Double currentShare = portfolio.getLastProfile().get(pair._1);
            if (currentShare != null && currentShare > 0) {
                double stockPrice = portfolio.stockPriceAtTime(pair._1, idx);
                if(idx>0) {
                    double lastPrice = portfolio.stockPriceAtTime(pair._1, idx - 1);
                    // stop loss
                    boolean shouldSell = stockPrice + stopLoss <= lastPrice ||
                    stockPrice - takeProfit >= lastPrice;
                    if (shouldSell) {
                        double shares = -(1d - pair._2) * currentShare * sellPercent;
                        if(shares*(stockPrice-lastPrice)>portfolio.getTransactionCost()||availableCash>portfolio.getTransactionCost()) {
                            Trade trade = new Trade(pair._1, shares);
                            if (portfolio.makeTrade(trade, stockPrice)) {
                                trades.add(trade);
                            }
                        }
                    }
                }
            }
        });
        return trades;
    }
}
