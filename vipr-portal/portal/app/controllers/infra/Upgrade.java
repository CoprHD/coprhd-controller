/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import com.emc.storageos.model.db.DbConsistencyStatusRestRep;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.DownloadProgress;
import com.emc.vipr.model.sys.NodeProgress;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.Maintenance;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.BourneUtil;
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
        boolean isDownloading = clusterState.equals(DOWNLOADING_CLUSTER_STATE);

        DbConsistencyStatusRestRep checkDbState = getSysClient().upgrade().getDbCheckState();
        String isDbCheckStatus = checkDbState.getStatus().toString();
        int checkProgress = checkDbState.getProgress();

        Map<String, DownloadStatus> downloadStatus = Maps.newHashMap();
        if (isDownloading) {
            DownloadProgress downloadProgress = getSysClient().upgrade().getDownloadProgress();
            downloadStatus = calculateDownloadStatus(downloadProgress);
        }

        render(clusterInfo, clusterState, newVersions, repositoryVersions, isStable, isWorking, isDownloading, downloadStatus,
                checkProgress, isDbCheckStatus);
    }


    /*
     * Method to trigger Database consistency check
     */
    public static void checkDbStatus() {
        try {
            BourneUtil.getSysClient().upgrade().triggerDbCheck();
        } catch (Exception e) {
            Logger.error(e, "Checking Database Consistency");
            flash.error(e.getMessage());
        }
        render();
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

    public static void installVersion(String version) {
        try {
            getSysClient().upgrade().setTargetVersion(version);
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
        DownloadProgress downloadProgress = getSysClient().upgrade().getDownloadProgress();
        Map<String, DownloadStatus> nodeProgress = calculateDownloadStatus(downloadProgress);

        renderJSON(nodeProgress);
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

        boolean statusChanged = !clusterState.equals(currentStatus);
        renderJSON(statusChanged);
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

    private static String calculateClusterState(ClusterInfo clusterInfo) {
        if (clusterInfo.getCurrentState().equalsIgnoreCase(ClusterInfo.ClusterState.SYNCING.toString())) {
            DownloadProgress downloadProgress = getSysClient().upgrade().getDownloadProgress();
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
