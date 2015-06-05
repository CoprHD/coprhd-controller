/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

public enum ExecutionStatus {
    NONE,
    PRECHECK,
    EXECUTE,
    ROLLBACK,
    COMPLETED,
    FAILED;
}
