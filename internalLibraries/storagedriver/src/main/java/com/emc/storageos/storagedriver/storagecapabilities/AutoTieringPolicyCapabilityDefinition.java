package com.emc.storageos.storagedriver.storagecapabilities;

public class AutoTieringPolicyCapabilityDefinition extends CapabilityDefinition {

    // The name of this capability
    public static final String CAPABILITY_UID = "autoTieringPolicy";
    
    // The names of the supported properties for this capability.
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
