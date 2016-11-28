/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

public enum ScheduleCycleType {
    MINUTELY,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    public static boolean isValid(String lengthType) {
        try {
            valueOf(lengthType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
