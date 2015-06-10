/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowType;

public class ExecutionWindowHelperTest {

    private static ExecutionWindow createDailyWindow(int hour, int minute) {
        ExecutionWindow window = new ExecutionWindow();
        window.setExecutionWindowType(ExecutionWindowType.DAILY.name());
        window.setHourOfDayInUTC(hour);
        window.setMinuteOfHourInUTC(minute);
        return window;
    }

    private static ExecutionWindow createWeeklyWindow(int dayOfWeek, int hour, int minute) {
        // Convert from Calendar day of week to JODA day of week
        if (dayOfWeek == 1) {
            dayOfWeek = 7;
        }
        else {
            dayOfWeek--;
        }
        ExecutionWindow window = new ExecutionWindow();
        window.setExecutionWindowType(ExecutionWindowType.WEEKLY.name());
        window.setHourOfDayInUTC(hour);
        window.setMinuteOfHourInUTC(minute);
        window.setDayOfWeek(dayOfWeek);
        return window;
    }

    private static ExecutionWindow createMonthlyWindow(int dayOfMonth, int hour, int minute) {
        ExecutionWindow window = new ExecutionWindow();
        window.setExecutionWindowType(ExecutionWindowType.MONTHLY.name());
        window.setHourOfDayInUTC(hour);
        window.setMinuteOfHourInUTC(minute);
        window.setDayOfMonth(dayOfMonth);
        return window;
    }

    private static void setLengthInHours(ExecutionWindow window, int hours) {
        window.setExecutionWindowLengthType(ExecutionWindowLengthType.HOURS.name());
        window.setExecutionWindowLength(hours);
    }

