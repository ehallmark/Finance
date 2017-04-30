package finance;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by Evan on 4/30/2017.
 */

public class Trade {
    @Getter @Setter
    protected double amount;

    @Getter @Setter
    protected String stock;


    public Trade(String stock, double shares) {
        this.amount=shares;
        this.stock=stock;
    }

}
