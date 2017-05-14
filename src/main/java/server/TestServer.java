package server;

import static spark.Spark.get;
import static spark.Spark.port;

/**
 * Created by Evan on 5/14/2017.
 */
public class TestServer {
    public static void main(String[] args) {
        port(4567);
        get("/",(req,res)->{
            return "Hello World!";
        });
    }
}
