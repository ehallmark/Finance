package model;

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
import util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/29/2017.
 */
public class TimeGraph {
    public static BayesianNet trainCSV(File csv) throws IOException {
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
        List<Map<String,int[]>> trainingSet = new ArrayList<>();
        List<Map<String,int[]>> validationSet = new ArrayList<>();
        List<Map<String,int[]>> testSet = new ArrayList<>();
        Random rand = new Random(69);
        int i = 0;
        while(line!=null) {
            pricesThisPeriod=getPricesFromRow(line);

            Map<String,int[]> assignment = createAssignment(pricesTwoPeriodsAgo,pricesLastPeriod,pricesThisPeriod,companies);

            double randomNumber = rand.nextDouble();
            if(randomNumber<=0.15) {
                testSet.add(assignment);
            } else {
                if(randomNumber <= 0.3) {
                    validationSet.add(assignment);
                } else {
                    trainingSet.add(assignment);
                }
            }

            // set prices as next prices
            pricesTwoPeriodsAgo=pricesLastPeriod;
            pricesLastPeriod=pricesThisPeriod;
            line=reader.readLine();
            System.out.println("Finished data point: "+i);
            i++;
        }
        reader.close();


        System.out.println("Training Size: "+trainingSet.size());
        System.out.println("Validation Size: "+validationSet.size());
        System.out.println("Test Size: "+testSet.size());

        net.setTrainingData(trainingSet);
        net.setTestData(testSet);
        net.setValidationData(validationSet);
        net.applyLearningAlgorithm(new TrainingAlgorithm(new DirichletCreator(5f),1),1);

        return net;
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
        BayesianNet bayesianNet = trainCSV(new File("sample_stock_output_small.csv"));
        bayesianNet.reNormalize(new DivideByPartition());

        Collection<Map<String,int[]>> tests = bayesianNet.getTestData();

        MarkovNet net = bayesianNet.moralize();

        // Triangulate and create clique tree
        net.triangulateInPlace(new MinimalCliqueSizeHeuristic());
        CliqueTree cliqueTree = net.createCliqueTree();
        cliqueTree.reNormalize(new DivideByPartition());

        System.out.println("Clique Tree: "+cliqueTree.toString());

        cliqueTree.runBeliefPropagation();
        cliqueTree.reNormalize(new DivideByPartition());
        System.out.println("Clique Tree (after BP): "+cliqueTree.toString());

        AtomicInteger correct = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);
        tests.forEach(test->{
            cliqueTree.setCurrentAssignment(test.entrySet().stream().map(e->{
                if(!e.getKey().endsWith("future")) return new Pair<>(e.getKey(),e.getValue()[0]);
                else return null;
            }).filter(a->a!=null).collect(Collectors.toList()));
            cliqueTree.getFactorNodes().forEach(factor->{
                List<String> labelsToSum = Arrays.stream(factor.getVarLabels()).filter(label->!label.endsWith("future")).collect(Collectors.toList());
                FactorNode f = factor;
                for(String label : labelsToSum) {
                    f=f.multiply(Graph.givenValueFactor(net.findNode(label),test.get(label)[0]));
                }
                FactorNode result = f.sumOut(labelsToSum.toArray(new String[labelsToSum.size()]));
                result.reNormalize(new DivideByPartition());
                boolean predictedWentUp = result.getWeights()[0]<result.getWeights()[1];
                boolean actualWentUp = test.get(result.getVarLabels()[0])[0]>0.5;
                if((predictedWentUp&&actualWentUp)||(!predictedWentUp&&!actualWentUp)) {
                    correct.getAndIncrement();
                }
                total.getAndIncrement();
                System.out.println(result);
            });
            // run BP
        });

        System.out.println("Correct: "+correct.get()+"/"+total.get());

    }
}
