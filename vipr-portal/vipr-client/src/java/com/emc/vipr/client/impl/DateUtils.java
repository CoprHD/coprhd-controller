/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtils {
    public static TimeZone UTC = TimeZone.getTimeZone("UTC");

    public static String formatUTC(Date date, String format) {
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        formatter.setTimeZone(UTC);
        return formatter.format(date);
    }
}
