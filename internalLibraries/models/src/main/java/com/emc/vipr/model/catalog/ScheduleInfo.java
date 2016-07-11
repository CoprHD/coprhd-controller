/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.util.List;

/*
 * Schedule Info for ScheduledEvent (a set of orders)
 * Note: all the time here is UTC.
 */
public class ScheduleInfo {
    // start hour and minute of the date
    private Integer hourOfDay;
    private Integer minuteOfHour;
    private Integer durationLength;  // in minutes

    private String cycleType; // Minutely, Hourly, Daily, Weekly, Monthly, Yearly

    // frequency for each cycle; e.g. 2 for every 2 hours, if the cycleType is Hourly
    private Integer cycleFrequency;

    // For each cycle, user might want to schedule multiple times in several sub sections.
    // e.g. 0,1,6 for Weekly meaning Sun, Mon and Sat for that Week
    private List<Integer> sectionsInCycle;

    private String startDate;  // the start date

    // number of reoccurrence (1 for ONCE event)
    private Integer reoccurrence;

    private String endDate;  // the end date

    // date exceptions for the schedule policy
    // date format: yyyymmddhhmm
    private List<String> dateExceptions;

    @XmlElement(name = "hourOfDay", required = true)
    public Integer getHourOfDayInUTC() {
        return hourOfDay;
    }

    public void setHourOfDay(Integer hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    @XmlElement(name = "minuteOfHour", required = true)
    public Integer getMinuteOfHour() {
        return minuteOfHour;
    }

    public void setMinuteOfHour(Integer minuteOfHour) {
        this.minuteOfHour = minuteOfHour;
    }

    @XmlElement(name = "durationLength", required = true)
    public Integer getDurationLength() {
        return durationLength;
    }

    public void setDurationLength(Integer durationLength) {
        this.durationLength = durationLength;
    }

    @XmlElement(name = "cycleType", required = true)
    public String getCycleType() {
        return cycleType;
    }

    public void setCycleType(String cycleType) {
        this.cycleType = cycleType;
    }

    @XmlElement(name = "cycleFrequency")
    public Integer getCycleFrequency() {
        return cycleFrequency;
    }

    public void setCycleFrequency(Integer cycleFrequency) {
        this.cycleFrequency = cycleFrequency;
    }

    @XmlElementWrapper(name = "sectionsInCycle")
    @XmlElement(name = "section")
    public List<Integer> getSectionsInCycle() {
        return sectionsInCycle;
    }

    public void setSectionsInCycle(List<Integer> sectionsInCycle) {
        this.sectionsInCycle = sectionsInCycle;
    }

    @XmlElement(name = "startDate", required = true)
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @XmlElement(name = "reoccurrence")
    public Integer getReoccurrence() {
        return reoccurrence;
    }

    public void setReoccurrence(Integer reoccurrence) {
        this.reoccurrence = reoccurrence;
    }

    @XmlElement(name = "endDate")
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @XmlElementWrapper(name = "dateExceptions")
    @XmlElement(name = "dateException")
    public List<String> getDateExceptions() {
        return dateExceptions;
    }

    public void setDateExceptions(List<String> dateExceptions) {
        this.dateExceptions = dateExceptions;
    }
}
