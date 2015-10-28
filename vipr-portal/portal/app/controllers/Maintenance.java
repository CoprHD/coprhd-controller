/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.UpgradeFailureInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.model.sys.ClusterInfo;
import controllers.deadbolt.Deadbolt;
import controllers.security.Security;
import play.Logger;
import play.mvc.Controller;
import play.mvc.With;
import plugin.StorageOsPlugin;
import util.BourneUtil;

public class Maintenance extends Controller {
    public static void maintenance(String targetUrl) {
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = getClusterState();
        } catch (Exception e) {
            // This is not necessarily a problem. The cluster could already be down
            Common.handleExpiredToken(e);
            Logger.info(e, "Failed to get cluster state");
            clusterInfo = defaultClusterInfo(clusterInfo);
        }
        render(targetUrl, clusterInfo);
    }
    
    public static void fail(String targetUrl) {
        CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
        UpgradeFailureInfo failureInfo = coordinatorClient.queryRuntimeState(Constants.UPGRADE_FAILURE_INFO, UpgradeFailureInfo.class);
        
        Logger.info("UpgradeFailureInfo=%s", failureInfo);
        render(failureInfo);
    }

    public static MigrationStatus getMigrationStatus() {
        CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
        return coordinatorClient.getMigrationStatus();
    }
    public static void clusterState() {
        request.format = "json";
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = getClusterState();
            Logger.info("cluster status %s", clusterInfo.getCurrentState());
        } catch (ViPRHttpException e) {
            Common.handleExpiredToken(e);
            Logger.error(e, "Failed to get cluster state");
            error(e.getHttpCode(), e.getMessage());
        } catch (Exception e) {
            Logger.error(e, "Failed to get cluster state");
            error(e.getMessage());
        }
        renderJSON(clusterInfo);
    }

    private static ClusterInfo getClusterState() {
        ClusterInfo clusterInfo = null;
        if (getMigrationStatus() == MigrationStatus.FAILED) {
            clusterInfo = getClusterStateFromCoordinator();
        } else if (Security.isSystemAdmin() || Security.isSecurityAdmin() || Security.isSystemMonitor()) {
            clusterInfo = getClusterStateFromSysClient();
        } else {
            clusterInfo = getClusterStateFromCoordinator();
        }
        return defaultClusterInfo(clusterInfo);
    }

    private static ClusterInfo getClusterStateFromSysClient() {
        return BourneUtil.getSysClient().upgrade().getClusterInfo();
    }

    private static ClusterInfo getClusterStateFromCoordinator() {
        if (StorageOsPlugin.isEnabled()) {
            CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
            ClusterInfo.ClusterState clusterState = coordinatorClient.getControlNodesState();
            if (clusterState != null) {
                ClusterInfo clusterInfo = new ClusterInfo();
                clusterInfo.setCurrentState(clusterState.toString());
                return clusterInfo;
            }
        }
        return null;
    }

    private static ClusterInfo defaultClusterInfo(ClusterInfo clusterInfo) {
        if (clusterInfo == null) {
            clusterInfo = new ClusterInfo();
        }
        if (StringUtils.isBlank(clusterInfo.getCurrentState())) {
            clusterInfo.setCurrentState(ClusterInfo.ClusterState.UNKNOWN.toString());
        }
        return clusterInfo;
    }
}
