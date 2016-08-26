/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowType;

public class ExecutionWindowHelper {
    private static final Logger log = LoggerFactory.getLogger(ExecutionWindowHelper.class);
    private ExecutionWindow window;

    static final int INFINITE_WINDOW_ORDER_EXECUTION_TIMEOUT = 1; // 1 hour

    public ExecutionWindowHelper(ExecutionWindow window) {
        this.window = window;
    }

    public boolean isActive(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return isActive(cal);
    }

    public boolean isActive(Calendar fromDate) {
        fromDate = inUTC(fromDate);

        Calendar startTime = getWindowStartTime(fromDate);
        Calendar endTime = getWindowEndTime(startTime);
        boolean duringWindow = (fromDate.compareTo(startTime) >= 0) && (fromDate.compareTo(endTime) < 0);
        return duringWindow;
    }

    /**
     * Check if order's scheduled time + schedule window already expire.
     * @param fromDate
     * @return
     */
    public boolean isExpired(Calendar fromDate) {
        Calendar endTime = (Calendar) fromDate.clone();
        if (window == null) {
            endTime.add(Calendar.HOUR_OF_DAY, INFINITE_WINDOW_ORDER_EXECUTION_TIMEOUT);
        } else {
            endTime.add(getWindowLengthCalendarField(), window.getExecutionWindowLength());
        }

        Calendar currTime = Calendar.getInstance();
        log.debug("currTime:{}, endTime: {}", currTime, endTime);
        return currTime.compareTo(endTime) > 0;
    }

    public Calendar calculateCurrentOrNext() {
        return calculateCurrentOrNext(Calendar.getInstance());
    }

    public Calendar calculateCurrentOrNext(Calendar fromDate) {
        return calculate(fromDate, true);
    }

    public Calendar calculateNext() {
        return calculateNext(Calendar.getInstance());
    }

    public Calendar calculateNext(Calendar fromDate) {
        return calculate(fromDate, false);
    }

    protected Calendar calculate(Calendar fromDate, boolean includeCurrent) {
        fromDate = inUTC(fromDate);

        Calendar startTime = getWindowStartTime(fromDate);
        Calendar endTime = getWindowEndTime(startTime);

        boolean duringWindow = (fromDate.compareTo(startTime) >= 0) && (fromDate.compareTo(endTime) < 0);
        boolean afterWindow = fromDate.compareTo(endTime) >= 0;

        if (afterWindow || (duringWindow && !includeCurrent)) {
            nextWindow(startTime);
        }

        return startTime;
    }

    /**
     * Determines if the window is a daily window.
     * 
     * @return true if the window is a daily window.
     */
    private boolean isDaily() {
        return ExecutionWindowType.DAILY.name().equals(window.getExecutionWindowType());
    }

    /**
     * Determines if the window is a weekly window.
     * 
     * @return true if the window is a weekly window.
     */
    private boolean isWeekly() {
        return ExecutionWindowType.WEEKLY.name().equals(window.getExecutionWindowType());
    }

    /**
     * Determines if the window is a monthly window.
     * 
     * @return true if the window is a monthly window.
     */
    private boolean isMonthly() {
        return ExecutionWindowType.MONTHLY.name().equals(window.getExecutionWindowType());
    }

    /**
     * Gets the window's start time immediately before the given date.
     * 
     * @return the window time calendar.
     */
    private Calendar getWindowStartTime(Calendar fromDate) {
        int year = fromDate.get(Calendar.YEAR);
        int month = fromDate.get(Calendar.MONTH);
        int day = fromDate.get(Calendar.DAY_OF_MONTH);
        int hour = window.getHourOfDayInUTC() != null ? window.getHourOfDayInUTC() : 0;
        int minute = window.getMinuteOfHourInUTC() != null ? window.getMinuteOfHourInUTC() : 0;

        Calendar startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startTime.set(year, month, day, hour, minute, 0);
        startTime.set(Calendar.MILLISECOND, 0);

        if (isWeekly()) {
            adjustDayOfWeek(startTime);
        }
        else if (isMonthly()) {
            adjustDayOfMonth(startTime, month);
        }

        if (startTime.after(fromDate)) {
            previousWindow(startTime);
        }
        return startTime;
    }

