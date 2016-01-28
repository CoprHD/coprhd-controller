/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.DownloadProgress;
import com.emc.vipr.model.sys.NodeProgress;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.Maintenance;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import models.datatable.DisasterRecoveryDataTable;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.BourneUtil;
import util.DisasterRecoveryUtils;
import util.MessagesUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static util.BourneUtil.*;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class Upgrade extends Controller {
    public static String TRUE = "1";
    public static String FALSE = "0";

    private static String DOWNLOADING_CLUSTER_STATE = "DOWNLOADING";

    private static String NOT_STARTED = "NOT_STARTED";

    public static void index() {
        render();
    }

    public static void clusterStatus() {
        ClusterInfo clusterInfo = getSysClient().upgrade().getClusterInfo();
        Collection<String> repositoryVersions = clusterInfo.getTargetState().getAvailable();
        Collection<String> newVersions = clusterInfo.getNewVersions() == null ? Collections.<String> emptyList() : clusterInfo
                .getNewVersions();

        String clusterState = calculateClusterState(clusterInfo);

        boolean isStable = clusterState.equalsIgnoreCase(ClusterInfo.ClusterState.STABLE.toString());
        boolean isWorking = !isStable && !clusterState.equalsIgnoreCase(ClusterInfo.ClusterState.UNKNOWN.toString());
        boolean isDownloading = clusterState.equals(DOWNLOADING_CLUSTER_STATE) || isStandbySiteDownloading();

        DbConsistencyStatusRestRep checkDbState = getSysClient().upgrade().getDbCheckState();
        String isDbCheckStatus = checkDbState.getStatus().toString();
        int checkProgress = checkDbState.getProgress();

        Map<String, Map<String, DownloadStatus>> downloadStatus = Maps.newLinkedHashMap();
        if (isDownloading) {
            SiteRestRep activeSite = DisasterRecoveryUtils.getActiveSite();
            DownloadProgress downloadProgress = getSysClient().upgrade().getDownloadProgress();
            downloadStatus.put(activeSite.getName(), calculateDownloadStatus(downloadProgress));

            for (SiteRestRep standby : DisasterRecoveryUtils.getStandbySites()) {
                downloadProgress = getSysClient().upgrade().getDownloadProgress(standby.getUuid());
                downloadStatus.put(standby.getName(), calculateDownloadStatus(downloadProgress));
            }
        }

        DisasterRecoveryDataTable dataTable = new DisasterRecoveryDataTable();

        render(clusterInfo, clusterState, newVersions, repositoryVersions, isStable, isWorking, isDownloading, downloadStatus,
                checkProgress, isDbCheckStatus, dataTable);
    }

    /*
     * Method to trigger Database consistency check
     */
    public static void checkDbStatus() {
    	ClusterInfo clusterInfo = getSysClient().upgrade().getClusterInfo();
    	String clusterState = calculateClusterState(clusterInfo);
        try {
            BourneUtil.getSysClient().upgrade().triggerDbCheck();
        } catch (Exception e) {
            Logger.error(e, "Checking Database Consistency");
            flash.error(e.getMessage());
        }
        render(clusterInfo,clusterState);
    }

    public static void checkDbStatusOK() {
        index();
    }

    /*
     * Method to cancel ongoing Database check
     */
    public static void cancelCheckDbStatus() {
        try {
            BourneUtil.getSysClient().upgrade().cancelDbCheck();
        } catch (Exception e) {
            Logger.error(e, "Cancelling Database Consistency");
            flash.error(e.getMessage());
        }
        index();
    }

    public static void checkDbProgress() {
        DbConsistencyStatusRestRep dbState = getSysClient().upgrade().getDbCheckState();
        renderJSON(dbState);
    }

    public static void installVersion(String version, boolean doPrecheck) {
        try {
            getSysClient().upgrade().setTargetVersion(version, doPrecheck);
        } catch (Exception e) {
            Logger.error(e, "Setting target version to  %s", version);
            flash.error(e.getMessage());
        }
        flash.success(MessagesUtils.get("upgrade.setTargetVersion", version));
        Maintenance.maintenance(Common.reverseRoute(Upgrade.class, "index"));
    }

    public static void removeImage(String version) {
        try {
            getSysClient().upgrade().removeImage(version, true);
        } catch (Exception e) {
            Logger.error(e, "Error removing Image %s", version);
            flash.error(e.getMessage());
        }

        index();
    }

    public static void downloadImage(String version) {
        try {
            getSysClient().upgrade().installImage(version, false);
        } catch (Exception e) {
            Logger.error(e, "Installing Image %s", version);
            flash.error(e.getMessage());
        }

        index();
    }

    public static void downloadProgress() {
        Map<String, Map<String, DownloadStatus>> siteProgress = Maps.newLinkedHashMap();

        SiteRestRep activeSite = DisasterRecoveryUtils.getActiveSite();
        DownloadProgress downloadProgress = getSysClient().upgrade().getDownloadProgress();
        siteProgress.put(activeSite.getName(), calculateDownloadStatus(downloadProgress));

        for (SiteRestRep standby : DisasterRecoveryUtils.getStandbySites()) {
            downloadProgress = getSysClient().upgrade().getDownloadProgress(standby.getUuid());
            siteProgress.put(standby.getName(), calculateDownloadStatus(downloadProgress));
        }

        renderJSON(siteProgress);
    }

    public static void cancelDownload() {
        try {
            getSysClient().upgrade().cancelInstallImage();
        } catch (Exception e) {
            Logger.error(e, "Cancelling Install Image");
            flash.error(e.getMessage());
        }

        index();
    }

    /**
     * Allows the UI to ping the backend so that it knows when the upgrade is complete
     */
    public static void statusChanged(String currentStatus) {
        ClusterInfo clusterInfo = getSysClient().upgrade().getClusterInfo();
        String clusterState = calculateClusterState(clusterInfo);

        // if the current status is downloading,
        // don't go back to the index page until download is complete on all sites
        if (currentStatus.equals(DOWNLOADING_CLUSTER_STATE)) {
            renderJSON(!clusterState.equals(currentStatus) && !isStandbySiteDownloading());
        } else {
            boolean statusChanged = !clusterState.equals(currentStatus);
            renderJSON(statusChanged);
        }
    }

    @Util
    private static boolean isStandbySiteDownloading() {
        for (SiteRestRep standby : DisasterRecoveryUtils.getStandbySites()) {
            if (SiteState.STANDBY_PAUSED.toString().equals(standby.getState()) ||
                    SiteState.STANDBY_PAUSING.toString().equals(standby.getState()) ||
                    SiteState.STANDBY_RESUMING.toString().equals(standby.getState())) {
                continue;
            }
            ClusterInfo clusterInfo = getSysClient().upgrade().getClusterInfo(standby.getUuid());
            if (calculateClusterState(clusterInfo, standby.getUuid()).equals(DOWNLOADING_CLUSTER_STATE)) {
                return true;
            }
        }
        return false;
    }

    @Util
    private static boolean isDownloadInProgress(DownloadProgress downloadProgress) {
        for (NodeProgress nodeProgress : downloadProgress.getProgress().values()) {
            if (nodeProgress.getStatus() == NodeProgress.DownloadStatus.NORMAL) {
                return true;
            }
        }

        return false;
    }

    @Util
    static Map<String, DownloadStatus> calculateDownloadStatus(DownloadProgress downloadProgress) {
        long imageSize = downloadProgress.getImageSize();

        Map<String, DownloadStatus> nodeProgress = Maps.newHashMap();
        for (Map.Entry<String, NodeProgress> nodeEntry : downloadProgress.getProgress().entrySet()) {
            String nodeId = nodeEntry.getKey().substring("syssvc-".length());

            nodeProgress.put(nodeId, new DownloadStatus(calculatePercentage(nodeEntry.getValue().getBytesDownloaded(), imageSize),
                    nodeEntry.getValue().getStatus().toString()));
        }

        return nodeProgress;
    }

    @Util
    private static int calculatePercentage(long bytes, long total) {
        return (int) Math.round(((double) bytes / (double) total) * 100);
    }

    @Util
    private static String calculateClusterState(ClusterInfo clusterInfo) {
        return calculateClusterState(clusterInfo, null);
    }

    @Util
    private static String calculateClusterState(ClusterInfo clusterInfo, String siteId) {
        if (clusterInfo.getCurrentState().equalsIgnoreCase(ClusterInfo.ClusterState.SYNCING.toString())) {
            DownloadProgress downloadProgress = getSysClient().upgrade().getDownloadProgress(siteId);
            if (isDownloadInProgress(downloadProgress)) {
                return DOWNLOADING_CLUSTER_STATE;
            }
        }

        return clusterInfo.getCurrentState();
    }

    public static class DownloadStatus {
        Integer percent;
        String status;

        public DownloadStatus(Integer percent, String status) {
            this.percent = percent;
            this.status = status;
        }

        public boolean isErrorStatus() {
            return !(status.equals("COMPLETED") || status.equals("NORMAL"));
        }
    }

}
