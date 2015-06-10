/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
