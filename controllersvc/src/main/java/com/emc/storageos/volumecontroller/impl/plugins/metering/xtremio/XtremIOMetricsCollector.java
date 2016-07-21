/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.xtremio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPerformanceResponse;

public class XtremIOMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(XtremIOMetricsCollector.class);

    private XtremIOClientFactory xtremioRestClientFactory;

    public void setXtremioRestClientFactory(XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }

    /**
     * Collect metrics.
     *
     * @param system the system
     * @param dbClient the db client
     * @throws Exception
     */
    public void collectMetrics(StorageSystem system, DbClient dbClient) throws Exception {
        log.info("Collecting statistics for XtremIO system {}", system.getNativeGuid());
        XtremIOClient xtremIOClient = XtremIOProvUtils.getXtremIOClient(dbClient, system, xtremioRestClientFactory);
        String xtremIOClusterName = xtremIOClient.getClusterDetails(system.getSerialNumber()).getName();
        // TODO Full support for Metering collection.
        // Currently only the XEnv's CPU Utilization will be collected and
        // used for resource placement to choose the best XtremIO Cluster.
        collectXEnvCPUUtilization(system, dbClient, xtremIOClient, xtremIOClusterName);
    }

    /**
     * Collect the CPU Utilization for all XEnv's in the cluster.
     *
     * @param system the system
     * @param dbClient the db client
     * @param xtremIOClient the xtremio client
     * @param xioClusterName the xtremio cluster name
     * @throws Exception
     */
    private void collectXEnvCPUUtilization(StorageSystem system, DbClient dbClient,
            XtremIOClient xtremIOClient, String xtremIOClusterName) throws Exception {
        // An XENV(XtremIO Environment) is composed of software defined modules responsible for internal data path on the array.
        // There are two CPU sockets per SC, and one distinct XENV runs on each socket.
        /**
         * Collect average CPU usage:
         * - Get the last processing time from the system,
         * - Query the XEnv metrics from from-time to current-time
         * --(add checks for from-time)
         * 
         * - persist in Stat object?
         * - calculate the new average (required?)
         * 
         * - persist in data object (existing avgPortMetric or new field?)
         */


        XtremIOPerformanceResponse response = xtremIOClient.getXtremIOObjectPerformance(xtremIOClusterName,
                XtremIOConstants.XTREMIO_ENTITY_TYPE.XEnv.name(), "from-time", "2016-07-19 00:00:00", "to-time", "2016-07-19 23:00:00",
                "granularity", "one_hour");
        log.info(response.getMembers().toString());
        log.info(response.getCounters().toString());

        // portMetricsProcessor.computeStorageSystemAvgPortMetrics(accessProfile.getSystemId());

        // check Placement logic.
        // for systems where no CPU% collected, it will be 0?
    }
}
