/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

public class TimeUtils {
    public static long SECONDS = 1000;
    public static long MINUTES = 60 * SECONDS;
    public static long HOURS = 60 * MINUTES;
    public static long DAYS = 24 * HOURS;

    public static long getCurrentTime() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
    }

    public static Date getCurrentDate() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime();
    }
}
