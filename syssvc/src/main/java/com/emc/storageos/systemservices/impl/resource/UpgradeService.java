/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.PUT;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.emc.storageos.coordinator.client.model.Constants.DOWNLOADINFO_KIND;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.DownloadingInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.vipr.model.sys.NodeProgress.DownloadStatus;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceUnavailableException;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.security.SecretsManager;
import com.emc.storageos.systemservices.impl.upgrade.*;
import com.emc.storageos.systemservices.impl.vdc.VdcManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.upgradevoter.UpgradeVoter;
import com.emc.vipr.model.sys.DownloadProgress;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.NodeProgress;
import com.emc.vipr.model.sys.TargetVersionResponse;
import static com.emc.storageos.coordinator.client.model.Constants.MAX_UPLOAD_SIZE;
import static com.emc.storageos.systemservices.mapper.ClusterInfoMapper.setInstallableRemovable;
import static com.emc.storageos.systemservices.mapper.ClusterInfoMapper.toClusterResponse;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.exceptions.RemoteRepositoryException;

@Path("/upgrade/")
public class UpgradeService {
    private static final String VIPR_UNKNOWN_IMAGE_VERSION = "unknown";
    private static final Logger _log = LoggerFactory.getLogger(UpgradeService.class);
    private static final String EVENT_SERVICE_TYPE = "upgrade";
    private CoordinatorClientExt _coordinator = null;
    private UpgradeManager _upgradeManager = null;
    private DrUtil drUtil;
    private final static String FORCE = "1";

    @Autowired
    private AuditLogManager _auditMgr;
    @Autowired
    private SecretsManager _secretsManager;
    @Autowired
    private PropertyManager _propertyManager;
    @Autowired
    private VdcManager _vdcManager;

    /**
     * Callback for other components to register itself for upgrade check before upgrade process starts.
     */
    private List<UpgradeVoter> _upgradeVoters;

    public void setProxy(CoordinatorClientExt proxy) {
        _coordinator = proxy;
    }

    public void setUpgradeManager(UpgradeManager upgradeManager) {
        _upgradeManager = upgradeManager;
    }