    /**
     * Gets the end time of the window from the given window start time.
     * 
     * @param startTime
     *            the start time.
     * @return the end time.
     */
    private Calendar getWindowEndTime(Calendar startTime) {
        Calendar endTime = (Calendar) startTime.clone();
        endTime.add(getWindowLengthCalendarField(), window.getExecutionWindowLength());
        return endTime;
    }

    /**
     * 48 hours time representation
     * @return
     */
    private int getHourMin(int hour, int min) {
        return Integer.valueOf(String.format("%02d%02d", hour, min));
    }

     private int getWindowStartHourMin() {
         int hour = window.getHourOfDayInUTC() != null ? window.getHourOfDayInUTC() : 0;
         int min = window.getMinuteOfHourInUTC() != null ? window.getMinuteOfHourInUTC() : 0;
         return getHourMin(hour, min);
     }

    private int getWindowEndHourMin() {
        int hour = window.getHourOfDayInUTC() != null ? window.getHourOfDayInUTC() : 0;
        int min = window.getMinuteOfHourInUTC() != null ? window.getMinuteOfHourInUTC() : 0;

        switch (ExecutionWindowLengthType.valueOf(window.getExecutionWindowLengthType())) {
            case DAYS:
                // maximum execution window is 24hours
                hour += 24;
                break;
            case HOURS:
                hour += window.getExecutionWindowLength();
                break;
            case MINUTES:
                hour += window.getExecutionWindowLength()/60;
                min += window.getExecutionWindowLength()%60;
                if (min>=60) {
                    hour ++;
                    min -= 60;
                }
                break;
        }

        return getHourMin(hour, min);
    }

    public boolean inHourMinWindow(int hour, int min) {
        int targetTime = getHourMin(hour, min);
        log.debug("target HourMin:{}", targetTime);

        int startTime, endTime;
        startTime = getWindowStartHourMin();
        log.debug("window start HourMin:{}", startTime);
        endTime = getWindowEndHourMin();
        log.debug("window end HourMin:{}", endTime);

        if (targetTime >= startTime && targetTime <= endTime) {
            return true;
        }
        // might in the 2nd day.
        targetTime += 2400;
        if (targetTime >= startTime && targetTime <= endTime) {
            return true;
        }
        return false;
    }

    /**
     * Adjusts the start time to the correct day of the week for a weekly window.
     * 
     * @param startTime
     *            the start time.
     */
    private void adjustDayOfWeek(Calendar startTime) {
        // Adjust the window time within the current week
        int daysDiff = getDayOfWeek() - startTime.get(Calendar.DAY_OF_WEEK);
        startTime.add(Calendar.DAY_OF_WEEK, daysDiff);
    }

    /**
     * Adjust the day of the month for the given month for a monthly window. If the day of the month is after the last
     * day of the month, it is set to the last day of the month.
     * 
     * @param startTime
     *            the start time.
     * @param month
     *            the month.
     */
    private void adjustDayOfMonth(Calendar startTime, int month) {
        // Set to the last day of the month
        applyLastDayOfMonth(startTime, month);
        // If this isn't a last day of month window, back up to the requested day
        if (!window.getLastDayOfMonth()) {
            int lastDayOfMonth = startTime.get(Calendar.DAY_OF_MONTH);
            if (lastDayOfMonth > getDayOfMonth()) {
                startTime.set(Calendar.DAY_OF_MONTH, getDayOfMonth());
            }
        }
    }

    /**
     * Sets the calendar to the last day of the given month.
     * 
     * @param cal
     *            the calendar.
     * @param month
     *            the month.
     */
    private void applyLastDayOfMonth(Calendar cal, int month) {
        cal.set(Calendar.MONTH, month + 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.DAY_OF_MONTH, -1);
    }

    /**
     * Changes to the previous window start time.
     * 
     * @param startTime
     *            the window start time.
     */
    private void previousWindow(Calendar startTime) {
        if (isDaily()) {
            startTime.add(Calendar.DAY_OF_MONTH, -1);
        }
        else if (isWeekly()) {
            startTime.add(Calendar.WEEK_OF_MONTH, -1);
        }
        else if (isMonthly()) {
            int month = startTime.get(Calendar.MONTH);
            adjustDayOfMonth(startTime, month + -1);
        }
    }

