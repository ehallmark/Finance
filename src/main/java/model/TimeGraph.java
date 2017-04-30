package model;

import finance.Portfolio;
import finance.TradingAlgorithm;
import lombok.Getter;
import model.functions.heuristic.MinimalCliqueSizeHeuristic;
import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.graphs.CliqueTree;
import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.learning.algorithms.TrainingAlgorithm;
import model.learning.distributions.DirichletCreator;
import model.nodes.FactorNode;
import model.nodes.Node;
import model.trade_policy.InferenceTradePolicy;
import model.trade_policy.MarketTradePolicy;
import model.trade_policy.SingleStockPolicy;
import util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/29/2017.
 */
public class TimeGraph {
    @Getter
    private BayesianNet network;
    @Getter
    private CliqueTree cliqueTree;
    @Getter
    private List<Map<String,Double>> stockToPricesList;
    @Getter
    private List<Map<String,Double>> testStockToPricesList;
    @Getter
    private List<Map<String,Double>> validationStockToPricesList;
    public TimeGraph(File csv) throws IOException{
        this.stockToPricesList=new ArrayList<>();
        this.testStockToPricesList=new ArrayList<>();
        this.validationStockToPricesList=new ArrayList<>();
        this.network=trainCSV(csv);
        network.reNormalize(new DivideByPartition());
        MarkovNet net = network.moralize();

        // Triangulate and create clique tree
        net.triangulateInPlace(new MinimalCliqueSizeHeuristic());
        cliqueTree = net.createCliqueTree();
        cliqueTree.reNormalize(new DivideByPartition());

        System.out.println("Clique Tree: "+cliqueTree.toString());

        cliqueTree.runBeliefPropagation();
        cliqueTree.reNormalize(new DivideByPartition());
        System.out.println("Clique Tree (after BP): "+cliqueTree.toString());
    }

    private BayesianNet trainCSV(File csv) throws IOException {
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
        companies.forEach(company->{
            net.addBinaryNode(company);
            net.addBinaryNode(company+"_future");
        });
        // Connect nodes
        companies.forEach(company->{
            Node n1 = net.findNode(company);
            net.addFactorNode(null,n1); // Unary factor
            companies.forEach(company2->{
                Node n2 = net.findNode(company2+"_future");
                net.connectNodes(n1,n2);
                net.addFactorNode(null,n1,n2);
            });
        });

        System.out.println("Companies: "+String.join(", ",companies));
        // Train
        System.out.println("Network: "+net.toString());

        double[] pricesTwoPeriodsAgo = getPricesFromRow(reader.readLine());
        double[] pricesLastPeriod = getPricesFromRow(reader.readLine());
        double[] pricesThisPeriod = null;

        String line = reader.readLine();
        List<Map<String,int[]>> trainingSet;
        List<Map<String,int[]>> validationSet;
        List<Map<String,int[]>> testSet;
        {
            List<Map<String, int[]>> allAssignments = new ArrayList<>();
            int i = 0;
            while (line != null) {
                pricesThisPeriod = getPricesFromRow(line);

                Map<String, int[]> assignment = createAssignment(pricesTwoPeriodsAgo, pricesLastPeriod, pricesThisPeriod, companies);
                Map<String, Double> prices = createPricesMap(pricesThisPeriod, companies);

                allAssignments.add(assignment);
                stockToPricesList.add(prices);

                // set prices as next prices
                pricesTwoPeriodsAgo = pricesLastPeriod;
                pricesLastPeriod = pricesThisPeriod;
                line = reader.readLine();
                System.out.println("Finished data point: " + i);
                i++;
            }
            trainingSet=allAssignments.subList(0,(3*allAssignments.size())/5);
            validationSet=allAssignments.subList((3*allAssignments.size())/5,(4*allAssignments.size())/5);
            validationStockToPricesList.addAll(stockToPricesList.subList((3*allAssignments.size())/5,(4*allAssignments.size())/5));
            testSet=allAssignments.subList((4*allAssignments.size())/5,allAssignments.size());
            testStockToPricesList.addAll(stockToPricesList.subList((4*allAssignments.size())/5,allAssignments.size()));
            stockToPricesList.removeAll(new ArrayList<>(stockToPricesList.subList((3*allAssignments.size())/5,allAssignments.size())));
        }
        reader.close();


        System.out.println("Training Size: "+trainingSet.size());
        System.out.println("Validation Size: "+validationSet.size());
        System.out.println("Test Size: "+testSet.size());

        net.setTrainingData(trainingSet);
        net.setTestData(testSet);
        net.setValidationData(validationSet);
        net.applyLearningAlgorithm(new TrainingAlgorithm(new DirichletCreator(30f),1),1);

        return net;
    }

    static Map<String,Double> createPricesMap(double[] prices, List<String> companies) {
        Map<String,Double> map = new HashMap<>();
        for(int i = 0; i < companies.size(); i++) {
            map.put(companies.get(i),prices[i]);
        }
        return map;
    }

    static Map<String,int[]> createAssignment(double[] twoPeriodsAgo, double[] lastPeriod, double[] thisPeriod, List<String> companies) {
        Map<String,int[]> assignment = new HashMap<>();
        for(int i = 0; i < companies.size(); i++) {
            String company = companies.get(i);
            boolean wentUpThisPeriod = thisPeriod[i]-lastPeriod[i]>0;
            int[] valThisPeriod;
            if(wentUpThisPeriod) {
                valThisPeriod=new int[]{1};
            } else {
                valThisPeriod=new int[]{0};
            }
            assignment.put(company+"_future",valThisPeriod);

            boolean wentUpLastPeriod = lastPeriod[i]-twoPeriodsAgo[i]>0;
            int[] valLastPeriod;
            if(wentUpLastPeriod) {
                valLastPeriod=new int[]{1};
            } else {
                valLastPeriod=new int[]{0};
            }
            assignment.put(company,valLastPeriod);
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
        File file = new File("sample_stock_output_small.csv");
        TimeGraph timeGraph = new TimeGraph(file);

        double startingCash = 50000d;
        double transactionCost = 1d;
        Portfolio portfolio = new Portfolio(timeGraph.getTestStockToPricesList(),startingCash,transactionCost);
        Portfolio portfolioCopy = new Portfolio(timeGraph.getTestStockToPricesList(),startingCash,transactionCost);
        Portfolio portfolioApple = new Portfolio(timeGraph.getTestStockToPricesList(),startingCash,transactionCost);
        double avgRateOfReturn = portfolio.determineTrades(new TimeGraphAlgorithm(timeGraph,timeGraph.getNetwork().getTestData()), new InferenceTradePolicy(timeGraph.getNetwork(),timeGraph.getCliqueTree(),portfolio));
        double avgRateOfReturnMarket = portfolioCopy.determineTrades(new TimeGraphAlgorithm(timeGraph,timeGraph.getNetwork().getTestData()), new MarketTradePolicy(portfolioCopy));
        double avgRateOfReturnApple = portfolioApple.determineTrades(new TimeGraphAlgorithm(timeGraph,timeGraph.getNetwork().getTestData()), new SingleStockPolicy("aapl",portfolioApple));

        NumberFormat formatter = new DecimalFormat("#0.00");
        System.out.println("Average Return **My Model**: "+ formatter.format(avgRateOfReturn*100)+"%");
        System.out.println("Average Return Market: "+ formatter.format(avgRateOfReturnMarket*100)+"%");
        System.out.println("Average Return Apple: "+ formatter.format(avgRateOfReturnApple*100)+"%");
    }
}
