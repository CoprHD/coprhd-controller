/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
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

    public static final int THREAD_CHECK_INTERVAL = 30;
}
