/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.util.Calendar;
import java.util.Date;

/**
 * Class to manage time computation for Backup Scheduler
 */
public class ScheduleTimeRange {
    public static enum ScheduleInterval {
        MINUTE, HOUR, DAY, WEEK, MONTH, YEAR, DISABLED
    }

    private Calendar start;
    private ScheduleInterval interval;
    private int multiple;

    public ScheduleTimeRange(ScheduleInterval interval, int multiple, Calendar now) {
        this.interval = interval;
        this.multiple = multiple;

        if (interval != ScheduleInterval.DISABLED) {
            this.start = (Calendar)now.clone();
            adjustToIntervalLowerBound(this.start, interval, multiple);
        }
    }

    public ScheduleInterval getInterval() {
        return this.interval;
    }

    public Date start() {
        return this.start.getTime();
    }

    public Date end() {
        return shiftDate(this.start, this.interval, this.multiple).getTime();
    }

    public ScheduleTimeRange next() {
        return new ScheduleTimeRange(this.interval, this.multiple, shiftDate(this.start, this.interval, this.multiple));
    }

    public Date minuteOffset(int minutes) {
        Calendar cal = (Calendar)this.start.clone();
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    // Test if the backup at specified time is contained in this time range
    public boolean contains(Date backupDateTime) {
        return (backupDateTime.equals(start()) || backupDateTime.after(start())) && backupDateTime.before(end());
    }

    @Override
    public String toString() {
        return String.format("%s@%s", this.interval.toString(), this.start == null ? "NA" : ScheduledBackupTag.toTimestamp(this.start.getTime()));
    }

    public static Date getExpectedMostRecentBackupDateTime(Calendar now, ScheduleInterval interval, int multiple, int offset) {
        Calendar expected = (Calendar)now.clone();
        adjustToIntervalLowerBound(expected, interval, multiple);

        // Then add the start offset to the start boundary
        expected.add(Calendar.MINUTE, offset);

        return expected.getTime();
    }

    public static ScheduleInterval parseInterval(String intervalStr) {
        return Enum.valueOf(ScheduleInterval.class, intervalStr.toUpperCase());
    }

    private static int alignDown(int value, int granularity, int minValue) {
        int remaining = value % granularity;
        value -= remaining;
        return value < minValue ? minValue : value;
    }

    /**
     * Adjust the Calendar object to the start boundary of specified time interval that it's currently in.
     * NOTE: If the multiple parameter exceeded the next level interval, it will be same as specifying next
     *       level interval with multiple = 1.
     * @param now
     * @param interval
     * @param multiple
     */
    private static void adjustToIntervalLowerBound(Calendar now, ScheduleInterval interval, int multiple) {
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        switch (interval) {
            case MINUTE:
                now.set(Calendar.MINUTE, alignDown(now.get(Calendar.MINUTE), multiple, 0));
                break;
            case HOUR:
                now.set(Calendar.HOUR_OF_DAY, alignDown(now.get(Calendar.HOUR_OF_DAY), multiple, 0));
                now.set(Calendar.MINUTE, 0);
                break;
            case DAY:
                now.set(Calendar.DAY_OF_MONTH, alignDown(now.get(Calendar.DAY_OF_MONTH), multiple, 1));
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                break;
            case WEEK:
                now.set(Calendar.WEEK_OF_YEAR, alignDown(now.get(Calendar.WEEK_OF_YEAR), multiple, 1));
                now.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                break;
            case MONTH:
                now.set(Calendar.MONTH, alignDown(now.get(Calendar.MONTH), multiple, Calendar.JANUARY));
                now.set(Calendar.DAY_OF_MONTH, 1);
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                break;
            case YEAR:
                now.set(Calendar.YEAR, alignDown(now.get(Calendar.YEAR), multiple, 1970));
                now.set(Calendar.MONTH, Calendar.JANUARY);
                now.set(Calendar.DAY_OF_MONTH, 1);
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                break;
            default:
                throw new IllegalArgumentException(String.format("Invalid interval: %s", interval.toString()));
        }
    }

    private static Calendar shiftDate(Calendar cal, ScheduleInterval interval, int multiple) {
        cal = (Calendar)cal.clone();

        adjustToIntervalLowerBound(cal, interval, multiple);

        switch (interval) {
            case MINUTE:
                cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE) + multiple);
                break;
            case HOUR:
                cal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY) + multiple);
                break;
            case DAY:
                cal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH) + multiple);
                break;
            case WEEK:
                cal.set(Calendar.WEEK_OF_YEAR, cal.get(Calendar.WEEK_OF_YEAR) + multiple);
                break;
            case MONTH:
                cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + multiple);
                break;
            case YEAR:
                cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) + multiple);
                break;
            default:
                throw new IllegalArgumentException(String.format("Invalid interval: %s", interval.toString()));
        }

        return cal;
    }
}
