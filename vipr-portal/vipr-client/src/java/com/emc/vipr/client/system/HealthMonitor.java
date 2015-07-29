/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import static com.emc.vipr.client.system.impl.PathConstants.MONITOR_DIAGNOSTICS_URL;
import static com.emc.vipr.client.system.impl.PathConstants.MONITOR_HEALTH_URL;
import static com.emc.vipr.client.system.impl.PathConstants.MONITOR_STATS_URL;
import static com.emc.vipr.client.system.impl.PathConstants.MONITOR_STORAGE_URL;
import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;

import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.model.sys.healthmonitor.StorageStats;
import com.emc.vipr.model.sys.healthmonitor.DiagnosticsRestRep;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.healthmonitor.HealthRestRep;
import com.emc.vipr.model.sys.healthmonitor.StatsRestRep;

public class HealthMonitor {

    private static final String NODE_ID_PARAM = "node_id";
    private static final String INTERVAL_PARAM = "interval";
    private static final String VERBOSE_PARAM = "verbose";
    private static final String VERBOSE = "1";

    private RestClient client;

    public HealthMonitor(RestClient client) {
        this.client = client;
    }

    /**
     * Convenience method to get stats of all nodes.
     * <p>
     * Get statistics of virtual machine and its active services Virtual machine stats include memory usage, I/O for each device, load
     * average numbers Service stats include service memory usage, command that invoked it, file descriptors count and other stats (uptime,
     * start time, thread count).
     * <p>
     * If interval value is passed it will return differential disk stats: difference between first report (contains stats for the time
     * since system startup) and second report (stats collected during the interval since the first report).
     * <p>
     * API Call: GET /monitor/stats
     * 
     * @return The stats response
     */
    public StatsRestRep getStats() {
        return getStats(null, null);
    }

    /**
     * Get statistics of virtual machine and its active services Virtual machine
     * stats include memory usage, I/O for each device, load average numbers Service
     * stats include service memory usage, command that invoked it, file descriptors
     * count and other stats (uptime, start time, thread count).
     * <p>
     * If interval value is passed it will return differential disk stats: difference between first report (contains stats for the time
     * since system startup) and second report (stats collected during the interval since the first report).
     * <p>
     * API Call: GET /monitor/stats
     * 
     * @return The stats response
     */
    public StatsRestRep getStats(List<String> nodeIds, Integer interval) {
        UriBuilder builder = client.uriBuilder(MONITOR_STATS_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeIds);
        addQueryParam(builder, INTERVAL_PARAM, interval);

        return client.getURI(StatsRestRep.class, builder.build());
    }

    /**
     * Gets health of node and its services. Convenience method to get health on all nodes.
     * <p>
     * Node health status: Good - when node is reachable and all its services are GOOD Unavailable - when node is not reachable Degraded -
     * when node is reachable and any of its service is Unavailable/Degraded Node/syssvc Unavailable - when node is down or syssvc is not
     * Unavailable on the node
     * <p>
     * Service health status: Good - when a service is up and running Unavailable - when a service is not running but is registered in
     * coordinator Restarted - when service is restarting
     * <p>
     * API Call: GET /monitor/health
     * 
     * @return The health response
     */
    public HealthRestRep getHealth() {
        return getHealth(null);
    }

    /**
     * Gets health of node and its services.
     * <p>
     * Node health status: Good - when node is reachable and all its services are GOOD Unavailable - when node is not reachable Degraded -
     * when node is reachable and any of its service is Unavailable/Degraded Node/syssvc Unavailable - when node is down or syssvc is not
     * Unavailable on the node
     * <p>
     * Service health status: Good - when a service is up and running Unavailable - when a service is not running but is registered in
     * coordinator Restarted - when service is restarting
     * <p>
     * API Call: GET /monitor/health
     * 
     * @param nodeIds Node ids for which health stats are collected.
     * @return The health response
     */
    public HealthRestRep getHealth(List<String> nodeIds) {
        UriBuilder builder = client.uriBuilder(MONITOR_HEALTH_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeIds);

        return client.getURI(HealthRestRep.class, builder.build());
    }

    /**
     * Gets the diagnostic results for all virtual machines in a ViPR
     * controller appliance. Also gives test details when verbose option is set.
     * <p>
     * API Call: GET /monitor/diagnostics
     * 
     * @param nodeIds Node ids for which diagnostic results are collected.
     * @param verbose If true, will run command with -v option.
     * @return The diagnostic test results
     */
    public DiagnosticsRestRep getDiagnostics(List<String> nodeIds, boolean verbose) {
        UriBuilder builder = client.uriBuilder(MONITOR_DIAGNOSTICS_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeIds);
        if (verbose) {
            addQueryParam(builder, VERBOSE_PARAM, VERBOSE);
        }

        return client.getURI(DiagnosticsRestRep.class, builder.build());
    }

    /**
     * Gets the diagnostic results for all virtual machines in a ViPR
     * controller appliance. Non-verbose.
     * 
     * @param nodeIds Node ids for which diagnostic results are collected.
     * @return
     */
    public DiagnosticsRestRep getDiagnostics(List<String> nodeIds) {
        return getDiagnostics(nodeIds, false);
    }

    /**
     * Get the current capacity for object, file and block storage.
     * <p>
     * API Call: GET /monitor/storage
     * 
     * @return Storage stats for controller (file & block) and object.
     */
    public StorageStats getStorageStats() {
        return client.get(StorageStats.class, MONITOR_STORAGE_URL);
    }
}