    public void setUpgradeVoters(List<UpgradeVoter> voters) {
        _upgradeVoters = voters;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    /**
     * Upgrade target version. Refer to product documentation for valid upgrade paths.
     * 
     * @brief Update the target version of the build
     * @param version The new version number
     * @prereq Target version should be installed and cluster state should be STABLE
     * @return Cluster state information.
     * @throws IOException
     */
    @PUT
    @Path("target-version/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response setTargetVersion(@QueryParam("version") String version, @QueryParam("force") String forceUpgrade) throws IOException {
        SoftwareVersion targetVersion = null;
        try {
            targetVersion = new SoftwareVersion(version);
        } catch (InvalidSoftwareVersionException e) {
            throw APIException.badRequests.parameterIsNotValid("version");
        }

        // validate
        if (!_coordinator.isClusterUpgradable()) {
            throw APIException.serviceUnavailable.clusterStateNotStable();
        }
        List<SoftwareVersion> available = null;
        try {
            available = _coordinator.getVersions(_coordinator.getMySvcId());
        } catch (CoordinatorClientException e) {
            throw APIException.internalServerErrors.getObjectFromError("available versions", "coordinator", e);
        }
        if (!available.contains(targetVersion)) {
            throw APIException.badRequests.versionIsNotAvailableForUpgrade(version);
        }
        // To Do - add a check for upgradable from current
        SoftwareVersion current = null;
        try {
            current = _coordinator.getRepositoryInfo(_coordinator.getMySvcId())
                    .getCurrentVersion();
        } catch (CoordinatorClientException e) {
            throw APIException.internalServerErrors.getObjectFromError("current version", "coordinator", e);
        }
        if (!current.isSwitchableTo(targetVersion)) {
            throw APIException.badRequests.versionIsNotUpgradable(targetVersion.toString(), current.toString());
        }

        // Check if allowed from upgrade voter and force option can veto
        if (FORCE.equals(forceUpgrade)) {
            _log.info("Force option supplied, skipping all geo/dr pre-checks");
        } else {
            for (UpgradeVoter voter : _upgradeVoters) {
                voter.isOKForUpgrade(current.toString(), version);
            }
        }
        try {
            _coordinator.setTargetInfo(new RepositoryInfo(targetVersion,
                    _coordinator.getTargetInfo(RepositoryInfo.class).getVersions()));
        } catch (Exception e) {
            throw APIException.internalServerErrors.setObjectToError("target version", "coordinator", e);
        }
        _log.info("target version changed successfully. new target {}", targetVersion);

        auditUpgrade(OperationTypeEnum.UPDATE_VERSION,
                AuditLogManager.AUDITLOG_SUCCESS,
                null, targetVersion.toString());

        ClusterInfo clusterInfo = _coordinator.getClusterInfo();
        if (clusterInfo == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster info");
        }
        return toClusterResponse(clusterInfo);
    }

    /**
     * Show the current target version
     * 
     * @brief Show the target version
     * @prereq none
     * @return Target version response
     */
    @GET
    @Path("target-version/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TargetVersionResponse getTargetVersion() {
        SoftwareVersion version = null;
        try {
            version = _coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("current version", "coordinator", e);
        }
        TargetVersionResponse resp = new TargetVersionResponse();
        resp.setTargetVersion(version.toString());
        return resp;
    }

    /**
     * Show cluster state
     * 
     * @brief Show cluster state
     * @param forceShow If force =, will show all removable versions even though the installed versions are less than MAX_SOFTWARE_VERSIONS
     * @prereq none
     * @return Cluster state information
     * @throws IOException
     */
    @GET
    @Path("cluster-state/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ClusterInfo getClusterState(@QueryParam("force") String forceShow) throws IOException {
        ClusterInfo clusterInfo = _coordinator.getClusterInfo();
        if (clusterInfo == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster info");
        }
        if (_upgradeManager.getRemoteRepository() != null) {
            try {
                Map<SoftwareVersion, List<SoftwareVersion>> cachedVersions = RemoteRepository.getCachedSoftwareVersions();
                _log.info("The cached software versions are:" + cachedVersions.toString());
                setInstallableRemovable(clusterInfo, _coordinator.getTargetInfo(RepositoryInfo.class),
                        cachedVersions, FORCE.equals(forceShow));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw APIException.internalServerErrors.getObjectFromError("target repository info", "coordinator", e);
            }
        }
        return clusterInfo;
    }

    /**
     * Install image. Image can be installed only if the number of installed images are less than MAX_SOFTWARE_VERSIONS
     * 
     * @brief Install image
     * @param versionStr Version to be installed
     * @prereq Cluster state should be STABLE
     * @return Cluster state information
     * @throws Exception
     */
    @POST
    @Path("image/install/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response installImage(@QueryParam("version") String versionStr) throws Exception {
        _log.info("installImage({})", versionStr);
        final SoftwareVersion version;
        try {
            version = new SoftwareVersion(versionStr);
        } catch (InvalidSoftwareVersionException e) {
            throw APIException.badRequests.parameterIsNotValid("version");
        }
        checkClusterState();
        RepositoryInfo repoInfo = null;
        try {
            repoInfo = _coordinator.getTargetInfo(RepositoryInfo.class);
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("target repository info", "coordinator", e);
        }
        SoftwareVersion currentVersion = repoInfo.getCurrentVersion();
        List<SoftwareVersion> localAvailableVersions = repoInfo.getVersions();
        if (localAvailableVersions.size() > SyncInfoBuilder.MAX_SOFTWARE_VERSIONS) {
            throw APIException.badRequests.numberOfInstalledExceedsMax();
        }
        RemoteRepository repo = _upgradeManager.getRemoteRepository();

        if (isInstalled(repoInfo.getVersions(), version)) {
            throw APIException.badRequests.versionIsInstalled(versionStr);
        }

        if (!isUpgradable(repoInfo.getCurrentVersion(), version)) {
            throw APIException.badRequests.versionIsNotAvailableForUpgrade(versionStr);
        }

        try {
            // check that the version can be downloaded from the remote repository
            repo.checkVersionDownloadable(version);
        } catch (RemoteRepositoryException e) {
            throw APIException.internalServerErrors.getObjectError("remote repository info", e);
        }

        List<SoftwareVersion> newList = new ArrayList<SoftwareVersion>(localAvailableVersions);
        newList.add(version);

        int versionSize = repo.checkVersionSize(version);
        _log.info("The size of the image is:" + versionSize);

        initializeDownloadProgress(versionStr, versionSize);

        try {
            _coordinator.setTargetInfo(
                    new RepositoryInfo(currentVersion, newList));
        } catch (Exception e) {
            throw APIException.internalServerErrors.setObjectToError("target versions", "coordinator", e);
        }
        auditUpgrade(OperationTypeEnum.INSTALL_IMAGE,
                AuditLogManager.AUDITLOG_SUCCESS,
                null, versionStr);

        ClusterInfo clusterInfo = _coordinator.getClusterInfo();
        if (clusterInfo == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster info");
        }
        return toClusterResponse(clusterInfo);
    }

    private void checkClusterState() {
        for (Site site : drUtil.listSites()) {
            // don't check cluster state for paused sites.
            if (site.getState().equals(SiteState.STANDBY_PAUSED)) {
                continue;
            }
            int nodeCount = site.getNodeCount();
            ClusterInfo.ClusterState state = _coordinator.getCoordinatorClient().getControlNodesState(site.getUuid(),
                    nodeCount);
            if (state != ClusterInfo.ClusterState.STABLE) {
                _log.error("Site {} is not stable: {}", site.getUuid(), state);
                throw APIException.serviceUnavailable.siteClusterStateNotStable(site.getUuid(), state.toString());
            }
        }
    }

    /**
     * Check if a version is installed or not.
     * true If the version is the same as one of the local available versions.
     * 
     * @param localAvailableVersions available versions
     * @param targetVersion version
     * @return true or false
     */
    private boolean isInstalled(List<SoftwareVersion> localAvailableVersions, SoftwareVersion targetVersion) {
        for (SoftwareVersion s : localAvailableVersions) {
            if (targetVersion.compareTo(s) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a version is upgradable or not.
     * 
     * @param currentVersion version
     * @param targetVersion version
     * @return true or false
     */
    private boolean isUpgradable(SoftwareVersion currentVersion, SoftwareVersion targetVersion) throws Exception {
        if (currentVersion.isNaturallySwitchableTo(targetVersion)) {
            return true;
        }
        RemoteRepository repo = _upgradeManager.getRemoteRepository();
        for (SoftwareVersion v : repo.getUpgradeFromVersions(targetVersion)) {
            if (v.weakEquals(currentVersion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For download progress monitoring, The zookeeper structure used in the setNodeGlobalScopeInfo() and getNodeGlobalScopeInfo() is
     * /sites/(site_uuid)/config/downloadinfo/(svcId)
     * Each node has a entry in the coordinator indicated by its svcId.
     * The remote download and internode download are monitored in the same way, because the process is the same in the UpgradeImageCommon
     * class.
     * Every second if the newly downloaded bytes are more that 1MB, we update the progress entry in the coordinator.
     * For the cancel function, it first check the progress entries in the coordinator to see if there is a download in progress, if there
     * is, get the
     * version from the entry, and erase this version from the target RepositoryInfo object in the coordinator. This operation will
     * terminate the ongoing download process.
     */

    /**
     * Check the version downloading progress. the downloading could be from remote repository
     * 
     * @return image downloading progress
     * @throws Exception
     */
    @GET
    @Path("image/download/progress/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public DownloadProgress checkDownloadProgress(@QueryParam("site") String siteId, @Context HttpHeaders headers)
            throws Exception {
        _log.info("checkDownloadProgress()");
        DownloadProgress progress = new DownloadProgress();
        DownloadingInfo targetDownloadInfo = _coordinator.getTargetInfo(DownloadingInfo.class);
        if (targetDownloadInfo == null || targetDownloadInfo._status == DownloadStatus.CANCELLED) {
            // return empty progress. No download in progress
            return progress;
        }
        progress.setImageSize(targetDownloadInfo._size);

        for (String svcId : _coordinator.getAllNodes(siteId)) {
            DownloadingInfo downloadInfo = _coordinator.getNodeGlobalScopeInfo(DownloadingInfo.class, siteId,
                    DOWNLOADINFO_KIND, svcId);
            if (null == downloadInfo) {
                progress.addNodeProgress(svcId, new NodeProgress(0, DownloadStatus.NORMAL, 0, 0));
            } else {
                int downloadErrorCount = downloadInfo._errorCounter.get(0);
                int checksumErrorCount = downloadInfo._errorCounter.get(1);
                progress.addNodeProgress(svcId, new NodeProgress(downloadInfo.downloadedBytes, downloadInfo._status, downloadErrorCount,
                        checksumErrorCount));
            }
        }
        return progress;
    }

    /**
     * Cancel installing a version or uploading a version. Remove it from the target RepositoryInfo
     * 
     * @prereq There should be image downloading in progress.
     * @return Cluster state information
     * @throws IOException
     */
    @POST
    @Path("image/install/cancel/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response cancelInstallingOrUploadingImage() throws IOException {
        _log.info("cancelInstallingOrUploadingImage()");
        DownloadingInfo downloadInfo = null;
        boolean inProgressFlag = false;

        DownloadingInfo downloadTargetInfo;
        try {
            downloadTargetInfo = _coordinator.getTargetInfo(DownloadingInfo.class);
        } catch (Exception e1) {
            throw APIException.internalServerErrors.getObjectFromError("Target downloading info", "coordinator", e1);
        }
        if (null == downloadTargetInfo || DownloadStatus.CANCELLED == downloadTargetInfo._status) { // Check the target info of the
                                                                                                    // DownloadingInfo class to see if user
                                                                                                    // sent a cancel request
            inProgressFlag = false; // No previous installation/upload or installation/upload got cancelled, not in progress
        }

        for (String svcId : _coordinator.getAllNodes()) {
            DownloadingInfo tmpInfo;
            try {
                tmpInfo = _coordinator.getNodeGlobalScopeInfo(DownloadingInfo.class, DOWNLOADINFO_KIND, svcId);
            } catch (Exception e) {
                throw APIException.internalServerErrors.getObjectFromError("Node downloading info", "coordinator", e);
            }
            if (null != tmpInfo && tmpInfo._status != DownloadStatus.COMPLETED) {
                downloadInfo = tmpInfo;
                inProgressFlag = true;
                break;
            }
        }

        if (!inProgressFlag) {
            throw APIException.badRequests.noDownloadInProgress();
        }
        String installingVersion = downloadInfo._version;
        if (installingVersion.equals(VIPR_UNKNOWN_IMAGE_VERSION)) {
            throw ServiceUnavailableException.serviceUnavailable.versionOfTheImageIsUnknownSoFar();
        }
        _coordinator.setTargetInfo(downloadInfo.cancel(), false);

        return removeImage(installingVersion, "1");
    }

    /**
     * Remove an image. Image can be removed only if the number of installed images are
     * greater than MAX_SOFTWARE_VERSIONS
     * 
     * @brief Remove an image
     * @param versionStr Version to be removed
     * @param forceRemove If force=1, image will be removed even if the maximum number of versions installed are less than
     *            MAX_SOFTWARE_VERSIONS
     * @prereq Image should be installed and cluster state should be STABLE
     * @return Cluster state information
     * @throws IOException
     */
    @POST
    @Path("image/remove/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response removeImage(@QueryParam("version") String versionStr, @QueryParam("force") String forceRemove) throws IOException {
        _log.info("removeImage({})", versionStr);
        final SoftwareVersion version;
        try {
            version = new SoftwareVersion(versionStr);
        } catch (InvalidSoftwareVersionException e) {
            throw APIException.badRequests.parameterIsNotValid("version");
        }
        RepositoryInfo targetInfo = null;
        try {
            targetInfo = _coordinator.getTargetInfo(RepositoryInfo.class);
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("target repository info", "coordinator", e);
        }
        final SyncInfo remoteSyncInfo = SyncInfoBuilder.removableVersions(targetInfo, FORCE.equals(forceRemove));

        if (remoteSyncInfo.isEmpty() ||
                remoteSyncInfo.getToRemove() == null ||
                !remoteSyncInfo.getToRemove().contains(version)) {
            throw APIException.badRequests.versionIsNotRemovable(versionStr);
        }
        List<SoftwareVersion> newList = new ArrayList<SoftwareVersion>(targetInfo.getVersions());
        newList.remove(version);
        try {
            _coordinator.setTargetInfo(
                    new RepositoryInfo(targetInfo.getCurrentVersion(), newList), !FORCE.equals(forceRemove));
        } catch (Exception e) {
            throw APIException.internalServerErrors.setObjectToError("target versions", "coordinator", e);
        }

        auditUpgrade(OperationTypeEnum.REMOVE_IMAGE,
                AuditLogManager.AUDITLOG_SUCCESS,
                null, versionStr, FORCE.equals(forceRemove));

        ClusterInfo clusterInfo = _coordinator.getClusterInfo();
        if (clusterInfo == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster info");
        }
        return toClusterResponse(clusterInfo);
    }

    /**
     * *Internal API, used only between nodes*
     * <p>
     * Get image
     * 
     * @param versionStr Version to be retrieved
     * @return Image details
     */
    @GET
    @Path("internal/image/")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getImage(@QueryParam("version") String versionStr) {
        _log.info("getImage({})", versionStr);
        final SoftwareVersion version;
        try {
            version = new SoftwareVersion(versionStr);
        } catch (InvalidSoftwareVersionException e) {
            throw APIException.badRequests.parameterIsNotValid("version");
        }
        final InputStream in;
        try {
            in = LocalRepository.getInstance().getImageInputStream(version);
        } catch (LocalRepositoryException e) {
            throw APIException.internalServerErrors.getObjectFromError("image input stream", "local repository", e);
        }
        return Response.ok(in).type(MediaType.APPLICATION_OCTET_STREAM).build();
    }

    /**
     * *Internal API, used only between nodes*
     * <p>
     * Wake up node
     * 
     * @return Cluster state information.
     */
    @POST
    @Path("internal/wakeup/")
    public Response wakeupManager(@QueryParam("type") String managerType) {
        if (managerType == null) {
            managerType = "all";
        }

        switch (managerType) {
            case "upgrade":
                _upgradeManager.wakeup();
                break;
            case "secrets":
                _secretsManager.wakeup();
                break;
            case "property":
                _propertyManager.wakeup();
                break;
            case "vdc":
                _vdcManager.wakeup();
                break;
            default:
                _upgradeManager.wakeup();
                _secretsManager.wakeup();
                _propertyManager.wakeup();
                _vdcManager.wakeup();
        }
        ClusterInfo clusterInfo = _coordinator.getClusterInfo();
        if (clusterInfo == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster info");
        }
        _log.debug("Successfully woke up {} manager(s)", managerType);
        return toClusterResponse(clusterInfo);
    }

    /**
     * Upload the image file given.
     * Consumes MediaType.APPLICATION_OCTET_STREAM.
     * This is an asynchronous operation.
     * 
     * @brief Upload the specified image file
     * @prereq Cluster state should be STABLE
     * @return Cluster information.
     */
    @POST
    @Path("image/upload")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response uploadImage(@Context HttpServletRequest request) {
        File file = null;
        String svcId = _coordinator.getMySvcId();
        _log.info("uploadImage to {} start", svcId);

        // validate
        if (!_coordinator.isClusterUpgradable()) {
            throw APIException.serviceUnavailable.clusterStateNotStable();
        }
        // maximal install number check
        RepositoryInfo targetInfo = null;
        try {
            targetInfo = _coordinator.getTargetInfo(RepositoryInfo.class);
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("target repository info", "coordinator", e);
        }
        if (targetInfo.getVersions().size() > SyncInfoBuilder.MAX_SOFTWARE_VERSIONS) {
            throw APIException.badRequests.numberOfInstalledExceedsMax();
        }
        // length check
        String contentLength = request.getHeader("Content-Length");
        if (Long.parseLong(contentLength) <= 0 ||
                Long.parseLong(contentLength) > MAX_UPLOAD_SIZE) {
            throw APIException.badRequests.fileSizeExceedsLimit(MAX_UPLOAD_SIZE);
        }

        try {
            // remove previous and upload to a temp file
            UpgradeImageUploader uploader = UpgradeImageUploader.getInstance(_upgradeManager);
            uploader.cleanUploadFiles();

            long versionSize = Long.valueOf(contentLength);
            _log.info("The size of the image is:" + versionSize);

            String version = VIPR_UNKNOWN_IMAGE_VERSION;
            initializeDownloadProgress(version, versionSize);

            file = uploader.startUpload(request.getInputStream(), version);

            // install image
            if (file == null || file != null && !file.exists()) {
                throw APIException.internalServerErrors.targetIsNullOrEmpty("Uploaded file");
            }
            version = _upgradeManager.getLocalRepository().installImage(file);
            // set target
            List<SoftwareVersion> newList = new ArrayList<SoftwareVersion>(targetInfo.getVersions());
            SoftwareVersion newVersion = new SoftwareVersion(version);
            if (newList.contains(newVersion)) {
                _log.info("Version has already been installed");
            } else {
                newList.add(newVersion);
                _coordinator.setTargetInfo(new RepositoryInfo(targetInfo.getCurrentVersion(), newList));

                DownloadingInfo temp = _coordinator.getNodeGlobalScopeInfo(DownloadingInfo.class, DOWNLOADINFO_KIND, svcId);
                _coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(version, versionSize, versionSize, DownloadStatus.COMPLETED,
                        temp._errorCounter), DOWNLOADINFO_KIND, svcId);

                _coordinator.setTargetInfo(new DownloadingInfo(version, versionSize), false);
            }
            _log.info("uploadImage to {} end", svcId);

            auditUpgrade(OperationTypeEnum.UPLOAD_IMAGE,
                    AuditLogManager.AUDITLOG_SUCCESS,
                    null, targetInfo.getCurrentVersion().toString(), svcId);

            // return cluster status
            ClusterInfo clusterInfo = _coordinator.getClusterInfo();
            if (clusterInfo == null) {
                throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster info");
            }
            return toClusterResponse(clusterInfo);
        } catch (APIException ae) {
            throw ae;
        } catch (Exception e) {
            throw APIException.internalServerErrors.uploadInstallError(e);
        } finally {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }

    /**
     * Initialize download progress for each node
     * 
     * @param version - the version that is being downloaded
     * @param versionSize - the size of the image file
     */
    private void initializeDownloadProgress(String version, long versionSize) {
        _coordinator.setTargetInfo(new DownloadingInfo(version, versionSize));
        for (String nodeId : _coordinator.getAllNodes()) {
            _coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(version, versionSize), DOWNLOADINFO_KIND, nodeId);
        }
    }

    /**
     * Record audit log for upgrade service
     * 
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public void auditUpgrade(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {

        _auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                description,
                descparams);
    }
}
