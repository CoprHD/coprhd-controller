/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.xtremio;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPerformanceResponse;
import com.google.common.collect.ArrayListMultimap;

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
         * - Get the last processing time for the system,
         * - If previously not queried or if it was long back, collect data for last one day
         * 
         * - Query the XEnv metrics from from-time to current-time with granularity based on cycle time gap
         * - Calculate the average for each XEnv
         * - Then calculate the average of all XEnvs for the system
         * 
         * - calculate the new average (exponential average?)
         * - persist in data object (existing avgPortMetric or new field?)
         */

        log.info("Collecting CPU usage for XtremIO system {}", system.getNativeGuid());
        Long lastProcessedTime = system.getLastMeteringRunTime();
        Long currentTime = System.currentTimeMillis();
        Long oneDayTime = TimeUnit.DAYS.toMillis(1);
        if (lastProcessedTime < 0 || ((currentTime - lastProcessedTime) > oneDayTime)) {
            lastProcessedTime = currentTime - oneDayTime;   // last 1 day
        }
        String granularity = getGranularity(lastProcessedTime, currentTime);
        SimpleDateFormat format = new SimpleDateFormat(XtremIOConstants.DATE_FORMAT);
        String fromTime = format.format(new Date(lastProcessedTime));
        String toTime = format.format(new Date(currentTime));

        // granularity is kept as 1 hour so that the minimum granular data obtained is hour wise
        XtremIOPerformanceResponse response = xtremIOClient.getXtremIOObjectPerformance(xtremIOClusterName,
                XtremIOConstants.XTREMIO_ENTITY_TYPE.XEnv.name(), XtremIOConstants.FROM_TIME, fromTime,
                XtremIOConstants.TO_TIME, toTime, XtremIOConstants.GRANULARITY, granularity);
        log.info("Response - Members: {}", Arrays.toString(response.getMembers()));
        log.info("Response - Counters: {}", Arrays.deepToString(response.getCounters()));

        // Segregate the responses by XEnv
        ArrayListMultimap<String, Integer> xEnvToCPUvalues = ArrayListMultimap.create();
        int xEnvIndex = getIndexForAttribute(response.getMembers(), XtremIOConstants.NAME);
        int cpuIndex = getIndexForAttribute(response.getMembers(), XtremIOConstants.AVG_CPU_USAGE);
        String[][] counters = response.getCounters();
        for (String[] counter : counters) {
            log.debug(Arrays.toString(counter));
            String xEnv = counter[xEnvIndex];
            String cpuUtilization = counter[cpuIndex];
            if (cpuUtilization != null) {
                xEnvToCPUvalues.put(xEnv, Integer.valueOf(cpuUtilization));
            }
        }

        // calculate the average usage for each XEnv
        List<Integer> avgCPUs = new ArrayList<Integer>();
        for (String xEnv : xEnvToCPUvalues.keySet()) {
            List<Integer> cpuUsageList = xEnvToCPUvalues.get(xEnv);
            int avgCPU = cpuUsageList.stream().mapToInt(Integer::intValue).sum() / cpuUsageList.size();
            log.info("XEnv: {}, collected CPU usage: {}, average: {}", xEnv, cpuUsageList.toString(), avgCPU);
            avgCPUs.add(avgCPU);
        }

        // calculate system's average usage by combining all XEnvs
        if (avgCPUs.size() > 0) {
            double systemAvgCPU = avgCPUs.stream().mapToInt(Integer::intValue).sum() / avgCPUs.size();
            log.info("System's average CPU Usage: {}", systemAvgCPU);
            // TODO compute new average
            system.setAveragePortMetrics(systemAvgCPU);
            dbClient.updateObject(system);
        }

        // portMetricsProcessor.computeStorageSystemAvgPortMetrics(accessProfile.getSystemId());

        // check Placement logic.
        // for systems where no CPU% collected, it will be 0?
    }

    /**
     * Get the granularity for the performance query based on the time gap.
     * Values: one_day, one_hour, one_minute, ten_minutes
     *
     * @param fromTime the last processed time
     * @param toTime the current time
     * @return the granularity
     */
    private String getGranularity(Long fromTime, Long toTime) {
        Long timeGap = toTime - fromTime;
        String granularity = XtremIOConstants.ONE_HOUR;    // default
        if (timeGap > TimeUnit.DAYS.toMillis(1)) {  // more than a day
            granularity = XtremIOConstants.ONE_DAY;
        } else {
            if (timeGap < TimeUnit.HOURS.toMillis(1)) {  // less than an hour
                granularity = XtremIOConstants.TEN_MINUTES;
            }
            if (timeGap < TimeUnit.MINUTES.toMillis(10)) {  // less than 10 minutes
                granularity = XtremIOConstants.ONE_MINUTE;
            }
        }
        return granularity;
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
