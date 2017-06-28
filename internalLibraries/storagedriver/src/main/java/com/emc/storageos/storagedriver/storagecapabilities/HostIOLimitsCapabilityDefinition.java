package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * Capability definition for host io limits: bandwidth and iops.
 */

public class HostIOLimitsCapabilityDefinition extends CapabilityDefinition {

    // The uid of this capability definition.
    public static final String CAPABILITY_UID = CapabilityUid.hostIOLimits.name();

    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        HOST_IO_LIMIT_BANDWIDTH,
        HOST_IO_LIMIT_IOPS

    };

    /**
     * Default Constructor
     */
    public HostIOLimitsCapabilityDefinition() {
        super(CAPABILITY_UID);
    }
}