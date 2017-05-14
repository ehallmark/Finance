package model;

import finance.Portfolio;
import lombok.Getter;
import model.functions.inference_methods.BeliefPropagation;
import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.learning.algorithms.LearningAlgorithm;
import model.learning.algorithms.MarkovLearningAlgorithm;
import model.nodes.Node;
import model.trade_policy.InferenceTradePolicy;
import model.trade_policy.MarketTradePolicy;
import model.trade_policy.SingleStockPolicy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Created by Evan on 4/29/2017.
 */
public class TimeGraph {
    @Getter
    private volatile Graph network;
    @Getter
    private List<Map<String,Double>> stockToPricesList;
    @Getter
    private List<Map<String,Double>> testStockToPricesList;
    @Getter
    private List<Map<String,Double>> validationStockToPricesList;
    @Getter
    protected LearningAlgorithm learningAlgorithm;
    protected double alpha;
    public TimeGraph(File csv, int numLayers, double alpha) throws IOException{
        this.stockToPricesList=new ArrayList<>();
        this.testStockToPricesList=new ArrayList<>();
        this.alpha=alpha;
        this.validationStockToPricesList=new ArrayList<>();
        this.network=trainCSV(csv,numLayers);
        network.reNormalize(new DivideByPartition());
    }

    private Graph trainCSV(File csv, int numLayers) throws IOException {
        if(numLayers<2) throw new RuntimeException("Num layers must be at least 2");
        BufferedReader reader = new BufferedReader(new FileReader(csv));
        List<String> companies = new ArrayList<>();
        // build net
        BayesianNet net = new BayesianNet();
        String[] headers = reader.readLine().split(",");
        // setup labels
        for(int i = 1; i < headers.length; i++) {
            // skip over date cell
            String[] split = headers[i].split("_");
            companies.add(split[1]);
        }
        // Add nodes
        companies.stream().forEach(company->{
            for(int i = 1; i < numLayers; i++) {
                net.addBinaryNode(company+"_"+i);
            }
            net.addBinaryNode(company+"_future");
        });
        // Connect nodes
        companies.stream().forEach(company->{
            for(int i = 1; i < numLayers; i++) {
                Node n1 = net.findNode(company+"_"+i);
                if(n1==null){
                    throw new RuntimeException("Cannot find n1");
                }
                if(i==1)net.addFactorNode(null, n1); // Unary factor
                Node n1NextStep = net.findNode(company+"_"+(i+1));
                if(n1NextStep!=null) {
                    net.connectNodes(n1, n1NextStep);
                    net.addFactorNode(null, n1, n1NextStep);
                } else {
                    companies.forEach(company2 -> {
                        Node n2 = net.findNode(company2 + "_future");
                        if(n2==null){
                            throw new RuntimeException("Cannot find n2");
                        }
                        net.connectNodes(n1, n2);
                        net.addFactorNode(null, n1, n2);
                    });
                }
            }
        });

        System.out.println("Companies: "+String.join(", ",companies));
        // Train
        System.out.println("Network: "+net.toString());

        double[][] periodsAgo = new double[numLayers][];
        for(int i = 0; i < numLayers; i++) {
            periodsAgo[i] = getPricesFromRow(reader.readLine());
        }
        double[] pricesThisPeriod;
        String line = reader.readLine();
        List<Map<String,Integer>> trainingSet;
        List<Map<String,Integer>> validationSet;
        List<Map<String,Integer>> testSet;
        {
            List<Map<String, Integer>> allAssignments = new ArrayList<>();
            int i = 0;
            while (line != null) {
                pricesThisPeriod = getPricesFromRow(line);

                Map<String, Integer> assignment = createAssignment(periodsAgo, pricesThisPeriod, companies);
                Map<String, Double> prices = createPricesMap(pricesThisPeriod, companies);

                allAssignments.add(assignment);
                stockToPricesList.add(prices);

                // set prices as next prices
                for(int j = 1; j < numLayers; j++) {
                    periodsAgo[j-1] = periodsAgo[j];
                }
                periodsAgo[periodsAgo.length-1] = pricesThisPeriod;
                line = reader.readLine();
                System.out.println("Finished data point: " + i);
                i++;
            }
            trainingSet=allAssignments.subList(0,(allAssignments.size())/2);
            validationSet=allAssignments.subList((allAssignments.size())/2,(3*allAssignments.size())/4);
            validationStockToPricesList.addAll(stockToPricesList.subList((allAssignments.size())/2,(3*allAssignments.size())/4));
            testSet=allAssignments.subList((3*allAssignments.size())/4,allAssignments.size());
            testStockToPricesList.addAll(stockToPricesList.subList((3*allAssignments.size())/4,allAssignments.size()));
            stockToPricesList.removeAll(new ArrayList<>(stockToPricesList.subList((allAssignments.size())/2,allAssignments.size())));
        }
        reader.close();


        System.out.println("Training Size: "+trainingSet.size());
        System.out.println("Validation Size: "+validationSet.size());
        System.out.println("Test Size: "+testSet.size());

        net.setTrainingData(trainingSet);
        net.setTestData(testSet);
        net.setValidationData(validationSet);
        learningAlgorithm=new BayesianLearningAlgorithm(net,alpha);
        net.applyLearningAlgorithm(learningAlgorithm,2);

        return net;
    }

