/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * Specifies the definition for the auto tiering policy capability.
 */
public class AutoTieringPolicyCapabilityDefinition extends CapabilityDefinition {

    // The uid of this capability definition.
    public static final String CAPABILITY_UID = "autoTieringPolicy";
    
    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        POLICY_ID,
        PROVISIONING_TYPE
    };
    
    /**
     * Default Constructor
     */
    public AutoTieringPolicyCapabilityDefinition() {
        super(CAPABILITY_UID);
    }
}
