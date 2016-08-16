/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.xtremio;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPerformanceResponse;
import com.google.common.collect.ArrayListMultimap;

public class XtremIOMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(XtremIOMetricsCollector.class);

    private XtremIOClientFactory xtremioRestClientFactory;
    private PortMetricsProcessor portMetricsProcessor;

    public void setXtremioRestClientFactory(XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
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

        // Performance API is available from v2.0 onwards
        if (!xtremIOClient.isVersion2()) {
            throw XtremIOApiException.exceptions.meteringNotSupportedFor3xVersions();
        }

        String xtremIOClusterName = xtremIOClient.getClusterDetails(system.getSerialNumber()).getName();

        // TODO Full support for Metering collection.
        // Currently only the XEnv's CPU Utilization will be collected and
        // used for resource placement to choose the best XtremIO Cluster.
        // Reason for CPU over port metrics: Some port metrics like KBytesTransferred are not available for XtremIO.
        // XtremIO team also suggested to consider CPU usage over IOPs, Bandwidth, Latency.
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
        // There are two CPU sockets per Storage Controller (SC), and one distinct XENV runs on each socket.
        /**
         * Collect average CPU usage:
         * - Get the last processing time for the system,
         * - If previously not queried or if it was long back, collect data for last one day
         * 
         * - Query the XEnv metrics for last one hour/day with granularity based on cycle time gap
         * - 1. Group the XEnvs by SC,
         * - 2. For each SC:
         * - - - Take the average of 2 XEnv's CPU usages
         * - - - Calculate exponential average by calling PortMetricsProcessor.processFEAdaptMetrics()
         * - - - - (persists cpuPercentBusy, emaPercentBusy and avgCpuPercentBusy)
         * 
         * - Average of all SC's avgCpuPercentBusy values is the average CPU usage for the system
         */

        log.info("Collecting CPU usage for XtremIO system {}", system.getNativeGuid());
        // Collect metrics for last one hour always. We are not using from-time to to-time because of machine time zone differences.
        Long lastProcessedTime = system.getLastMeteringRunTime();
        Long currentTime = System.currentTimeMillis();
        Long oneDayTime = TimeUnit.DAYS.toMillis(1);
        String timeFrame = XtremIOConstants.LAST_HOUR;
        String granularity = XtremIOConstants.TEN_MINUTES;
        if (lastProcessedTime < 0 || ((currentTime - lastProcessedTime) >= oneDayTime)) {
            timeFrame = XtremIOConstants.LAST_DAY;     // last 1 day
            granularity = XtremIOConstants.ONE_HOUR;
        }

        XtremIOPerformanceResponse response = xtremIOClient.getXtremIOObjectPerformance(xtremIOClusterName,
                XtremIOConstants.XTREMIO_ENTITY_TYPE.XEnv.name(), XtremIOConstants.TIME_FRAME, timeFrame,
                XtremIOConstants.GRANULARITY, granularity);
        log.info("Response - Members: {}", Arrays.toString(response.getMembers()));
        log.info("Response - Counters: {}", Arrays.deepToString(response.getCounters()));

        // Segregate the responses by XEnv
        ArrayListMultimap<String, Double> xEnvToCPUvalues = ArrayListMultimap.create();
        int xEnvIndex = getIndexForAttribute(response.getMembers(), XtremIOConstants.NAME);
        int cpuIndex = getIndexForAttribute(response.getMembers(), XtremIOConstants.AVG_CPU_USAGE);
        String[][] counters = response.getCounters();
        for (String[] counter : counters) {
            log.debug(Arrays.toString(counter));
            String xEnv = counter[xEnvIndex];
            String cpuUtilization = counter[cpuIndex];
            if (cpuUtilization != null) {
                xEnvToCPUvalues.put(xEnv, Double.valueOf(cpuUtilization));
            }
        }

        // calculate the average usage for each XEnv for the queried period of time
        Map<String, Double> xEnvToAvgCPU = new HashMap<>();
        for (String xEnv : xEnvToCPUvalues.keySet()) {
            List<Double> cpuUsageList = xEnvToCPUvalues.get(xEnv);
            Double avgCPU = cpuUsageList.stream().mapToDouble(Double::doubleValue).sum() / cpuUsageList.size();
            log.info("XEnv: {}, collected CPU usage: {}, average: {}", xEnv, cpuUsageList.toString(), avgCPU);
            xEnvToAvgCPU.put(xEnv, avgCPU);
        }

        // calculate the average usage for each Storage controller (from it's 2 XEnvs)
        Map<URI, Double> scToAvgCPU = new HashMap<>();
        for (String xEnv : xEnvToAvgCPU.keySet()) {
            StorageHADomain sc = getStorageControllerForXEnv(xEnv, system, dbClient);
            if (sc == null) {
                log.debug("StorageHADomain not found for XEnv {}", xEnv);
                continue;
            }

            Double scCPU = scToAvgCPU.get(sc.getId());
            Double xEnvCPU = xEnvToAvgCPU.get(xEnv);
            Double avgScCPU = (scCPU == null) ? xEnvCPU : ((xEnvCPU + scCPU) / 2.0);
            scToAvgCPU.put(sc.getId(), avgScCPU);
        }

        // calculate exponential average for each Storage controller
        double emaFactor = PortMetricsProcessor.getEmaFactor(DiscoveredDataObject.Type.valueOf(system.getSystemType()));
        if (emaFactor > 1.0) {
            emaFactor = 1.0;  // in case of invalid user input
        }
        for (URI scURI : scToAvgCPU.keySet()) {
            Double avgScCPU = scToAvgCPU.get(scURI);
            StorageHADomain sc = dbClient.queryObject(StorageHADomain.class, scURI);
            log.info("StorageHADomain: {}, average CPU Usage: {}", sc.getAdapterName(), avgScCPU);

            portMetricsProcessor.processFEAdaptMetrics(avgScCPU, 0l, sc, currentTime.toString(), false);

            StringMap dbMetrics = sc.getMetrics();
            Double scAvgBusy = MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, dbMetrics);
            Double scEmaBusy = MetricsKeys.getDouble(MetricsKeys.emaPercentBusy, dbMetrics);
            Double scPercentBusy = (scAvgBusy * emaFactor) + ((1 - emaFactor) * scEmaBusy);
            MetricsKeys.putDouble(MetricsKeys.avgCpuPercentBusy, scPercentBusy, dbMetrics);
            MetricsKeys.putLong(MetricsKeys.lastProcessingTime, currentTime, dbMetrics);
            sc.setMetrics(dbMetrics);
            dbClient.updateObject(sc);
        }

        // calculate storage system's average CPU usage by combining all XEnvs
        portMetricsProcessor.computeStorageSystemAvgPortMetrics(system.getId());
    }

    /**
     * Gets the storage controller (StorageHADomain) for the given XEnv name.
     */
    private StorageHADomain getStorageControllerForXEnv(String xEnv, StorageSystem system, DbClient dbClient) {
        StorageHADomain haDomain = null;
        String haDomainNativeGUID = NativeGUIDGenerator.generateNativeGuid(system,
                xEnv.substring(0, xEnv.lastIndexOf(Constants.HYPHEN)), NativeGUIDGenerator.ADAPTER);
        URIQueryResultList haDomainQueryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(haDomainNativeGUID),
                haDomainQueryResult);
        Iterator<URI> itr = haDomainQueryResult.iterator();
        if (itr.hasNext()) {
            haDomain = dbClient.queryObject(StorageHADomain.class, itr.next());
        }
        return haDomain;
    }

    /**
     * Get the location index in the array for the given string.
     */
    private int getIndexForAttribute(String[] members, String name) {
        for (int index = 0; index < members.length; index++) {
            if (name != null && name.equalsIgnoreCase(members[index])) {
                return index;
            }
        }
        return 0;
    }
}
