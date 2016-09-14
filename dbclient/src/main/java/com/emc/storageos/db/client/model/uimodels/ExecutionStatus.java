/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

public enum ExecutionStatus {
    NONE,
    PRECHECK,
    PRELAUNCH,
    EXECUTE,
    POSTLAUNCH,
    ROLLBACK,
    COMPLETED,
    FAILED;
}
