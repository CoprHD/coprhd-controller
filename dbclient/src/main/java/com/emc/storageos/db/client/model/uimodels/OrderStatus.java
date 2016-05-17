/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

public enum OrderStatus {
    PENDING, EXECUTING, SUCCESS, PARTIAL_SUCCESS, ERROR, SCHEDULED, CANCELLED, APPROVAL, APPROVED, REJECTED, SUSPENDED_NO_ERROR,
    SUSPENDED_ERROR
}
