package com.emc.storageos.api.service.impl.placement;

public enum VpoolUse {
    ROOT,               // root Vpool passed in apisvc call
    VPLEX_HA,           // VPLEX high availability
    RP_JOURNAL,         // RP Journal
    RP_STANDBY_JOURNAL, // RP standby Journal
    SRDF_COPY,          // SRDF Copy
    RP_COPY             // RP Copy
}
