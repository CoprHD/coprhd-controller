/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/*
 * Schedule Info for ScheduledEvent (will launch a set of orders)
 * Note: all the time here is UTC.
 */
public class ScheduleInfo implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(ScheduleInfo.class);
    static final long serialVersionUID = 2016081711117510155L;

    public static final String HOUR_OF_DAY = "hourOfDay";
    public static final String MINUTE_OF_HOUR = "minuteOfHour";
    public static final String DURATION_LENGTH = "durationLength";
    public static final String CYCLE_TYPE = "cycleType";
    public static final String CYCLE_FREQUENCE = "cycleFrequency";
    public static final String SECTIONS_IN_CYCLE = "sectionsInCycle";
    public static final String START_DATE = "startDate";
    public static final String REOCCURRENCE = "reoccurrence";
    public static final String END_DATE = "endDate";
    public static final String DATE_EXCEPTIONS = "dateExceptions";

    public static final String FULL_DAYTIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String FULL_DAY_FORMAT = "yyyy-MM-dd";

    public static final int MAX_REOCCURRENCE = 200;  // valid range for reoccurrence is [0 .. 200]
    public static final int MAX_CYCLE_FREQUENCE = 100;  // valid range for cycle frequence is [1 .. 100]

    // start hour and minute of the date
    private Integer hourOfDay;        // [0..23)
    private Integer minuteOfHour;    // [0..59)
    private Integer durationLength;  // in minutes [1..60*24]

    private ScheduleCycleType cycleType = ScheduleCycleType.DAILY; // Minutely, Hourly, Daily, Weekly, Monthly, Yearly

    // frequency for each cycle; e.g. 2 for every 2 hours, if the cycleType is Hourly
    private Integer cycleFrequency;  // [1..)

    // For each cycle, user might want to schedule multiple times in several sub sections.
    // Format: numeric string.
    //    For MINUTELY, meaning minute level execution. sectionsInCycle would be empty.
    //    For HOURLY, normally the set is empty meaning execution time would be minuteOfHour in the hour.
    //               while if the set, e.g, is [10, 59], it means execution 3 times at 10m, 59m in the hour.
    //    For DAILY, normally the set is empty meaning execution time would be hourOfDay:minuteOfHour in the day.
    //               while if the set, e.g, is [0, 5, 23], it means execution 3 times at 00:minuteOfHour, 05:minuteOfHour and 23:minuteOfHour in the day.
    //    For WEEKLY: e.g set [1,5,7] meaning hourOfDay:minuteOfHour at Mon, Fri and Sun of the week
    //    For MONTHLY: e.g set [1,12] meaning hourOfDay:minuteOfHour at 1th, 12th of the month
    //    For YEARLY: e.g. set [02/29, 07/31] meanning hourOfDay:minuteOfHour at Feb 29th and Jul 31th of the year
    private List<String> sectionsInCycle;    // singe sub section for now

    private String startDate;  // the start date. Format: "yyyy-MM-dd"

    // number of recurrence (0 for Indefinitely, 1 for ONCE event, others for limited recurrences)
    private Integer reoccurrence;     // [0..)

    private String endDate;  // the end date; not used for now

    // date exceptions for the schedule policy
    // date format: "yyyy-MM-dd HH:mm:ss"
    private List<String> dateExceptions;

    @XmlElement(name = HOUR_OF_DAY, required = true)
    public Integer getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(Integer hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    @XmlElement(name = MINUTE_OF_HOUR, required = true)
    public Integer getMinuteOfHour() {
        return minuteOfHour;
    }

    public void setMinuteOfHour(Integer minuteOfHour) {
        this.minuteOfHour = minuteOfHour;
    }

    @XmlElement(name = DURATION_LENGTH)
    public Integer getDurationLength() {
        return durationLength;
    }

    public void setDurationLength(Integer durationLength) {
        this.durationLength = durationLength;
    }

    @XmlElement(name = CYCLE_TYPE)
    public ScheduleCycleType getCycleType() {
        return cycleType;
    }

    public void setCycleType(ScheduleCycleType cycleType) {
        this.cycleType = cycleType;
    }

    @XmlElement(name = CYCLE_FREQUENCE)
    public Integer getCycleFrequency() {
        return cycleFrequency;
    }

    public void setCycleFrequency(Integer cycleFrequency) {
        this.cycleFrequency = cycleFrequency;
    }

    @XmlElementWrapper(name = SECTIONS_IN_CYCLE)
    @XmlElement(name = "section")
    public List<String> getSectionsInCycle() {
        return sectionsInCycle;
    }

    public void setSectionsInCycle(List<String> sectionsInCycle) {
        this.sectionsInCycle = sectionsInCycle;
    }

    @XmlElement(name = START_DATE, required = true)
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @XmlElement(name = REOCCURRENCE, required = true)
    public Integer getReoccurrence() {
        return reoccurrence;
    }

    public void setReoccurrence(Integer reoccurrence) {
        this.reoccurrence = reoccurrence;
    }

    @XmlElement(name = END_DATE)
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @XmlElementWrapper(name = DATE_EXCEPTIONS)
    @XmlElement(name = "dateException")
    public List<String> getDateExceptions() {
        return dateExceptions;
    }

    public void setDateExceptions(List<String> dateExceptions) {
        this.dateExceptions = dateExceptions;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        try {
            out.writeObject(this);
        } finally {
            out.close();
        }
        return bos.toByteArray();
    }
    public static ScheduleInfo deserialize(byte[] data) throws IOException,
            ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        try {
            obj = in.readObject();
        } finally {
            in.close();
        }
        return (ScheduleInfo) obj;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        try {
            DateFormat formatter = new SimpleDateFormat(ScheduleInfo.FULL_DAY_FORMAT);
            Date date = formatter.parse(getStartDate());
            Calendar startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startTime.setTimeZone(TimeZone.getTimeZone("UTC"));
            startTime.setTime(date);
            startTime.set(Calendar.HOUR_OF_DAY, getHourOfDay());
            startTime.set(Calendar.MINUTE, getMinuteOfHour());
            startTime.set(Calendar.SECOND, 0);

            sb.append("StartTime=").append(startTime.toString()).append(";");
            sb.append(CYCLE_TYPE).append("=").append(cycleType.toString()).append(";");
            sb.append(CYCLE_FREQUENCE).append("=").append(cycleFrequency).append(";");
            sb.append(REOCCURRENCE).append("=").append(reoccurrence).append(";");

        } catch (Exception e) {
            log.error("Failed to execute toString", e);
        }

        return sb.toString();
    }
}