    static Map<String,Double> createPricesMap(double[] prices, List<String> companies) {
        Map<String,Double> map = new HashMap<>();
        for(int i = 0; i < companies.size(); i++) {
            map.put(companies.get(i),prices[i]);
        }
        return map;
    }

    static Map<String,Integer> createAssignment(double[][] periodsAgo, double[] thisPeriod, List<String> companies) {
        Map<String,Integer> assignment = new HashMap<>();
        for(int i = 0; i < companies.size(); i++) {
            String company = companies.get(i);
            double[] lastPeriod = periodsAgo[periodsAgo.length-1];
            boolean wentUpThisPeriod = thisPeriod[i]-lastPeriod[i]>0;
            int valThisPeriod;
            if(wentUpThisPeriod) {
                valThisPeriod=1;
            } else {
                valThisPeriod=0;
            }
            assignment.put(company+"_future",valThisPeriod);

            for(int j = periodsAgo.length-1; j >0; j--) {
                boolean wentUpLastPeriod = periodsAgo[j][i] - periodsAgo[j-1][i] > 0;
                int valLastPeriod;
                if(wentUpLastPeriod) {
                    valLastPeriod=1;
                } else {
                    valLastPeriod=0;
                }
                assignment.put(company+"_"+j, valLastPeriod);
            }
        }
        return assignment;
    };

    static double[] getPricesFromRow(String line) {
        String[] cells = line.split(",");
        double[] prices = new double[cells.length-1];
        for(int c = 1; c < cells.length; c++) {
            prices[c-1]=Float.valueOf(cells[c]);
        }
        return prices;
    }

    public static void main(String[] args) throws Exception {
        NumberFormat formatter = new DecimalFormat("#0.00");
        File file = new File("sample_stock_output_small.csv");
        double startingCash = 100000d;
        double transactionCost = 1d;
        int numLayers = 10;
        double stopLoss = 1111110d;
        double takeProfit =1111110d;

        double bestReturn = Double.MIN_VALUE;
        int bestLayerSize = -1;
        double bestAlpha = 0d;
        for(int i = 4; i < numLayers; i+=2) {
            for(double alpha = 2d; alpha < 6d; alpha+=5) {
                TimeGraph timeGraph = new TimeGraph(file, i, alpha);
                Portfolio portfolio = new Portfolio(timeGraph.getTestStockToPricesList(), startingCash, transactionCost);
                double avgRateOfReturn = portfolio.determineTrades(new TimeGraphAlgorithm(timeGraph.getNetwork().getTestData()), new InferenceTradePolicy(timeGraph.getNetwork(), portfolio, timeGraph.getLearningAlgorithm(),stopLoss,takeProfit));
                if (avgRateOfReturn > bestReturn) {
                    bestReturn = avgRateOfReturn;
                    bestLayerSize = i;
                    bestAlpha=alpha;
                }
                System.out.println("Average Return **My Model [numLayers=" + i + ", alpha="+alpha+"]**: " + formatter.format(avgRateOfReturn * 100) + "%");
            }

        }

        TimeGraph timeGraph = new TimeGraph(file,2,20);

        Portfolio portfolioCopy = new Portfolio(timeGraph.getTestStockToPricesList(),startingCash,transactionCost);
        Portfolio portfolioApple = new Portfolio(timeGraph.getTestStockToPricesList(),startingCash,transactionCost);
        double avgRateOfReturnMarket = portfolioCopy.determineTrades(new TimeGraphAlgorithm(timeGraph.getNetwork().getTestData()), new MarketTradePolicy(portfolioCopy));
        double avgRateOfReturnApple = portfolioApple.determineTrades(new TimeGraphAlgorithm(timeGraph.getNetwork().getTestData()), new SingleStockPolicy("kr",portfolioApple));

        System.out.println("Average Return Market (DJI): "+ formatter.format(avgRateOfReturnMarket*100)+"%");
        System.out.println("Average Return Apple: "+ formatter.format(avgRateOfReturnApple*100)+"%");
        System.out.println("Best Layer Size **MY MODEL**: "+bestLayerSize);
        System.out.println("Best Alpha **MY MODEL**: "+bestAlpha);
        System.out.println("    Return: "+formatter.format(bestReturn*100)+"%");
    }
}
