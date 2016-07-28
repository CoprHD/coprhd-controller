/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowType;
import com.emc.vipr.model.catalog.ScheduleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduleTimeHelper {
    private static final Logger log = LoggerFactory.getLogger(ScheduleTimeHelper.class);

    public ScheduleTimeHelper() {
    }

    /**
     * Get the first desired schedule time based on current time, start time and schedule schema
     * @param scheduleInfo  schedule schema
     * @return                calendar for the first desired schedule time
     * @throws Exception
     */
    public static Calendar getFirstScheduledTime(ScheduleInfo scheduleInfo) throws Exception{

        DateFormat formatter = new SimpleDateFormat(ScheduleInfo.FULL_DAY_FORMAT);
        Date date = formatter.parse(scheduleInfo.getStartDate());

        Calendar startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        startTime.setTime(date);
        startTime.set(Calendar.HOUR_OF_DAY, scheduleInfo.getHourOfDay());
        startTime.set(Calendar.MINUTE, scheduleInfo.getMinuteOfHour());
        startTime.set(Calendar.SECOND, 0);
        log.info("startTime: {}", startTime.toString());

        if (scheduleInfo.getReoccurrence() == 1) {
            return startTime;
        }

        Calendar currTZTime = Calendar.getInstance();
        Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        currTime.setTimeInMillis(currTZTime.getTimeInMillis());
        log.info("currTime: {}", currTime.toString());

        Calendar initTime = startTime.before(currTime)? currTime:startTime;
        log.info("initTime: {}", initTime.toString());

        int year = initTime.get(Calendar.YEAR);
        int month = initTime.get(Calendar.MONTH);
        int day = initTime.get(Calendar.DAY_OF_MONTH);
        int hour = scheduleInfo.getHourOfDay();
        int min = scheduleInfo.getMinuteOfHour();

        Calendar scheduledTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        scheduledTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        scheduledTime.set(year, month, day, hour, min, 0);

        switch (scheduleInfo.getCycleType()) {
            case MONTHLY:
                day = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                scheduledTime.set(Calendar.DAY_OF_MONTH, day);
                break;
            case WEEKLY:
                day = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                int daysDiff = (day%7 + 1) - scheduledTime.get(Calendar.DAY_OF_WEEK); // java dayOfWeek starts from Sun.
                scheduledTime.add(Calendar.DAY_OF_WEEK, daysDiff);
                break;
            case DAILY:
            case HOURLY:
            case MINUTELY:
                break;
            default:
                log.error("not expected schedule cycle.");
        }

        log.info("scheduledTime: {}", scheduledTime.toString());

        while (scheduledTime.before(initTime)) {
            scheduledTime = getNextScheduledTime(scheduledTime, scheduleInfo);
            log.info("scheduledTime in loop: {}", scheduledTime.toString());
        }

        return scheduledTime;
    }

    /**
     * Get next desired schedule time based on the previous one and schedule schema
     * @param scheduledTime     previous schedule time
     * @param scheduleInfo      schedule schema
     * @return
     */
    public static Calendar getNextScheduledTime(Calendar scheduledTime, ScheduleInfo scheduleInfo) {
        switch (scheduleInfo.getCycleType()) {
            case MONTHLY:
                scheduledTime.add(Calendar.MONTH, scheduleInfo.getCycleFrequency());
                break;
            case WEEKLY:
                scheduledTime.add(Calendar.WEEK_OF_MONTH, scheduleInfo.getCycleFrequency());
                break;
            case DAILY:
                scheduledTime.add(Calendar.DAY_OF_MONTH, scheduleInfo.getCycleFrequency());
                break;
            case HOURLY:
                scheduledTime.add(Calendar.HOUR_OF_DAY, scheduleInfo.getCycleFrequency());
                break;
            case MINUTELY:
                scheduledTime.add(Calendar.MINUTE, scheduleInfo.getCycleFrequency());
                break;
            default:
                log.error("not expected schedule cycle.");
        }
        return scheduledTime;
    }

    public static Calendar getScheduledTime(ExecutionWindow window) {
        Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        log.info("currTime: {}", currTime.toString());

        int year = currTime.get(Calendar.YEAR);
        int month = currTime.get(Calendar.MONTH);
        int day = currTime.get(Calendar.DAY_OF_MONTH);
        int hour = window.getHourOfDayInUTC();
        int min = window.getMinuteOfHourInUTC();

        Calendar scheduledTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        scheduledTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        scheduledTime.set(year, month, day, hour, min, 0);

        if (window.getExecutionWindowType().equals(ExecutionWindowType.MONTHLY.name())) {
            scheduledTime.set(Calendar.DAY_OF_MONTH, window.getDayOfMonth());
        } else if (window.getExecutionWindowType().equals(ExecutionWindowType.WEEKLY.name())) {
            int daysDiff = (window.getDayOfWeek()%7 + 1) - scheduledTime.get(Calendar.DAY_OF_WEEK); // java dayOfWeek starts from Sun.
            scheduledTime.add(Calendar.DAY_OF_WEEK, daysDiff);
        }

        log.info("scheduledTime: {}", scheduledTime.toString());

        while (scheduledTime.before(currTime)) {
            scheduledTime = getNextScheduledTime(scheduledTime, window);
            log.info("scheduledTime in loop: {}", scheduledTime.toString());
        }

        return scheduledTime;
    }

    /**
     * Get next desired schedule time based on the previous one and schedule schema
     * @param scheduledTime     previous schedule time
     * @param window             execution window
     * @return
     */
    private static Calendar getNextScheduledTime(Calendar scheduledTime, ExecutionWindow window) {
        if (window.getExecutionWindowType().equals(ExecutionWindowType.MONTHLY.name())) {
            scheduledTime.add(Calendar.MONTH, 1);
        } else if (window.getExecutionWindowType().equals(ExecutionWindowType.WEEKLY.name())) {
            scheduledTime.add(Calendar.WEEK_OF_MONTH, 1);
        } else if (window.getExecutionWindowType().equals(ExecutionWindowType.DAILY.name())) {
            scheduledTime.add(Calendar.DAY_OF_MONTH, 1);
        }
        return scheduledTime;
    }

    /**
     * Convert a Calendar to a readable time string.
     * @param cal
     * @return
     * @throws Exception
     */
    public static String convertCalendarToStr(Calendar cal) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat(ScheduleInfo.FULL_DAYTIME_FORMAT);
        String formatted = format.format(cal.getTime());
        log.info("converted calendar time:{}", formatted);
        return formatted;
    }

    public static Calendar convertStrToCalendar(String formattedTime) {
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ScheduleInfo.FULL_DAYTIME_FORMAT);
            cal.setTime(sdf.parse(formattedTime));
        } catch (Exception e) {
            log.info(e.getMessage());
        }
        return cal;
    }
}
