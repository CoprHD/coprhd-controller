/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.plugins.AccessProfile;

/**
 * Establish the methods to collect statistics for a VPlex system
 */
public interface VPlexStatsCollector {

    /**
     * Implementation of the interface will allow the collection of statistics from the VPlex system
     *
     * @param accessProfile [IN] - Has credential and other information that will be used for accessing the VPlex system for stats
     * @return StringMap that has values mapping to com/emc/storageos/volumecontroller/impl/plugins/metering/smis/processor/MetricsKeys.java
     */
            StringMap collect(AccessProfile accessProfile);
}
