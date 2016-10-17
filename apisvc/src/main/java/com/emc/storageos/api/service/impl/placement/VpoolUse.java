/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

/**
 * Possible uses for a Virtual Pool. ROOT is a top level virtual pool, that may contain
 * other virtual pools of the type(s) specified herein.
 * Note: the scheduler does not implement separate scheduling for all such vpool uses at this time.
 * See PlacementManager.
 */
public enum VpoolUse {
    ROOT,               // root Vpool passed in apisvc call
    VPLEX_HA,           // VPLEX high availability
    RP_JOURNAL,         // RP Journal
    RP_STANDBY_JOURNAL, // RP standby Journal
    SRDF_COPY,          // SRDF Copy
    RP_COPY             // RP Copy
}
