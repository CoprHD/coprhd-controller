/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * Class defines capability definition for user defined set of remote replication attributes.
 * Capability instances for this definition have map of name-value pairs as defined by user.
 */
public class RemoteReplicationAttributes extends CapabilityDefinition {

    // The uid of this capability definition.
    public static final String CAPABILITY_UID = CapabilityDefinition.CapabilityUid.remoteReplicationAttributes.name();

    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        CREATE_STATE
    };

    /**
     * Default Constructor
     */
    public RemoteReplicationAttributes() {
        super(CAPABILITY_UID);
    }

    // System values for create state
    public static enum CREATE_STATE {
        ACTIVE,
        INACTIVE
    }
}
