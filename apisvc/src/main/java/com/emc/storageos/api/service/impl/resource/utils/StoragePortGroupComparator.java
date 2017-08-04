/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.Comparator;

import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;

/**
 * To sort storage port group list by its metrics
 *
 */
public class StoragePortGroupComparator implements Comparator<StoragePortGroup> {
    
    /**
     * Sort port group in ascending order of its port metric (first order),
     * and in descending order of its volume count port usage metrics (second order),
     */
    @Override
    public int compare(StoragePortGroup pg1, StoragePortGroup pg2) {
        StringMap metrics1 = pg1.getMetrics();
        StringMap metrics2 = pg2.getMetrics();
        int result = 0;
        
        if (metrics1 != null && !metrics1.isEmpty() && metrics2 != null && !metrics2.isEmpty()) {
            Double portMetric1 = MetricsKeys.getDoubleOrNull(MetricsKeys.portMetric, metrics1);
            Double portMetric2 = MetricsKeys.getDoubleOrNull(MetricsKeys.portMetric, metrics2);
            if (portMetric1 != null && portMetric2 != null) {
                result = Double.compare(portMetric1.doubleValue(), portMetric2.doubleValue());
            }
            if (result == 0) {
                Long volumeCount1 = MetricsKeys.getLong(MetricsKeys.volumeCount, metrics1);
                Long volumeCount2 = MetricsKeys.getLong(MetricsKeys.volumeCount, metrics2);
                if (volumeCount1 != null && volumeCount2 != null) {
                    result = Long.compare(volumeCount2.longValue(), volumeCount1.longValue());
                }
            }
        }
        return result;
    }
}
