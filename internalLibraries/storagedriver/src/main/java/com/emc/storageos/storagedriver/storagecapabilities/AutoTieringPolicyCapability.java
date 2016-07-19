/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A storage capability to capture auto tiering policy support.
 */
public class AutoTieringPolicyCapability extends CapabilityInstance {
    
    // The name of this capability
    public static final String CAPABILITY_NAME = "AutoTieringPolicy";
    
    // The names of the supported properties for this capability.
    public static enum PROPERTY_NAMES {
        POLICY_ID,
        PROVISIONING_TYPE
    };
    
    /**
     * Constructor
     * 
     * @param uid The unique identifier for this instance.
     */
    public AutoTieringPolicyCapability(String uid) {
        super(CAPABILITY_NAME, uid, new HashMap<String, List<String>>());
    }
    
    /**
     * Constructor
     * 
     * @param uid The unique identifier for this instance.
     * @param properties The properties map.
     */
    public AutoTieringPolicyCapability(String uid, Map<String, List<String>> properties) {
        super(CAPABILITY_NAME, uid, properties);
    }
}
