/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class TimeUtils {
    public static long SECONDS = 1000;
    public static long MINUTES = 60 * SECONDS;
    public static long HOURS = 60 * MINUTES;
    public static long DAYS = 24 * HOURS;

    // Constant defines the date/time format for a request parameter.
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    public static final String DATE_TIME_PATTERN = "{datetime}";
    public static final String SNAPSHOT_DATE_TIME_FORMAT = "yyyyMMdd_HHmmss";

    public static long getCurrentTime() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
    }

    public static Date getCurrentDate() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime();
    }

    /**
     * Converts the passed timestamp to a Date reference, thereby validating the
     * request timestamp is a valid formatted date/time string.
     *
     * @param timestampStr The request timestamp as a string.
     * @return The passed timestamp as a Date reference. A null is returned if
     *         the passed timestamp string is null or blank.
     */
    public static Date getDateTimestamp(String timestampStr) {
        Date timestamp = null;

        // This is OK. Just means no timestamp was passed in the request.
        if ((timestampStr == null) || (timestampStr.length() == 0)) {
            return timestamp;
        }

        try {
            // Ensure the passed timestamp can be parsed.
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
            timestamp = dateFormat.parse(timestampStr);
        } catch (ParseException pe) {
            timestamp = getDateFromLong(timestampStr);
        }
        return timestamp;
    }

    /**
     * Converts the passed timestamp in milliseconds to a Date reference.
     *
     * @param timestampStr the requested timestamp in milliseconds as a String
     * @return the passed timestamp as a Date reference
     */
    private static Date getDateFromLong(String timestampStr) {
        Date timestamp = null;

        try {
            long timeInMs = Long.parseLong(timestampStr);
            timestamp = new Date(timeInMs);
        } catch (NumberFormatException n) {
            throw APIException.badRequests.invalidDate(timestampStr, DATE_TIME_FORMAT+" or long type");
        }
        return timestamp;
    }

    /**
     *
     * Format a string against current date time if it includes pattern string {datetime}
     *
     * @param source - source string including pattern {datetime}
     * @return formatted string
     */
    public static String formatDateForCurrent(String source) {
        if (source.contains(DATE_TIME_PATTERN)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(SNAPSHOT_DATE_TIME_FORMAT);
            Date current = getCurrentDate();
            String formattedDate = dateFormat.format(current);
            return source.replace(DATE_TIME_PATTERN, formattedDate);
        }

        return source;
    }

    /**
     * Validates that the specified end time comes after the specified start
     * time. Note that it is OK for the start/end times to be null. It just
     * means they were not specified in the request.
     *
     * @param startTime The requested start time or null.
     * @param endTime The requested end time or null.
     * @throws APIException When the passed end time comes before the
     *             passed start time.
     */
    public static void validateTimestamps(Date startTime, Date endTime) {
        if ((startTime != null) && (endTime != null)) {
            if (endTime.before(startTime)) {
                throw APIException.badRequests.endTimeBeforeStartTime(startTime.toString(), endTime.toString());
            }
        }
    }
}
