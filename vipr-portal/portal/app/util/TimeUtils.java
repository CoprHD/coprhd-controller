/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.bind.DatatypeConverter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;

/**
 * Utilities for working with dates/calendars. Intended mostly for working with Execution Windows.
 *
 * @author Chris Dail
 */
public class TimeUtils {
    public static final long MILLIS_IN_SECOND = 1000;
    public static final long SECONDS_IN_HOUR = 3600;
    public static final long SECONDS_IN_DAY = 3600 * 24;
    public static final long SECONDS_IN_MIN = 60;
    
    public static final int INVALID_LARGE_DAY_OF_MONTH = 32;
    
    public static Long getEndDate(DateTime start, int lengthInMillis) {
        DateTime end = start.plusMillis(lengthInMillis);
        return end.getMillis() / 1000;
    }

    public static Calendar unixToCal(Long unixTimestamp) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(unixTimestamp * 1000);
        return cal;
    }
    
    public static Long dateToUnix(Date d) {
        Long result = null;
        if(d != null) {
            result = d.getTime() / 1000;
        }
        return result;
    }

    public static Calendar toCal(Date date) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(date);
        return cal;
    }

    public static String toIso(Date date) {
        return DatatypeConverter.printDateTime(toCal(date));
    }
    
    public static Date fromIso(String s) {
        return DatatypeConverter.parseDateTime(s).getTime();
    }

    public static Duration toDuration(long millis) {
        long seconds = millis / MILLIS_IN_SECOND;
        if (seconds % SECONDS_IN_DAY == 0) {
            return new Duration(seconds / SECONDS_IN_DAY, ExecutionWindowLengthType.DAYS);
        }
        else if (seconds % SECONDS_IN_HOUR == 0) {
            return new Duration(seconds / SECONDS_IN_HOUR, ExecutionWindowLengthType.HOURS);
        }
        return new Duration((int) seconds / SECONDS_IN_MIN, ExecutionWindowLengthType.MINUTES);
    }

    public static long toMillis(long duration, String unit) {
        ExecutionWindowLengthType lengthType = ExecutionWindowLengthType.valueOf(unit);
        if (ExecutionWindowLengthType.DAYS.equals(lengthType)) {
            return duration * SECONDS_IN_DAY * MILLIS_IN_SECOND;
        }
        else if (ExecutionWindowLengthType.HOURS.equals(lengthType)) {
            return duration * SECONDS_IN_HOUR * MILLIS_IN_SECOND;
        }
        return duration * SECONDS_IN_MIN * MILLIS_IN_SECOND;
    }
    
    public static TimeZone getTimeZoneForOffset(int offsetInMinutes) {
        int rawOffset = (offsetInMinutes * 60 * 1000) * -1;
        String[] timeZoneIds = TimeZone.getAvailableIDs(rawOffset);
        if (timeZoneIds != null && timeZoneIds.length > 0) {
            String timeZoneId = timeZoneIds[0];
            return TimeZone.getTimeZone(timeZoneId);
        }
        return null;
    }
    
    public static int getLocalHourOfDay(int hourOfDayInUTC, int offsetInMinutes) {
        DateTime utcDate = new DateTime(DateTimeZone.UTC);
        utcDate = utcDate.withHourOfDay(hourOfDayInUTC);
        DateTime localDate = utcDate.withZone(getLocalTimeZone(offsetInMinutes));
        return localDate.getHourOfDay();
    }
    
    public static int getLocalDayOfWeek(int dayOfWeekInUTC, int hourOfDayInUTC, int offsetInMinutes) {
        DateTime utcDate = new DateTime(DateTimeZone.UTC);
        utcDate = utcDate.withDayOfWeek(dayOfWeekInUTC).withHourOfDay(hourOfDayInUTC);
        DateTime localDate = utcDate.withZone(getLocalTimeZone(offsetInMinutes));
        return localDate.getDayOfWeek();
    }
    
    public static int getLocalDayOfMonth(int dayOfMonthInUTC, int hourOfDayInUTC, int offsetInMinutes) {
        DateTime utcDate = new DateTime(DateTimeZone.UTC);
        if (dayOfMonthInUTC >= utcDate.dayOfMonth().getMaximumValue()) {
            utcDate = utcDate.withDayOfMonth(utcDate.dayOfMonth().getMaximumValue()).withHourOfDay(hourOfDayInUTC);
        }
        else {
            utcDate = utcDate.withDayOfMonth(dayOfMonthInUTC).withHourOfDay(hourOfDayInUTC);
        }
        DateTime localDate = utcDate.withZone(getLocalTimeZone(offsetInMinutes));
        return localDate.getDayOfMonth();
    }    
    
    public static DateTimeZone getLocalTimeZone(int offsetInMinutes) {
        return DateTimeZone.forOffsetMillis((offsetInMinutes * 60 * 1000) * -1);
    }
    
    public static int getUTCHourOfDay(int hourOfDayInLocal, int offsetInMinutes) {
        DateTime localDate = new DateTime(getLocalTimeZone(offsetInMinutes));
        localDate = localDate.withHourOfDay(hourOfDayInLocal);
        DateTime utcDate = localDate.withZone(DateTimeZone.UTC);
        return utcDate.getHourOfDay();        
    }
    
    public static int getUTCDayOfWeek(int dayOfWeekInLocal, int hourOfDayInLocal, int offsetInMinutes) {
        DateTime localDate = new DateTime(getLocalTimeZone(offsetInMinutes));
        localDate = localDate.withDayOfWeek(dayOfWeekInLocal).withHourOfDay(hourOfDayInLocal);
        DateTime utcDate = localDate.withZone(DateTimeZone.UTC);
        return utcDate.getDayOfWeek();        
    }
    
    public static Integer getUTCDayOfMonth(int dayOfMonthInLocal, int hourOfDayInLocal, int offsetInMinutes) {
        DateTime localDate = new DateTime(getLocalTimeZone(offsetInMinutes));
        localDate = localDate.withDayOfMonth(dayOfMonthInLocal).withHourOfDay(hourOfDayInLocal);
        DateTime utcDate = localDate.withZone(DateTimeZone.UTC);
        if (isInPreviousMonth(utcDate, localDate)) {
            return INVALID_LARGE_DAY_OF_MONTH;
        }
        return utcDate.getDayOfMonth();
    }    
    
    public static boolean isUTCInPreviousMonth(Integer dayOfMonthInLocal, int hourOfDayInLocal, int offsetInMinutes) {
        if (dayOfMonthInLocal == 1) {
            DateTime localDate = new DateTime(getLocalTimeZone(offsetInMinutes));
            localDate = localDate.withDayOfMonth(dayOfMonthInLocal).withHourOfDay(hourOfDayInLocal);
            DateTime utcDate = localDate.withZone(DateTimeZone.UTC);
            return isInPreviousMonth(utcDate, localDate);
        }
        return false;
    }
    
    private static boolean isInPreviousMonth(DateTime value, DateTime current) {
        return value.getMonthOfYear() < current.getMonthOfYear() && value.getYear() <= current.getYear();
    }
    
    public static Calendar getCalendar(int hourOfDay, int offset) {
        Calendar cal = Calendar.getInstance(getTimeZoneForOffset(offset));
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);        
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        return cal;
    }

    public static class Duration {
        public ExecutionWindowLengthType unit;
        public long duration;

        public Duration(long duration, ExecutionWindowLengthType unit) {
            this.unit = unit;
            this.duration = duration;
        }
    }
}
