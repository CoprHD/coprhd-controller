/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc.parse;

import java.util.Calendar;
import java.util.Date;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

public abstract class LogParser {
    protected static Calendar logDate = Calendar.getInstance();

    public abstract LogMessage parseLine(String line, LogRequest info);

    // test if log entry match level filter
    // return < 0 -- satisfied (level value smaller than request)
    // return 0 -- satisfied (level value equals to request)
    // return > 0 -- not satisfied (level value bigger than request)
    protected int matchLevelFilter(int levelValue, LogRequest request) {
        int levelRequest = request.getLogLevel();
        return levelValue - levelRequest;
    }

    /**
     * Test time is in request's range
     */
    protected int inTimeRange(Date logDate, LogRequest request) {
        return LogUtil.timeInRange(logDate, request.getStartTime(),
                request.getEndTime());
    }

    // test if log entry is in request's time range
    // return -1 -- earlier than start time
    // return 0 -- in time range
    // return -1 --later than end time
    protected int inTimeRange(int year, int month, int days, int hours, int mins,
                            int secs, int msecs, LogRequest request) {
        logDate.set(year, (month - 1), days, hours, mins, secs);
        logDate.set(Calendar.MILLISECOND, msecs);
        return LogUtil.timeInRange(logDate.getTime(), request.getStartTime(),
                request.getEndTime());
    }

    // Returns the number of milliseconds
    protected long getTime(int year, int month, int days, int hours, int mins,
                         int secs, int msecs) {
        logDate.set(year, (month - 1), days, hours, mins, secs);
        logDate.set(Calendar.MILLISECOND, msecs);
        Date date = logDate.getTime();
        return date.getTime();
    }

    protected static int toNumber(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }
}
