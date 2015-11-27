/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.common;

/**
 * Constants for all jobs.
 */
public interface JobConstants {

    // Allowing 15secs for service to start. Run any scheduler after this lag. Allowing 6 minutes for service to start (instead of 15secs)
    public static final int SERVICE_START_LAG = 360;
    // Run diagtool scheduler every 15 mins. Measured in seconds.
    public static final int LAG_BETWEEN_RUNS_ALERTS = 900;
}
