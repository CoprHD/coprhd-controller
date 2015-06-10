/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.util;

import java.util.Date;

import com.emc.vipr.client.impl.DateUtils;

public class TimeBucketUtils {
    public static final String HOUR_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH";
    public static final String MINUTE_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm";

    public static String forHour(Date date) {
        return DateUtils.formatUTC(date, HOUR_BUCKET_TIME_FORMAT);
    }

    public static String forMinute(Date date) {
        return DateUtils.formatUTC(date, MINUTE_BUCKET_TIME_FORMAT);
    }
}
