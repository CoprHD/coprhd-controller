/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getSysClient;

import java.util.List;

import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.model.sys.healthmonitor.NodeDiagnostics;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.NodeStats;
import com.emc.vipr.model.sys.healthmonitor.StorageStats;
import com.google.common.collect.Lists;

public class MonitorUtils {

    public static List<NodeStats> getNodeStats() {
        return getNodeStats(getSysClient());
    }

    public static List<NodeHealth> getNodeHealth() {
        return getNodeHealth(getSysClient());
    }

    public static StorageStats getStorageStats() {
        return getStorageStats(getSysClient());
    }

    public static List<NodeDiagnostics> getNodeDiagnostics() {
        return getNodeDiagnotics(getSysClient());
    }

    public static List<NodeStats> getNodeStats(ViPRSystemClient client) {
        return client.health().getStats().getNodeStatsList();
    }

    public static List<NodeHealth> getNodeHealth(ViPRSystemClient client) {
        return client.health().getHealth().getNodeHealthList();
    }

    public static StorageStats getStorageStats(ViPRSystemClient client) {
        return client.health().getStorageStats();
    }

    public static List<NodeDiagnostics> getNodeDiagnotics(ViPRSystemClient client) {
        return client.health().getDiagnostics(null, null).getNodeDiagnosticsList();
    }

    public static NodeStats getNodeStats(String nodeId) {
        try {
            for (NodeStats node : getSysClient().health().getStats(Lists.newArrayList(nodeId), null, null).getNodeStatsList()) {
                if (node.getNodeId().equals(nodeId)) {
                    return node;
                }
            }
            return null;
        } catch (ServiceErrorException e) {
            if (e.getHttpCode() == 400) {
                return null;
            }
            throw e;
        }
    }

    public static NodeHealth getNodeHealth(String nodeId) {
        try {
            for (NodeHealth node : getSysClient().health().getHealth(Lists.newArrayList(nodeId), null).getNodeHealthList()) {
                if (node.getNodeId().equals(nodeId)) {
                    return node;
                }
            }
            return null;
        } catch (ServiceErrorException e) {
            if (e.getHttpCode() == 400) {
                return null;
            }
            throw e;
        }
    }

    public static NodeDiagnostics getNodeDiagnostics(String nodeId) {
        try {
            for (NodeDiagnostics node : getSysClient().health().getDiagnostics(Lists.newArrayList(nodeId), null).getNodeDiagnosticsList()) {
                if (node.getNodeId().equals(nodeId)) {
                    return node;
                }
            }
            return null;
        } catch (ServiceErrorException e) {
            if (e.getHttpCode() == 400) {
                return null;
            }
            throw e;
        }
    }
}
