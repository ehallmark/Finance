package google_api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Created by Evan on 4/29/2017.
 */
public class DateHelper {
    public static LocalDate fromDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    public static Date fromLocalDate(LocalDate date) {
        return Date.from(date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant());
    }
}
