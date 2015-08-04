/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

/**
 * Constants for diagnostic tests.
 */
public interface DiagConstants {
    public static final String DIAGTOOl_CMD = "/etc/diagtool";
    public static final String VERBOSE = "-v";
    public static final long DIAGTOOL_TIMEOUT = 240000; // 4 min
}
