/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

public class VolumeCompressionCapabilityDefinition extends CapabilityDefinition {
    // The uid of this capability definition.
    public static final String CAPABILITY_UID = CapabilityDefinition.CapabilityUid.volumeCompression.toString();

    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        ENABLED   // true/false
    };

    /**
     * Default Constructor
     */
    public VolumeCompressionCapabilityDefinition() {
        super(CAPABILITY_UID);
    }
}