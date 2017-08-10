/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * Specifies remote replication mode capability definition
 */
public class ReplicationModeCapabilityDefinition extends CapabilityDefinition {

    // The uid of this capability definition.
    public static final String CAPABILITY_UID = CapabilityUid.replicationMode.name();

    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        MODE_ID,
        GROUP_CONSISTENCY_ENFORCED_AUTOMATICALLY,
        GROUP_CONSISTENCY_NOT_SUPPORTED
    };

    public ReplicationModeCapabilityDefinition() {
        super(CAPABILITY_UID);
    }
}
