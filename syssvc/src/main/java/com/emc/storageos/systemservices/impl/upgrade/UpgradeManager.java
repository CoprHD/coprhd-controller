/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.DownloadingInfo;
import static com.emc.storageos.coordinator.client.model.Constants.*;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.systemservices.exceptions.*;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.util.AbstractManager;
import com.emc.vipr.model.sys.ClusterInfo.NodeState;
import com.emc.vipr.model.sys.NodeProgress.DownloadStatus;

public class UpgradeManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(UpgradeManager.class);

    private static final String dbNoEncryptFlagFile = "/data/db/no_db_encryption";

    // max number of tries of connecting remote repository
    private final static int MAX_REPO_RETRIES = 3;
    // time out interval (in millisecond)
    private final static int TIMEOUT_INTERVAL = 5 * 60 * 1000;

    @Autowired
    PropertyManager propertyManager;

    private RemoteRepository remoteRepository;

    // local and target info properties
    private RepositoryInfo localInfo;
    private RepositoryInfo targetInfo;

    private static boolean isValidRepo;

    // current number of tries of connecting remote repository
    private int tryRepoCnt = 0;
    // timer expire time
    private long expireTime = 0;

    public LocalRepository getLocalRepository() {
        return localRepository;
    }

    public RemoteRepository getRemoteRepository() {
        return remoteRepository;
    }

    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_UPGRADE_MANAGER;
    }

    /**
     * Register repository info listener to monitor repository version changes
     */
    private void addRepositoryInfoListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new RepositoryInfoListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for repository info target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for repository info target znode");
    }

    /**
     * the listener class to listen the repository target node change.
     */
    class RepositoryInfoListener implements NodeListener {
        public String getPath() {
            return String.format("/config/%s/%s", RepositoryInfo.CONFIG_KIND, RepositoryInfo.CONFIG_ID);
        }

        /**
         * called when user update the target version
         */
        @Override
        public void nodeChanged() {
            log.info("Repository info changed. Waking up the upgrade manager...");
            wakeup();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("Repository info connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the upgrade manager...");
                wakeup();
            }
        }
    }

    @Override
    protected void innerRun() {
        final String svcId = coordinator.getMySvcId();
        boolean dbEncrypted = false;
        boolean dbCurrentVersionEncrypted = false;
        boolean isDBMigrationDone = false;
        isValidRepo = localRepository.isValidRepository();

        addRepositoryInfoListener();

        while (doRun) {
            log.debug("Main loop: Start");

            shortSleep = false;

            // Step0: check DB encryption status and change it if necessary
            try {
                dbEncrypted = isDbEncrypt();

                dbCurrentVersionEncrypted = isDbCurrentVersionEncrypted();
                isDBMigrationDone = coordinator.isDBMigrationDone();
            } catch (Exception e) {
                log.info("Step0: Exception when getting DB encryption status and will be retried: {}", e.getMessage());
                retrySleep();
                continue;
            }

            log.info("Step0: dbCurrentVersionEncrypted={} dbEncrypted={} migration done={}",
                    new Object[] { dbCurrentVersionEncrypted, dbEncrypted, isDBMigrationDone });

            if (isDBMigrationDone && !dbEncrypted) {
                // we've finished the upgrade, so
                // turn on db encrypt feature then reboot
                enableDbEncrypt();
                log.info("enable db encryption so re-configure then restart geodbsvc and dbsvc");
                reconfigAndStartDBSerivces();

            } else if (!isDBMigrationDone && !dbCurrentVersionEncrypted && dbEncrypted) {
                disableDbEncrypt();
                log.info("disable db encryption, so re-configure then restart geodbsvc and dbsvc");
                shortSleep = true;
            }

            if (!isDBMigrationDone && !dbCurrentVersionEncrypted && !dbEncrypted) {
                shortSleep = true;
            }

            // Step1: check if we have the upgrade lock
            boolean hasLock;
            try {
                hasLock = coordinator.hasPersistentLock(svcId, upgradeLockId);
            } catch (Exception e) {
                log.info("Step1: Failed to verify if the current node has the upgrade lock ", e);
                retrySleep();
                continue;
            }

            if (hasLock) {

                try {
                    coordinator.releasePersistentLock(svcId, upgradeLockId);
                    log.info("Step1: Released upgrade lock for node: {}", svcId);
                    wakeupOtherNodes();
                } catch (Exception e) {
                    log.info("Step1: Failed to release the upgrade lock and will retry: {}", e.getMessage());
                    retrySleep();
                    continue;
                }
            }

            // Step2: publish current state, and set target if empty
            try {
                initializeLocalAndTargetInfo(svcId);
            } catch (Exception e) {
                log.info("Step2b failed and will be retried: {}", e.getMessage());
                retrySleep();
                continue;
            }

            // Step3: syncing repository
            final SyncInfo syncinfo = getSyncInfoCommon(localInfo, targetInfo);
            if (!syncinfo.isEmpty()) {
                // Step3: nodeInSync discovery
                String controlNodeInSync = null;
                try {
                    controlNodeInSync = getAControlNodeInSync(targetInfo);
                    log.info("Step3: Control node in syc: {}", controlNodeInSync);
                } catch (Exception e) {
                    log.info("Step3 failed and will be retried: {}", e.getMessage());
                    retrySleep();
                    continue;
                }

                // check and update images
                boolean waitSyncingFinish = syncNodes(syncinfo, controlNodeInSync, svcId);
                if (waitSyncingFinish) {
                    retrySleep();
                    continue;
                } else {
                    // For restored cluster or redeployed node, the image files don't exist.it will need to download
                    // the upgrade image from the remote repository. If the node can't connenct with the repository,
                    // or the image doesn't exist in it, syssvc would keep throwing exceptions and restart.
                    // So here break the syncing and it will retry in next check loop(loopInterval=10mins).
                    log.info("Step3: Give up syncing upgrade image, and will retry in next check loop");
                }
            }

            // Step4: if target version is changed, update
            log.info("Step4: If target version is changed, update");
            final SoftwareVersion currentVersion = localInfo.getCurrentVersion();
            final SoftwareVersion targetVersion = targetInfo.getCurrentVersion();
            if (currentVersion != null && targetVersion != null && !currentVersion.equals(targetVersion)) {
                log.info("Step4: Current version: {} != target version: {}. Switch version.", currentVersion, targetVersion);

                if (!getUpgradeLock(svcId)) {
                    retrySleep();
                    continue;
                }

                if (!isQuorumMaintained()) {
                    try {
                        coordinator.releasePersistentLock(svcId, upgradeLockId);
                    } catch (Exception e) {
                        log.error("Failed to release the upgrade lock:", e);
                    }
                    retrySleep();
                    continue;
                }

                try {
                    updateCurrentVersion(targetVersion);
                } catch (Exception e) {
                    log.info("Step4: Upgrade failed and will be retried: {}", e.getMessage());
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
            }

            // Step5: adjust dbsvc num_tokens if necessary
            log.info("Step5: Adjust dbsvc num_tokens if necessary");
            if (!coordinator.isLocalNodeTokenAdjusted()) {
                try {
                    if (!getUpgradeLock(svcId)) {
                        log.info("Step5: Get upgrade lock for adjusting dbsvc num_tokens failed. Retry");
                        retrySleep();
                        continue;
                    }

                    if (!areAllDbsvcActive()) {
                        try {
                            coordinator.releasePersistentLock(svcId, upgradeLockId);
                        } catch (Exception e) {
                            log.error("Failed to release the upgrade lock:", e);
                        }
                        retrySleep();
                        continue;
                    }
                    try (DbManagerOps dbOps = new DbManagerOps(Constants.DBSVC_NAME)) {
                        if (dbOps.adjustNumTokens()) {
                            log.info("Adjusted dbsvc num_tokens, restarting dbsvc...");
                            localRepository.restart(Constants.DBSVC_NAME);
                        }
                    }
                    continue;
                } catch (Exception e) {
                    log.error("Step5: Adjust dbsvc num_tokens failed", e);
                    retrySleep();
                    continue;
                }
            }

            // Step6: sleep
            log.info("Step6: sleep");
            longSleep();
        }
    }

    /**
     * Try to acquire the property lock, like upgrade lock, this also requires rolling reboot
     * so upgrade lock should be acquired at the same time
     * 
     * @param svcId
     * @return
     */
    private boolean getUpgradeLock(String svcId) {
        if (!coordinator.getPersistentLock(svcId, upgradeLockId)) {
            log.info("Acquiring upgrade lock failed. Retrying...");
            return false;
        }

        if (!coordinator.getPersistentLock(svcId, propertyLockId)) {
            log.info("Acquiring property lock failed. Retrying...");
            return false;
        }

        // release the property lock
        try {
            coordinator.releasePersistentLock(svcId, propertyLockId);
        } catch (Exception e) {
            log.error("Failed to release the property lock:", e);
        }
        log.info("Successfully acquired the upgrade lock.");
        return true;
    }

    private void updateCurrentVersion(SoftwareVersion targetVersion) throws Exception {
        log.info("Step4: Got upgrade lock. Update target version one more time");
        // retrieve the target version once again, since it might have been changed (reverted to be specific)
        // by the first upgraded node holding the lock during upgrade from 2.0/2.1 to 2.2.
        targetInfo = coordinator.getTargetInfo(RepositoryInfo.class);
        SoftwareVersion newTargetVersion = targetInfo.getCurrentVersion();
        if (!targetVersion.equals(newTargetVersion)) {
            log.warn("Step4: target version has changed (was: {}, now is: {}). Aborting version change.",
                    targetVersion, newTargetVersion);
        } else {
            log.info("Step4: Switching to version: {}", newTargetVersion);
            localRepository.setCurrentVersion(targetVersion);
            reboot();
        }
    }

    private String getPreviousVersion() {
        String previousVersion = null;
        log.info("Trying to find previous version from control nodes...");
        Map<String, NodeState> nodestates = coordinator.getClusterInfo().getControlNodes();
        for (Entry<String, NodeState> entry : nodestates.entrySet()) {
            if (!entry.getKey().equals(coordinator.getMyNodeId())) {
                previousVersion = entry.getValue().getCurrent();
                if (previousVersion != null) {
                    return previousVersion;
                }
            }
        }

        return null;
    }

    private void reconfigAndStartDBSerivces() {
        localRepository.reconfig();
        localRepository.restart(Constants.DBSVC_NAME);
        localRepository.restart(Constants.GEODBSVC_NAME);
    }

    private boolean isDbCurrentVersionEncrypted() {
        String currentDbVersion = coordinator.getCurrentDbSchemaVersion();
        if (currentDbVersion.startsWith("1.") || // Vipr 1.x
                currentDbVersion.startsWith("2.0") || // Vipr 2.0.x
                currentDbVersion.startsWith("2.1")) {
            return false;
        }

        return true;
    }

    private boolean isDbEncrypt() {
        File dbEncryptFlag = new File(dbNoEncryptFlagFile);
        if (dbEncryptFlag.exists()) {
            return false;
        }
        return true;
    }

    private boolean enableDbEncrypt() {
        File dbEncryptFlag = new File(dbNoEncryptFlagFile);
        try {
            dbEncryptFlag.delete();
        } catch (Exception e) {
            log.error("Failed to delete file {} e", dbEncryptFlag.getName(), e);
            return false;
        }
        return true;
    }

    private boolean disableDbEncrypt() {
        File dbEncryptFlag = new File(dbNoEncryptFlagFile);
        try {
            new FileOutputStream(dbEncryptFlag).close();
        } catch (Exception e) {
            log.error("Failed to create file {} e", dbEncryptFlag.getName(), e);
            return false;
        }
        return true;
    }

    /**
     * Initialize local and target info
     * 
     * @throws Exception
     */
    private void initializeLocalAndTargetInfo(String svcId) throws Exception {
        // Step1: publish current state, and set target if empty
        // publish node state
        localInfo = localRepository.getRepositoryInfo();
        log.info("Step2a: Local repository information: {}", localInfo);
        coordinator.setNodeSessionScopeInfo(localInfo);

        // set target if empty
        targetInfo = coordinator.getTargetInfo(RepositoryInfo.class);
        if (targetInfo == null || !isValidRepo) {
            try {
                // Set the updated propperty info in coordinator
                // on devkits, don't check the stability of the "cluster"
                coordinator.setTargetInfo(localInfo, isValidRepo);

                targetInfo = coordinator.getTargetInfo(RepositoryInfo.class);
                log.info("Step2b: Target repository set to local state: {}", targetInfo);
            } catch (CoordinatorClientException e) {
                log.info("Step2b: Wait another control node to set target");
                retrySleep();
                throw e;
            }
        }

        // initialize remoteRepository
        remoteRepository = RemoteRepository.getInstance();
    }

    /**
     * Syncing nodes
     * 
     * @param syncinfo syncing info
     * @param controlNodeInSync control node which is in sync with target
     * @param svcId node service id
     */
    private boolean syncNodes(SyncInfo syncinfo, String controlNodeInSync, String svcId) {
        boolean needToWaitSyncFinish = true;
        if (controlNodeInSync == null) {
            // if no control node is synced, compete for leader to download
            if (!isRemoteDownloadAllowed()) {
                if (coordinator.hasRemoteDownloadLock(svcId)) {
                    log.info("Step3a: Leader gives up lock");
                    coordinator.releaseRemoteDownloadLock(svcId);
                    wakeupOtherNodes();
                }
            } else if (coordinator.hasRemoteDownloadLock(svcId) || coordinator.getRemoteDownloadLock(svcId)) {
                log.info("Step3a: Leader block");
                try {
                    if (syncWithRemote(localInfo, targetInfo, syncinfo)) {
                        coordinator.setNodeSessionScopeInfo(localRepository.getRepositoryInfo());
                        coordinator.releaseRemoteDownloadLock(svcId);
                        wakeupOtherNodes();
                    }
                } catch (Exception e) {
                    log.error("Step3a: ", e);
                    if ((e instanceof APIException) &&
                            (((APIException) e).getServiceCode() == ServiceCode.SYS_DOWNLOAD_IMAGE_ERROR)) {
                        needToWaitSyncFinish = false;
                        log.info("Step3a: Leader gives up lock");
                        coordinator.releaseRemoteDownloadLock(svcId);
                        wakeupOtherNodes();
                    }
                }
            } else {
                // Non-nodeInSync block
                // do nothing, wait nodeInSync to complete download
                log.info("Step3a: Wait nodeInSync to finish download");
            }
        } else if (controlNodeInSync != null) {
            // both control node and extra node can sync with controlNodeInSync if it's not null
            try {
                if (syncToNodeInSync(localInfo, targetInfo, controlNodeInSync, syncinfo)) {
                    coordinator.setNodeSessionScopeInfo(localRepository.getRepositoryInfo());
                    wakeupOtherNodes();
                }
            } catch (Exception e) {
                log.error("Step3b: {}", e);
            }
        }
        return needToWaitSyncFinish;
    }

    private SyncInfo getSyncInfoCommon(final RepositoryInfo localInfo,
            final RepositoryInfo targetInfo) {
        log.info("Step3: Synchronizing with target repository. Local repository information: {}", localInfo);
        log.info("Target repository information: {}", targetInfo);
        SyncInfo syncinfo = SyncInfoBuilder.getTargetSyncInfo(localInfo, targetInfo);
        log.info("Sync information: {}", syncinfo);

        return syncinfo;
    }

    private boolean syncWithRemote(final RepositoryInfo localInfo,
            final RepositoryInfo targetInfo,
            final SyncInfo syncinfo)
            throws RemoteRepositoryException, LocalRepositoryException {

        // Step1 - if something to install, install
        if (syncinfo.getToInstall() != null && !syncinfo.getToInstall().isEmpty()) {
            final SoftwareVersion toInstall = syncinfo.getToInstall().get(0);
            File image = null;
            if (toInstall != null && (image = getRemoteImage(toInstall)) == null) {
                return false;
            }
            if (image != null) {
                try {
                    localRepository.installImage(image);
                } finally {
                    image.delete();
                }
            }
        }

        // Step2 - if something to remove, remove
        if (syncinfo.getToRemove() != null && !syncinfo.getToRemove().isEmpty()) {
            for (SoftwareVersion v : syncinfo.getToRemove()) {
                localRepository.removeVersion(v);
            }
        }
        return true;
    }

    private boolean syncToNodeInSync(final RepositoryInfo localInfo,
            final RepositoryInfo leaderInfo, final String leader,
            final SyncInfo syncinfo)
            throws SysClientException, LocalRepositoryException {
        // Step1 - if something to install, install
        if (syncinfo.getToInstall() != null && !syncinfo.getToInstall().isEmpty()) {
            final SoftwareVersion toInstall = syncinfo.getToInstall().get(0);
            File image = null;
            if (toInstall != null && (image = getLeaderImage(toInstall, leader)) == null) {
                return false;
            }
            if (image != null) {
                try {
                    localRepository.installImage(image);
                } finally {
                    image.delete();
                }
            }
        }

        // Step2 - if something to remove, remove
        if (syncinfo.getToRemove() != null && !syncinfo.getToRemove().isEmpty()) {
            for (SoftwareVersion v : syncinfo.getToRemove()) {
                localRepository.removeVersion(v);
            }
        }
        return true;
    }

    /**
     * Get a control node which repository info is synced with target
     * 
     * @param targetRepository target repository
     * @return node id
     * @throws Exception
     */
    private String getAControlNodeInSync(RepositoryInfo targetRepository) throws Exception {
        final Map<Service, RepositoryInfo> localRepo = coordinator.getAllNodeInfos(RepositoryInfo.class, CONTROL_NODE_SYSSVC_ID_PATTERN);
        final List<SoftwareVersion> targetVersions = targetRepository.getVersions();

        List<String> candidates = new ArrayList<String>();
        for (Map.Entry<Service, RepositoryInfo> entry : localRepo.entrySet()) {
            if (targetVersions.equals(entry.getValue().getVersions())) {
                candidates.add(entry.getKey().getId());
            }
        }

        // return nodeId which is synced
        if (!candidates.isEmpty()) {
            return candidates.get(new Random().nextInt(candidates.size()));
        }

        return null;
    }

    private File getRemoteImage(final SoftwareVersion version) throws RemoteRepositoryException {
        final File file = new File(DOWNLOAD_DIR + '/' + version + SOFTWARE_IMAGE_SUFFIX);
        String prefix = MessageFormat.format("Step3a: version={0} local path=\"{1}\": ", version, file);

        log.info(prefix);

        if (isDownloadInProgress()) {
            return null;
        }

        if (file.exists()) {
            DownloadingInfo downloadingInfo;
            try {
                downloadingInfo = coordinator.getTargetInfo(DownloadingInfo.class);
            } catch (Exception e) {
                throw APIException.internalServerErrors.getObjectFromError("Node downloading info", "coordinator", e);
            }
            coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(downloadingInfo._version, downloadingInfo._size, downloadingInfo._size,
                    DownloadStatus.COMPLETED, new ArrayList<Integer>(Arrays.asList(0, 0))), "downloadinfo", coordinator.getMySvcId());
            // Because the file exists, we set the downloadinfo directly to COMPLETED status
            log.info(prefix + "Success!");
            return file;
        }

        if (!tryRemoteDownload()) {
            return null;
        }

        final URL url = getRemoteImageURL(version);
        prefix = MessageFormat.format("Step3a: version={0} local path=\"{1}\" URL=\"{2}\": ", version, file, url.toString());
        log.info(prefix + "Opening remote image stream");
        final InputStream in = remoteRepository.getImageInputStream(url);

        log.info(prefix + "Starting background download.");
        UpgradeImageDownloader.getInstance(this).startBackgroundDownload(prefix, file, in, url.toString(), version.toString());

        return null;
    }

    private URL getRemoteImageURL(final SoftwareVersion version) {
        try {
            URL url = remoteRepository.getImageURL(version);
            if (url == null) {
                throw new IllegalStateException("Image URL is null");
            }
            return url;
        } catch (Exception e) {
            log.error("Get remote image URL for version({}) failed", version.toString(), e);
            throw APIException.internalServerErrors.downloadUpgradeImageError(e);
        }
    }

    private File getLeaderImage(final SoftwareVersion version, final String leader) throws SysClientException {
        final File file = new File(DOWNLOAD_DIR + '/' + version + SOFTWARE_IMAGE_SUFFIX);
        final String prefix = MessageFormat.format("Step3b(): path=\"{0}\" leader=\"{1}\": ", file, leader);

        log.info(prefix);

        if (isDownloadInProgress()) {
            return null;
        }

        if (file.exists()) {
            DownloadingInfo downloadingInfo;
            try {
                downloadingInfo = coordinator.getNodeGlobalScopeInfo(DownloadingInfo.class, "downloadinfo", coordinator.getMySvcId());
                // if the downloading info is present and the version is the same then update the progress
                if (downloadingInfo != null && version.toString().equals(downloadingInfo._version)) {
                    coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(downloadingInfo._version, downloadingInfo._size,
                            downloadingInfo._size, DownloadStatus.COMPLETED, new ArrayList<Integer>(Arrays.asList(0, 0))), "downloadinfo",
                            coordinator.getMySvcId());
                }
            } catch (Exception e) {
                throw APIException.internalServerErrors.getObjectFromError("Node downloading info", "coordinator", e);
            }

            // Because the file exists, we set the downloadinfo directly to COMPLETED status
            log.info(prefix + "Success!");
            return file;
        }

        log.info(prefix + "Opening remote image stream");
        try {
            String uri = SysClientFactory.URI_GET_IMAGE + "?version=" + version;
            final InputStream in = SysClientFactory.getSysClient(coordinator
                    .getNodeEndpointForSvcId(leader))
                    .get(new URI(uri),
                            InputStream.class, MediaType.APPLICATION_OCTET_STREAM);

            log.info(prefix + "Starting background download.");
            UpgradeImageDownloader.getInstance(this).startBackgroundDownload(prefix, file, in, uri, version.toString());
        } catch (URISyntaxException e) {
            log.error("Internal error occurred while prepareing get image URI: {}", e);
        }
        return null;
    }

    /**
     * Check if remote download is progressing
     * 
     * @return true if remote download is progressing; false otherwise
     */
    private boolean isDownloadInProgress() {
        return UpgradeImageDownloader.getInstance(this).isDownloading();
    }

    /**
     * Method used to try remote download
     * 
     * if the method is called the first time, since expireTime is initialized as 0,
     * set expireTime to Now() + 5 mins and set counter to 1.
     * if current time is less than expireTime, increment counter and return true if counter not greater than maximal try count
     * if current time is not less than expireTime, reset timer and set counter to 1
     * 
     * @return true if succeed; false otherwise
     */
    private boolean tryRemoteDownload() {
        if (System.currentTimeMillis() < expireTime) {
            tryRepoCnt++;
            return tryRepoCnt <= MAX_REPO_RETRIES;
        } else {
            expireTime = System.currentTimeMillis() + TIMEOUT_INTERVAL;
            tryRepoCnt = 1;
            return true;
        }
    }

    /**
     * Method used to decide if remote download is allowed
     * similar to tryRemoteDownload except tryRemoteDownload will increment the try count
     * 
     * @return true if allowed; false otherwise
     */
    private boolean isRemoteDownloadAllowed() {
        return tryRepoCnt <= MAX_REPO_RETRIES || System.currentTimeMillis() >= expireTime;
    }

    @Override
    public void stop() {
        super.stop();
        UpgradeImageDownloader.getInstance(this).shutdownNow();
    }
}
