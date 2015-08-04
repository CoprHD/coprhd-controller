/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.netapp;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;

public class NetAppStatsRecorder {

    private Logger _log = LoggerFactory.getLogger(NetAppStatsRecorder.class);

    private ZeroRecordGenerator zeroRecordGenerator;

    private CassandraInsertion statsColumnInjector;

    /**
     * Instantiates a new net app stats recorder.
     * 
     * @param zeroRecordGenerator
     * @param statsColumnInjector
     */
    public NetAppStatsRecorder(ZeroRecordGenerator zeroRecordGenerator,
            CassandraInsertion statsColumnInjector) {
        this.zeroRecordGenerator = zeroRecordGenerator;
        this.statsColumnInjector = statsColumnInjector;
    }

    /**
     * Adds a Stat for usage.
     * 
     * @param fsNativeGuid native Guid of the file share
     * @param keyMap
     * @param provisioned
     * @param usage
     * @return the stat
     */
    public Stat addUsageStat(String fsNativeGuid, Map<String, Object> keyMap, Map<String, Number> metrics) {
        Stat stat = zeroRecordGenerator.injectattr(keyMap, fsNativeGuid, null);
        if (stat != null) {
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            stat.setTimeInMillis((Long) keyMap.get(Constants._TimeCollected));
            stat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));

            statsColumnInjector.injectColumns(stat, dbClient);

            stat.setProvisionedCapacity((Long) metrics.get(Constants.SIZE_TOTAL));
            stat.setAllocatedCapacity((Long) metrics.get(Constants.SIZE_USED));
            stat.setSnapshotCapacity((Long) metrics.get(Constants.SNAPSHOT_BYTES_RESERVED));
            stat.setSnapshotCount((Integer) metrics.get(Constants.SNAPSHOT_COUNT));

            _log.debug(String.format("Stat: %s: %s: provisioned(%s): used(%s)",
                    stat.getResourceId(), fsNativeGuid, stat.getProvisionedCapacity(),
                    stat.getAllocatedCapacity()));
            _log.debug(String.format("Stat: %s: %s: snapshot capacity (%s), count (%s)",
                    stat.getResourceId(), fsNativeGuid, stat.getSnapshotCapacity(),
                    stat.getSnapshotCount()));
        }
        return stat;
    }
}
