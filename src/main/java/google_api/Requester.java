package google_api;

import com.github.kevinsawicki.stocks.DateUtils;
import com.github.kevinsawicki.stocks.StockQuoteRequest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalField;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/29/2017.
 */
public class Requester {
    public static final List<String> HEADERS = Collections.unmodifiableList(Arrays.asList("date","price"));

    public static List<List<String>> requestStockData(LocalDate startDate, LocalDate endDate, String... stockSymbols) throws IOException{
        List<List<List<String>>> toMerge = new ArrayList<>();
        // test
        Arrays.stream(stockSymbols).forEach(stockSymbol->{
            try {
                StockQuoteRequest request = new StockQuoteRequest();
                request.setSymbol(stockSymbol);
                request.setStartDate(DateHelper.fromLocalDate(startDate));
                request.setEndDate(DateHelper.fromLocalDate(endDate));

                List<List<String>> data = new ArrayList<>();
                data.add(HEADERS.stream().map(header->header+"_"+stockSymbol).collect(Collectors.toList()));
                while (request.next()) {
                    List<String> point = new ArrayList<>();
                    LocalDate date = DateHelper.fromDate(request.getDate());
                    point.add(date.toString());
                    point.add(String.valueOf((request.getOpen()+request.getClose()+request.getHigh()+request.getLow())/4));
                    data.add(1,point);
                }
                toMerge.add(data);
            } catch(Exception e) {
                System.out.println("Exception on stock: "+stockSymbol);
                e.printStackTrace();
            }

        });


        List<List<String>> mergedData = new ArrayList<>();
        toMerge.forEach(timeSeries->{
            AtomicInteger idx = new AtomicInteger(0);
            timeSeries.forEach(point->{
                int i = idx.getAndIncrement();
                if(mergedData.size()>i) {
                    mergedData.get(i).addAll(point.subList(1,point.size()));
                    if(i > 0 && !point.get(0).equals(mergedData.get(i).get(0))) {
                        System.out.println(point.get(0)+" is not "+mergedData.get(i).get(0));
                        throw new RuntimeException("Stock dates do not match up!");
                    }

                } else {
                    if(i==0)point.set(0,HEADERS.get(0));
                    mergedData.add(point);
                }
            });
        });
        return mergedData;
    }



    public static void main(String[] args) throws IOException{
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("sample_stock_output.csv")));
        requestStockData(LocalDate.of(2014,4,29),LocalDate.of(2017,4,29),"goog","vti","s","luv","msft","fez","vpl","vgk","vea","bnd","intc","wmt","kr","tsla","gm","cmg","t","aapl","amzn","nflx","sina","pcln","pg").forEach(line->{
            String dataLine = String.join(",",line);
            System.out.println(dataLine);
            try {
                writer.write(dataLine+System.lineSeparator());
                writer.flush();
            }catch(Exception e) {
                e.printStackTrace();
            }
        });

        writer.close();
    }
}
