/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

public enum ValCk {
    /**
     * Identity - Volumes: typically device nativeId, WWN, and size
     */
    ID,
    /**
     * VPLEX - Volumes: identities of associatedVolumes and any mirrors; local/distributed; consistency group
     * membership
     * storage view membership
     */
    VPLEX
}
