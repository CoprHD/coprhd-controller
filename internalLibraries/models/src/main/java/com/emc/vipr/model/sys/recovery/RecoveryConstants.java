/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys.recovery;

/**
 * Constants for node recovery project
 */
public class RecoveryConstants {

    public static final String RECOVERY_LEADER_PATH = "recoveryleader";
    public static final long RECOVERY_CONNECT_INTERVAL = 1000L;

    public static final String RECOVERY_LOCK = "recovery";
    public static final int RECOVERY_LOCK_TIMEOUT = 10 * 1000;

    public static final long RECOVERY_CHECK_INTERVAL = 10 * 1000L;
    public static final long RECOVERY_CHECK_TIMEOUT = 30 * 60 * 1000L;

    public static final int RECOVERY_RETRY_COUNT = 3;

    public static final String RECOVERY_STATUS = "status";
    public static final String RECOVERY_STARTTIME = "starttime";
    public static final String RECOVERY_ENDTIME = "endtime";
    public static final String RECOVERY_ERRCODE = "errorcode";
}
