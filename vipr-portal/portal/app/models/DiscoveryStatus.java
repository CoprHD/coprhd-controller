/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class DiscoveryStatus {
    public static final String CREATED = "CREATED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String COMPLETE = "COMPLETE";
    public static final String ERROR = "ERROR";
    public static final String NOT_CONNECTED = "NOTCONNECTED";
    
    public static boolean isCreated(String type) {
        return CREATED.equals(type);
    }

    public static boolean isInProgress(String type) {
        return IN_PROGRESS.equals(type);
    }
    
    public static boolean isScheduled(String type){
        return SCHEDULED.equals(type);
    }

    public static boolean isComplete(String type) {
        return COMPLETE.equals(type);
    }

    public static boolean isError(String type) {
        return ERROR.equals(type);
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, "DiscoveryStatus");
    }
}