    private static Calendar getDateTimeUTC(int year, int month, int day, int hour, int minute, int second) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month, day, hour, minute, second);
        return cal;

    }

    private static void assertTime(Calendar cal, int hour, int minute, int second) {
        Assert.assertEquals(hour, cal.get(Calendar.HOUR_OF_DAY));
        Assert.assertEquals(minute, cal.get(Calendar.MINUTE));
        Assert.assertEquals(second, cal.get(Calendar.SECOND));
    }

    private static void assertDate(Calendar cal, int year, int month, int day) {
        Assert.assertEquals(year, cal.get(Calendar.YEAR));
        Assert.assertEquals(month, cal.get(Calendar.MONTH));
        Assert.assertEquals(day, cal.get(Calendar.DAY_OF_MONTH));
    }

    private static void assertDateTime(Calendar cal, int year, int month, int day, int hour, int minute, int second) {
        assertDate(cal, year, month, day);
        assertTime(cal, hour, minute, second);
    }

    @Test
    public void testDailySpanningMidnight() {
        // Daily at 11:30pm
        ExecutionWindow window = createDailyWindow(23, 30);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        Calendar beforeTime = getDateTimeUTC(2013, Calendar.MAY, 6, 23, 0, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 6, 23, 30, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 6, 23, 30, 0);

        Calendar duringTime = getDateTimeUTC(2013, Calendar.MAY, 6, 23, 45, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.MAY, 7, 23, 30, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 6, 23, 30, 0);
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 7, 0, 15, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.MAY, 7, 23, 30, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 6, 23, 30, 0);

        Calendar afterTime = getDateTimeUTC(2013, Calendar.MAY, 7, 1, 0, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 7, 23, 30, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 7, 23, 30, 0);
    }

    @Test
    public void testDailyWindowBeforeTime() {
        // Daily at 1:15am
        ExecutionWindow window = createDailyWindow(1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        // before, different hour
        Calendar beforeTime = getDateTimeUTC(2000, Calendar.JANUARY, 1, 0, 10, 0);
        // Should be the same date
        assertDateTime(helper.calculateNext(beforeTime), 2000, Calendar.JANUARY, 1, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2000, Calendar.JANUARY, 1, 1, 15, 0);

        // a few minutes before, the same hour
        beforeTime = getDateTimeUTC(2000, Calendar.JANUARY, 1, 1, 10, 0);
        // Should be the same date
        assertDateTime(helper.calculateNext(beforeTime), 2000, Calendar.JANUARY, 1, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2000, Calendar.JANUARY, 1, 1, 15, 0);
    }

    @Test
    public void testDailyWindowDuringTime() {
        // Daily at 1:15am
        ExecutionWindow window = createDailyWindow(1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        // Right at the start of the window
        Calendar duringTime = getDateTimeUTC(2000, Calendar.JANUARY, 10, 1, 15, 0);
        // Next will be the day after
        assertDateTime(helper.calculateNext(duringTime), 2000, Calendar.JANUARY, 11, 1, 15, 0);
        // Current or next will be today
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2000, Calendar.JANUARY, 10, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));

        // One minute after the start of the window
        duringTime = getDateTimeUTC(2000, Calendar.JANUARY, 10, 1, 16, 0);
        // Next will be the day after
        assertDateTime(helper.calculateNext(duringTime), 2000, Calendar.JANUARY, 11, 1, 15, 0);
        // Current or next will be today
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2000, Calendar.JANUARY, 10, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));

        // One minute before the end of the window
        duringTime = getDateTimeUTC(2000, Calendar.JANUARY, 10, 2, 14, 0);
        // Next will be the day after
        assertDateTime(helper.calculateNext(duringTime), 2000, Calendar.JANUARY, 11, 1, 15, 0);
        // Current or next will be today
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2000, Calendar.JANUARY, 10, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));

        // Right at the end of the window
        duringTime = getDateTimeUTC(2000, Calendar.JANUARY, 10, 2, 15, 0);
        // Next will be the day after
        assertDateTime(helper.calculateNext(duringTime), 2000, Calendar.JANUARY, 11, 1, 15, 0);
        // Current or next will be tomorrow
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2000, Calendar.JANUARY, 11, 1, 15, 0);
        Assert.assertFalse(helper.isActive(duringTime));
    }

    @Test
    public void testDailyWindowAfterTime() {
        // Daily at 1:15am
        ExecutionWindow window = createDailyWindow(1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        // Right after the window ends
        Calendar afterTime = getDateTimeUTC(2000, Calendar.FEBRUARY, 5, 2, 16, 0);
        assertDateTime(helper.calculateNext(afterTime), 2000, Calendar.FEBRUARY, 6, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2000, Calendar.FEBRUARY, 6, 1, 15, 0);

        // Should be the next day
        afterTime = getDateTimeUTC(2000, Calendar.FEBRUARY, 5, 10, 16, 0);
        assertDateTime(helper.calculateNext(afterTime), 2000, Calendar.FEBRUARY, 6, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2000, Calendar.FEBRUARY, 6, 1, 15, 0);

        // Last day of the month, check for rollover
        afterTime = getDateTimeUTC(2000, Calendar.JANUARY, 31, 5, 0, 0);
        assertDateTime(helper.calculateNext(afterTime), 2000, Calendar.FEBRUARY, 1, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2000, Calendar.FEBRUARY, 1, 1, 15, 0);

        // Last day of the year, check for rollover
        afterTime = getDateTimeUTC(2000, Calendar.DECEMBER, 31, 5, 0, 0);
        assertDateTime(helper.calculateNext(afterTime), 2001, Calendar.JANUARY, 1, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2001, Calendar.JANUARY, 1, 1, 15, 0);
    }

    @Test
    public void testWeeklyBeforeTime() {
        // Weekly on Sunday at 1:15am
        ExecutionWindow window = createWeeklyWindow(Calendar.SUNDAY, 1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        Calendar beforeTime = getDateTimeUTC(2013, Calendar.MAY, 5, 1, 10, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);

        beforeTime = getDateTimeUTC(2013, Calendar.MAY, 4, 1, 10, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);

        beforeTime = getDateTimeUTC(2013, Calendar.MAY, 3, 1, 10, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);

        beforeTime = getDateTimeUTC(2013, Calendar.MAY, 2, 1, 10, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);

        beforeTime = getDateTimeUTC(2013, Calendar.MAY, 1, 1, 10, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 5, 1, 15, 0);
    }

    @Test
    public void testWeeklyAfterTime() {
        // Weekly on Sunday at 1:15am
        ExecutionWindow window = createWeeklyWindow(Calendar.SUNDAY, 1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        Calendar afterTime = getDateTimeUTC(2013, Calendar.MAY, 5, 3, 15, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);

        afterTime = getDateTimeUTC(2013, Calendar.MAY, 6, 1, 10, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);

        afterTime = getDateTimeUTC(2013, Calendar.MAY, 7, 1, 10, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);

        afterTime = getDateTimeUTC(2013, Calendar.MAY, 8, 1, 10, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);

        afterTime = getDateTimeUTC(2013, Calendar.MAY, 9, 1, 10, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 12, 1, 15, 0);
    }

    @Test
    public void testWeeklyDuringTime() {
        // Weekly on Sunday at 1:15am
        ExecutionWindow window = createWeeklyWindow(Calendar.SUNDAY, 1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        // Start of the window
        Calendar duringTime = getDateTimeUTC(2013, Calendar.MAY, 5, 1, 15, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));

        // One minute after start
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 5, 1, 16, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));

        // One minute before end
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 5, 2, 14, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 5, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));

        // End of the window
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 5, 2, 15, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 12, 1, 15, 0);
        Assert.assertFalse(helper.isActive(duringTime));
    }

    @Test
    public void testMonthly() {
        // Monthly on the 15th at 1:15am
        ExecutionWindow window = createMonthlyWindow(15, 1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        // Just before window
        Calendar beforeTime = getDateTimeUTC(2013, Calendar.MAY, 15, 1, 10, 0);
        assertDateTime(helper.calculateNext(beforeTime), 2013, Calendar.MAY, 15, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(beforeTime), 2013, Calendar.MAY, 15, 1, 15, 0);

        // Just after the window
        Calendar afterTime = getDateTimeUTC(2013, Calendar.APRIL, 16, 1, 10, 0);
        assertDateTime(helper.calculateNext(afterTime), 2013, Calendar.MAY, 15, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(afterTime), 2013, Calendar.MAY, 15, 1, 15, 0);

        // Start of the window
        Calendar duringTime = getDateTimeUTC(2013, Calendar.MAY, 15, 1, 15, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.JUNE, 15, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 15, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));
        // One minute after start
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 15, 1, 16, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.JUNE, 15, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 15, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));
        // One minute before end
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 15, 2, 14, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.JUNE, 15, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.MAY, 15, 1, 15, 0);
        Assert.assertTrue(helper.isActive(duringTime));
        // End of the window
        duringTime = getDateTimeUTC(2013, Calendar.MAY, 15, 2, 15, 0);
        assertDateTime(helper.calculateNext(duringTime), 2013, Calendar.JUNE, 15, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(duringTime), 2013, Calendar.JUNE, 15, 1, 15, 0);
        Assert.assertFalse(helper.isActive(duringTime));
    }

    @Test
    public void testMonthlyNearEndOfMonth() {
        // Monthly on the 31st at 1:15am
        ExecutionWindow window = createMonthlyWindow(31, 1, 15);
        setLengthInHours(window, 1);
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);

        // February
        Calendar february = getDateTimeUTC(2013, Calendar.FEBRUARY, 1, 0, 0, 0);
        assertDateTime(helper.calculateNext(february), 2013, Calendar.FEBRUARY, 28, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(february), 2013, Calendar.FEBRUARY, 28, 1, 15, 0);

        // March
        Calendar march = getDateTimeUTC(2013, Calendar.MARCH, 1, 0, 0, 0);
        assertDateTime(helper.calculateNext(march), 2013, Calendar.MARCH, 31, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(march), 2013, Calendar.MARCH, 31, 1, 15, 0);

        // April
        Calendar april = getDateTimeUTC(2013, Calendar.APRIL, 1, 0, 0, 0);
        assertDateTime(helper.calculateNext(april), 2013, Calendar.APRIL, 30, 1, 15, 0);
        assertDateTime(helper.calculateCurrentOrNext(april), 2013, Calendar.APRIL, 30, 1, 15, 0);
    }
}
