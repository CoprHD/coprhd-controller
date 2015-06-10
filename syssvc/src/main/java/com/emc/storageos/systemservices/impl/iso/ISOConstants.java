/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.iso;

/**
 * Constants for configuring ISO image.
 */
public class ISOConstants {

    public static final int BUFFER_SIZE = 131072;
    public static final int SECTOR_SIZE = 2048;
    public static final String VOLUME_IDENTIFIER = "CONFIG";
    public static final String OVERRIDES_FILE_NAME = "config-override.properties";
    public static final String CONTROLLER_OVF_FILE_NAME = "controller-ovf.properties";

}
