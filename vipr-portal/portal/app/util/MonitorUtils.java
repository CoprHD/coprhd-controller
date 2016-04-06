/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getSysClient;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import play.mvc.Util;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.project.ProjectRestRep;
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
        List<NodeHealth> nodeList = client.health().getHealth().getNodeHealthList();
        Collections.sort(nodeList , new Comparator<NodeHealth>() {
        	public int compare(NodeHealth n1, NodeHealth n2) {
        		return n1.getNodeName().compareTo(n2.getNodeName());
        }
        });
        return nodeList;
    }
    
    public static StorageStats getStorageStats(ViPRSystemClient client) {
        return client.health().getStorageStats();
    }

    public static List<NodeDiagnostics> getNodeDiagnotics(ViPRSystemClient client) {
        return client.health().getDiagnostics(null).getNodeDiagnosticsList();
    }

    public static NodeStats getNodeStats(String nodeId) {
        try {
            for (NodeStats node : getSysClient().health().getStats(Lists.newArrayList(nodeId), null).getNodeStatsList()) {
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
            for (NodeHealth node : getSysClient().health().getHealth(Lists.newArrayList(nodeId)).getNodeHealthList()) {
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
            for (NodeDiagnostics node : getSysClient().health().getDiagnostics(Lists.newArrayList(nodeId)).getNodeDiagnosticsList()) {
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
