/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;

import java.util.Set;

public class RegistrationData {

    // Driver name (fully qualified name of driver class)
    private String driverName;

    // Storage system type managed by driver
    private String storageSystemType;

    // Metadata for capabilities provided by storage systems managed by driver
    private Set<CapabilityDefinition> capabilityDefinitions;

}
