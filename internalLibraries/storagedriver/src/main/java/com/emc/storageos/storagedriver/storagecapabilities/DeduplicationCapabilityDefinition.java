/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

public class DeduplicationCapabilityDefinition extends CapabilityDefinition {
    // The uid of this capability definition.
    public static final String CAPABILITY_UID = CapabilityUid.deduplication.toString();

    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        ENABLED   // true/false
    };

    /**
     * Default Constructor
     */
    public DeduplicationCapabilityDefinition() {
        super(CAPABILITY_UID);
    }

}
