/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public enum JobStatus {
    CREATED,
    SCHEDULED,
	RUNNING,
    SUCCEEDED,
	FAILED,
    ABORTED,
	UNKNOWN,
    VALIDATING,
	VALIDATED,
    VALIDATE_FAILED,
	INVALID,
    RETRIEVING_PICTURE
}
