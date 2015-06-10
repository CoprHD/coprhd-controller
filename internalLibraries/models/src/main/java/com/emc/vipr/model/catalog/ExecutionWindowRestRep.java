/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "execution_window")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ExecutionWindowRestRep extends DataObjectRestRep {
    
    public static final URI NEXT = URI.create("urn:storageos:ExecutionWindow:NEXT:");
    
    public static final String DAILY = "DAILY";
    public static final String MONTHLY = "MONTHLY";
    public static final String WEEKLY = "WEEKLY";
    
    public static final String DAYS = "DAYS";
    public static final String HOURS = "HOURS";
    public static final String MINUTES = "MINUTES";
    
    /**
     * Hour of the day for this execution window
     */
    private Integer hourOfDayInUTC;             
    
    /**
     * Minute of the day for this execution window
     */
    private Integer minuteOfHourInUTC;            
    
    /**
     * Length of this execution window
     */
    private Integer executionWindowLength;
    
    /**
     * Length type of the execution window: MINUTES, HOURS, DAYS
     */
    private String executionWindowLengthType;           
    
    /**
     * Type of the execution window: DAILY, WEEKLY, MONTHLY
     */
    private String executionWindowType;                 
    
    /**
     * Day of the week for this execution window
     */
    private Integer dayOfWeek;                          
    
    /**
     * Day of the month for this execution window
     */
    private Integer dayOfMonth;                         
    
    /**
     * Indicates the day of the week is the last day of the month
     */
    private Boolean lastDayOfMonth = Boolean.FALSE;     
    
    /**
     * Tenant that this execution window applies to
     */
    private RelatedResourceRep tenant;                              
    
    @XmlElement(name = "hour_of_day_in_utc")
    public Integer getHourOfDayInUTC() {
        return hourOfDayInUTC;
    }
    public void setHourOfDayInUTC(Integer hourOfDayInUTC) {
        this.hourOfDayInUTC = hourOfDayInUTC;
    }
    
    @XmlElement(name = "minute_of_hour_in_utc")
    public Integer getMinuteOfHourInUTC() {
        return minuteOfHourInUTC;
    }
    public void setMinuteOfHourInUTC(Integer minuteOfHourInUTC) {
        this.minuteOfHourInUTC = minuteOfHourInUTC;
    }
    
    @XmlElement(name = "execution_window_length")
    public Integer getExecutionWindowLength() {
        return executionWindowLength;
    }
    public void setExecutionWindowLength(Integer executionWindowLength) {
        this.executionWindowLength = executionWindowLength;
    }
    
    @XmlElement(name = "execution_window_length_type")
    public String getExecutionWindowLengthType() {
        return executionWindowLengthType;
    }
    public void setExecutionWindowLengthType(String executionWindowLengthType) {
        this.executionWindowLengthType = executionWindowLengthType;
    }
    
    @XmlElement(name = "execution_window_type")
    public String getExecutionWindowType() {
        return executionWindowType;
    }
    public void setExecutionWindowType(String executionWindowType) {
        this.executionWindowType = executionWindowType;
    }
    
    @XmlElement(name = "day_of_week")
    public Integer getDayOfWeek() {
        return dayOfWeek;
    }
    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }
    
    @XmlElement(name = "day_of_month")
    public Integer getDayOfMonth() {
        return dayOfMonth;
    }
    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }
    
    @XmlElement(name = "last_day_of_month")
    public Boolean getLastDayOfMonth() {
        return lastDayOfMonth;
    }
    public void setLastDayOfMonth(Boolean lastDayOfMonth) {
        this.lastDayOfMonth = lastDayOfMonth;
    }
    
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }
    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    /**
     * Determines if this ID corresponds to the 'next' execution window.
     * 
     * @param id
     *        the ID.
     * @return true if the ID refers to the next window.
     */
    public static boolean isNextWindow(URI id) {
        return id == null || NEXT.equals(id);
    }    
    
    public static boolean isNextWindow(RelatedResourceRep relatedResourceRep) {
        return relatedResourceRep == null || isNextWindow(relatedResourceRep.getId());
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
     * Gets the calendar in UTC time.
     * 
     * @param cal
     *        the input calendar.
     * @return a calendar instance in UTC.
     */
    private Calendar inUTC(Calendar cal) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(cal.getTimeInMillis());
        return utc;
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
        int hour = this.hourOfDayInUTC != null ? this.hourOfDayInUTC : 0;
        int minute = this.minuteOfHourInUTC != null ? this.minuteOfHourInUTC : 0;

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
     *        the start time.
     * @return the end time.
     */
    private Calendar getWindowEndTime(Calendar startTime) {
        Calendar endTime = (Calendar) startTime.clone();
        endTime.add(getWindowLengthCalendarField(), this.executionWindowLength);
        return endTime;
    }    
    
    /**
     * Determines if the window is a daily window.
     * 
     * @return true if the window is a daily window.
     */
    private boolean isDaily() {
        return DAILY.equalsIgnoreCase(this.executionWindowType);
    }

    /**
     * Determines if the window is a weekly window.
     * 
     * @return true if the window is a weekly window.
     */
    private boolean isWeekly() {
        return WEEKLY.equalsIgnoreCase(this.executionWindowType);
    }

    /**
     * Determines if the window is a monthly window.
     * 
     * @return true if the window is a monthly window.
     */
    private boolean isMonthly() {
        return MONTHLY.equalsIgnoreCase(this.executionWindowType);
    }    
    
    /**
     * Adjusts the start time to the correct day of the week for a weekly window.
     * 
     * @param startTime
     *        the start time.
     */
    private void adjustDayOfWeek(Calendar startTime) {
        // Adjust the window time within the current week
        int daysDiff = getDayOfWeek() - getDayOfWeek(startTime);
        startTime.add(Calendar.DAY_OF_WEEK, daysDiff);
    }

    /**
     * Gets the day of week from the calendar. Calendar uses Sunday as the start of the week, however we are using
     * Monday as the start of the week in execution windows.
     * 
     * @param time
     *        the calendar time.
     * @return the day of the week in the same terms used for execution windows.
     */
    private static int getDayOfWeek(Calendar time) {
        // Calendar's Day of Week starts with SUNDAY, we are using MONDAY as the start of the week
        int dayOfWeek = time.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SUNDAY) {
            return 7;
        }
        else {
            return dayOfWeek - 1;
        }
    }

    /**
     * Adjust the day of the month for the given month for a monthly window. If the day of the month is after the last
     * day of the month, it is set to the last day of the month.
     * 
     * @param startTime
     *        the start time.
     * @param month
     *        the month.
     */
    private void adjustDayOfMonth(Calendar startTime, int month) {
        // Set to the last day of the month
        applyLastDayOfMonth(startTime, month);
        // If this isn't a last day of month window, back up to the requested day
        if (!this.lastDayOfMonth) {
            int lastDayOfMonth = startTime.get(Calendar.DAY_OF_MONTH);
            if (lastDayOfMonth > getDayOfMonth()) {
                startTime.set(Calendar.DAY_OF_MONTH, getDayOfMonth());
            }
        }
    }    
    
    /**
     * Changes to the previous window start time.
     * 
     * @param startTime
     *        the window start time.
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
     * Gets the calendar field that is used for the window length.
     * 
     * @return the window length calendar field.
     */
    private int getWindowLengthCalendarField() {
        switch (this.executionWindowLengthType) {
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
     * Sets the calendar to the last day of the given month.
     * 
     * @param cal
     *        the calendar.
     * @param month
     *        the month.
     */
    private void applyLastDayOfMonth(Calendar cal, int month) {
        cal.set(Calendar.MONTH, month + 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.DAY_OF_MONTH, -1);
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
     * Changes to the next window start time.
     * 
     * @param startTime
     *        the window start time.
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
    
}