    /**
     * Changes to the next window start time.
     * 
     * @param startTime
     *            the window start time.
     */
    private void nextWindow(Calendar startTime) {
        if (isDaily()) {
            startTime.add(Calendar.DAY_OF_MONTH, 1);
        }
        else if (isWeekly()) {
            startTime.add(Calendar.WEEK_OF_MONTH, 1);
        }
        else if (isMonthly()) {
            int month = startTime.get(Calendar.MONTH);
            adjustDayOfMonth(startTime, month + 1);
        }
    }

    /**
     * Gets the calendar in UTC time.
     * 
     * @param cal
     *            the input calendar.
     * @return a calendar instance in UTC.
     */
    private Calendar inUTC(Calendar cal) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(cal.getTimeInMillis());
        return utc;
    }

    /**
     * Gets the calendar field that is used for the window length.
     * 
     * @return the window length calendar field.
     */
    private int getWindowLengthCalendarField() {
        switch (ExecutionWindowLengthType.valueOf(window.getExecutionWindowLengthType())) {
            case DAYS:
                return Calendar.DAY_OF_MONTH;
            case HOURS:
                return Calendar.HOUR_OF_DAY;
            case MINUTES:
                return Calendar.MINUTE;
        }
        throw new IllegalStateException("Invalid window length");
    }

    /**
     * Gets the day of the week used for Calendar objects. The stored value treats Monday as the first day of the week,
     * but Java's Calendar uses Sunday as the first day of the week.
     * 
     * @return the day of the week.
     */
    private int getDayOfWeek() {
        // JODA time starts day of week on mondays (1)
        int dayOfWeek = window.getDayOfWeek();
        if (dayOfWeek == 7) {
            dayOfWeek = 1;
        }
        else {
            dayOfWeek++;
        }
        return dayOfWeek;
    }

    /**
     * Gets the day of the month used for Calendar objects.
     * 
     * @return the day of the month.
     */
    private int getDayOfMonth() {
        return window.getDayOfMonth();
    }

    /**
     * Get expected schedule time based on execution window
     * @return
     */
    public Calendar getScheduledTime() {
        Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        log.debug("currTime: {}", currTime.toString());
        if (isActive(currTime)) {
            log.debug("currTime {} is in active window, set it as scheduled time.", currTime.toString());
            return currTime;
        }

        int year = currTime.get(Calendar.YEAR);
        int month = currTime.get(Calendar.MONTH);
        int day = currTime.get(Calendar.DAY_OF_MONTH);
        int hour = window.getHourOfDayInUTC() != null ? window.getHourOfDayInUTC() : 0;
        int min = window.getMinuteOfHourInUTC() != null ? window.getMinuteOfHourInUTC() : 0;

        Calendar scheduledTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        scheduledTime.set(year, month, day, hour, min, 0);

        if (window.getExecutionWindowType().equals(ExecutionWindowType.MONTHLY.name())) {
            scheduledTime.set(Calendar.DAY_OF_MONTH, window.getDayOfMonth());
        } else if (window.getExecutionWindowType().equals(ExecutionWindowType.WEEKLY.name())) {
            int daysDiff = (window.getDayOfWeek()%7 + 1) - scheduledTime.get(Calendar.DAY_OF_WEEK); // java dayOfWeek starts from Sun.
            scheduledTime.add(Calendar.DAY_OF_WEEK, daysDiff);
        }

        while (scheduledTime.before(currTime)) {
            scheduledTime = getNextScheduledTime(scheduledTime, window);
            log.debug("scheduledTime in loop: {}", scheduledTime.toString());
        }

        log.debug("scheduledTime: {}", scheduledTime.toString());
        return scheduledTime;
    }

    /**
     * Get next desired schedule time based on the previous one and execution window
     * @param scheduledTime     previous schedule time
     * @param window             execution window
     * @return
     */
    private Calendar getNextScheduledTime(Calendar scheduledTime, ExecutionWindow window) {
        if (window.getExecutionWindowType().equals(ExecutionWindowType.MONTHLY.name())) {
            scheduledTime.add(Calendar.MONTH, 1);
        } else if (window.getExecutionWindowType().equals(ExecutionWindowType.WEEKLY.name())) {
            scheduledTime.add(Calendar.WEEK_OF_MONTH, 1);
        } else if (window.getExecutionWindowType().equals(ExecutionWindowType.DAILY.name())) {
            scheduledTime.add(Calendar.DAY_OF_MONTH, 1);
        }
        return scheduledTime;
    }

}
