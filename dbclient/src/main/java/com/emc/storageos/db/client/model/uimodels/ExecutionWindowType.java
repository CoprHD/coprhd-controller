/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

public enum ExecutionWindowType {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static boolean isValid(String lengthType) {
        try {
            valueOf(lengthType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
