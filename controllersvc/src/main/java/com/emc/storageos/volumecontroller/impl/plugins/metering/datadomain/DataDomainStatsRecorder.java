/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.datadomain;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.datadomain.restapi.DataDomainClient;
import com.emc.storageos.datadomain.restapi.model.DDStatsCapacityInfo;
import com.emc.storageos.datadomain.restapi.model.DDStatsIntervalQuery;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;

public class DataDomainStatsRecorder {

    private static final long MINUTE_IN_SECONDS = 60;
    private static final long HOUR_IN_SECONDS = 60 * MINUTE_IN_SECONDS;
    private static final long DAY_IN_SECONDS = 24 * HOUR_IN_SECONDS;
    private static final long WEEK_IN_SECONDS = 7 * DAY_IN_SECONDS;

    private Logger _log = LoggerFactory.getLogger(DataDomainStatsRecorder.class);

    private ZeroRecordGenerator zeroRecordGenerator;

    private CassandraInsertion statsColumnInjector;

    /**
     * Constructor.
     * 
     * @param zeroRecordGenerator
     * @param statsColumnInjector
     */
    public DataDomainStatsRecorder(ZeroRecordGenerator zeroRecordGenerator,
            CassandraInsertion statsColumnInjector) {
        this.zeroRecordGenerator = zeroRecordGenerator;
        this.statsColumnInjector = statsColumnInjector;
    }

    /**
     * Adds capacity stats
     * 
     * @param quota
     * @param keyMap
     * @param fsNativeGuid native Guid of the file share
     * @param isilonApi
     * @return the stat
     */
    public Stat addUsageInfo(DDStatsCapacityInfo statsCapInfo, Map<String, Object> keyMap,
            String fsNativeGuid, DataDomainClient ddClient) {
        Stat stat = zeroRecordGenerator.injectattr(keyMap, fsNativeGuid, null);

        if (stat != null) {
            try {
                DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
                long measurementTimePeriodInSec = 0;
                DDStatsIntervalQuery granularity = (DDStatsIntervalQuery)
                        keyMap.get(Constants._Granularity);
                switch (granularity) {
                    case hour:
                        measurementTimePeriodInSec = HOUR_IN_SECONDS;
                        break;
                    case day:
                        measurementTimePeriodInSec = DAY_IN_SECONDS;
                        break;
                    case week:
                        measurementTimePeriodInSec = WEEK_IN_SECONDS;
                        break;
                }
                stat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));
                // DD returns epochs (seconds) - convert to ms
                stat.setTimeInMillis(statsCapInfo.getCollectionEpoch() * 1000);

                long used = statsCapInfo.getLogicalCapacity().getUsed();
                long total = statsCapInfo.getLogicalCapacity().getTotal();

                // Convert data written from Bytes/sec to Bytes
                long preCompressionBytesWritten = 0;
                long postCompressionBytesWritten = 0;
                float compressionFactor = 1;
                if ((statsCapInfo != null) &&
                        (statsCapInfo.getDataWritten() != null)) {
                    preCompressionBytesWritten = statsCapInfo.getDataWritten().getPreCompWritten();
                    postCompressionBytesWritten = statsCapInfo.getDataWritten().getPostCompWritten();
                    compressionFactor = statsCapInfo.getDataWritten().getCompressionFactor();
                }
                keyMap.put(Constants._FilePreCompressionBytesWritten, preCompressionBytesWritten);
                keyMap.put(Constants._FilePostCompressionBytesWritten, postCompressionBytesWritten);
                keyMap.put(Constants._CompressionRatio, compressionFactor);

                // Provisioned capacity is not available for mtrees
                stat.setAllocatedCapacity(used);
                stat.setBandwidthIn(preCompressionBytesWritten);

                statsColumnInjector.injectColumns(stat, dbClient);

                _log.debug(String.format("Stat: %s: %s: provisioned(): used(%s)",
                        stat.getResourceId(), fsNativeGuid, used));
            } catch (DatabaseException ex) {
                _log.error("Query to db failed for FileShare id {}, skipping recording usage stat.",
                        stat.getResourceId(), ex);
            }
        }

        return stat;
    }

    /**
     * Adds performance stats
     * 
     * @param quota
     * @param keyMap
     * @param fsNativeGuid native Guid of the file share
     * @param isilonApi
     * @return the stat
     */
    // TODO
    // PErformance stats are not currently available for mtrees
    // public Stat addPerformanceStats() {
    //
    // }

}
