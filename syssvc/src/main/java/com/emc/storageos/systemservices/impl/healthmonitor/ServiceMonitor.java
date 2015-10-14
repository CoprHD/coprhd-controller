package com.emc.storageos.systemservices.impl.healthmonitor;

import java.lang.String;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.jobs.JobConstants;

/**
 * Service monitor will check dbsvc and geodbsvc status timely, and persist timestamp and downtime info to ZK.
 */
public class ServiceMonitor {
    private static final Logger log = LoggerFactory.getLogger(ServiceMonitor.class);
    private List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);
    private static final String SERVICE_MONITOR_LOCK = "servicemonitor";
    // Monitor check service status every 15 mins by default
    private static final long MONITOR_CHECK_INTERVAL = JobConstants.LAG_BETWEEN_RUNS_ALERTS * TimeUtils.SECONDS;

    @Autowired
    private CoordinatorClientExt coordinator;

    public ServiceMonitor() {
    }

    /**
     * Monitor dbsvc and geodbsvc status
     */
    public void monitor() {
        log.info("Begin to update service monitor info");
        try (AutoCloseable lock = getServiceMonitorLock()) {
            for (String serviceName : serviceNames) {
                log.info("Updating monitor info for {} begin", serviceName);
                List<String> availableNodes = coordinator.getServiceAvailableNodes(serviceName);
                Map<String, String> monitorInfo = queryServiceMonitorInfo(serviceName);
                Map<String, String> updatedMonitorInfo = getUpdatedMonitorInfo(serviceName, availableNodes, monitorInfo);
                persistServiceMonitorInfo(serviceName, updatedMonitorInfo);
                log.info("Updating monitor info for {} finish", serviceName);
            }
        } catch (Exception e) {
            log.warn("Failed to update service monitor info", e);
        }
    }

    private AutoCloseable getServiceMonitorLock() throws Exception {
        return new InterProcessLockHolder(this.coordinator.getCoordinatorClient(), SERVICE_MONITOR_LOCK, this.log);
    }

    /**
     * Get updated service monitor info according to current service status
     */
    private Map<String, String> getUpdatedMonitorInfo(String serviceName, List<String> activeNodes,
                                                         Map<String, String> monitorInfo) {
        String offlineInfo = monitorInfo.get(Constants.MONITOR_OFFLINE_INFO);
        String updatedOfflineInfo = updateOfflineInfo(serviceName, activeNodes, offlineInfo);

        String timestampInfo = monitorInfo.get(Constants.MONITOR_TIMESTAMP_INFO);
        String updatedTimestampInfo = updateTimestampInfo(serviceName, activeNodes, timestampInfo);

        monitorInfo.put(Constants.MONITOR_OFFLINE_INFO, updatedOfflineInfo);
        monitorInfo.put(Constants.MONITOR_TIMESTAMP_INFO, updatedTimestampInfo);

        log.info("Get updated monitor info for {}: {}", serviceName, monitorInfo);
        return monitorInfo;
    }

    private String updateOfflineInfo(String serviceName, List<String> activeNodes, String offlineInfo) {
        Map<String, String> offlineMap = convertStringToMap(offlineInfo);

        long currentTimeStamp = TimeUtils.getCurrentTime();
        String lastTimeStampStr = offlineMap.get(Constants.TIMESTAMP);
        long lastTimeStamp = (lastTimeStampStr == null) ? currentTimeStamp : Long.parseLong(lastTimeStampStr);
        long interval = Math.min((currentTimeStamp - lastTimeStamp), MONITOR_CHECK_INTERVAL);
        offlineMap.put(Constants.TIMESTAMP, String.valueOf(currentTimeStamp));
        log.info("Last timestamp is: {}, current timestamp is: {}", lastTimeStamp, currentTimeStamp);

        int nodeCount = coordinator.getNodeCount();
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr" + i;
            if (!activeNodes.contains(nodeId)) {
                long lastDownTime = offlineMap.containsKey(nodeId) ? Long.parseLong(offlineMap.get(nodeId)) : 0;
                long updatedDownTime = lastDownTime + interval;
                offlineMap.put(nodeId, String.valueOf(updatedDownTime));
                log.info(String.format("Service(%s) of node(%s) has been unavailable for %s mins",
                        serviceName, nodeId, updatedDownTime / TimeUtils.MINUTES));
            } else {
                if (offlineMap.keySet().contains(nodeId)) {
                    offlineMap.remove(nodeId);
                    log.info("Service({}) of node({}) is recovered", serviceName, nodeId);
                }
            }
        }
        return convertMapToString(offlineMap);
    }

    private String updateTimestampInfo(String serviceName, List<String> activeNodes, String timestampInfo) {
        Map<String, String> timestampMap = convertStringToMap(timestampInfo);
        String timestamp = String.valueOf(TimeUtils.getCurrentTime());
        for (String nodeId : activeNodes){
            timestampMap.put(nodeId, timestamp);
            log.info(String.format("Service(%s) of node(%s) timestamp has been updated to %s",
                    serviceName, nodeId, timestamp));
        }
        return convertMapToString(timestampMap);
    }

    private String convertMapToString(Map<String, String> mapInfo) {
        List<String> nodeIds = new ArrayList<String>();
        for (Map.Entry<String, String> entry : mapInfo.entrySet()) {
            nodeIds.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
        }
        Collections.sort(nodeIds);
        return Strings.join(",", nodeIds);
    }

    private Map<String, String> convertStringToMap(String stringInfo) {
        Map<String, String> mapInfo = new HashMap<String, String>();
        if (stringInfo != null  && stringInfo.length() > 0) {
            List<String> nodeIds = Arrays.asList(stringInfo.replaceAll("\\[|\\]", "").split("\\s*,\\s*"));
            for (String node : nodeIds) {
                String[] nodeInfo = node.split("=");
                mapInfo.put(nodeInfo[0], nodeInfo[1]);
            }
        }
        return mapInfo;
    }

    /**
     * Query service monitor info from ZK
     */
    public Map<String, String> queryServiceMonitorInfo(String serviceName) {
        Map<String, String> monitorInfo = new HashMap<String, String>();
        Configuration config = coordinator.getCoordinatorClient().queryConfiguration(
                Constants.SERVICE_MONITOR_CONFIG, serviceName);
        if (config == null) {
            return monitorInfo;
        }
        String offlineInfo = config.getConfig(Constants.MONITOR_OFFLINE_INFO);
        if (offlineInfo != null && offlineInfo.length() > 0) {
            log.info("Get service offline info: {}", offlineInfo);
            monitorInfo.put(Constants.MONITOR_OFFLINE_INFO, offlineInfo);
        }
        String timestampInfo = config.getConfig(Constants.MONITOR_TIMESTAMP_INFO);
        if (timestampInfo != null && timestampInfo.length() > 0) {
            log.info("Get service timestamp info: {}", timestampInfo);
            monitorInfo.put(Constants.MONITOR_TIMESTAMP_INFO, timestampInfo);
        }
        log.info("Query service monitor info from zk successfully: {}", monitorInfo);
        return monitorInfo;
    }

    /**
     * Persist service monitor info to ZK(path=/config/healthmonitor/dbsvc and /config/healthmonitor/geodbsvc)
     */
    public void persistServiceMonitorInfo(String serviceName, Map<String, String> monitorInfo) {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(Constants.SERVICE_MONITOR_CONFIG);
        config.setId(serviceName);
        config.setConfig(Constants.MONITOR_OFFLINE_INFO, monitorInfo.get(Constants.MONITOR_OFFLINE_INFO));
        config.setConfig(Constants.MONITOR_TIMESTAMP_INFO, monitorInfo.get(Constants.MONITOR_TIMESTAMP_INFO));

        coordinator.getCoordinatorClient().persistServiceConfiguration(config);
        log.info("Persist service monitor info to zk successfully");
    }
}
