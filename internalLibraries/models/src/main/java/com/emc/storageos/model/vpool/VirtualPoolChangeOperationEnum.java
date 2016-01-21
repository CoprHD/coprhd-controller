/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vpool;

public enum VirtualPoolChangeOperationEnum {
    VPLEX_LOCAL_TO_DISTRIBUTED("Change one or more volumes from local VPLEX to distributed VPLEX virtual pool"),
    VPLEX_DATA_MIGRATION("Migrate data from one or more volumes to new virtual pool"),
    NON_VPLEX_TO_VPLEX("Change one or more volumes from non-VPLEX to a local or distributed virtual pool"),
    RP_PROTECTED("Change one or more volumes to include RecoverPoint protection"),
    RP_REMOVE_PROTECTION("Change one or more volumes already protected by RecoverPoint and remove that protection leaving the source volume in an unprotected state"),
    RP_PROTECTED_CHANGE("Change one or more volumes already protected by RecoverPoint to different RecoverPoint protection settings/setup"),    
    SRDF_PROTECED("Change one or more volumes to include SRDF protection"),
    ADD_MIRRORS("Change one or more volumes to include continuous copies protection"),
    EXPORT_PATH_PARAMS("Change one or more volumes' path paramters"),
    AUTO_TIERING_POLICY("Change one or more volumes' Auto-tiering policy");

    private String description;

    private VirtualPoolChangeOperationEnum(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
