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
            throw APIException.badRequests.invalidDate(timestampStr);
        }
        return timestamp;
    }
}
