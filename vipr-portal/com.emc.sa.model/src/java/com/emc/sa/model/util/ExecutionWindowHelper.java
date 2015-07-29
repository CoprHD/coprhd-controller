/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowType;

public class ExecutionWindowHelper {
    private ExecutionWindow window;

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
}
