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
        String xioClusterName = xtremIOClient.getClusterDetails(system.getSerialNumber()).getName();
        // TODO collect array metrics, adapter metrics
        collectPortMetrics(system, dbClient, xtremIOClient, xioClusterName);
    }

    /**
     * Collect port metrics.
     *
     * @param system the system
     * @param dbClient the db client
     * @param xtremIOClient the xtrem io client
     * @param xioClusterName the xio cluster name
     */
    private void collectPortMetrics(StorageSystem system, DbClient dbClient, XtremIOClient xtremIOClient, String xioClusterName) {
        // TODO Auto-generated method stub

    }
}
