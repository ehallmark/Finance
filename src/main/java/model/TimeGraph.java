package model;

import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.learning.algorithms.TrainingAlgorithm;
import model.learning.distributions.DirichletCreator;
import model.nodes.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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

        float[] pricesTwoPeriodsAgo = getPricesFromRow(reader.readLine());
        float[] pricesLastPeriod = getPricesFromRow(reader.readLine());
        float[] pricesThisPeriod = null;

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

        net.setAssignments(trainingSet);
        net.applyLearningAlgorithm(new TrainingAlgorithm(new DirichletCreator(10f),1),1);


        net.reNormalize(new DivideByPartition());

        net.getFactorNodes().forEach(factor->{
            System.out.println("Factor: "+factor);
        });

        return net;
    }

    static Map<String,int[]> createAssignment(float[] twoPeriodsAgo, float[] lastPeriod, float[] thisPeriod, List<String> companies) {
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

    static float[] getPricesFromRow(String line) {
        String[] cells = line.split(",");
        float[] prices = new float[cells.length-1];
        for(int c = 1; c < cells.length; c++) {
            prices[c-1]=Float.valueOf(cells[c]);
        }
        return prices;
    }

    public static void main(String[] args) throws Exception {
        trainCSV(new File("sample_stock_output.csv"));
    }
}
