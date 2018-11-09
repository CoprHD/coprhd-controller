/*

 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsServerMap;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyPriority;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.FileReplicationCopyMode;
import com.emc.storageos.db.client.model.FilePolicy.FileReplicationType;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.NasCifsServer;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.SchedulePolicy.ScheduleFrequency;
import com.emc.storageos.db.client.model.SchedulePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationUtils;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApi.IsilonList;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonGroup;
import com.emc.storageos.isilon.restapi.IsilonIdentity;
import com.emc.storageos.isilon.restapi.IsilonNFSACL;
import com.emc.storageos.isilon.restapi.IsilonNFSACL.Acl;
import com.emc.storageos.isilon.restapi.IsilonSMBShare;
import com.emc.storageos.isilon.restapi.IsilonSMBShare.Permission;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.isilon.restapi.IsilonSnapshot;
import com.emc.storageos.isilon.restapi.IsilonSnapshotSchedule;
import com.emc.storageos.isilon.restapi.IsilonSshApi;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.Action;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.JobState;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy8Above;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicyReport;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy.FOFB_STATES;
import com.emc.storageos.isilon.restapi.IsilonUser;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.model.file.policy.FileReplicationPolicyParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.plugins.metering.isilon.IsilonCollectionException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.file.AbstractFileStorageDevice;
import com.google.common.collect.Sets;

/**
 * Isilon specific file controller implementation.
 */
public class IsilonFileStorageDevice extends AbstractFileStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(IsilonFileStorageDevice.class);

    private static final String IFS_ROOT = "/ifs";
    private static final String FW_SLASH = "/";
    private static final String VIPR_DIR = "vipr";

    private static final String QUOTA = "quota";

    private static final String LSA_AD_PROVIDER = "lsa-activedirectory-provider";
    private static final String LSA_LDAP_PROVIDER = "lsa-ldap-provider";
    private static final String LSA_NIS_PROVIDER = "lsa-nis-provider";
    private static final String LSA_LOCAL_PROVIDER = "lsa-local-provider";
    private static final String LSA_FILE_PROVIDER = "lsa-file-provider";
    private static final String COLON = ":";

    private static final String EXPORT_OP_NAME = "Snapshot Export";
    private static final String SHARE_OP_NAME = "Snapshot Share";
    public static final long SEC_IN_MILLI = 1000L;
    private static final String STR_WITH_NO_SPECIAL_SYMBOLS = "[^A-Za-z0-9_\\-/]";
    private static final String MIRROR_POLICY = "_mirror";

    private static final String ONEFS_V8 = "8.0.0.0";

    private IsilonApiFactory _factory;
    private HashMap<String, String> configinfo;

    private DbClient _dbClient;
    @Autowired
    private CustomConfigHandler customConfigHandler;
    @Autowired
    private DataSourceFactory dataSourceFactory;

    private IsilonMirrorOperations mirrorOperations;

    public IsilonMirrorOperations getMirrorOperations() {
        return mirrorOperations;
    }

    public void setMirrorOperations(IsilonMirrorOperations mirrorOperations) {
        this.mirrorOperations = mirrorOperations;
    }

    /**
     * Set Isilon API factory
     * 
     * @param factory
     */
    public void setIsilonApiFactory(IsilonApiFactory factory) {
        _factory = factory;
    }

    /**
     * Get Isilon config info
     * 
     * @param factory
     */
    public HashMap<String, String> getConfiginfo() {
        return configinfo;
    }

    /**
     * Set Isilon config info
     * 
     * @param factory
     */
    public void setConfiginfo(HashMap<String, String> configinfo) {
        this.configinfo = configinfo;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    /**
     * Set the controller config info
     * 
     * @return
     */
    public void setCustomConfigHandler(CustomConfigHandler customConfigHandler) {
        this.customConfigHandler = customConfigHandler;
    }

    /**
     * Get isilon device represented by the StorageDevice
     * 
     * @param StorageSystem
     *            object
     * @return IsilonSshApi object
     */
    IsilonSshApi getIsilonDeviceSsh(StorageSystem device) throws IsilonException {
        IsilonSshApi sshDmApi = new IsilonSshApi();
        sshDmApi.setConnParams(device.getIpAddress(), device.getUsername(), device.getPassword());
        return sshDmApi;
    }

    /**
     * Get isilon device represented by the StorageDevice
     * 
     * @param device
     *            StorageDevice object
     * @return IsilonApi object
     * @throws IsilonException
     */
    IsilonApi getIsilonDevice(StorageSystem device) throws IsilonException {
        IsilonApi isilonAPI;
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, device.getIpAddress(), device.getPortNumber(), "/", null, null);
        } catch (URISyntaxException ex) {
            throw IsilonException.exceptions.errorCreatingServerURL(device.getIpAddress(), device.getPortNumber(), ex);
        }
        if (device.getUsername() != null && !device.getUsername().isEmpty()) {
            isilonAPI = _factory.getRESTClient(deviceURI, device.getUsername(), device.getPassword());
        } else {
            isilonAPI = _factory.getRESTClient(deviceURI);
        }

        return isilonAPI;

    }

    /**
     * create isilon snapshot path from file share path and snapshot name
     * 
     * @param fsMountPath
     *            mount path of the fileshare
     * @param name
     *            snapshot name
     * @return String
     */
    private String getSnapshotPath(String fsMountPath, String name) {
        String prefix = IFS_ROOT + "/" + VIPR_DIR;
        return String.format("%1$s/.snapshot/%2$s/%3$s%4$s", IFS_ROOT, name, VIPR_DIR,
                fsMountPath.substring(prefix.length()));
    }

    /**
     * Delete isilon export
     * 
     * @param isi
     *            IsilonApi object
     * @param exportMap
     *            exports to be deleted
     * @throws IsilonException
     */
    private void isiDeleteExports(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        FSExportMap exportMap = null;

        if (args.getFileOperation()) {
            FileShare fileObj = args.getFs();
            if (fileObj != null) {
                exportMap = fileObj.getFsExports();
            }
        } else {
            Snapshot snap = args.getFileSnapshot();
            if (snap != null) {
                exportMap = snap.getFsExports();
            }
        }

        if (exportMap == null || exportMap.isEmpty()) {
            return;
        }

        String zoneName = getZoneName(args.getvNAS());

        Set<String> deletedExports = new HashSet<String>();
        Iterator<Map.Entry<String, FileExport>> it = exportMap.entrySet().iterator();
        try {
            while (it.hasNext()) {
                Map.Entry<String, FileExport> entry = it.next();
                String key = entry.getKey();
                FileExport fsExport = entry.getValue();
                if (zoneName != null) {
                    isi.deleteExport(fsExport.getIsilonId(), zoneName);
                } else {
                    isi.deleteExport(fsExport.getIsilonId());
                }

                // Safe removal from the backing map. Can not do this through
                // iterator since this does not track changes and is not
                // reflected in the database.
                deletedExports.add(key);
            }
        } finally {
            // remove exports from the map in database.
            for (String key : deletedExports) {
                exportMap.remove(key);
            }
        }
    }

    /**
     * Deletes existing exports and smb shares for the
     * file share (only created by storage os)
     * 
     * @param isi
     * @param args
     * @throws IsilonException
     */
    private void isiDeleteFileSystemRefObjects(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        /*
         * Delete the exports for this file system
         */
        isiDeleteExports(isi, args);

        /*
         * Delete the SMB shares for this file system
         */
        isiDeleteShares(isi, args);

        /*
         * Delete quota on this path, if one exists
         */
        if (args.getFsExtensions() != null && args.getFsExtensions().containsKey(QUOTA)) {
            isi.deleteQuota(args.getFsExtensions().get(QUOTA));
            // delete from extensions
            args.getFsExtensions().remove(QUOTA);
        }

        /*
         * Delete the snapshots for this file system
         */
        isiDeleteSnapshots(isi, args);

        /*
         * Delete quota dirs, if one exists
         */
        isiDeleteQuotaDirs(isi, args);

        /**
         * Delete the directory associated with the file share.
         */
        isi.deleteDir(args.getFsMountPath());

        /**
         * Delete the Schedule Policy for the file system
         */
        isiDeleteSnapshotSchedules(isi, args);
    }

    /**
     * Deleting a file share: - Delete the file share only
     * if the file system directory has no files or directories
     * 
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteFS(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        // Fail the request with force delete
        if (args.getForceDelete()) {
            _log.error("File System delete operation is not supported with force delete {} ", args.getForceDelete());
            throw IsilonException.exceptions.deleteFileSystemNotSupported();

        }
        /*
         * Do not delete file system if it has some data in it.
         * In Api service we have check (ViPR db check) for existing shares and exports and
         * it fails operation if filesystem has any share or export.
         */
        if (isi.fsDirHasData(args.getFsMountPath())) {
            // Fail to delete file system directory which has data in it!!!
            _log.error("File system deletion failed as it's directory {} has content in it", args.getFsMountPath());
            throw IsilonException.exceptions.failToDeleteFileSystem(args.getFsMountPath());
        }

        /**
         * Delete quota on this path, if one exists
         */
        if (args.getFsExtensions() != null && args.getFsExtensions().containsKey(QUOTA)) {
            isi.deleteQuota(args.getFsExtensions().get(QUOTA));
            // delete from extensions
            args.getFsExtensions().remove(QUOTA);
        }

        /**
         * Delete the directory associated with the file share.
         * with recursive flag false
         */
        isi.deleteDir(args.getFsMountPath());
    }

    /**
     * Deleting snapshots: - deletes snapshots of a file system
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteSnapshots(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        List<URI> snapURIList = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(args.getFsId()));
        for (URI snapURI : snapURIList) {
            Snapshot snap = _dbClient.queryObject(Snapshot.class, snapURI);
            if (snap != null && (!snap.getInactive())) {
                args.addSnapshot(snap);
                isiDeleteSnapshot(isi, args);
            }
        }
    }

    /**
     * Deleting snapshots: - deletes snapshots of a file system
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteSnapshotSchedules(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        StringSet policies = args.getFs().getFilePolicies();

        for (String policy : policies) {
            SchedulePolicy fp = _dbClient.queryObject(SchedulePolicy.class, URI.create(policy));
            String snapshotScheduleName = fp.getPolicyName() + "_" + args.getFsName();
            isi.deleteSnapshotSchedule(snapshotScheduleName);
        }
    }

    /**
     * Deleting a snapshot: - deletes existing exports and smb shares for the
     * snapshot (only created by storage os)
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteSnapshot(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        args.setFileOperation(false);
        /*
         * Delete the exports first
         */
        isiDeleteExports(isi, args);

        /*
         * Delete the SMB shares
         */
        isiDeleteShares(isi, args);

        /**
         * Delete the snapshot.
         */
        if (args.getSnapshotExtensions() != null && args.getSnapshotExtensions().containsKey("id")) {
            isi.deleteSnapshot(args.getSnapshotExtensions().get("id"));
        }
    }

    /**
     * Deleting Quota dirs: - deletes quota dirs of a file system
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteQuotaDirs(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {
        List<URI> quotaDirURIList = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory.getQuotaDirectoryConstraint(args.getFsId()));
        for (URI quotaDirURI : quotaDirURIList) {
            QuotaDirectory quotaDir = _dbClient.queryObject(QuotaDirectory.class, quotaDirURI);
            if (quotaDir != null && (!quotaDir.getInactive())) {
                if (quotaDir.getExtensions() != null && quotaDir.getExtensions().containsKey(QUOTA)) {

                    String quotaDirPath = args.getFsMountPath() + "/" + quotaDir.getName();
                    // Do not delete quota directory
                    // if the quota directory has some data in it.
                    if (isi.fsDirHasData(quotaDirPath)) {
                        // Fail to delete file system quota directory which has data in it!!!
                        _log.error("Quota directory deletion failed as it's directory path {} has content in it", quotaDirPath);
                        throw DeviceControllerException.exceptions.failToDeleteQuotaDirectory(quotaDirPath);
                    }

                    String quotaId = quotaDir.getExtensions().get(QUOTA);
                    _log.info("IsilonFileStorageDevice isiDeleteQuotaDirs , Delete Quota {}", quotaId);
                    isi.deleteQuota(quotaId);
                    // delete from quota extensions
                    quotaDir.getExtensions().remove(QUOTA);

                    // delete directory for the Quota Directory
                    isi.deleteDir(quotaDirPath);
                }
            }
        }

    }

    /**
     * Create/modify Isilon SMB share.
     * 
     * @param isi
     * @param args
     * @param smbFileShare
     * @throws IsilonException
     */
    private void isiShare(IsilonApi isi, FileDeviceInputOutput args, SMBFileShare smbFileShare) throws IsilonException {

        IsilonSMBShare isilonSMBShare = new IsilonSMBShare(smbFileShare.getName(), smbFileShare.getPath(),
                smbFileShare.getDescription());

        // Check if this is a new share or update of the existing share
        SMBShareMap smbShareMap = args.getFileObjShares();
        SMBFileShare existingShare = (smbShareMap == null) ? null : smbShareMap.get(smbFileShare.getName());

        String shareId;

        String zoneName = getZoneName(args.getvNAS());

        if (existingShare != null) {
            shareId = existingShare.getNativeId();
            // modify share
            if (zoneName != null) {
                isi.modifyShare(shareId, zoneName, isilonSMBShare);
            } else {
                isi.modifyShare(shareId, isilonSMBShare);
            }

        } else {
            /**
             * inheritablePathAcl - true: Apply Windows Default ACLs false: Do
             * not change existing permissions.
             **/
            boolean inheritablePathAcl = true;
            if (configinfo != null && configinfo.containsKey("inheritablePathAcl")) {
                inheritablePathAcl = Boolean.parseBoolean(configinfo.get("inheritablePathAcl"));
                isilonSMBShare.setInheritablePathAcl(inheritablePathAcl);
            }
            // new share
            if (zoneName != null) {
                _log.debug("Share will be created in zone: {}", zoneName);
                shareId = isi.createShare(isilonSMBShare, zoneName);
            } else {
                shareId = isi.createShare(isilonSMBShare);
            }
        }
        smbFileShare.setNativeId(shareId);

        // Set Mount Point
        smbFileShare.setMountPoint(smbFileShare.getStoragePortNetworkId(), smbFileShare.getStoragePortName(),
                smbFileShare.getName());
        // int file share map
        if (args.getFileObjShares() == null) {
            args.initFileObjShares();
        }
        args.getFileObjShares().put(smbFileShare.getName(), smbFileShare);
    }

    private void isiDeleteShare(IsilonApi isi, FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws IsilonException {

        SMBShareMap currentShares = args.getFileObjShares();
        // Do nothing if there are no shares
        if (currentShares == null || smbFileShare == null) {
            return;
        }

        SMBFileShare fileShare = currentShares.get(smbFileShare.getName());
        if (fileShare != null) {

            String nativeId = fileShare.getNativeId();
            String zoneName = getZoneName(args.getvNAS());
            _log.info("delete the share {} with native id {}", smbFileShare.getName(), nativeId);
            if (zoneName != null) {
                isi.deleteShare(nativeId, zoneName);
            } else {
                isi.deleteShare(nativeId);
            }

            currentShares.remove(smbFileShare.getName());
        }
    }

    private void isiDeleteShares(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {
        _log.info("IsilonFileStorageDevice:isiDeleteShares()");
        SMBShareMap currentShares = null;
        if (args.getFileOperation()) {
            FileShare fileObj = args.getFs();
            if (fileObj != null) {
                currentShares = fileObj.getSMBFileShares();
            }
        } else {
            Snapshot snap = args.getFileSnapshot();
            if (snap != null) {
                currentShares = snap.getSMBFileShares();
            }
        }
        if (currentShares == null || currentShares.isEmpty()) {
            return;
        }

        Set<String> deletedShares = new HashSet<String>();
        Iterator<Map.Entry<String, SMBFileShare>> it = currentShares.entrySet().iterator();

        String zoneName = getZoneName(args.getvNAS());

        try {
            while (it.hasNext()) {
                Map.Entry<String, SMBFileShare> entry = it.next();
                String key = entry.getKey();
                SMBFileShare smbFileShare = entry.getValue();
                _log.info("delete the share name {} and native id {}", smbFileShare.getName(), smbFileShare.getNativeId());
                if (zoneName != null) {
                    isi.deleteShare(smbFileShare.getNativeId(), zoneName);
                } else {
                    isi.deleteShare(smbFileShare.getNativeId());
                }

                // Safe removal from the backing map. Can not do this through
                // iterator since this does not track changes and is not
                // reflected in the database.
                deletedShares.add(key);
            }
        } finally {
            // remove shares from the map in database.
            for (String key : deletedShares) {
                currentShares.remove(key);
            }
        }

    }

    /**
     * Create isilon exports
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput object
     * @param exports
     *            new exports to add
     * @throws IsilonException
     */
    private void isiExport(IsilonApi isi, FileDeviceInputOutput args, List<FileExport> exports) throws IsilonException {

        // process and export each NFSExport independently.
        for (FileExport fileExport : exports) {

            // create and set IsilonExport instance from NFSExport
            String permissions = fileExport.getPermissions();
            Set<String> orderedSecTypes = new TreeSet<String>();
            for (String securityType : fileExport.getSecurityType().split(",")) {
                securityType = securityType.trim();
                orderedSecTypes.add(securityType);
            }
            Iterator<String> orderedList = orderedSecTypes.iterator();
            String strCSSecurityType = orderedList.next().toString();
            while (orderedList.hasNext()) {
                strCSSecurityType += "," + orderedList.next().toString();
            }

            String root_user = fileExport.getRootUserMapping();
            String storagePortName = fileExport.getStoragePortName();
            String storagePort = fileExport.getStoragePort();
            String protocol = fileExport.getProtocol();
            String path = fileExport.getPath();
            String mountPath = fileExport.getMountPath();
            String comments = fileExport.getComments();
            String subDirectory = fileExport.getSubDirectory();

            List<String> securityTypes = new ArrayList<String>(orderedSecTypes);
            IsilonExport newIsilonExport = setIsilonExport(fileExport, permissions, securityTypes, root_user, mountPath,
                    comments);

            _log.info("IsilonExport:" + fileExport.getClients() + ":" + fileExport.getStoragePortName() + ":"
                    + fileExport.getStoragePort() + ":" + fileExport.getRootUserMapping() + ":"
                    + fileExport.getPermissions() + ":" + fileExport.getProtocol() + ":" + fileExport.getSecurityType()
                    + ":" + fileExport.getMountPoint() + ":" + fileExport.getPath() + ":" + fileExport.getSubDirectory()
                    + ":" + fileExport.getComments());
            // Initialize exports map, if its not already initialized
            if (args.getFileObjExports() == null) {
                args.initFileObjExports();
            }

            String accessZoneName = getZoneName(args.getvNAS());

            // Create/update export in Isilon.
            String exportKey = fileExport.getFileExportKey();
            // If export with the given key does not exist, we create a new
            // export in Isilon and add it to the exports map.
            // In the other case, when export with a given key already exists in
            // Isilon, we need to overwrite endpoints in the current
            // export with endpoints in the
            // new export.
            FileExport fExport = args.getFileObjExports().get(exportKey);

            // check Isilon to verify if export does not exist.
            IsilonExport currentIsilonExport = null;
            if (fExport != null) {
                if (accessZoneName != null) {
                    currentIsilonExport = isi.getExport(fExport.getIsilonId(), accessZoneName);
                } else {
                    currentIsilonExport = isi.getExport(fExport.getIsilonId());
                }

            }
            if (fExport == null || currentIsilonExport == null) {
                // There is no Isilon export. Create Isilon export and set it
                // the map.
                String id = null;
                if (accessZoneName != null) {
                    _log.debug("Export will be created in zone: {}", accessZoneName);
                    id = isi.createExport(newIsilonExport, accessZoneName, args.getBypassDnsCheck());
                } else {
                    id = isi.createExport(newIsilonExport, args.getBypassDnsCheck());
                }

                // set file export data and add it to the export map
                fExport = new FileExport(newIsilonExport.getClients(), storagePortName, mountPath, strCSSecurityType,
                        permissions, root_user, protocol, storagePort, path, mountPath, subDirectory, comments);
                setClientsForFileExport(fExport, newIsilonExport );
                fExport.setIsilonId(id);
            } else {
                // There is export in Isilon with the given id.
                // Overwrite this export with a new set of clients.
                // We overwrite only clients element in exports. Isilon API does
                // not use read_only_clients, read_write_clients or
                // root_clients.
                List<String> newClients = newIsilonExport.getClients();
                newIsilonExport.setClients(new ArrayList<String>(newClients));

                // modify current export in isilon.
                if (accessZoneName != null) {
                    isi.modifyExport(fExport.getIsilonId(), accessZoneName, newIsilonExport, args.getBypassDnsCheck());
                } else {
                    isi.modifyExport(fExport.getIsilonId(), newIsilonExport, args.getBypassDnsCheck());
                }

                // update clients
                setClientsForFileExport(fExport, newIsilonExport );
            }

            args.getFileObjExports().put(exportKey, fExport);
        }
    }

    //Method to set the clients in FileExport to RO, RW or Root clients from IsilonExport based on permissions
    private void setClientsForFileExport(FileExport fExport, IsilonExport isilonExport){
       if (FileShareExport.Permissions.ro.name().equals(fExport.getPermissions())){
          _log.debug("Setting clients to RO clients");
          fExport.setClients(isilonExport.getReadOnlyClients());
       } else if (FileShareExport.Permissions.rw.name().equals(fExport.getPermissions())){
          _log.debug("Setting clients to RW clients");
          fExport.setClients(isilonExport.getReadWriteClients());
       } else if (FileShareExport.Permissions.root.name().equals(fExport.getPermissions())){
          _log.debug("Setting clients to root clients");
          fExport.setClients(isilonExport.getRootClients());
       } else if (fExport.getPermissions() == null){
          _log.debug("Permissions is null for FileExport; not setting clients");
       }


    }

    private IsilonExport setIsilonExport(FileExport fileExport, String permissions, List<String> securityType,
            String root_user, String mountPath, String comments) {

        IsilonExport newIsilonExport = new IsilonExport();
        newIsilonExport.addPath(mountPath);
        if (comments == null) {
            comments = "";
        }
        newIsilonExport.setComment(comments);

        // set security type
        // Need to use "unix" instead of "sys" . Isilon requires "unix", not
        // "sys".
        List<String> securityFlavors = new ArrayList<String>();
        for (String secType : securityType) {
            if (secType.equals(FileShareExport.SecurityTypes.sys.name())) {
                securityFlavors.add("unix");
            } else {
                securityFlavors.add(secType);
            }
        }
        newIsilonExport.setSecurityFlavors(new ArrayList<String>(securityFlavors));
        newIsilonExport.setMapRoot(root_user);

        // set permission and add clients (endpoints) to the right group
        // we need to set/reset read_only and map_all to support case when list
        // of clients in the request is empty.
        if (permissions.equals(FileShareExport.Permissions.ro.name())) {
            newIsilonExport.addReadOnlyClients(fileExport.getClients());
            newIsilonExport.setReadOnly();
        } else if (permissions.equals(FileShareExport.Permissions.rw.name())) {
            newIsilonExport.addReadWriteClients(fileExport.getClients());
            newIsilonExport.resetReadOnly();
        } else if (permissions.equals(FileShareExport.Permissions.root.name())) {
            newIsilonExport.addRootClients(fileExport.getClients());
            newIsilonExport.resetReadOnly();
        }

        return newIsilonExport;
    }

    private IsilonExport setIsilonExport(ExportRule expRule) {

        // String permissions, List<String> securityType, String root_user,
        // String mountPath, String comments) {

        _log.info("setIsilonExport called with {}", expRule.toString());
        String mountPath = expRule.getExportPath();
        String comments = "";
        String root_user = expRule.getAnon();

        IsilonExport newIsilonExport = new IsilonExport();
        newIsilonExport.addPath(mountPath);
        newIsilonExport.setComment(comments);

        int roHosts = 0;

        // Empty list of clients means --- all clients.
        if (expRule.getReadOnlyHosts() != null) {
            roHosts = expRule.getReadOnlyHosts().size();
            newIsilonExport.addReadOnlyClients(new ArrayList<String>(expRule.getReadOnlyHosts()));
        }

        if (expRule.getReadWriteHosts() != null) {
            newIsilonExport.addReadWriteClients(new ArrayList<String>(expRule.getReadWriteHosts()));
        }

        if (expRule.getRootHosts() != null) {
            newIsilonExport.addRootClients(new ArrayList<String>(expRule.getRootHosts()));
        }

        // set security type
        // Need to use "unix" instead of "sys" . Isilon requires "unix", not
        // "sys".
        // input export may contain one or more security types in a string separated by comma.
        ArrayList<String> secFlavors = new ArrayList<>();
        for (String securityType : expRule.getSecFlavor().split(",")) {
            securityType = securityType.trim();
            if (securityType.equals(FileShareExport.SecurityTypes.sys.name())) {
                securityType = "unix";
            }
            secFlavors.add(securityType);
        }
        newIsilonExport.setSecurityFlavors(secFlavors);
        newIsilonExport.setMapRoot(root_user);
        newIsilonExport.resetReadOnly();


        _log.info("setIsilonExport completed with creating {}", newIsilonExport.toString());
        return newIsilonExport;
    }

    /**
     * Delete exports
     * 
     * @param isi
     *            IsilonApi object to be used for communicating to the isilon
     *            system
     * @param currentExports
     *            Current exports map
     * @param exports
     *            exports to be deleted
     * @throws ControllerException
     * @throws IsilonException
     */
    private void isiUnexport(IsilonApi isi, FileDeviceInputOutput args, List<FileExport> exports)
            throws ControllerException, IsilonException {

        FSExportMap currentExports = args.getFileObjExports();
        // Do nothing if there are no exports
        if (currentExports == null || exports == null || exports.isEmpty()) {
            return;
        }

        for (FileExport fileExport : exports) {
            String key = fileExport.getFileExportKey(); // isiExportKey(req);
            String id = null;

            FileExport fExport = currentExports.get(key);
            if (fExport != null) {
                id = fExport.getIsilonId();
            }
            if (id != null) {
                String zoneName = getZoneName(args.getvNAS());
                if (zoneName != null) {
                    isi.deleteExport(id, zoneName);
                } else {
                    isi.deleteExport(id);
                }

                currentExports.remove(key);
            }
        }
    }

    private void isiExpandFS(IsilonApi isi, String quotaId, FileDeviceInputOutput args) throws ControllerException, IsilonException {

        // get quota from Isilon and check that requested capacity is larger than the current capacity
        Long capacity = args.getNewFSCapacity();

        IsilonSmartQuota quota = isi.getQuota(quotaId);
        Long hard = quota.getThresholds().getHard();
        if (capacity.compareTo(hard) < 0) {
            String msg = String
                    .format(
                            "In expanding Isilon FS requested capacity is less than current capacity of file system. Path: %s, current capacity: %d",
                            quota.getPath(), quota.getThresholds().getHard());
            _log.error(msg);
            throw IsilonException.exceptions.expandFsFailedinvalidParameters(quota.getPath(),
                    quota.getThresholds().getHard());
        }
        // Modify quota for file system.
        IsilonSmartQuota expandedQuota = getExpandedQuota(isi, args, capacity);
        isi.modifyQuota(quotaId, expandedQuota);
    }

    /**
     * restapi request for reduction of fileshare size.
     * 
     * @param isi
     * @param quotaId
     * @param args
     * @throws ControllerException
     * @throws IsilonException
     */
    private void isiReduceFS(IsilonApi isi, String quotaId, FileDeviceInputOutput args) throws ControllerException, IsilonException {
        Long capacity = args.getNewFSCapacity();
        IsilonSmartQuota quota = isi.getQuota(quotaId);
        // Modify quoties for fileshare
        quota = getExpandedQuota(isi, args, capacity);
        isi.modifyQuota(quotaId, quota);
    }

    private IsilonSmartQuota getExpandedQuota(IsilonApi isi, FileDeviceInputOutput args, Long capacity) {
        Long notificationLimit = 0L;
        Long softLimit = 0L;
        Long softGracePeriod = 0L;

        if (args.getFsNotificationLimit() != null) {
            notificationLimit = Long.valueOf(args.getFsNotificationLimit());
        }

        if (args.getFsSoftLimit() != null) {
            softLimit = Long.valueOf(args.getFsSoftLimit());
        }

        if (args.getFsSoftGracePeriod() != null) {
            softGracePeriod = Long.valueOf(args.getFsSoftGracePeriod());
        }

        return isi.constructIsilonSmartQuotaObjectWithThreshold(null, null, capacity, false, null, capacity,
                notificationLimit, softLimit, softGracePeriod);
    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        Boolean fsDirExists = true;
        Boolean fsDirCreatedByMe = false;
        try {
            _log.info("IsilonFileStorageDevice doCreateFS {} with name {} - start", args.getFsId(), args.getFsName());
            IsilonApi isi = getIsilonDevice(storage);

            VirtualNAS vNAS = args.getvNAS();
            String vNASPath = null;

            // get the custom path from the controller configuration
            String customPath = getCustomPath(storage, args);
            if (vNAS != null) {
                vNASPath = vNAS.getBaseDirPath();
                _log.info("vNAS base directory path: {}", vNASPath);
            }

            String usePhysicalNASForProvisioning = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.USE_PHYSICAL_NAS_FOR_PROVISIONING, "isilon", null);
            _log.info("Use System access zone to provision filesystem? {}", usePhysicalNASForProvisioning);

            String mountPath = null;
            String fsName = args.getFsName();
            if (args.getFs().getPersonality() != null && args.getFs().getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
                FileShare fsParent = _dbClient.queryObject(FileShare.class, args.getFs().getParentFileShare().getURI());
                fsName = fsParent.getName();
                // Add if there is any suffix in target fs label!!
                if (args.getFs().getLabel().contains("-target")) {
                    String[] fsNameSuffix = args.getFs().getLabel().split(fsParent.getName() + "-target");
                    if (fsNameSuffix != null && fsNameSuffix.length > 1 && !fsNameSuffix[1].isEmpty()) {
                        fsName = fsName + fsNameSuffix[1];
                    }
                } else if (args.getFs().getLabel().contains("-localTarget")) {
                    String[] fsNameSuffix = args.getFs().getLabel().split(fsParent.getName() + "-localTarget");
                    if (fsNameSuffix != null && fsNameSuffix.length > 1 && !fsNameSuffix[1].isEmpty()) {
                        fsName = fsName + fsNameSuffix[1];
                    }
                }
            }
            // Update the mount path as required
            if (vNASPath != null && !vNASPath.trim().isEmpty()) {
                mountPath = vNASPath + FW_SLASH + customPath + FW_SLASH + fsName;

            } else if (Boolean.valueOf(usePhysicalNASForProvisioning)) {

                mountPath = IFS_ROOT + FW_SLASH + getSystemAccessZoneNamespace() + FW_SLASH + customPath + FW_SLASH + fsName;
            } else {
                _log.error(
                        "No suitable access zone found for provisioning. Provisioning on System access zone is disabled");
                throw DeviceControllerException.exceptions.createFileSystemOnPhysicalNASDisabled();
            }

            // replace extra forward slash with single one
            mountPath = mountPath.replaceAll("/+", "/");
            _log.info("Mount path to mount the Isilon File System {}", mountPath);
            args.setFsMountPath(mountPath);
            args.setFsNativeGuid(args.getFsMountPath());
            args.setFsNativeId(args.getFsMountPath());
            args.setFsPath(args.getFsMountPath());

            // Update the mount path for local target!!!
            updateLocalTargetFileSystemPath(storage, args);

            // Create the target directory only if the replication policy was not applied!!
            // If policy was applied at higher level, policy would create target file system directories!
            boolean replicationExistsOnTarget = FileOrchestrationUtils.isReplicationPolicyExistsOnTarget(_dbClient, storage,
                    args.getVPool(), args.getProject(), args.getFs());
            if (FileOrchestrationUtils.isPrimaryFileSystemOrNormalFileSystem(args.getFs())
                    || !replicationExistsOnTarget) {

                if (!replicationExistsOnTarget) {
                    // Verify if the path of the new policy to be created only if the policy does not exist in the storage system
                    checkNewPolicyPathHasData(args.getVPool(), args.getProject(), args.getFs(), args.getvNAS(), isi);
                }

                // Verify the file system directory exists or not!!
                fsDirExists = isi.existsDir(args.getFsMountPath());
                if (!fsDirExists) {
                    // create directory for the file share
                    isi.createDir(args.getFsMountPath(), true);
                    fsDirCreatedByMe = true;
                } else {
                    // Fail to create file system, as the directory already exists!!
                    _log.error("File system creation failed due to directory path {} already exists.", args.getFsMountPath());
                    throw DeviceControllerException.exceptions.failToCreateFileSystem(args.getFsMountPath());
                }

                Long softGrace = null;
                if (args.getFsSoftGracePeriod() != null) {
                    softGrace = Long.valueOf(args.getFsSoftGracePeriod());
                }

                // set quota - save the quota id to extensions
                String qid = createQuotaWithThreshold(args.getFsMountPath(), args.getFsCapacity(), args.getFsSoftLimit(),
                        args.getFsNotificationLimit(), softGrace, null, isi);

                if (args.getFsExtensions() == null) {
                    args.initFsExtensions();
                }
                args.getFsExtensions().put(QUOTA, qid);
            }

            // set protection level
            // String protection = args.getFSProtectionLevel();
            // Call isilon api to set protection level

            _log.info("IsilonFileStorageDevice doCreateFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doCreateFS failed.", e);
            // Delete the file system directory only if it was created from this workflow
            // instead of delete entire fs system tree and deleting its objects
            // delete the fs directory alone!!!
            if (fsDirCreatedByMe) {
                // delete isilon directory
                _log.info("doCreateFS failed, deleting the isilon directory {} which has been created in this workflow",
                        args.getFsMountPath());
                IsilonApi isi = getIsilonDevice(storage);
                isi.deleteDir(args.getFsMountPath());
            }

            return BiosCommandResult.createErrorResult(e);
        }
    }


    /**
     * Method to check if the path of the 'to be created' file policy on the target array contains data
     * 
     * @param vpool
     * @param project
     * @param fs - Target Filesystem
     * @param virtualNAS
     * @param isi - Isilon Api Object
     */
    private void checkNewPolicyPathHasData(VirtualPool vpool, Project project, FileShare fs, VirtualNAS virtualNAS,
            IsilonApi isi) {

        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            FileShare sourceFs = _dbClient.queryObject(FileShare.class, fs.getParentFileShare().getURI());
            List<FilePolicy> replicationPolicies = FileOrchestrationUtils.getReplicationPolices(_dbClient, vpool, project, sourceFs);
            if (replicationPolicies != null && !replicationPolicies.isEmpty()) {
                if (replicationPolicies.size() > 1) {
                    _log.warn("More than one replication policy found {}", replicationPolicies.size());
                    throw DeviceControllerException.exceptions.failToCreateFileSystem(String.format(
                            "More than one replication policy found: %s for Fileshare %s", replicationPolicies.size(), sourceFs.getId()));
                }

                FilePolicy fileRepPolicy = replicationPolicies.get(0);
                String policyPath = null;

                FileDeviceInputOutput args = new FileDeviceInputOutput();
                args.setVPool(vpool);
                args.setProject(project);
                args.setvNAS(virtualNAS);
                policyPath = generatePathForPolicy(fileRepPolicy, fs, args);

                // _localTarget suffix is not needed for policy at file system level
                // Add the suffix only for local replication policy at higher level
                if (fileRepPolicy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.LOCAL.name())
                        && !FilePolicyApplyLevel.file_system.name().equalsIgnoreCase(fileRepPolicy.getApplyAt())) {
                    policyPath = policyPath + "_localTarget";
                }

                // Policy path on target array will be checked at all levels
                _log.info("Check if Policy path has data on Target array: " + policyPath);
                if (StringUtils.isNotEmpty(policyPath) && isi.existsDir(policyPath) && isi.fsDirHasData(policyPath)) {
                    _log.error("File system creation failed due to directory path {} already exists and contains data",
                            policyPath);
                    throw DeviceControllerException.exceptions.failToCreateFileSystem(policyPath);
                } else {
                    _log.info("Policy path doesn't exist on target array. Proceeding with policy creation");

                }
            }
        }
    }

    private FileDeviceInputOutput prepareFileDeviceInputOutput(boolean forceDelete, URI uri, String opId) {
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        boolean isFile = false;
        args.setOpId(opId);
        if (URIUtil.isType(uri, FileShare.class)) {
            isFile = true;
            args.setForceDelete(forceDelete);
            FileShare fsObj = _dbClient.queryObject(FileShare.class, uri);

            if (fsObj.getVirtualNAS() != null) {
                VirtualNAS vNAS = _dbClient.queryObject(VirtualNAS.class, fsObj.getVirtualNAS());
                args.setvNAS(vNAS);
            }

            args.addFileShare(fsObj);
            args.setFileOperation(isFile);
        }
        return args;
    }

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteFS {} - start", args.getFsId());
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteFS(isi, args);
            _log.info("IsilonFileStorageDevice doDeleteFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public boolean doCheckFSExists(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        _log.info("checking file system existence on array: {}", args.getFsName());
        boolean isFSExists = true; // setting true by default for safer side
        try {
            IsilonApi isi = getIsilonDevice(storage);
            isFSExists = isi.existsDir(args.getFsMountPath());
        } catch (IsilonException e) {
            _log.error("Querying FS failed", e);
        }
        return isFSExists;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doExpandFS {} - start", args.getFsId());
            IsilonApi isi = getIsilonDevice(storage);
            String quotaId = null;
            if (args.getFsExtensions() != null && args.getFsExtensions().get(QUOTA) != null) {
                quotaId = args.getFsExtensions().get(QUOTA);
            } else {
                // when policy is applied at higher level, we will ignore the target filesystem
                FileShare fileShare = args.getFs();
                if (null != fileShare.getPersonality() &&
                        PersonalityTypes.TARGET.name().equals(fileShare.getPersonality()) &&
                        null == fileShare.getExtensions()) {
                    _log.info("Quota id is not found so ignore the expand filesystem ", fileShare.getLabel());
                    return BiosCommandResult.createSuccessfulResult();
                }
                final ServiceError serviceError = DeviceControllerErrors.isilon.doExpandFSFailed(args.getFsId());
                _log.error(serviceError.getMessage());
                return BiosCommandResult.createErrorResult(serviceError);
            }

            isiExpandFS(isi, quotaId, args);
            _log.info("IsilonFileStorageDevice doExpandFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doExpandFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("doExpandFS failed.", e);
            // convert this to a ServiceError and create/or reuse a service
            // code
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToExpandFileSystem();
            return BiosCommandResult.createErrorResult(serviceError);
        }
    }

    @Override
    public BiosCommandResult doReduceFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doReduceFS {} - start", args.getFsId());
            IsilonApi isi = getIsilonDevice(storage);
            String quotaId = null;
            if (args.getFsExtensions() != null && args.getFsExtensions().get(QUOTA) != null) {
                quotaId = args.getFsExtensions().get(QUOTA);

                Long capacity = args.getNewFSCapacity();
                IsilonSmartQuota quota = isi.getQuota(quotaId);
                // new capacity should be less than usage capacity of a filehare
                if (capacity.compareTo(quota.getUsagePhysical()) < 0) {

                    Double dUsageSize = SizeUtil.translateSize(quota.getUsagePhysical(), SizeUtil.SIZE_GB);
                    Double dNewCapacity = SizeUtil.translateSize(capacity, SizeUtil.SIZE_GB);

                    String msg = String.format(
                            "as requested reduced size [%.1fGB] is smaller than used capacity [%.1fGB] for filesystem %s",
                            dNewCapacity, dUsageSize, args.getFs().getName());

                    _log.error(msg);
                    final ServiceError serviceError = DeviceControllerErrors.isilon.unableUpdateQuotaDirectory(msg);
                    return BiosCommandResult.createErrorResult(serviceError);
                } else {
                    isiReduceFS(isi, quotaId, args);
                }
            } else {
                // when policy is applied at higher level, we will ignore the target filesystem
                FileShare fileShare = args.getFs();
                if (null != fileShare.getPersonality() &&
                        PersonalityTypes.TARGET.name().equals(fileShare.getPersonality())
                        && null == fileShare.getExtensions()) {
                    _log.info("Quota id is not found, so ignore the reduce filesystem ", fileShare.getLabel());
                    return BiosCommandResult.createSuccessfulResult();
                }
                final ServiceError serviceError = DeviceControllerErrors.isilon.doReduceFSFailed(args.getFsId());
                _log.error(serviceError.getMessage());
                return BiosCommandResult.createErrorResult(serviceError);
            }
            _log.info("IsilonFileStorageDevice doReduceFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doReduceFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage, FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {

        // Snapshot Export operation is not supported by ISILON.
        if (args.getFileOperation() == false) {
            return BiosCommandResult
                    .createErrorResult(DeviceControllerErrors.isilon.unSupportedOperation(EXPORT_OP_NAME));
        }

        try {
            _log.info("IsilonFileStorageDevice doExport {} - start", args.getFileObjId());
            IsilonApi isi = getIsilonDevice(storage);
            isiExport(isi, args, exportList);
            _log.info("IsilonFileStorageDevice doExport {} - complete", args.getFileObjId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doExport failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage, FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {

        try {
            _log.info("IsilonFileStorageDevice doUnexport: {} - start", args.getFileObjId());
            IsilonApi isi = getIsilonDevice(storage);
            isiUnexport(isi, args, exportList);
            _log.info("IsilonFileStorageDevice doUnexport {} - complete", args.getFileObjId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doUnexport failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doShare(StorageSystem storage, FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        // Snapshot Share operation is not supported by ISILON.
        if (args.getFileOperation() == false) {
            return BiosCommandResult
                    .createErrorResult(DeviceControllerErrors.isilon.unSupportedOperation(SHARE_OP_NAME));
        }

        try {
            _log.info("IsilonFileStorageDevice doShare() - start");
            IsilonApi isi = getIsilonDevice(storage);
            isiShare(isi, args, smbFileShare);
            _log.info("IsilonFileStorageDevice doShare() - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doShare failed.", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("doShare failed.", e);
            // convert this to a ServiceError and create/or reuse a service
            // code
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToCreateFileShare();
            return BiosCommandResult.createErrorResult(serviceError);
        }
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage, FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteShare: {} - start");
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteShare(isi, args, smbFileShare);
            _log.info("IsilonFileStorageDevice doDeleteShare {} - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteShare failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteShares: {} - start");
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteShares(isi, args);
            _log.info("IsilonFileStorageDevice doDeleteShares {} - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteShares failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doModifyFS {} - start", args.getFsId());
            IsilonApi isi = getIsilonDevice(storage);
            String quotaId = null;
            if (args.getFsExtensions() != null && args.getFsExtensions().get(QUOTA) != null) {
                quotaId = args.getFsExtensions().get(QUOTA);
            } else {
                final ServiceError serviceError = DeviceControllerErrors.isilon.unableToUpdateFileSystem(args.getFsId());
                _log.error(serviceError.getMessage());
                return BiosCommandResult.createErrorResult(serviceError);
            }

            IsilonSmartQuota expandedQuota = getExpandedQuota(isi, args, args.getFsCapacity());
            isi.modifyQuota(quotaId, expandedQuota);
            _log.info("IsilonFileStorageDevice doModifyFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doModifyFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("doModifyFS failed.", e);
            // convert this to a ServiceError and create/or reuse a service
            // code
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToUpdateFileSystem(args.getFsId());
            return BiosCommandResult.createErrorResult(serviceError);
        }
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doSnapshotFS {} {} - start", args.getSnapshotId(),
                    args.getSnapshotName());
            IsilonApi isi = getIsilonDevice(storage);
            // To Do - add timestamp for uniqueness
            String snapId = isi.createSnapshot(args.getSnapshotName(), args.getFsMountPath());
            if (args.getSnapshotExtensions() == null) {
                args.initSnapshotExtensions();
            }
            args.getSnapshotExtensions().put("id", snapId);
            args.setSnapNativeId(snapId);
            String path = getSnapshotPath(args.getFsMountPath(), args.getSnapshotName());
            args.setSnapshotMountPath(path);
            args.setSnapshotPath(path);
            _log.info("IsilonFileStorageDevice doSnapshotFS {} - complete", args.getSnapshotId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doSnapshotFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        String opName = ResourceOperationTypeEnum.RESTORE_FILE_SNAPSHOT.getName();
        ServiceError serviceError = IsilonException.errors.jobFailed(opName);
        result.error(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteSnapshot {} - start", args.getSnapshotId());
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteSnapshot(isi, args);
            _log.info("IsilonFileStorageDevice doDeleteSnapshot {} - complete", args.getSnapshotId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteSnapshot failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    // Get FS snapshot list from the array
    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage, FileDeviceInputOutput args,
            List<String> snapshots) throws ControllerException {

        String op = "getFSSnapshotList";
        String devType = storage.getSystemType();
        BiosCommandResult result = BiosCommandResult
                .createErrorResult(DeviceControllerException.errors.unsupportedOperationOnDevType(op, devType));

        return result;

    }

    @Override
    public void doConnect(StorageSystem storage) throws ControllerException {
        try {
            _log.info("doConnect {} - start", storage.getId());
            IsilonApi isi = getIsilonDevice(storage);
            isi.getClusterInfo();
            String msg = String.format("doConnect %1$s - complete", storage.getId());
            _log.info(msg);
        } catch (IsilonException e) {
            _log.error("doConnect failed.", e);
            throw DeviceControllerException.exceptions.connectStorageFailed(e);
        }
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        // not much to do here ... just reply success
    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToGetPhysicalInventory(storage.getId());
        return BiosCommandResult.createErrorResult(serviceError);
    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage, FileDeviceInputOutput args,
            QuotaDirectory quotaDir) throws ControllerException {

        // Get Parent FS mount path
        // Get Quota Directory Name
        // Get Quota Size
        // Call create Directory
        // Call create Quota (Aways use that quota for updating the size)

        String fsMountPath = args.getFsMountPath();
        Long qDirSize = quotaDir.getSize();
        String qDirPath = fsMountPath + "/" + quotaDir.getName();
        _log.info("IsilonFileStorageDevice doCreateQuotaDirectory {} with size {} - start", qDirPath, qDirSize);
        Boolean fsDirCreatedByMe = false;
        try {
            IsilonApi isi = getIsilonDevice(storage);
            // Verify the quota directory path exists or not!!
            if (!isi.existsDir(qDirPath)) {
                // create directory for the quota directory
                isi.createDir(qDirPath, true);
                fsDirCreatedByMe = true;
            } else {
                // Fail to create quota directory, as the directory already exists!!
                _log.error("Quota directory creation failed due to directory path {} already exists.", qDirPath);
                throw DeviceControllerException.exceptions.failToCreateQuotaDirectory(qDirPath);
            }

            String qid = checkThresholdAndcreateQuota(quotaDir, qDirSize, qDirPath, args.getFsCapacity(), isi);

            if (args.getQuotaDirExtensions() == null) {
                args.initQuotaDirExtensions();
            }
            args.getQuotaDirExtensions().put(QUOTA, qid);

            _log.info("IsilonFileStorageDevice doCreateQuotaDirectory {} with size {} - complete", qDirPath, qDirSize);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            // Delete the quota directory only if it was created from this workflow
            // instead of delete entire quota tree
            // delete the directory alone with recursive false!!!
            if (fsDirCreatedByMe) {
                // delete isilon directory
                _log.info("doCreateQuotaDirectory failed, deleting the isilon directory {} which has been created in this workflow",
                        qDirPath);
                IsilonApi isi = getIsilonDevice(storage);
                isi.deleteDir(qDirPath);
            }
            _log.error("doCreateQuotaDirectory failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {

        // Get Parent FS Mount Path
        // Get Quota Directory Name
        // Get Quota Size
        // Call Delete Quota
        // Call Delete Directory recursively

        QuotaDirectory quotaDir = args.getQuotaDirectory();
        String fsMountPath = args.getFsMountPath();
        Long qDirSize = quotaDir.getSize();
        String qDirPath = fsMountPath + "/" + quotaDir.getName();
        _log.info("IsilonFileStorageDevice doDeleteQuotaDirectory {} with size {} - start", qDirPath, qDirSize);
        try {
            IsilonApi isi = getIsilonDevice(storage);

            // Do not delete quota directory
            // if the quota directory has some data in it.
            if (isi.fsDirHasData(qDirPath)) {
                // Fail to delete quota directory which has data in it!!!
                _log.error("Quota directory deletion failed as it's directory path {} has content in it", qDirPath);
                throw DeviceControllerException.exceptions.failToDeleteQuotaDirectory(qDirPath);
            }

            String quotaId = null;
            if (quotaDir.getExtensions() != null) {
                quotaId = quotaDir.getExtensions().get(QUOTA);
            }
            if (quotaId != null) {
                _log.info("IsilonFileStorageDevice doDeleteQuotaDirectory , Delete Quota {}", quotaId);
                isi.deleteQuota(quotaId);
            }

            // delete directory for the Quota Directory
            isi.deleteDir(qDirPath);
            _log.info("IsilonFileStorageDevice doDeleteQuotaDirectory {} with size {} - complete", qDirPath, qDirSize);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteQuotaDirectory failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage, FileDeviceInputOutput args,
            QuotaDirectory quotaDir) throws ControllerException {
        // Get Parent FS mount path
        // Get Quota Directory Name
        // Get Quota Size
        // Call Update Quota (Aways use that quota for updating the size)
        QuotaDirectory quotaDirObj = null;
        String fsMountPath = args.getFsMountPath();
        Long qDirSize = quotaDir.getSize();
        String qDirPath = fsMountPath + "/" + quotaDir.getName();
        _log.info("IsilonFileStorageDevice doUpdateQuotaDirectory {} with size {} - start", qDirPath, qDirSize);
        try {
            IsilonApi isi = getIsilonDevice(storage);
            URI qtreeURI = quotaDir.getId();
            quotaDirObj = _dbClient.queryObject(QuotaDirectory.class, qtreeURI);

            String quotaId = null;
            if (quotaDirObj.getExtensions() != null) {
                quotaId = quotaDirObj.getExtensions().get(QUOTA);
            }

            if (quotaId != null) {
                // Isilon does not allow to update quota directory to zero.
                IsilonSmartQuota isiCurrentSmartQuota = isi.getQuota(quotaId);
                long quotaUsageSpace = isiCurrentSmartQuota.getUsagePhysical();

                if (qDirSize > 0 && qDirSize.compareTo(quotaUsageSpace) > 0) {
                    _log.info("IsilonFileStorageDevice doUpdateQuotaDirectory , Update Quota {} with Capacity {}", quotaId, qDirSize);
                    IsilonSmartQuota expandedQuota = getQuotaDirectoryExpandedSmartQuota(quotaDir, qDirSize, args.getFsCapacity(), isi);
                    isi.modifyQuota(quotaId, expandedQuota);
                } else {
                    Double dUsage = SizeUtil.translateSize(quotaUsageSpace, SizeUtil.SIZE_GB);
                    Double dQuotaSize = SizeUtil.translateSize(qDirSize, SizeUtil.SIZE_GB);
                    String msg = String.format(
                            "as requested reduced size [%.1fGB] is smaller than used capacity [%.1fGB] for filesystem %s",
                            dQuotaSize, dUsage, args.getFs().getName());
                    _log.error("doUpdateQuotaDirectory : " + msg);
                    ServiceError error = DeviceControllerErrors.isilon.unableUpdateQuotaDirectory(msg);
                    return BiosCommandResult.createErrorResult(error);
                }

            } else {
                // Create a new Quota
                String qid = checkThresholdAndcreateQuota(quotaDir, qDirSize, qDirPath, null, isi);

                if (args.getQuotaDirExtensions() == null) {
                    args.initQuotaDirExtensions();
                }
                args.getQuotaDirExtensions().put(QUOTA, qid);

            }
            _log.info("IsilonFileStorageDevice doUpdateQuotaDirectory {} with size {} - complete", qDirPath, qDirSize);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doUpdateQuotaDirectory failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    private IsilonSmartQuota getQuotaDirectoryExpandedSmartQuota(QuotaDirectory quotaDir, Long qDirSize, Long fsSize, IsilonApi isi) {
        Long notificationLimit = 0L;
        Long softlimit = 0L;
        Long softGrace = 0L;

        if (quotaDir.getNotificationLimit() != null) {
            notificationLimit = Long.valueOf(quotaDir.getNotificationLimit());
        }

        if (quotaDir.getSoftLimit() != null) {
            softlimit = Long.valueOf(quotaDir.getSoftLimit());
        }

        if (quotaDir.getSoftGrace() != null) {
            softGrace = Long.valueOf(quotaDir.getSoftGrace());
        }
        return isi.constructIsilonSmartQuotaObjectWithThreshold(null, null, fsSize, false, null, qDirSize,
                notificationLimit, softlimit, softGrace);
    }

    private String checkThresholdAndcreateQuota(QuotaDirectory quotaDir, Long qDirSize, String qDirPath, Long fsSize, IsilonApi isi) {
        Long notificationLimit = 0L;
        Long softlimit = 0L;
        Long softGrace = 0L;

        if (quotaDir.getNotificationLimit() != null) {
            notificationLimit = Long.valueOf(quotaDir.getNotificationLimit());
        }

        if (quotaDir.getSoftLimit() != null) {
            softlimit = Long.valueOf(quotaDir.getSoftLimit());
        }

        if (quotaDir.getSoftGrace() != null) {
            softGrace = Long.valueOf(quotaDir.getSoftGrace());
        }

        return createQuotaWithThreshold(qDirPath, qDirSize,
                softlimit, notificationLimit, softGrace, fsSize, isi);
    }

    public String createQuotaWithThreshold(String qDirPath, Long qDirSize, Long softLimitSize, Long notificationLimitSize,
            Long softGracePeriod, Long fsSize, IsilonApi isi) {
        boolean bThresholdsIncludeOverhead = true;
        boolean bIncludeSnapshots = true;

        if (configinfo != null) {
            if (configinfo.containsKey("thresholdsIncludeOverhead")) {
                bThresholdsIncludeOverhead = Boolean.parseBoolean(configinfo.get("thresholdsIncludeOverhead"));
            }
            if (configinfo.containsKey("includeSnapshots")) {
                bIncludeSnapshots = Boolean.parseBoolean(configinfo.get("includeSnapshots"));
            }

        }

        // set quota - save the quota id to extensions
        String qid = isi.createQuota(qDirPath, fsSize, bThresholdsIncludeOverhead,
                bIncludeSnapshots, qDirSize, notificationLimitSize != null ? notificationLimitSize : 0L,
                softLimitSize != null ? softLimitSize : 0L, softGracePeriod != null ? softGracePeriod : 0L);
        return qid;
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        List<ExportRule> allExports = args.getExistingDBExportRules();
        String subDir = args.getSubDirectory();
        boolean allDirs = args.isAllDir();

        String exportPath;
        String subDirExportPath = "";
        subDir = args.getSubDirectory();

        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null && subDir.length() > 0) {
                subDirExportPath = args.getSnapshotPath() + "/" + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null && subDir.length() > 0) {
                subDirExportPath = args.getFs().getPath() + "/" + subDir;
            }
        }

        _log.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        _log.info("Number of existing exports found {}", allExports.size());

        try {

            IsilonApi isi = getIsilonDevice(storage);
            String zoneName = getZoneName(args.getvNAS());

            if (allDirs) {
                // ALL EXPORTS
                _log.info(
                        "Deleting all exports specific to filesystem at device and rules from DB including sub dirs rules and exports");
                for (ExportRule rule : allExports) {
                    _log.info("Delete IsilonExport id {} for path {}", rule.getDeviceExportId(), rule.getExportPath());
                    if (zoneName != null) {
                        isi.deleteExport(rule.getDeviceExportId(), zoneName);
                    } else {
                        isi.deleteExport(rule.getDeviceExportId());
                    }
                }

            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _log.info("Deleting all subdir exports rules at ViPR and  sub directory export at device {}", subDir);
                for (ExportRule rule : allExports) {
                    _log.info("Delete IsilonExport id for path {} f containing subdirectory {}",
                            rule.getDeviceExportId() + ":" + rule.getExportPath(), subDir);

                    String fsExportPathWithSub = args.getFsPath() + "/" + subDir;
                    if (rule.getExportPath().equalsIgnoreCase(fsExportPathWithSub)) {
                        _log.info("Delete IsilonExport id {} for path {}", rule.getDeviceExportId(),
                                rule.getExportPath());
                        if (zoneName != null) {
                            isi.deleteExport(rule.getDeviceExportId(), zoneName);
                        } else {
                            isi.deleteExport(rule.getDeviceExportId());
                        }
                    }
                }

            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                _log.info("Deleting all export rules  from DB and export at device not included sub dirs");
                for (ExportRule rule : allExports) {
                    if (rule.getExportPath().equalsIgnoreCase(exportPath)) {
                        _log.info("Delete IsilonExport id {} for path {}", rule.getDeviceExportId(),
                                rule.getExportPath());
                        if (zoneName != null) {
                            isi.deleteExport(rule.getDeviceExportId(), zoneName);
                        } else {
                            isi.deleteExport(rule.getDeviceExportId());
                        }
                    }
                }
            }

        } catch (IsilonException ie) {
            _log.info("Exception: {}", ie);

            throw new DeviceControllerException("Exception while performing export for {0} ",
                    new Object[] { args.getFsId() });
        }

        _log.info("IsilonFileStorageDevice exportFS {} - complete", args.getFsId());
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        return result;
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        // Requested Export Rules
        List<ExportRule> exportAdd = args.getExportRulesToAdd();
        List<ExportRule> exportDelete = args.getExportRulesToDelete();
        List<ExportRule> exportModify = args.getExportRulesToModify();

        // To be processed export rules
        List<ExportRule> exportsToRemove = new ArrayList<>();
        List<ExportRule> exportsToModify = new ArrayList<>();
        List<ExportRule> exportsToAdd = new ArrayList<>();

        // Calculate Export Path
        String exportPath;
        String subDir = args.getSubDirectory();

        // It is a Snapshot Export Update and so Sub Directory will be
        // ".snapshot"
        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null && subDir.length() > 0) {
                exportPath = args.getSnapshotPath() + "/" + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null && subDir.length() > 0) {
                exportPath = args.getFs().getPath() + "/" + subDir;
            }
        }

        _log.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        try {
            // add the new export rule from the array into the update request.
            Map<String, ExportRule> arrayExportRuleMap = extraExportRuleFromArray(storage, args);

            if (!arrayExportRuleMap.isEmpty()) {
                if (exportModify != null) {
                    // merge the end point for which sec flavor is common.
                    for (ExportRule exportRule : exportModify) {
                        ExportRule arrayExportRule = arrayExportRuleMap.remove(exportRule.getSecFlavor());
                        if (arrayExportRule != null) {

                            if (exportRule.getReadOnlyHosts() != null) {
                                exportRule.getReadOnlyHosts().addAll(arrayExportRule.getReadOnlyHosts());
                            } else {
                                exportRule.setReadOnlyHosts(arrayExportRule.getReadOnlyHosts());

                            }
                            if (exportRule.getReadWriteHosts() != null) {
                                exportRule.getReadWriteHosts().addAll(arrayExportRule.getReadWriteHosts());
                            } else {
                                exportRule.setReadWriteHosts(arrayExportRule.getReadWriteHosts());

                            }
                            if (exportRule.getRootHosts() != null) {
                                exportRule.getRootHosts().addAll(arrayExportRule.getRootHosts());
                            } else {
                                exportRule.setRootHosts(arrayExportRule.getRootHosts());

                            }
                        }
                    }
                    // now add the remaining export rule
                    exportModify.addAll(arrayExportRuleMap.values());

                } else {
                    // if exportModify is null then create a new export rule and add
                    exportModify = new ArrayList<ExportRule>();
                    exportModify.addAll(arrayExportRuleMap.values());

                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            _log.error("Not able to fetch latest Export rule from backend array.", e);

        }

        // ALL EXPORTS
        List<ExportRule> existingDBExportRule = args.getExistingDBExportRules();
        List<ExportRule> exportsToprocess = new ArrayList<>();
        for (ExportRule rule : existingDBExportRule) {
            if (rule.getExportPath().equalsIgnoreCase(exportPath)) {
                exportsToprocess.add(rule);
            }
        }

        _log.info("Number of existing Rules found {} for exportPath {}", exportsToprocess.size(), exportPath);
        // Isilon have separate entry for read only and read/write host list
        // if we want to modify export from host H1 with permission read to H2
        // with read/write. then need to delete the entry from read
        // list and add to read/Write list.
        if (!exportsToprocess.isEmpty() || !exportAdd.isEmpty()) {
            if (exportModify != null && !exportModify.isEmpty()) {
                for (ExportRule existingRule : exportsToprocess) {
                    for (ExportRule newExportRule : exportModify) {
                        if (newExportRule.getSecFlavor().equals(existingRule.getSecFlavor())) {
                            newExportRule.setDeviceExportId(existingRule.getDeviceExportId());
                            exportsToModify.add(newExportRule);
                        }
                    }
                }
            }
            // Handle Delete export Rules
            if (exportDelete != null && !exportDelete.isEmpty()) {
                for (ExportRule existingRule : exportsToprocess) {
                    for (ExportRule oldExport : exportDelete) {
                        if (oldExport.getSecFlavor().equals(existingRule.getSecFlavor())) {
                            _log.info("Deleting Export Rule {}", existingRule);
                            exportsToRemove.add(existingRule);
                        }
                    }
                }
            }
            // No of exports found to remove from the list
            _log.info("No of exports found to remove from the existing exports list {}", exportsToRemove.size());
            exportsToprocess.removeAll(exportsToRemove);

            // Handle Add Export Rules
            if (exportAdd != null && !exportAdd.isEmpty()) {
                for (ExportRule newExport : exportAdd) {
                    _log.info("Add Export Rule {}", newExport);
                    newExport.setExportPath(exportPath);
                    exportsToAdd.add(newExport);
                }
            }
            exportsToprocess.addAll(exportAdd);
        }

        // Process Mods
        IsilonApi isi = getIsilonDevice(storage);

        for (ExportRule existingRule : exportsToModify) {
            _log.info("Modify Export rule : {}", existingRule.toString());
        }

        processIsiExport(isi, args, exportsToModify);

        for (ExportRule existingRule : exportsToRemove) {
            _log.info("Remove Export rule : {}", existingRule.toString());
        }

        processRemoveIsiExport(isi, args, exportsToRemove);

        for (ExportRule existingRule : exportsToAdd) {
            _log.info("Add Export rule : {}", existingRule.toString());
        }

        processAddIsiExport(isi, args, exportsToAdd);

        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;

    }

    /**
     * Get the export rule which are present in arry but not in CoprHD Database.
     * 
     * @param storage
     * @param args
     * @return map with security flavor and export rule
     */
    private Map<String, ExportRule> extraExportRuleFromArray(StorageSystem storage, FileDeviceInputOutput args) {

        // map to store the export rule grouped by sec flavor
        Map<String, ExportRule> exportRuleMap = new HashMap<>();
        List<IsilonExport> exportsList = new ArrayList<IsilonExport>();

        Set<String> arrayReadOnlyHost = new HashSet<>();
        Set<String> arrayReadWriteHost = new HashSet<>();
        Set<String> arrayRootHost = new HashSet<>();

        Set<String> dbReadOnlyHost = new HashSet<>();
        Set<String> dbReadWriteHost = new HashSet<>();
        Set<String> dbRootHost = new HashSet<>();

        // get all export rule from CoprHD data base
        List<ExportRule> existingDBExportRules = args.getExistingDBExportRules();

        // get the all the export from the storage system.
        IsilonApi isi = getIsilonDevice(storage);
        for (ExportRule exportRule : existingDBExportRules) {
            if (exportRule.getReadOnlyHosts() != null) {
                dbReadOnlyHost.addAll(exportRule.getReadOnlyHosts());
            }
            if (exportRule.getReadWriteHosts() != null) {
                dbReadWriteHost.addAll(exportRule.getReadWriteHosts());
            }
            if (exportRule.getRootHosts() != null) {
                dbRootHost.addAll(exportRule.getRootHosts());
            }

            String isilonExportId = exportRule.getDeviceExportId();
            if (isilonExportId != null) {
                IsilonExport isilonExport = null;
                String zoneName = getZoneName(args.getvNAS());
                if (zoneName != null) {
                    isilonExport = isi.getExport(isilonExportId, zoneName);
                } else {
                    isilonExport = isi.getExport(isilonExportId);
                }
                exportsList.add(isilonExport);

                arrayReadOnlyHost.addAll(isilonExport.getReadOnlyClients());
                arrayReadWriteHost.addAll(isilonExport.getReadWriteClients());
                arrayRootHost.addAll(isilonExport.getRootClients());

            }

            // find out the change between array and CoprHD database.
            Set<String> arrayExtraReadOnlyHost = Sets.difference(arrayReadOnlyHost, dbReadOnlyHost);
            Set<String> arrayExtraReadWriteHost = Sets.difference(arrayReadWriteHost, dbReadWriteHost);
            Set<String> arrayExtraRootHost = Sets.difference(arrayRootHost, dbRootHost);
            // if change found update the exportRuleMap
            if (!arrayExtraReadOnlyHost.isEmpty() || !arrayExtraReadWriteHost.isEmpty() || !arrayExtraRootHost.isEmpty()) {
                ExportRule extraRuleFromArray = new ExportRule();
                extraRuleFromArray.setDeviceExportId(exportRule.getDeviceExportId());
                extraRuleFromArray.setAnon(exportRule.getAnon());
                extraRuleFromArray.setSecFlavor(exportRule.getSecFlavor());
                extraRuleFromArray.setExportPath(exportRule.getExportPath());
                extraRuleFromArray.setReadOnlyHosts(arrayExtraReadOnlyHost);
                extraRuleFromArray.setReadWriteHosts(arrayExtraReadWriteHost);
                extraRuleFromArray.setRootHosts(arrayExtraRootHost);
                exportRuleMap.put(exportRule.getSecFlavor(), extraRuleFromArray);
            }

        }

        return exportRuleMap;

    }

    /**
     * Add isilon exports
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput object
     * @param exports
     *            new exports to add
     * @throws IsilonException
     */
    private void processAddIsiExport(IsilonApi isi, FileDeviceInputOutput args, List<ExportRule> exports)
            throws IsilonException {

        _log.info("ProcessAddExport  Start");

        List<ExportRule> modifyRules = new ArrayList<>();

        // process and export each NFSExport independently.
        for (ExportRule exportRule : exports) {

            // create and set IsilonExport instance from ExportRule

            _log.info("Add this export rule {}", exportRule.toString());

            String isilonExportId = exportRule.getDeviceExportId();
            String zoneName = getZoneName(args.getvNAS());
            if (isilonExportId != null) {
                // The Export Rule already exists on the array so modify it
                _log.info("Export rule exists on the device so modify it: {}", exportRule);
                modifyRules.add(exportRule);
            } else {
                // Create the Export
                _log.info("Export rule does not exist on the device so create it: {}", exportRule);
                IsilonExport newIsilonExport = setIsilonExport(exportRule);
                String expId = null;
                if (zoneName != null) {
                    expId = isi.createExport(newIsilonExport, zoneName, args.getBypassDnsCheck());
                } else {
                    expId = isi.createExport(newIsilonExport, args.getBypassDnsCheck());
                }
                exportRule.setDeviceExportId(expId);
            }

            if (!modifyRules.isEmpty()) {
                // Call Process Isi Export
                processIsiExport(isi, args, modifyRules);
            }
        }
        _log.info("ProcessAddExport completed.");
    }

    /**
     * Update isilon exports
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput object
     * @param exports
     *            new exports to add
     * @throws IsilonException
     */
    private void processIsiExport(IsilonApi isi, FileDeviceInputOutput args, List<ExportRule> exports)
            throws IsilonException {

        _log.info("ProcessIsiExport  Start");
        // process and export each NFSExport independently.
        for (ExportRule exportRule : exports) {

            // create and set IsilonExport instance from ExportRule

            String root_user = exportRule.getAnon();
            Set<String> rootHosts = exportRule.getRootHosts();

            String isilonExportId = exportRule.getDeviceExportId();

            if (isilonExportId != null) {
                IsilonExport isilonExport = null;
                String zoneName = getZoneName(args.getvNAS());
                if (zoneName != null) {
                    isilonExport = isi.getExport(isilonExportId, zoneName);
                } else {
                    isilonExport = isi.getExport(isilonExportId);
                }

                // Update the comment
                if (exportRule.getComments() != null && !exportRule.getComments().isEmpty()) {
                    isilonExport.setComment(exportRule.getComments());
                }

                _log.info("Update Isilon Export with id {} and {}", isilonExportId, isilonExport);
                if (isilonExport != null) {
                	boolean hasroClients = false;
                	
                	if ((isilonExport.getReadOnlyClients() != null && !isilonExport.getReadOnlyClients().isEmpty())
                            || (exportRule.getReadOnlyHosts() != null && !exportRule.getReadOnlyHosts().isEmpty())) {
                		hasroClients = true;
                	}
                	
                    List<String> roClients = new ArrayList<>();
                    // over write roClients
                    if (exportRule.getReadOnlyHosts() != null) {
                        roClients.addAll(exportRule.getReadOnlyHosts());

                        List<String> existingRWRootClients = new ArrayList<String>();
                        existingRWRootClients.addAll(isilonExport.getReadWriteClients());
                        existingRWRootClients.addAll(isilonExport.getRootClients());

                        List<String> commonHosts = getIntersection(existingRWRootClients, roClients);

                        if (!commonHosts.isEmpty()) {
                            // RW, RO and Root permissions cannot co-exist for
                            // same client hosts
                            // Using Set to eliminate duplicates
                            Set<String> existingRWClients = new HashSet<String>(isilonExport.getReadWriteClients());
                            Set<String> existingRootClients = new HashSet<String>(isilonExport.getRootClients());
                            // Remove common hosts
                            existingRWClients.removeAll(commonHosts);
                            existingRootClients.removeAll(commonHosts);
                            isilonExport.setRootClients(new ArrayList<String>(existingRootClients));
                            isilonExport.setReadWriteClients(new ArrayList<String>(existingRWClients));
                        } else {
                            setClientsIntoIsilonExport("root", exportRule.getRootHosts(), isilonExport);
                            setClientsIntoIsilonExport("rw", exportRule.getReadWriteHosts(), isilonExport);
                        }
                        isilonExport.setReadOnlyClients(new ArrayList<String>(roClients));
                    }

                    List<String> rwClients = new ArrayList<>();
                    // over write rwClients has emptypayload or it contains
                    // elements
                    if (exportRule.getReadWriteHosts() != null) {
                        rwClients.addAll(exportRule.getReadWriteHosts());

                        List<String> existingRORootClients = new ArrayList<String>();
                        existingRORootClients.addAll(isilonExport.getReadOnlyClients());
                        existingRORootClients.addAll(isilonExport.getRootClients());

                        List<String> commonHosts = getIntersection(existingRORootClients, rwClients);

                        if (!commonHosts.isEmpty()) {

                            // RW, RO and Root permissions cannot co-exist for
                            // same client hosts
                            // Using Set to eliminate duplicates
                            Set<String> existingROClients = new HashSet<String>(isilonExport.getReadOnlyClients());
                            Set<String> existingRootClients = new HashSet<String>(isilonExport.getRootClients());
                            // Remove common hosts
                            existingROClients.removeAll(commonHosts);
                            existingRootClients.removeAll(commonHosts);
                            isilonExport.setRootClients(new ArrayList<String>(existingRootClients));
                            isilonExport.setReadOnlyClients(new ArrayList<String>(existingROClients));
                        } else {
                            setClientsIntoIsilonExport("root", exportRule.getRootHosts(), isilonExport);
                            setClientsIntoIsilonExport("ro", exportRule.getReadOnlyHosts(), isilonExport);
                        }
                        isilonExport.setReadWriteClients(new ArrayList<String>(rwClients));
                    }

                    // over write rootClients
                    List<String> rootClients = new ArrayList<>();
                    if (rootHosts != null) {
                        rootClients.addAll(rootHosts);

                        List<String> existingRORWClients = new ArrayList<String>();
                        existingRORWClients.addAll(isilonExport.getReadOnlyClients());
                        existingRORWClients.addAll(isilonExport.getReadWriteClients());

                        List<String> commonHosts = getIntersection(existingRORWClients, rootClients);

                        if (!commonHosts.isEmpty()) {
                            // RW, RO and Root permissions cannot co-exist for
                            // same client hosts

                            Set<String> existingROClients = new HashSet<String>(isilonExport.getReadOnlyClients());
                            Set<String> existingRWClients = new HashSet<String>(isilonExport.getReadWriteClients());
                            existingROClients.removeAll(commonHosts);
                            existingRWClients.removeAll(commonHosts);
                            isilonExport.setReadWriteClients(new ArrayList<String>(existingRWClients));
                            isilonExport.setReadOnlyClients(new ArrayList<String>(existingROClients));
                        } else {
                            setClientsIntoIsilonExport("ro", exportRule.getReadOnlyHosts(), isilonExport);
                            setClientsIntoIsilonExport("rw", exportRule.getReadWriteHosts(), isilonExport);
                        }
                        isilonExport.setRootClients(new ArrayList<String>(rootClients));
                    }

                    isilonExport.resetReadOnly();

                    isilonExport.setMapAll(null);
                    isilonExport.setMapRoot(root_user);

                    // There is export in Isilon with the given id.
                    // Overwrite this export with a new set of clients.
                    // No longer using Clients list -- so set this to empty list
                    List<String> allClients = new ArrayList<>();
                    isilonExport.setClients(new ArrayList<String>(allClients));

                    IsilonExport clonedExport = cloneExport(isilonExport);

                    _log.info("Update Isilon Export with id {} and new info {}", isilonExportId,
                            clonedExport.toString());

                    if (zoneName != null) {
                        isi.modifyExport(isilonExportId, zoneName, clonedExport, args.getBypassDnsCheck());
                    } else {
                        isi.modifyExport(isilonExportId, clonedExport, args.getBypassDnsCheck());
                    }

                }
            }
        }
        _log.info("ProcessIsiExport  Completed");
    }

    /**
     * Delete isilon exports
     * 
     * @param isi
     *            IsilonApi object
     * @param args
     *            FileDeviceInputOutput object
     * @param exports
     *            new exports to add
     * @throws IsilonException
     */
    private void processRemoveIsiExport(IsilonApi isi, FileDeviceInputOutput args, List<ExportRule> exports)
            throws IsilonException {

        _log.info("processRemoveIsiExport  Start");

        // process and export each NFSExport independently.
        for (ExportRule exportRule : exports) {

            // create and set IsilonExport instance from ExportRule
            _log.info("Remove this export rule {}", exportRule.toString());
            String isilonExportId = exportRule.getDeviceExportId();

            if (isilonExportId != null) {
                // The Export Rule already exists on the array so modify it
                _log.info("Export rule exists on the device so remove it: {}", exportRule);
                String zoneName = getZoneName(args.getvNAS());
                if (zoneName != null) {
                    isi.deleteExport(isilonExportId, zoneName);
                } else {
                    isi.deleteExport(isilonExportId);
                }

            }
        }
        _log.info("processRemoveIsiExport  Completed");
    }

    private IsilonExport cloneExport(IsilonExport exp) {
        IsilonExport newExport = new IsilonExport();

        newExport.addPath(exp.getPaths().get(0));
        newExport.addRootClients(exp.getRootClients());
        newExport.addReadWriteClients(exp.getReadWriteClients());
        newExport.addReadOnlyClients(exp.getReadOnlyClients());

        if (exp.getReadOnly()) {
            newExport.setReadOnly();
        } else {
            newExport.resetReadOnly();
        }

        if (exp.getAllDirs()) {
            newExport.setAllDirs();
        } else {
            newExport.resetAllDirs();
        }
        newExport.addClients(exp.getClients());
        if (exp.getComment() != null) {
            newExport.setComment(exp.getComment());
        }
        newExport.setSecurityFlavors(exp.getSecurityFlavors());

        if (exp.getMap_all().getUser() != null && !exp.getMap_all().getUser().isEmpty()) {
            newExport.setMapAll(exp.getMap_all().getUser());
        }
        if (exp.getMap_root().getUser() != null && !exp.getMap_root().getUser().isEmpty()) {
            newExport.setMapRoot(exp.getMap_root().getUser());
        }

        return newExport;
    }

    private List<String> getIntersection(List<String> oldList, List<String> newList) {

        Set<String> a = new HashSet<String>(oldList);
        a.retainAll(newList);
        return new ArrayList<String>(a);
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage, FileDeviceInputOutput args) {
        // Requested Share ACL
        List<ShareACL> aclsToAdd = args.getShareAclsToAdd();
        List<ShareACL> aclsToDelete = args.getShareAclsToDelete();
        List<ShareACL> aclsToModify = args.getShareAclsToModify();
        Map<String, ShareACL> arrayExtraShareACL = null;
        try {
            boolean cifsSidEnable = customConfigHandler.getComputedCustomConfigBooleanValue(
                    CustomConfigConstants.ISILON_USER_TO_SID_MAPPING_FOR_CIFS_SHARE_ENABLED, storage.getSystemType(),
                    null);
            // add the new Share ACL from the array into the add request.
            if (cifsSidEnable) {
                arrayExtraShareACL = extraShareACLBySidFromArray(storage, args);
            } else {
                arrayExtraShareACL = extraShareACLFromArray(storage, args);
            }
            _log.info("Number of extra ACLs found on array  is: {}", arrayExtraShareACL.size());
            if (!arrayExtraShareACL.isEmpty()) {
                if (aclsToAdd != null) {
                    // now add the remaining Share ACL
                    aclsToAdd.addAll(arrayExtraShareACL.values());
                } else {
                    // if add acl is null then create a new Share ACL and add
                    aclsToAdd = new ArrayList<ShareACL>();
                    aclsToAdd.addAll(arrayExtraShareACL.values());
                    // update the args so new acl get persisted in CoprHD DB.
                    args.setShareAclsToAdd(aclsToAdd);
                }

            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            _log.error("Not able to fetch latest Share ACL from backend array.", e);

        }
        // Get existing Acls for the share
        List<ShareACL> aclsToProcess = args.getExistingShareAcls();

        _log.info("Share name : {}", args.getShareName());

        // Process Acls
        _log.info("Number of existing ACLs found {}", aclsToProcess.size());

        // Process ACLs to add
        aclsToProcess.addAll(aclsToAdd);

        // Process ACLs to modify
        for (ShareACL existingAcl : aclsToProcess) {
            String domainOfExistingAce = existingAcl.getDomain();
            if (domainOfExistingAce == null) {
                domainOfExistingAce = "";
            }
            for (ShareACL aclToModify : aclsToModify) {
                String domainOfmodifiedAce = aclToModify.getDomain();
                if (domainOfmodifiedAce == null) {
                    domainOfmodifiedAce = "";
                }

                if (aclToModify.getUser() != null && existingAcl.getUser() != null) {
                    if (domainOfExistingAce.concat(existingAcl.getUser())
                            .equalsIgnoreCase(domainOfmodifiedAce.concat(aclToModify.getUser()))) {

                        existingAcl.setPermission(aclToModify.getPermission());
                    }
                }

                if (aclToModify.getGroup() != null && existingAcl.getGroup() != null) {
                    if (domainOfExistingAce.concat(existingAcl.getGroup())
                            .equalsIgnoreCase(domainOfmodifiedAce.concat(aclToModify.getGroup()))) {
                        existingAcl.setPermission(aclToModify.getPermission());
                    }
                }
            }
        }

        // Process ACLs to delete
        for (ShareACL aclToDelete : aclsToDelete) {

            String domainOfDeleteAce = aclToDelete.getDomain();
            if (domainOfDeleteAce == null) {
                domainOfDeleteAce = "";
            }

            for (Iterator<ShareACL> iterator = aclsToProcess.iterator(); iterator.hasNext();) {
                ShareACL existingAcl = iterator.next();

                String domainOfExistingAce = existingAcl.getDomain();
                if (domainOfExistingAce == null) {
                    domainOfExistingAce = "";
                }

                if (aclToDelete.getUser() != null && existingAcl.getUser() != null) {
                    if (domainOfDeleteAce.concat(aclToDelete.getUser())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getUser()))) {
                        iterator.remove();
                    }
                }

                if (aclToDelete.getGroup() != null && existingAcl.getGroup() != null) {
                    if (domainOfDeleteAce.concat(aclToDelete.getGroup())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getGroup()))) {
                        iterator.remove();
                    }
                }
            }
        }

        // Process new ACLs
        IsilonApi isi = getIsilonDevice(storage);
        processAclsForShare(isi, args, aclsToProcess);

        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;

    }

    /**
     * Get the Share ACL which are present in array but not in CoprHD Database.
     * 
     * @param storage
     * @param args
     * @return Map with domain+ group or username with ShareACL
     */
    private Map<String, ShareACL> extraShareACLFromArray(StorageSystem storage, FileDeviceInputOutput args) {

        // get all Share ACL from CoprHD data base
        List<ShareACL> existingDBShareACL = args.getExistingShareAcls();

        Map<String, ShareACL> arrayShareACLMap = new HashMap<>();

        // get the all the Share ACL from the storage system.
        IsilonApi isi = getIsilonDevice(storage);
        String zoneName = getZoneName(args.getvNAS());
        IsilonSMBShare share = null;
        if (zoneName != null) {
            share = isi.getShare(args.getShareName(), zoneName);
        } else {
            share = isi.getShare(args.getShareName());
        }
        if (share != null) {
            List<Permission> permissions = share.getPermissions();
            for (Permission perm : permissions) {
                if (perm.getPermissionType().equalsIgnoreCase(Permission.PERMISSION_TYPE_ALLOW)) {
                    ShareACL shareACL = new ShareACL();
                    shareACL.setPermission(perm.getPermission());
                    String userAndDomain = perm.getTrustee().getName();
                    String[] trustees = new String[2];
                    trustees = userAndDomain.split("\\\\");
                    String trusteesType = perm.getTrustee().getType();
                    if (trustees.length > 1) {
                        shareACL.setDomain(trustees[0]);
                        if (trusteesType.equals("group")) {
                            shareACL.setGroup(trustees[1]);
                        } else {
                            shareACL.setUser(trustees[1]);
                        }
                    } else {
                        if (trusteesType.equals("group")) {
                            shareACL.setGroup(trustees[0]);
                        } else {
                            shareACL.setUser(trustees[0]);
                        }
                    }
                    arrayShareACLMap.put(perm.getTrustee().getName(), shareACL);

                }
            }
            for (Iterator iterator = existingDBShareACL.iterator(); iterator.hasNext();) {
                ShareACL shareACL = (ShareACL) iterator.next();
                String key = "";
                String domain = "";
                String user = shareACL.getUser();
                String group = shareACL.getGroup();
                if (shareACL.getDomain() != null && !shareACL.getDomain().isEmpty()) {
                    domain = shareACL.getDomain() + "\\";
                }
                if (user != null && !user.isEmpty()) {
                    key = domain + user;
                } else if (group != null && !group.isEmpty()) {
                    key = domain + group;
                }
                if (arrayShareACLMap.containsKey(key)) {

                    arrayShareACLMap.remove(key);
                }
            }
        }
        return arrayShareACLMap;

    }

    /**
     * By using Sid get the CIFS Share ACL which are present in array but not in CoprHD Database .
     * 
     * @param storage
     * @param args
     * @return Map with user sid with ShareACL
     */
    private Map<String, ShareACL> extraShareACLBySidFromArray(StorageSystem storage, FileDeviceInputOutput args) {

        // get all Share ACL from CoprHD data base
        List<ShareACL> existingDBShareACL = args.getExistingShareAcls();
        NASServer nas = getNasServerForFileSystem(args, storage);
        Map<String, ShareACL> arrayShareACLMap = new HashMap<>();

        // get the all the Share ACL from the storage system.
        IsilonApi isi = getIsilonDevice(storage);
        String zoneName = getZoneName(args.getvNAS());
        IsilonSMBShare share = null;
        if (zoneName != null) {
            share = isi.getShare(args.getShareName(), zoneName);
        } else {
            share = isi.getShare(args.getShareName());
        }
        if (share != null) {
            List<Permission> permissions = share.getPermissions();
            for (Permission perm : permissions) {
                if (perm.getPermissionType().equalsIgnoreCase(Permission.PERMISSION_TYPE_ALLOW)) {
                    ShareACL shareACL = new ShareACL();
                    shareACL.setPermission(perm.getPermission());
                    String userAndDomain = perm.getTrustee().getName();
                    String[] trustees = new String[2];
                    trustees = userAndDomain.split("\\\\");
                    String trusteesType = perm.getTrustee().getType();
                    if (trustees.length > 1) {
                        shareACL.setDomain(trustees[0]);
                        if (trusteesType.equals("group")) {
                            shareACL.setGroup(trustees[1]);
                        } else {
                            shareACL.setUser(trustees[1]);
                        }
                    } else {
                        if (trusteesType.equals("group")) {
                            shareACL.setGroup(trustees[0]);
                        } else {
                            shareACL.setUser(trustees[0]);
                        }
                    }
                    arrayShareACLMap.put(perm.getTrustee().getId(), shareACL);

                }
            }

            for (Iterator<ShareACL> iterator = existingDBShareACL.iterator(); iterator.hasNext();) {
                ShareACL shareACL = iterator.next();
                String name = "";
                String domain = shareACL.getDomain();
                String user = shareACL.getUser();
                String group = shareACL.getGroup();
                String type = "user";
                if (user != null && !user.isEmpty()) {
                    name = user;
                } else if (group != null && !group.isEmpty()) {
                    name = group;
                    type = "group";
                }
                String sid = getIdForDomainUserOrGroup(isi, nas, domain, name, type, false);
                if (arrayShareACLMap.containsKey(sid)) {

                    arrayShareACLMap.remove(sid);
                }
            }
        }
        return arrayShareACLMap;

    }

    /**
     * get share details
     * 
     * @param isilonApi
     * @param shareId
     * @return
     */
    private IsilonSMBShare getIsilonSMBShare(IsilonApi isilonApi, String shareId, String zoneName) {
        _log.debug("call getIsilonSMBShare for {} ", shareId);
        IsilonSMBShare isilonSMBShare = null;
        try {
            if (isilonApi != null) {
                isilonSMBShare = isilonApi.getShare(shareId, zoneName);
                _log.debug("call getIsilonSMBShare {}", isilonSMBShare.toString());
            }
        } catch (Exception e) {
            _log.error("Exception while getting SMBShare for {}", shareId);
        }
        return isilonSMBShare;
    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storage, FileDeviceInputOutput args) {

        IsilonApi isi = getIsilonDevice(storage);
        processAclsForShare(isi, args, null);

        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }

    /**
     * Sets permissions on Isilon SMB share.
     * 
     * @param isi
     *            the isilon API handle
     * @param args
     *            in which the attribute <code>shareName</code> must be set
     * @param aclsToProcess
     *            the ACEs to set on Isilon SMB share. If this value is null,
     *            then no permissions (ACEs) will be set
     */
    private void processAclsForShare(IsilonApi isi, FileDeviceInputOutput args, List<ShareACL> aclsToProcess) {

        _log.info("Start processAclsForShare to set ACL for share {}: ACL: {}", args.getShareName(), aclsToProcess);

        IsilonSMBShare isilonSMBShare = new IsilonSMBShare(args.getShareName());
        ArrayList<Permission> permissions = new ArrayList<Permission>();
        String permissionValue = null;
        String permissionTypeValue = null;
        if (aclsToProcess != null) {
            for (ShareACL acl : aclsToProcess) {
                String domain = acl.getDomain();
                if (domain == null) {
                    domain = "";
                }
                domain = domain.toLowerCase();
                String userOrGroup = acl.getUser() == null ? acl.getGroup().toLowerCase() : acl.getUser().toLowerCase();
                if (domain.length() > 0) {
                    userOrGroup = domain + "\\" + userOrGroup;
                }
                permissionValue = acl.getPermission().toLowerCase();
                if (permissionValue.startsWith("full")) {
                    permissionValue = Permission.PERMISSION_FULL;
                }

                permissionTypeValue = Permission.PERMISSION_TYPE_ALLOW;
                Permission permission = isilonSMBShare.new Permission(permissionTypeValue, permissionValue,
                        userOrGroup);
                permissions.add(permission);
            }
        }
        /*
         * If permissions array list is empty, it means to remove all ACEs on
         * the share.
         */
        isilonSMBShare.setPermissions(permissions);
        _log.info("Calling Isilon API: modifyShare. Share {}, permissions {}", isilonSMBShare, permissions);
        String zoneName = getZoneName(args.getvNAS());
        if (zoneName != null) {
            isi.modifyShare(args.getShareName(), zoneName, isilonSMBShare);
        } else {
            isi.modifyShare(args.getShareName(), isilonSMBShare);
        }

        _log.info("End processAclsForShare");
    }

    /**
     * getIsilonAclFromNfsACE function will convert the nfsACE object to Isilon
     * ACL object.
     * 
     * @param nfsACE
     *            vipr ACE object.
     * @return
     */
    private Acl getIsilonAclFromNfsACE(NfsACE nfsACE) {

        IsilonNFSACL isilonAcl = new IsilonNFSACL();
        Acl acl = isilonAcl.new Acl();

        ArrayList<String> inheritFlags = new ArrayList<String>();

        // Set empty inherit flag for now TODO make it user configurable.
        acl.setInherit_flags(inheritFlags);
        acl.setAccessrights(getIsilonAccessList(nfsACE.getPermissionSet()));
        acl.setOp("add");
        acl.setAccesstype(nfsACE.getPermissionType());
        String user = nfsACE.getUser();
        String domain = nfsACE.getDomain();
        if (domain != null && !domain.isEmpty()) {
            user = domain + "\\" + user;
        }
        String sid = null;
        if (nfsACE.getSid() != null && !nfsACE.getSid().isEmpty()) {
            sid = nfsACE.getSid();
        }

        IsilonNFSACL.Persona trustee = isilonAcl.new Persona(nfsACE.getType(), sid, user);
        acl.setTrustee(trustee);

        return acl;
    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        try {
            // read nameToSid flag from controller config.
            Boolean sidEnable = customConfigHandler.getComputedCustomConfigBooleanValue(
                    CustomConfigConstants.ISILON_USER_TO_SID_MAPPING_FOR_NFS_ENABLED, storage.getSystemType(),
                    null);
            // get sid mapping based on Controller config and it belong to VirtualNAS.
            if (sidEnable && args.getvNAS() != null) {
                updateSidInfoForNfsACE(args, storage);
            }

            IsilonNFSACL isilonAcl = new IsilonNFSACL();
            ArrayList<Acl> aclCompleteList = new ArrayList<Acl>();
            List<NfsACE> aceToAdd = args.getNfsAclsToAdd();
            for (NfsACE nfsACE : aceToAdd) {
                Acl acl = getIsilonAclFromNfsACE(nfsACE);
                acl.setOp("add");
                aclCompleteList.add(acl);
            }

            List<NfsACE> aceToModify = args.getNfsAclsToModify();
            for (NfsACE nfsACE : aceToModify) {
                Acl acl = getIsilonAclFromNfsACE(nfsACE);
                acl.setOp("replace");
                aclCompleteList.add(acl);
            }

            List<NfsACE> aceToDelete = args.getNfsAclsToDelete();
            for (NfsACE nfsACE : aceToDelete) {
                Acl acl = getIsilonAclFromNfsACE(nfsACE);
                acl.setOp("delete");
                aclCompleteList.add(acl);
            }

            isilonAcl.setAction("update");
            isilonAcl.setAuthoritative("acl");
            isilonAcl.setAcl(aclCompleteList);
            String path = args.getFileSystemPath();
            if (args.getSubDirectory() != null && !args.getSubDirectory().isEmpty()) {
                path = path + "/" + args.getSubDirectory();

            }

            // Process new ACLs
            IsilonApi isi = getIsilonDevice(storage);
            _log.info("Calling Isilon API: modify NFS Acl for {}, acl  {}", args.getFileSystemPath(), isilonAcl);
            isi.modifyNFSACL(path, isilonAcl);
            _log.info("End updateNfsACLs");
            BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
            return result;
        } catch (IsilonException e) {
            _log.error("updateNfsACLs failed ", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("updateNfsACLs failed ", e);
            final ServiceCoded serviceCoded = DeviceControllerException.errors.jobFailedOpMsg(
                    OperationTypeEnum.UPDATE_FILE_SYSTEM_NFS_ACL.toString(), e.getMessage());
            return BiosCommandResult.createErrorResult(serviceCoded);
        }
    }

    private void updateSidInfoForNfsACE(FileDeviceInputOutput args, StorageSystem storage) {
        IsilonApi isi = getIsilonDevice(storage);
        List<NfsACE> list = new ArrayList<NfsACE>();
        list.addAll(args.getNfsAclsToAdd());
        list.addAll(args.getNfsAclsToModify());
        list.addAll(args.getNfsAclsToDelete());
        for (NfsACE nfsACE : list) {
            String id = getIdForDomainUserOrGroup(isi, args.getvNAS(), nfsACE.getDomain(), nfsACE.getUser(), nfsACE.getType(), true);
            if (!id.isEmpty()) {
                nfsACE.setSid(id);

            }
        }

    }

    /**
     * To get NasServer form for the file system
     * 
     * @param args
     * @param storage
     * @return NASServer if found or null value
     */
    private NASServer getNasServerForFileSystem(FileDeviceInputOutput args, StorageSystem storage) {
        NASServer nas = null;
        // Check VirtualNAS is associated with file system or not.
        if (args.getvNAS() != null) {
            nas = args.getvNAS();
        } else {
            // mean it file is created on system access zone.
            // We do not have direct reference of physical nas servers from StorageSystem or fsobj
            URIQueryResultList pNasURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDevicePhysicalNASConstraint(storage.getId()),
                    pNasURIs);
            if (pNasURIs.iterator().hasNext()) {
                // storage system should have only one PhysicalNAS instance.
                URI pNasURI = pNasURIs.iterator().next();
                nas = _dbClient.queryObject(PhysicalNAS.class, pNasURI);
            }
        }
        return nas;
    }

    private ArrayList<String> getIsilonAccessList(Set<String> permissions) {

        ArrayList<String> accessRights = new ArrayList<String>();
        for (String per : permissions) {

            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_READ)) {
                accessRights.add(IsilonNFSACL.AccessRights.dir_gen_read.toString());
            }

            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_WRITE)) {
                accessRights.add(IsilonNFSACL.AccessRights.dir_gen_write.toString());
            }

            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_EXECUTE)) {
                accessRights.add(IsilonNFSACL.AccessRights.dir_gen_execute.toString());
            }

            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_FULLCONTROL)) {
                accessRights.add(IsilonNFSACL.AccessRights.dir_gen_all.toString());
            }
        }
        return accessRights;
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {

        IsilonNFSACL isilonAcl = new IsilonNFSACL();
        ArrayList<Acl> aclCompleteList = new ArrayList<Acl>();

        List<NfsACE> aceToDelete = args.getNfsAclsToDelete();
        for (NfsACE nfsACE : aceToDelete) {
            Acl acl = getIsilonAclFromNfsACE(nfsACE);
            acl.setOp("delete");
            aclCompleteList.add(acl);
        }

        isilonAcl.setAction("update");
        isilonAcl.setAuthoritative("acl");
        isilonAcl.setAcl(aclCompleteList);
        String path = args.getFileSystemPath();
        if (args.getSubDirectory() != null && !args.getSubDirectory().isEmpty()) {
            path = path + "/" + args.getSubDirectory();

        }

        // Process new ACLs
        IsilonApi isi = getIsilonDevice(storage);
        _log.info("Calling Isilon API: to delete NFS Acl for  {}, acl  {}", args.getFileSystemPath(), isilonAcl);
        isi.modifyNFSACL(path, isilonAcl);
        _log.info("End deleteNfsACLs");
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }

    private String getZoneName(VirtualNAS vNAS) {
        String zoneName = null;
        if (vNAS != null) {
            zoneName = vNAS.getNasName();
        }
        return zoneName;

    }

    /**
     * Set the clients to isilon export based on type
     * 
     * @param type
     *            one of "rw", "root" or "ro"
     * @param hosts
     *            the clients to be set
     * @param isilonExport
     */
    private void setClientsIntoIsilonExport(String type, Set<String> hosts, IsilonExport isilonExport) {

        ArrayList<String> clients = new ArrayList<String>();
        if (hosts != null && !hosts.isEmpty()) {
            clients.addAll(hosts);
        }

        switch (type) {
            case "root":
                isilonExport.setRootClients(clients);
                break;
            case "rw":
                isilonExport.setReadWriteClients(clients);
                break;
            case "ro":
                isilonExport.setReadOnlyClients(clients);
                break;
        }

    }

    @Override
    public void doCreateMirrorLink(StorageSystem system, URI source, URI target, TaskCompleter completer) {
        // mirrorOperations.createMirrorFileShareLink(system, source, target, completer);
    }

    @Override
    public BiosCommandResult doStartMirrorLink(StorageSystem system, FileShare fs, TaskCompleter completer) {
        FileShare sourceFS = null;
        FileShare targetFS = null;
        StorageSystem sourceSystem = null;
        StorageSystem targetSystem = null;
        boolean isMirrorPolicy = false;
        if (fs.getPersonality().equals(PersonalityTypes.TARGET.name())) {
            sourceFS = _dbClient.queryObject(FileShare.class, fs.getParentFileShare());
            targetFS = fs;
            isMirrorPolicy = true;
        } else if (fs.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
            sourceFS = fs;
            List<String> targetfileUris = new ArrayList<String>();
            targetfileUris.addAll(fs.getMirrorfsTargets());
            if (!targetfileUris.isEmpty()) {
                targetFS = _dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                ServiceError serviceError = DeviceControllerErrors.isilon.unableToGetTargetFileSystem(sourceFS.getLabel());
                return BiosCommandResult.createErrorResult(serviceError);
            }
        }
        sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFS.getStorageDevice());
        targetSystem = _dbClient.queryObject(StorageSystem.class, targetFS.getStorageDevice());
        PolicyStorageResource policyStrRes = getEquivalentPolicyStorageResource(sourceFS, _dbClient);
        if (policyStrRes != null) {
            // get the policy details by policy native id
            IsilonSyncPolicy syncPolicy = policyNativeIdValidation(sourceSystem, policyStrRes);
            String policyName = syncPolicy.getName();
            // In case of fail back we need to append _mirror name since we are starting the target FS mirror policy
            if (isMirrorPolicy) {
                String mirrorPolicyName = syncPolicy.getName();
                mirrorPolicyName = mirrorPolicyName.concat(MIRROR_POLICY);
                // call start operation on mirror policy from target system to source system
                return doStartTargetMirrorPolicy(sourceSystem, policyName, targetSystem, mirrorPolicyName, completer);
            } else {
                // call action 'start' on source policy
                return mirrorOperations.doStartReplicationPolicy(system, policyName, completer);
            }
        }
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToCreateFileShare();
        return BiosCommandResult.createErrorResult(serviceError);
    }

    /**
     * validate policy name
     * 
     * @param system - storage system
     * @param policyStrRes -replication policy resource
     */
    private IsilonSyncPolicy policyNativeIdValidation(StorageSystem system, PolicyStorageResource policyStrRes) {
        IsilonSyncPolicy syncPolicy = mirrorOperations.getIsilonSyncPolicy(system,
                policyStrRes.getPolicyNativeId());
        // if policy name is null then name-> nativeid. then update policy details in DB with policy id generated in Isilon device.
        if (policyStrRes.getName() == null || policyStrRes.getName().isEmpty()) {
            _log.info("Replication policy name is null and Updating policy object with name {} and policy id {}", syncPolicy.getName(),
                    policyStrRes.getPolicyNativeId());
            // policy name that is generated from ViPR
            policyStrRes.setLabel(policyStrRes.getPolicyNativeId());
            // get new policy name
            policyStrRes.setName(syncPolicy.getName());
            // policy id generated by isilon device.
            policyStrRes.setPolicyNativeId(syncPolicy.getId());
            _dbClient.updateObject(policyStrRes);
        } else {
            if (!policyStrRes.getName().equals(syncPolicy.getName())) {
                _log.info("Updated the Replication old policy name {} to new policy name {}", policyStrRes.getName(), syncPolicy.getName());
                policyStrRes.setName(syncPolicy.getName());
                _dbClient.updateObject(policyStrRes);
            }
        }
        return syncPolicy;
    }

    /**
     * start the mirror policy on target system
     * 
     * @param sourceSystem - source system
     * @param syncPolicyName - source policy name
     * @param targetSystem - target system
     * @param syncMirrorPolicyName - target mirror policy name
     * @param completer
     * @return
     */
    private BiosCommandResult doStartTargetMirrorPolicy(StorageSystem sourceSystem, String policyName, StorageSystem targetSystem,
            String mirrorPolicyName, TaskCompleter completer) {

        // get source policy details of local target on target system (policy : source -> target)
        IsilonSyncTargetPolicy mirrorPolicy = mirrorOperations.getIsilonSyncTargetPolicy(sourceSystem, mirrorPolicyName);

        // get target mirror policy details of local target on source system (*_mirror : target -> source)
        IsilonSyncTargetPolicy policy = mirrorOperations.getIsilonSyncTargetPolicy(targetSystem, policyName);

        _log.info("doStartTaregetMirrorPolicy - target policy details : {}", mirrorPolicy.toString());
        // if already on target writes enable and perform start operation failback workflow
        if (JobState.finished.equals(policy.getLastJobState()) &&
                JobState.finished.equals(mirrorPolicy.getLastJobState()) &&
                FOFB_STATES.writes_enabled.equals(mirrorPolicy.getFoFbState())) {
            _log.info("Skipped the starting the mirror policy : {}", mirrorPolicyName);
            _log.info(String.format("Source policy details : - %s and Target mirror policy details :- %s", policy.toString(),
                    mirrorPolicy.toString()));
            return BiosCommandResult.createSuccessfulResult();
        } else if (JobState.failed.equals(mirrorPolicy.getLastJobState()) ||
                JobState.needs_attention.equals(mirrorPolicy.getLastJobState())) {
            return getSyncPolicyErrorReport(sourceSystem, mirrorPolicy);
        } else if (JobState.failed.equals(policy.getLastJobState()) ||
                JobState.needs_attention.equals(policy.getLastJobState())) {
            return getSyncPolicyErrorReport(targetSystem, policy);
        } else {
            // call to isilon api
            _log.info(String.format("Starting a mirror policy %s on target system %s ", policyName, targetSystem.getLabel()));
            return mirrorOperations.doStartReplicationPolicy(targetSystem, mirrorPolicyName, completer);
        }
    }

    @Override
    public BiosCommandResult doRefreshMirrorLink(StorageSystem system, FileShare source) {
        IsilonSyncPolicy syncpolicy = null;
        PolicyStorageResource policyStrRes = getEquivalentPolicyStorageResource(source, _dbClient);
        if (policyStrRes != null) {
            syncpolicy = policyNativeIdValidation(system, policyStrRes);
            String policyId = syncpolicy.getId();
            return mirrorOperations.doRefreshMirrorFileShareLink(system, source, policyId);
        }
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToCreateFileShare();
        return BiosCommandResult.createErrorResult(serviceError);
    }

    @Override
    public BiosCommandResult doPauseLink(StorageSystem system, FileShare source) {
        IsilonSyncPolicy syncpolicy = null;
        PolicyStorageResource policyStrRes = getEquivalentPolicyStorageResource(source, _dbClient);
        if (policyStrRes != null) {
            syncpolicy = policyNativeIdValidation(system, policyStrRes);
            String policyId = syncpolicy.getId();
            JobState policyState = syncpolicy.getLastJobState();

            if (policyState.equals(JobState.running) || policyState.equals(JobState.paused)) {
                mirrorOperations.doCancelReplicationPolicy(system, policyId);
            }
            return mirrorOperations.doStopReplicationPolicy(system, policyId);
        }
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToCreateFileShare();
        return BiosCommandResult.createErrorResult(serviceError);
    }

    String gerneratePolicyName(StorageSystem system, FileShare fileShare) {
        return fileShare.getLabel();
    }

    @Override
    public BiosCommandResult doResumeLink(StorageSystem system, FileShare source, TaskCompleter completer) {

        IsilonSyncPolicy syncPolicy = null;
        PolicyStorageResource policyStrRes = getEquivalentPolicyStorageResource(source, _dbClient);
        if (policyStrRes != null) {
            syncPolicy = policyNativeIdValidation(system, policyStrRes);
            String policyId = syncPolicy.getId();
            return mirrorOperations.doResumeReplicationPolicy(system, policyId);
        }
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToCreateFileShare();
        return BiosCommandResult.createErrorResult(serviceError);
    }

    /**
     * perform failover operation
     * systemTarget - failover to target system
     * fs - target filesystem
     * completer - task completer
     * 
     * return BiosCommandResult
     */
    @Override
    public BiosCommandResult doFailoverLink(StorageSystem systemTarget, FileShare fs, TaskCompleter completer) {
        _log.info("IsilonFileStorageDevice -  doFailoverLink started ");
        FileShare sourceFS = null;
        FileShare targetFS = null;
        StorageSystem sourceSystem = null;
        StorageSystem targetSystem = null;

        boolean failback = false;
        if (fs.getPersonality().equals(PersonalityTypes.TARGET.name())) {
            sourceFS = _dbClient.queryObject(FileShare.class, fs.getParentFileShare());
            targetFS = fs;
        } else if (fs.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
            sourceFS = fs;
            List<String> targetfileUris = new ArrayList<String>();
            targetfileUris.addAll(fs.getMirrorfsTargets());
            if (!targetfileUris.isEmpty()) {
                targetFS = _dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                ServiceError serviceError = DeviceControllerErrors.isilon.unableToFailoverFileSystem(
                        systemTarget.getIpAddress(), "Unable to get target filesystem for source filesystem " + fs.getName());
                return BiosCommandResult.createErrorResult(serviceError);
            }
            failback = true;
        }

        sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFS.getStorageDevice());
        targetSystem = _dbClient.queryObject(StorageSystem.class, targetFS.getStorageDevice());

        PolicyStorageResource policyStrRes = getEquivalentPolicyStorageResource(sourceFS, _dbClient);
        if (policyStrRes != null) {

            IsilonSyncPolicy syncPolicy = policyNativeIdValidation(sourceSystem, policyStrRes);
            String policyName = syncPolicy.getName();
            BiosCommandResult cmdResult = null;
            // In case of failback we do failover on the source file system, so we need to append _mirror
            if (failback) {
                String mirrorPolicyName = syncPolicy.getName();
                mirrorPolicyName = mirrorPolicyName.concat(MIRROR_POLICY);

                // prepared policy for failback
                cmdResult = prepareFailbackOp(sourceSystem, policyName);
                if (!cmdResult.isCommandSuccess()) {
                    return cmdResult;
                }
                // Call Isilon Api failback job
                return doFailoverMirrorPolicy(sourceSystem, policyName, targetSystem, mirrorPolicyName, completer);
            } else {
                // prepared policy for failover
                cmdResult = prepareFailoverOp(sourceSystem, policyName);
                if (!cmdResult.isCommandSuccess()) {
                    _log.info("Unable to stop replication policy on source");
                    _log.info("Proceeding with failover anyway");
                }
                // Call Isilon Api failover job
                return mirrorOperations.doFailover(targetSystem, syncPolicy.getId(), completer);
            }
        }
        ServiceError serviceError = DeviceControllerErrors.isilon
                .unableToFailoverFileSystem(
                        systemTarget.getIpAddress(), "Unable to get the policy details for filesystem :" + fs.getName());
        return BiosCommandResult.createErrorResult(serviceError);
    }

    /**
     * Failover on target mirror policy that enable write on source system.
     * allow writes on source filesystem and on target system trasfer the controller to source system
     * on write will be disable and next step resync-prep on target will sync source policy
     * 
     * @param sourceSystem - source system
     * @param policyName - source to target policy
     * @param targetSystem - target system
     * @param mirrorPolicyName - target to source mirror policy on failback operation
     * @param completer
     * @return
     */
    private BiosCommandResult doFailoverMirrorPolicy(StorageSystem sourceSystem, String policyName, StorageSystem targetSystem,
            String mirrorPolicyName, TaskCompleter completer) {
        IsilonSyncTargetPolicy policy = null;
        IsilonSyncTargetPolicy mirrorPolicy = null;

        // if policy enables on target storage then We should disable it before failback job
        BiosCommandResult result = mirrorOperations.doStopReplicationPolicy(targetSystem, mirrorPolicyName);

        if (!result.isCommandSuccess()) {
            return result;
        }
        // get source policy details of local target on target system (policy : source -> target)
        mirrorPolicy = mirrorOperations.getIsilonSyncTargetPolicy(sourceSystem, mirrorPolicyName);
        // get target mirror policy details of local target on source system (policy : target -> source)
        policy = mirrorOperations.getIsilonSyncTargetPolicy(targetSystem, policyName);

        // if mirror has already "write_enabled" the skip the step
        // always source policy should be in 'resync_policy_create' state
        if (JobState.finished.equals(policy.getLastJobState()) &&
                FOFB_STATES.resync_policy_created.equals(policy.getFoFbState()) &&
                JobState.finished.equals(mirrorPolicy.getLastJobState()) &&
                FOFB_STATES.writes_enabled.equals(mirrorPolicy.getFoFbState())) {
            _log.info("Skipped the failover action on mirror policy : {}", mirrorPolicyName);
            _log.info(String.format("Source policy details : - %s and Target policy details :- %s", policy.toString(),
                    mirrorPolicy.toString()));
            return BiosCommandResult.createSuccessfulResult();
            // if policy is in error state then call to get error reports from device.
        } else if (JobState.failed.equals(mirrorPolicy.getLastJobState()) ||
                JobState.needs_attention.equals(mirrorPolicy.getLastJobState())) {
            return getSyncPolicyErrorReport(sourceSystem, mirrorPolicy);
            // get source policy error reports
        } else if (JobState.failed.equals(policy.getLastJobState()) ||
                JobState.needs_attention.equals(policy.getLastJobState())) {
            return getSyncPolicyErrorReport(targetSystem, policy);
        } else {
            // call isilon api
            // failover action mirror policy that sync data from target to source.
            // on failover of target mirror policy, the target local policy will be "writes enabled"
            return mirrorOperations.doFailover(sourceSystem, mirrorPolicyName, completer);
        }
    }

    /**
     * prepare policy to failover.
     * 
     * @param sourceSystem - source storagesystem
     * @param policyName - failover policy
     * @return
     */
    private BiosCommandResult prepareFailoverOp(final StorageSystem sourceSystem, String policyName) {
        BiosCommandResult cmdResult = null;
        // check for device is up and able query the data.
        cmdResult = mirrorOperations.doTestReplicationPolicy(sourceSystem, policyName);
        if (cmdResult.isCommandSuccess()) {
            // if policy enables on failed storage then We should disable it before failover job
            cmdResult = mirrorOperations.doStopReplicationPolicy(sourceSystem, policyName);
        } else {
            _log.error("Unabled get the replcation policy details.", cmdResult.getMessage());
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToFailoverReplicationPolicy(
                    sourceSystem.getIpAddress(), policyName, cmdResult.getMessage());
            return BiosCommandResult.createErrorResult(serviceError);
        }
        return cmdResult;
    }

    /**
     * prepare policy to failback.
     * 
     * @param systemTarget - target storagesystem
     * @param policyName -failback mirror policy
     * @return
     */
    private BiosCommandResult prepareFailbackOp(final StorageSystem targetSystem, String policyName) {
        BiosCommandResult cmdResult = null;
        // check for target device up and then disable the policy
        cmdResult = mirrorOperations.doTestReplicationPolicy(targetSystem, policyName);
        if (cmdResult.isCommandSuccess()) {
            // if policy enables on target storage then We should disable it before failback job
            cmdResult = mirrorOperations.doStopReplicationPolicy(targetSystem, policyName);
        } else {
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToFailbackReplicationPolicy(
                    targetSystem.getIpAddress(), policyName, cmdResult.getMessage());
            return BiosCommandResult.createErrorResult(serviceError);
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doResyncLink(StorageSystem system, FileShare fs,
            TaskCompleter completer) {
        FileShare sourceFS = null;
        FileShare targetFS = null;
        StorageSystem targetSystem = null;
        StorageSystem sourceSystem = null;

        boolean isMirrorPolicy = false;
        if (fs.getPersonality().equals(PersonalityTypes.TARGET.name())) {
            sourceFS = _dbClient.queryObject(FileShare.class, fs.getParentFileShare());
            targetFS = fs;
            isMirrorPolicy = true;
        } else if (fs.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
            sourceFS = fs;
            if (null != fs.getMirrorfsTargets() && !fs.getMirrorfsTargets().isEmpty()) {
                List<String> targetfileUris = new ArrayList<String>();
                targetfileUris.addAll(fs.getMirrorfsTargets());
                targetFS = _dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                ServiceError serviceError = DeviceControllerErrors.isilon.unableToGetTargetFileSystem(
                        sourceFS.getLabel());
                return BiosCommandResult.createErrorResult(serviceError);
            }
        }
        targetSystem = _dbClient.queryObject(StorageSystem.class, targetFS.getStorageDevice());
        sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFS.getStorageDevice());

        PolicyStorageResource policyStrRes = getEquivalentPolicyStorageResource(sourceFS, _dbClient);
        if (policyStrRes != null) {
            IsilonSyncPolicy syncTargetPolicy = policyNativeIdValidation(sourceSystem, policyStrRes);
            // In case of failback step 4 we do resysc on the target file system, so we need to append _mirror
            if (isMirrorPolicy) {
                String mirrorPolicyName = syncTargetPolicy.getName();
                mirrorPolicyName = mirrorPolicyName.concat(MIRROR_POLICY);
                // 'resync-prep' on target mirror policy
                return doResyncPrepTargetPolicy(sourceSystem, syncTargetPolicy.getName(), targetSystem, mirrorPolicyName, completer);
            } else {
                // 'resync-prep' operation on source storagesystem
                return doResyncPrepSourcePolicy(sourceSystem, targetSystem, syncTargetPolicy.getId(), completer);
            }
        }
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToGetPolicy(system.getLabel(), "Unable to get policy details");
        return BiosCommandResult.createErrorResult(serviceError);
    }

    /**
     * this command issue on source system when source policy in failover state.
     * when resync-prep issue on source policy then it will create mirror policy.
     * this mirror policy will from source to target
     * 
     * 'resync-prep' call on source policy will create new "*_mirror" policy
     * 
     * @param sourceSystem - source system
     * @param targetSystem - target system
     * @param sourcePolicyName - source policy name from source to target
     * @param completer
     * @return
     */
    private BiosCommandResult doResyncPrepSourcePolicy(StorageSystem sourceSystem, StorageSystem targetSystem, String policyName,
            TaskCompleter completer) {
        _log.info("doResyncPrepSourcePolicy - resync-prep action on source policy {} ", policyName);
        BiosCommandResult cmdResult = null;
        IsilonSyncTargetPolicy policy = null;

        // test the source policy details
        cmdResult = mirrorOperations.doTestReplicationPolicy(sourceSystem, policyName);
        if (cmdResult.isCommandSuccess()) {
            // get source policy details on target storage system

            policy = mirrorOperations.getIsilonSyncTargetPolicy(targetSystem, policyName);
            if (cmdResult.isCommandSuccess() && null != policy) {
                // enable the replication policy
                cmdResult = mirrorOperations.doEnablePolicy(sourceSystem, policyName);
                if (!cmdResult.isCommandSuccess()) {
                    return cmdResult;
                }
                // resync-prep operation already done and mirror policy exist
                // get source policy details of local target on target system (policy : source -> target)
                if (JobState.finished.equals(policy.getLastJobState()) &&
                        FOFB_STATES.resync_policy_created.equals(policy.getFoFbState())) {
                    _log.info("Skipped the resyncprep action on policy : {}", policyName);
                    _log.info(String.format("Source policy details : - %s ", policy.toString()));
                    return BiosCommandResult.createSuccessfulResult();
                    // if policy is failed then we call to get the reports, return error
                } else if (JobState.failed.equals(policy.getLastJobState()) ||
                        JobState.needs_attention.equals(policy.getLastJobState())) {
                    return getSyncPolicyErrorReport(sourceSystem, policy);
                } else {
                    // call isilon api
                    return mirrorOperations.doResyncPrep(sourceSystem, policyName, completer);
                }
            } else {
                return cmdResult;
            }
        } else {
            return cmdResult;
        }
    }

    /**
     * 'resync-prep' call on mirror policy of target system that enable source policy.
     * the target will be writes_disable and source will allow the writes.
     * * this operation activate source policy and source policy allow the write operations.
     * 
     * 
     * @param targetSystem - target system
     * @param mirrorPolicy - mirror policy name from target to source.
     * @param sourceSystem - source system
     * @param sourcePolicyName - source policy name from source to target
     * @param completer
     * @return
     */
    private BiosCommandResult doResyncPrepTargetPolicy(StorageSystem sourceSystem, String policyName, StorageSystem targetSystem,
            String mirrorPolicyName, TaskCompleter completer) {
        _log.info("doResyncPrepTargetPolicy - resync-prep action on mirror policy ", mirrorPolicyName);
        IsilonSyncTargetPolicy policy = null;
        IsilonSyncTargetPolicy mirrorPolicy = null;

        // get mirror policy of local targets on source system (mirror_policy: target-> source )
        mirrorPolicy = mirrorOperations.getIsilonSyncTargetPolicy(sourceSystem, mirrorPolicyName);
        // get source policy details of local target on target system (policy : source -> target)
        policy = mirrorOperations.getIsilonSyncTargetPolicy(targetSystem, policyName);

        // if mirror has already in "resync-policy-created" and source policy is "write_disabled"
        if (JobState.finished.equals(mirrorPolicy.getLastJobState()) &&
                FOFB_STATES.resync_policy_created.equals(mirrorPolicy.getFoFbState()) &&
                JobState.finished.equals(policy.getLastJobState()) &&
                FOFB_STATES.writes_disabled.equals(policy.getFoFbState())) {
            _log.info("Skipped the resyncprep action on mirror policy : {}", mirrorPolicyName);
            _log.info(String.format("Source policy details : - %s and Target policy details :- %s", policy.toString(),
                    mirrorPolicy.toString()));
            return BiosCommandResult.createSuccessfulResult();
            // if policy is in error state then call to get error reports from device.
        } else if (JobState.failed.equals(mirrorPolicy.getLastJobState()) ||
                JobState.needs_attention.equals(mirrorPolicy.getLastJobState())) {
            return getSyncPolicyErrorReport(sourceSystem, mirrorPolicy);
            // get the source policy error report
        } else if (JobState.failed.equals(policy.getLastJobState()) ||
                JobState.needs_attention.equals(policy.getLastJobState())) {
            return getSyncPolicyErrorReport(targetSystem, policy);
        } else {
            // call isilon api
            return mirrorOperations.doResyncPrep(targetSystem, mirrorPolicyName, completer);
        }
    }

    /**
     * get the error reports from device
     * 
     * @param device - storage system
     * @param syncPolicy - synciq policy name
     * @return
     */
    private BiosCommandResult getSyncPolicyErrorReport(StorageSystem device, IsilonSyncTargetPolicy policy) {
        List<IsilonSyncPolicyReport> listMirrorPolicyReports = null;
        StringBuffer errorMsgBuff = new StringBuffer();

        errorMsgBuff.append(String.format("Policy details  - failback-failover state : [%s] and policy status: [%s] ",
                policy.getFoFbState().toString(), policy.getLastJobState()));

        // get policy reports from device.
        IsilonApi isi = getIsilonDevice(device);
        listMirrorPolicyReports = isi.getReplicationPolicyReports(policy.getName()).getList();

        String errorMsg = mirrorOperations.isiGetReportErrMsg(listMirrorPolicyReports);

        errorMsgBuff.append(String.format("Policy Error Report details: %s", errorMsg));

        ServiceError serviceError = DeviceControllerErrors.isilon.unableToResyncPrepPolicy(device.getIpAddress(), policy.getName(),
                errorMsgBuff.toString());
        _log.error(errorMsgBuff.toString());
        return BiosCommandResult.createErrorResult(serviceError);
    }

    /**
     * rollback the target filesystems
     */
    @Override
    public void doRollbackMirrorLink(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer, String opId) {
        BiosCommandResult biosCommandResult = null;
        // delete the target objects
        if (targets != null && !targets.isEmpty()) {
            for (URI target : targets) {
                FileShare fileShare = _dbClient.queryObject(FileShare.class, target);
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());
                URI uriParent = fileShare.getParentFileShare().getURI();
                if (sources.contains(uriParent) == true) {
                    // Do not delete the file target file system with force flag
                    biosCommandResult = rollbackCreatedFilesystem(storageSystem, target, opId, false);
                    if (biosCommandResult.getCommandSuccess()) {
                        fileShare.getOpStatus().updateTaskStatus(opId, biosCommandResult.toOperation());
                        fileShare.setInactive(true);
                        _dbClient.updateObject(fileShare);
                    }
                }
            }
        }
        completer.ready(_dbClient);
    }

    /**
     * 
     * Get Synciq policy from policyStorageResource
     * 
     * @param isi - isilonapi object
     * @param policyRes - synciq policy resource
     * @return IsilonSyncPolicy
     */
    private IsilonSyncPolicy getSyncPolicy(IsilonApi isi, PolicyStorageResource policyRes) {
        IsilonSyncPolicy syncpolicyAtPath = null;
        // if it existing policy and policy object not updated
        if (null == policyRes.getName() || policyRes.getName().isEmpty()) {
            ArrayList<IsilonSyncPolicy> isiSyncIQPolicies = isi.getReplicationPolicies().getList();
            _log.info("Checking the right syncIQ policy ...");
            for (IsilonSyncPolicy syncIqPolicy : isiSyncIQPolicies) {
                // check for policy path
                if (syncIqPolicy.getSourceRootPath() != null
                        && syncIqPolicy.getSourceRootPath().equalsIgnoreCase(policyRes.getResourcePath())
                        && syncIqPolicy.getName() != null && syncIqPolicy.getName().equalsIgnoreCase(policyRes.getPolicyNativeId())) {
                    syncpolicyAtPath = syncIqPolicy;
                    break;
                }
            }
            return syncpolicyAtPath;
        } else {
            return isi.getReplicationPolicy(policyRes.getPolicyNativeId());
        }
    }

    /**
     * rollback the filesystem
     * 
     * @param system
     * @param uri
     * @param opId
     * @param isForceDelete
     * @return
     */
    private BiosCommandResult rollbackCreatedFilesystem(StorageSystem system, URI uri, String opId, boolean isForceDelete) {
        FileDeviceInputOutput fileInputOutput = this.prepareFileDeviceInputOutput(isForceDelete, uri, opId);
        return this.doDeleteFS(system, fileInputOutput);
    }

    @Deprecated
    @Override
    public BiosCommandResult assignFilePolicy(StorageSystem storage, FileDeviceInputOutput args) {
        // for isilon we need to create a new policy for each individual file system

        SchedulePolicy fp = args.getFilePolicy();
        String snapshotScheduleName = fp.getPolicyName() + "_" + args.getFsName();
        String pattern = snapshotScheduleName + "_%Y-%m-%d_%H-%M";
        String Schedulevalue = getIsilonScheduleString(fp);
        Integer expireValue = getSnapshotExpireValue(fp);
        _log.info("File Policy  name : {}", snapshotScheduleName);
        IsilonApi isi = getIsilonDevice(storage);
        try {
            isi.createSnapshotSchedule(snapshotScheduleName, args.getFileSystemPath(), Schedulevalue, pattern, expireValue);

        } catch (IsilonException e) {
            _log.error("assign file policy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
        return BiosCommandResult.createSuccessfulResult();
    }

    @Deprecated
    @Override
    public BiosCommandResult unassignFilePolicy(StorageSystem storageObj, FileDeviceInputOutput args) {

        SchedulePolicy fp = args.getFilePolicy();
        String snapshotScheduleName = fp.getPolicyName() + "_" + args.getFsName();
        IsilonApi isi = getIsilonDevice(storageObj);
        try {
            isi.deleteSnapshotSchedule(snapshotScheduleName);
        } catch (IsilonException e) {
            _log.error("unassign file policy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
        return BiosCommandResult.createSuccessfulResult();

    }

    @Deprecated
    private String getIsilonScheduleString(SchedulePolicy schedule) {
        StringBuilder builder = new StringBuilder();

        ScheduleFrequency scheduleFreq = ScheduleFrequency.valueOf(schedule.getScheduleFrequency().toUpperCase());
        switch (scheduleFreq) {

            case DAYS:
                builder.append("every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" days at ");
                builder.append(schedule.getScheduleTime());
                break;
            case WEEKS:
                builder.append("every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" weeks on ");
                builder.append(schedule.getScheduleDayOfWeek());
                builder.append(" at ");
                builder.append(schedule.getScheduleTime());
                break;
            case MONTHS:
                builder.append("the ");
                builder.append(schedule.getScheduleDayOfMonth());
                builder.append(" every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" month at ");
                builder.append(schedule.getScheduleTime());
                break;
            default:
                _log.error("Not a valid schedule frequency: " + schedule.getScheduleFrequency().toLowerCase());
                return null;

        }
        return builder.toString();

    }

    @Deprecated
    private Integer getSnapshotExpireValue(SchedulePolicy schedulePolicy) {
        Long seconds = 0L;
        String snapshotExpire = schedulePolicy.getSnapshotExpireType();
        if (snapshotExpire != null && !snapshotExpire.isEmpty()) {
            Long expireValue = schedulePolicy.getSnapshotExpireTime();
            SnapshotExpireType expireType = SnapshotExpireType.valueOf(snapshotExpire.toUpperCase());
            switch (expireType) {
                case HOURS:
                    seconds = TimeUnit.HOURS.toSeconds(expireValue);
                    break;
                case DAYS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue);
                    break;
                case WEEKS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue * 7);
                    break;
                case MONTHS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue * 30);
                    break;
                case NEVER:
                    return null;
                default:
                    _log.error("Not a valid expire type: " + expireType);
                    return null;
            }
        }
        return seconds.intValue();
    }

    private Integer getSnapshotExpireValue(FileSnapshotPolicyExpireParam snapExpireParam) {
        Long seconds = 0L;
        String snapshotExpire = snapExpireParam.getExpireType();
        if (snapshotExpire != null && !snapshotExpire.isEmpty()) {
            int expireValue = snapExpireParam.getExpireValue();
            SnapshotExpireType expireType = SnapshotExpireType.valueOf(snapshotExpire.toUpperCase());
            switch (expireType) {
                case HOURS:
                    seconds = TimeUnit.HOURS.toSeconds(expireValue);
                    break;
                case DAYS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue);
                    break;
                case WEEKS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue * 7);
                    break;
                case MONTHS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue * 30);
                    break;
                case NEVER:
                    return null;
                default:
                    _log.error("Not a valid expire type: " + expireType);
                    return null;
            }
        }
        return seconds.intValue();
    }

    @Override
    public BiosCommandResult listSanpshotByPolicy(StorageSystem storageObj, FileDeviceInputOutput args) {
        FilePolicy sp = args.getFileProtectionPolicy();
        FileShare fs = args.getFs();
        String snapshotScheduleName = sp.getFilePolicyName() + "_" + args.getFsName();

        if (sp.getPolicyStorageResources() != null && !sp.getPolicyStorageResources().isEmpty()) {
            for (String uriResource : sp.getPolicyStorageResources()) {
                PolicyStorageResource policyRes = _dbClient.queryObject(PolicyStorageResource.class, URI.create(uriResource));
                if (policyRes != null && policyRes.getStorageSystem().equals(storageObj.getId())) {
                    snapshotScheduleName = policyRes.getPolicyNativeId();
                    break;
                }
            }
        }

        IsilonApi isi = getIsilonDevice(storageObj);
        String resumeToken = null;

        try {
            do {
                IsilonList<IsilonSnapshot> snapshots = isi.listSnapshotsCreatedByPolicy(resumeToken, snapshotScheduleName);
                if (snapshots != null) {

                    for (IsilonSnapshot islon_snap : snapshots.getList()) {
                        _log.info("file policy snapshot is  : " + islon_snap.getName());
                        Snapshot snap = new Snapshot();
                        snap.setLabel(islon_snap.getName());
                        snap.setMountPath(islon_snap.getPath());
                        snap.setName(islon_snap.getName());
                        snap.setId(URIUtil.createId(Snapshot.class));
                        snap.setOpStatus(new OpStatusMap());
                        snap.setProject(new NamedURI(fs.getProject().getURI(), islon_snap.getName()));
                        snap.setMountPath(getSnapshotPath(islon_snap.getPath(), islon_snap.getName()));
                        snap.setParent(new NamedURI(fs.getId(), islon_snap.getName()));
                        StringMap map = new StringMap();
                        Long createdTime = Long.parseLong(islon_snap.getCreated()) * SEC_IN_MILLI;
                        String expiresTime = "Never";
                        if (islon_snap.getExpires() != null && !islon_snap.getExpires().isEmpty()) {
                            Long expTime = Long.parseLong(islon_snap.getExpires()) * SEC_IN_MILLI;
                            expiresTime = expTime.toString();
                        }
                        map.put("created", createdTime.toString());
                        map.put("expires", expiresTime);
                        map.put("schedule", sp.getFilePolicyName());
                        snap.setExtensions(map);
                        _dbClient.updateObject(snap);

                    }
                    resumeToken = snapshots.getToken();
                }
            } while (resumeToken != null && !resumeToken.equalsIgnoreCase("null"));

        } catch (IsilonException e) {
            _log.error("listing snapshot by file policy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
        Task task = TaskUtils.findTaskForRequestId(_dbClient, fs.getId(), args.getOpId());
        // set task to completed and progress to 100 and store in DB, so waiting thread in apisvc can read it.
        task.ready();
        task.setProgress(100);
        _dbClient.updateObject(task);
        return BiosCommandResult.createSuccessfulResult();
    }

    @Override
    public BiosCommandResult updateStorageSystemFileProtectionPolicy(StorageSystem storage, FileDeviceInputOutput args) {
        FilePolicy existingPolicy = args.getFileProtectionPolicy();
        PolicyStorageResource policyRes = args.getPolicyStorageResource();
        FilePolicyUpdateParam policyUpdateParam = args.getFileProtectionPolicyUpdateParam();
        IsilonApi isi = getIsilonDevice(storage);

        BiosCommandResult result = null;

        try {
            if (existingPolicy.getFilePolicyType().equals(FilePolicy.FilePolicyType.file_replication.name())) {
                boolean isVersion8above = false;
                if (VersionChecker.verifyVersionDetails(ONEFS_V8, storage.getFirmwareVersion()) >= 0) {
                    isVersion8above = true;
                }
                return updateStorageSystemFileReplicationPolicy(isi, policyRes, existingPolicy, policyUpdateParam, isVersion8above);
            } else if (existingPolicy.getFilePolicyType().equals(FilePolicy.FilePolicyType.file_snapshot.name())) {
                return updateStorageSystemFileSnapshotPolicy(isi, policyRes, existingPolicy, policyUpdateParam);
            } else {
                String errorMsg = "Invalid policy type {} " + existingPolicy.getFilePolicyType();
                _log.error(errorMsg);
                final ServiceCoded serviceCoded = DeviceControllerException.errors.jobFailedOpMsg(
                        OperationTypeEnum.UPDATE_STORAGE_SYSTEM_POLICY_BY_POLICY_RESOURCE.toString(), errorMsg);
                result = BiosCommandResult.createErrorResult(serviceCoded);
                existingPolicy.getOpStatus().updateTaskStatus(args.getOpId(), result.toOperation());
                return result;
            }
        } catch (IsilonException e) {
            _log.error("Update storage system policy for file policy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    private BiosCommandResult updateStorageSystemFileReplicationPolicy(IsilonApi isi, PolicyStorageResource policyRes,
            FilePolicy viprPolicy, FilePolicyUpdateParam policyUpdateParam, boolean isVersion8above) {

        try {
            IsilonSyncPolicy syncpolicyAtPath = getSyncPolicy(isi, policyRes);
            if (syncpolicyAtPath != null) {
                _log.info("Found SyncIQ policy{} for path {} ", syncpolicyAtPath.getName(), syncpolicyAtPath.getSourceRootPath());
                boolean bModifyPolicy = false;
                // Temp policy to store modified values.
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setName(syncpolicyAtPath.getName());
                if (policyUpdateParam.getNumWorkerThreads() > 0
                        && syncpolicyAtPath.getWorkersPerNode() != policyUpdateParam.getNumWorkerThreads()) {
                    _log.debug("Changing NumWorkerThreads to {} ", policyUpdateParam.getNumWorkerThreads());
                    modifiedPolicy.setWorkersPerNode(policyUpdateParam.getNumWorkerThreads());
                    bModifyPolicy = true;
                }

                if (policyUpdateParam.getPolicyDescription() != null && !policyUpdateParam.getPolicyDescription().isEmpty()
                        && !policyUpdateParam.getPolicyDescription().equalsIgnoreCase(syncpolicyAtPath.getDescription())) {
                    modifiedPolicy.setDescription(policyUpdateParam.getPolicyDescription());
                    bModifyPolicy = true;
                }

                if (policyUpdateParam.getReplicationPolicyParams() != null) {
                    FileReplicationPolicyParam replParam = policyUpdateParam.getReplicationPolicyParams();

                    if (replParam.getReplicationCopyMode() != null && !replParam.getReplicationCopyMode().isEmpty()
                            && !FileReplicationCopyMode.ASYNC.name().equalsIgnoreCase(replParam.getReplicationCopyMode())) {
                        _log.warn("Replication copy mode {} is not supported by Isilon {} ", replParam.getReplicationCopyMode());
                    }

                    if (replParam.getPolicySchedule() != null) {
                        String strSchedule = getIsilonPolicySchedule(replParam.getPolicySchedule());
                        if (strSchedule != null && !strSchedule.isEmpty()
                                && !strSchedule.equalsIgnoreCase(syncpolicyAtPath.getSchedule())) {
                            modifiedPolicy.setSchedule(strSchedule);
                            bModifyPolicy = true;
                        }

                    }
                }

                /*
                 * Changes made for addressing new fields added in sync Policy in OneFSv 8.0 and above
                 */
                IsilonSyncPolicy8Above modifiedPolicycopy = new IsilonSyncPolicy8Above();
                IsilonSyncPolicy8Above syncpolicyAtPath8 = null;
                if (isVersion8above && policyUpdateParam.getPriority() != null) {
                    syncpolicyAtPath8 = isi.getReplicationPolicy8above(syncpolicyAtPath.getName());
                    modifiedPolicycopy = modifiedPolicycopy.copy(modifiedPolicy);
                    if (syncpolicyAtPath8 != null) {
                        if (FilePolicyPriority.valueOf(policyUpdateParam.getPriority()).ordinal() != syncpolicyAtPath8.getPriority()) {
                            modifiedPolicycopy.setPriority(FilePolicyPriority.valueOf(policyUpdateParam.getPriority()).ordinal());
                            bModifyPolicy = true;
                        }
                    }
                }

                if (bModifyPolicy) {
                    JobState policyState = syncpolicyAtPath.getLastJobState();
                    if (!policyState.equals(JobState.running) && !policyState.equals(JobState.paused)) {
                        if (isVersion8above) {
                            // Possible NPE..syncpolicyAtPath8 can be null here if policyUpdateParam.getPriority() is NULL..
                            // Better to use syncpolicyAtPath.getName()..
                            isi.modifyReplicationPolicy8above(syncpolicyAtPath.getName(), modifiedPolicycopy);
                            _log.info("Modify Replication Policy- {} finished successfully", syncpolicyAtPath.getName());
                        } else {
                            isi.modifyReplicationPolicy(syncpolicyAtPath.getName(), modifiedPolicy);
                            _log.info("Modify Replication Policy- {} finished successfully", syncpolicyAtPath.getName());
                        }
                        // set native id and policy existing and then update the DB
                        if (null == policyRes.getName()) {
                            policyRes.setLabel(policyRes.getPolicyNativeId()); // before fix,
                            policyRes.setName(syncpolicyAtPath.getName());
                            policyRes.setPolicyNativeId(syncpolicyAtPath.getId());
                            _dbClient.updateObject(policyRes);
                        } else {
                            policyRes.setName(syncpolicyAtPath.getName());
                            _dbClient.updateObject(policyRes);
                        }
                        return BiosCommandResult.createSuccessfulResult();
                    } else {
                        _log.error("Replication Policy - {} can't be MODIFIED because policy has an active job",
                                syncpolicyAtPath.getName());
                        ServiceError error = DeviceControllerErrors.isilon
                                .jobFailed("doModifyReplicationPolicy as : The policy has an active job and cannot be modified.");
                        return BiosCommandResult.createErrorResult(error);
                    }
                } else {
                    _log.info("No parameters changed to modify Replication Policy- {} finished successfully", syncpolicyAtPath.getName());
                    return BiosCommandResult.createSuccessfulResult();
                }
            } else {
                _log.error("No SyncIQ policy found at path {} , Hence can't be MODIFIED",
                        policyRes.getResourcePath());
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("doModifyReplicationPolicy as : No SyncIQ policy found at given path.");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
    }

    private BiosCommandResult updateStorageSystemFileSnapshotPolicy(IsilonApi isi, PolicyStorageResource policyRes,
            FilePolicy viprPolicy, FilePolicyUpdateParam policyUpdateParam) {
        try {
            ArrayList<IsilonSnapshotSchedule> isiSnapshotPolicies = isi.getSnapshotSchedules().getList();
            IsilonSnapshotSchedule snapPolicyAtPath = null;
            _log.info("Checking the right snapshotIQ policy ...");
            for (IsilonSnapshotSchedule snapPolicy : isiSnapshotPolicies) {
                // Check for policy path
                // Policy name was stored as nativeId in old builds
                // Policy Id is stored in current release(3.6.1.4)
                // Hence identify the storage system policy in either way
                if (snapPolicy.getPath() != null && snapPolicy.getPath().equalsIgnoreCase(policyRes.getResourcePath())
                        && policyRes.getPolicyNativeId() != null && (policyRes.getPolicyNativeId().equalsIgnoreCase(snapPolicy.getName())
                                || policyRes.getPolicyNativeId().equalsIgnoreCase(snapPolicy.getId().toString()))) {
                    snapPolicyAtPath = snapPolicy;
                    break;
                }
            }
            if (snapPolicyAtPath != null) {
                _log.info("Found SnapshotIQ policy{} for path {} ", snapPolicyAtPath.getName(), snapPolicyAtPath.getPath());
                boolean bModifyPolicy = false;
                // Temp policy to store modified values.
                IsilonSnapshotSchedule modifiedPolicy = new IsilonSnapshotSchedule();
                modifiedPolicy.setName(snapPolicyAtPath.getName());

                if (policyUpdateParam.getSnapshotPolicyPrams() != null) {
                    FileSnapshotPolicyParam snapParam = policyUpdateParam.getSnapshotPolicyPrams();

                    if (snapParam.getSnapshotExpireParams() != null) {
                        Integer expireTime = getSnapshotExpireValue(snapParam.getSnapshotExpireParams());
                        if (expireTime != null && snapPolicyAtPath.getDuration() != null) {
                            if (snapPolicyAtPath.getDuration().intValue() != expireTime.intValue()) {
                                modifiedPolicy.setDuration(expireTime);
                                bModifyPolicy = true;
                            }
                        } else if (expireTime != null && snapPolicyAtPath.getDuration() == null) {
                            modifiedPolicy.setDuration(expireTime);
                            bModifyPolicy = true;
                        } else if (snapPolicyAtPath.getDuration() != null) {
                            modifiedPolicy.setDuration(0);
                            bModifyPolicy = true;
                        }
                    }

                    if (snapParam.getPolicySchedule() != null) {
                        String strSchedule = getIsilonPolicySchedule(snapParam.getPolicySchedule());
                        if (strSchedule != null && !strSchedule.isEmpty()
                                && !strSchedule.equalsIgnoreCase(snapPolicyAtPath.getSchedule())) {
                            modifiedPolicy.setSchedule(strSchedule);
                            bModifyPolicy = true;
                        }
                    }

                }

                if (bModifyPolicy) {
                    isi.modifySnapshotSchedule(snapPolicyAtPath.getName(), modifiedPolicy);
                    // if the existing policy is modified save policy details in db
                    policyRes.setPolicyNativeId(snapPolicyAtPath.getId().toString());
                    policyRes.setName(snapPolicyAtPath.getName());
                    _dbClient.updateObject(policyRes);
                    _log.info("Modify Snapshot Policy- {} finished successfully", snapPolicyAtPath.getName());
                    return BiosCommandResult.createSuccessfulResult();
                } else {
                    _log.info("No parameters changed to modify Snapshot Policy- {} finished successfully", snapPolicyAtPath.getName());
                    return BiosCommandResult.createSuccessfulResult();
                }
            } else {
                _log.error("No SnapshotIQ policy found at path {} , Hence can't be MODIFIED",
                        policyRes.getResourcePath());
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("Modify Snapshot policy Failed as : No SnapshotIQ policy found at given path.");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
    }

    /**
     * Gets the file system custom path value from controller configuration
     * 
     * @param storage
     *            Isilon storage system
     * @param args
     *            FileDeviceInputOutput object
     * @return evaluated custom path
     */
    private String getCustomPath(StorageSystem storage, FileDeviceInputOutput args) {

        String path = "";

        IsilonApi isi = getIsilonDevice(storage);
        String clusterName = isi.getClusterConfig().getName();
        FileShare fs = args.getFs();
        // Source and taget path sould be same
        // source cluster name should be included in target path instead of target cluster name.
        if (fs != null && fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            FileShare sourceFS = _dbClient.queryObject(FileShare.class, fs.getParentFileShare());
            if (sourceFS != null && sourceFS.getStorageDevice() != null) {
                StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFS.getStorageDevice());
                if (sourceSystem != null) {
                    IsilonApi sourceCluster = getIsilonDevice(sourceSystem);
                    clusterName = sourceCluster.getClusterConfig().getName();
                    // Add source access zone name to cluster name
                    // if the replication happens from user defined access zone to system access zone!!
                    if (sourceFS.getVirtualNAS() != null) {
                        VirtualNAS sourcevNAS = _dbClient.queryObject(VirtualNAS.class, sourceFS.getVirtualNAS());
                        if (sourcevNAS != null) {
                            String vNASName = sourcevNAS.getNasName();
                            vNASName = getNameWithNoSpecialCharacters(vNASName, args);
                            clusterName = clusterName + vNASName;
                            _log.info("Source file system is on virtual NAS {}", vNASName);
                        }
                    }
                    _log.debug("Generating path for target and the source cluster is is  {}", clusterName);
                }
            }
        } else if (args.isTarget()) {
            if (args.getSourceSystem() != null) {
                IsilonApi sourceCluster = getIsilonDevice(args.getSourceSystem());
                clusterName = sourceCluster.getClusterConfig().getName();
            }
            // Add source access zone name to cluster name
            // if the replication happens from user defined access zone to system access zone!!
            if (args.getSourceVNAS() != null && args.getvNAS() == null) {
                VirtualNAS sourcevNAS = args.getSourceVNAS();
                String vNASName = sourcevNAS.getNasName();
                vNASName = getNameWithNoSpecialCharacters(vNASName, args);
                clusterName = clusterName + vNASName;
            }
            _log.debug("Generating path for target and the source cluster is is  {}", clusterName);
        }

        DataSource dataSource = dataSourceFactory.createIsilonFileSystemPathDataSource(args.getProject(), args.getVPool(),
                args.getTenantOrg(), storage);
        dataSource.addProperty(CustomConfigConstants.ISILON_CLUSTER_NAME, clusterName);
        String configPath = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.ISILON_PATH_CUSTOMIZATION, "isilon",
                dataSource);
        _log.debug("The isilon user defined custom path is  {}", configPath);
        if (configPath != null && !configPath.isEmpty()) {
            path = args.getPathWithoutSpecialCharacters(configPath);
        }
        return path;
    }

    /**
     * Get the File System default system access zone from
     * controller configuration.
     * 
     * @return access zone folder name
     */

    private String getSystemAccessZoneNamespace() {

        String namespace = "";
        DataSource dataSource = new DataSource();
        dataSource.addProperty(CustomConfigConstants.ISILON_NO_DIR, "");
        dataSource.addProperty(CustomConfigConstants.ISILON_DIR_NAME, "");
        namespace = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.ISILON_SYSTEM_ACCESS_ZONE_NAMESPACE, "isilon",
                dataSource);
        // framework does't allow empty variable to be set. To work around if = is added to variable via conf and then
        // remove it here
        namespace = namespace.replaceAll("=", "");
        return namespace;
    }
	
    /**
     * 
     * This method checks these entries for existing policy
     * return true, if the policy is already applied on the path, otherwise false.
     * 
     * Policy storage resource is storing all storage system paths
     * on which the policy template is applied.
     * 
     * @param storageObj - The storage system on which the policy is to be checked
     * @param args
     * @param policyPath
     * @return
     */
    private boolean checkPolicyAppliedOnPath(StorageSystem storageObj, FileDeviceInputOutput args, String policyPath) {
        //
        FilePolicy filePolicy = args.getFileProtectionPolicy();
        if (filePolicy != null && !filePolicy.getInactive()) {
            StringSet policyStrRes = filePolicy.getPolicyStorageResources();
            if (policyStrRes != null && !policyStrRes.isEmpty()) {
                for (String policyStrRe : policyStrRes) {
                    PolicyStorageResource strRes = _dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(policyStrRe));
                    if (strRes != null && strRes.getStorageSystem().toString().equals(storageObj.getId().toString())
                            && strRes.getResourcePath().equalsIgnoreCase(policyPath)) {
                        return true;
                    }
                }
            }
        }
        String msg = String.format("File Policy template %s was not applied on storage system %s path %s",
                filePolicy.getFilePolicyName(), storageObj.getLabel(), policyPath);
        _log.info(msg);
        return false;
    }

    @Override
    public BiosCommandResult doApplyFilePolicy(StorageSystem storageObj, FileDeviceInputOutput args) {

        FileShare fs = args.getFs();
        try {
            FilePolicy filePolicy = args.getFileProtectionPolicy();
            String policyPath = generatePathForPolicy(filePolicy, fs, args);
            // Verify the ViPR resource on which the policy is applying is present in
            // Isilon path definition.
            // Otherwise, this method throws corresponding exception.
            checkAppliedResourceNamePartOfFilePolicyPath(policyPath, filePolicy, args);

            // Verify policy is already applied on the storage system path
            // Otherwise applied the policy on corresponding Isilon device path.
            if (checkPolicyAppliedOnPath(storageObj, args, policyPath)) {
                String msg = String.format("File Policy template %s is already applied on storage system %s path %s",
                        filePolicy.getFilePolicyName(), storageObj.getLabel(), policyPath);
                _log.info(msg);
                return BiosCommandResult.createSuccessfulResult();
            } else {
                if (filePolicy.getFilePolicyType().equals(FilePolicy.FilePolicyType.file_replication.name())) {
                    doApplyFileReplicationPolicy(filePolicy, args, fs, storageObj);
                } else if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_snapshot.name())) {
                    doApplyFileSnapshotPolicy(filePolicy, args, fs, storageObj);
                }
                return BiosCommandResult.createSuccessfulResult();
            }
        } catch (IsilonException e) {
            _log.error("apply file policy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doUnassignFilePolicy(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {

        try {
            IsilonApi isi = getIsilonDevice(storage);
            FilePolicy filePolicy = args.getFileProtectionPolicy();
            PolicyStorageResource policyResource = args.getPolicyStorageResource();

            if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_replication.name())) {
                // Get the policy details by id
                IsilonSyncPolicy isiSyncPolicy = isi.getReplicationPolicy(policyResource.getPolicyNativeId());
                if (isiSyncPolicy != null) {
                    _log.info("deleting Isilon replication policy: {}", isiSyncPolicy.toString());
                    String policyName = isiSyncPolicy.getName();
                    JobState policyState = isiSyncPolicy.getLastJobState();

                    if (policyState.equals(JobState.running) || policyState.equals(JobState.paused)) {
                        _log.info("Canceling Replication Policy  -{} because policy is in - {} state ", policyName,
                                policyState);
                        // If the policy is running, Cancel the job before unassign policy!!
                        BiosCommandResult cmdResult = mirrorOperations.doCancelReplicationPolicy(isi, isiSyncPolicy.getName());
                        if (!cmdResult.isCommandSuccess()) {
                            return cmdResult;
                        } else {
                            // If the replication job still running through exception
                            isiSyncPolicy = isi.getReplicationPolicy(isiSyncPolicy.getName());
                            if (isiSyncPolicy.getLastJobState().equals(JobState.running)) {
                                ServiceError error = DeviceControllerErrors.isilon.jobFailed(
                                        "Unable Stop Replication policy and policy state  :" + isiSyncPolicy.getLastJobState().toString());
                                return BiosCommandResult.createErrorResult(error);
                            }
                        }
                    }
                    // delete replication policy using policy id
                    isi.deleteReplicationPolicy(isiSyncPolicy.getId());
                } else {
                    _log.info("replication policy: {} doesn't exists on storage system", filePolicy.toString());
                }
                return BiosCommandResult.createSuccessfulResult();

            } else if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_snapshot.name())) {

                IsilonSnapshotSchedule isiSchedulePolicy = getEquivalentIsilonSnapshotSchedule(isi,
                        policyResource.getResourcePath());

                if (isiSchedulePolicy != null) {
                    _log.info("deleting Isilon Snapshot schedule: {}", isiSchedulePolicy.toString());
                    isi.deleteSnapshotSchedule(policyResource.getPolicyNativeId());
                } else {
                    _log.info("snapshot schedule: {} doesn't exists on storage system", filePolicy.toString());
                }
                return BiosCommandResult.createSuccessfulResult();
            }
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("unassign file policy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    private String getNameWithNoSpecialCharacters(String str, FileDeviceInputOutput args) {
        // Custom configuration using the below two level regular expressions to generate name with no special symbols.
        // Using the same here.
        String regex = STR_WITH_NO_SPECIAL_SYMBOLS;
        String path = str.replaceAll(regex, "");
        if (path != null && !path.isEmpty()) {
            path = args.getPathWithoutSpecialCharacters(path);
        }
        return path;
    }

    private String generatePathForPolicy(FilePolicy filePolicy, FileShare fileShare, FileDeviceInputOutput args) {
        String policyPath = "";
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                String vpool = getNameWithNoSpecialCharacters(args.getVPool().getLabel(), args);
                if (fileShare.getNativeId().contains(vpool)) {
                    policyPath = fileShare.getNativeId().split(vpool)[0] + vpool;
                } else {
                    _log.info("File system path {} does not contain vpool name {}", fileShare.getNativeId(), vpool);
                }
                break;
            case project:
                String project = getNameWithNoSpecialCharacters(args.getProject().getLabel(), args);
                if (fileShare.getNativeId().contains(project)) {
                    policyPath = fileShare.getNativeId().split(project)[0] + project;
                } else {
                    _log.info("File system path {} does not contain project name {}", fileShare.getNativeId(), project);
                }
                break;
            case file_system:
                policyPath = fileShare.getNativeId();
                break;
            default:
                _log.error("Not a valid policy applied at level {} ", applyLevel);
        }
        return policyPath;
    }

    private static IsilonSnapshotSchedule getEquivalentIsilonSnapshotSchedule(IsilonApi isi, String path) {
        IsilonSnapshotSchedule isiSchedule = null;
        ArrayList<IsilonSnapshotSchedule> isiSnapshotPolicies = isi.getSnapshotSchedules().getList();
        if (isiSnapshotPolicies != null && !isiSnapshotPolicies.isEmpty()) {
            for (IsilonSnapshotSchedule isiSnapshotPolicy : isiSnapshotPolicies) {
                if (isiSnapshotPolicy.getPath().equals(path)) {
                    isiSchedule = isiSnapshotPolicy;
                    break;
                }
            }
        }
        return isiSchedule;
    }

    private static IsilonSyncPolicy getEquivalentIsilonSyncIQPolicy(IsilonApi isi, String path) {
        IsilonSyncPolicy isiSyncPolicy = null;
        ArrayList<IsilonSyncPolicy> isiSyncIQPolicies = isi.getReplicationPolicies().getList();
        if (isiSyncIQPolicies != null && !isiSyncIQPolicies.isEmpty()) {
            for (IsilonSyncPolicy isiSyncIQPolicy : isiSyncIQPolicies) {
                if (isiSyncIQPolicy.getSourceRootPath().equals(path)) {
                    isiSyncPolicy = isiSyncIQPolicy;
                }
            }
        }
        return isiSyncPolicy;
    }

    private static String getIsilonPolicySchedule(FilePolicy policy) {

        FilePolicyScheduleParams scheduleParam = new FilePolicyScheduleParams();
        scheduleParam.setScheduleDayOfMonth(policy.getScheduleDayOfMonth());
        scheduleParam.setScheduleDayOfWeek(policy.getScheduleDayOfWeek());
        scheduleParam.setScheduleFrequency(policy.getScheduleFrequency());
        scheduleParam.setScheduleRepeat(policy.getScheduleRepeat());
        scheduleParam.setScheduleTime(policy.getScheduleTime());
        return getIsilonPolicySchedule(scheduleParam);
    }

    private static String getIsilonPolicySchedule(FilePolicyScheduleParams schedule) {
        StringBuilder builder = new StringBuilder();

        ScheduleFrequency scheduleFreq = ScheduleFrequency.valueOf(schedule.getScheduleFrequency().toUpperCase());
        switch (scheduleFreq) {

            case MINUTES:
                builder.append("every 1 days every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" minutes between ");
                builder.append(schedule.getScheduleTime());
                builder.append(" and ");
                // If we add 23 hours 59 min to start time to get end time
                // result time come smaller in most of the case
                // Like for start time 3:00 AM it comes at 2:59 AM. and Isilon API does not accept it.
                // Fixing End time at 11:59 PM for now.(need to get it from user in future)
                builder.append("11:59 PM");
                break;

            case HOURS:
                builder.append("every 1 days every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" hours between ");
                builder.append(schedule.getScheduleTime());
                builder.append(" and ");
                builder.append("11:59 PM");
                break;

            case DAYS:
                builder.append("every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" days at ");
                builder.append(schedule.getScheduleTime());
                break;
            case WEEKS:
                builder.append("every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" weeks on ");
                builder.append(schedule.getScheduleDayOfWeek());
                builder.append(" at ");
                builder.append(schedule.getScheduleTime());
                break;
            case MONTHS:
                builder.append("the ");
                builder.append(schedule.getScheduleDayOfMonth());
                builder.append(" every ");
                builder.append(schedule.getScheduleRepeat());
                builder.append(" month at ");
                builder.append(schedule.getScheduleTime());
                break;
            default:
                _log.error("Not a valid schedule frequency: " + schedule.getScheduleFrequency().toLowerCase());
                return null;

        }
        return builder.toString();

    }

    private Integer getIsilonSnapshotExpireValue(FilePolicy policy) {
        Long seconds = 0L;
        String snapshotExpire = policy.getSnapshotExpireType();
        if (snapshotExpire != null && !snapshotExpire.isEmpty()) {
            Long expireValue = policy.getSnapshotExpireTime();
            SnapshotExpireType expireType = SnapshotExpireType.valueOf(snapshotExpire.toUpperCase());
            switch (expireType) {
                case HOURS:
                    seconds = TimeUnit.HOURS.toSeconds(expireValue);
                    break;
                case DAYS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue);
                    break;
                case WEEKS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue * 7);
                    break;
                case MONTHS:
                    seconds = TimeUnit.DAYS.toSeconds(expireValue * 30);
                    break;
                case NEVER:
                    return null;
                default:
                    _log.error("Not a valid expire type: " + expireType);
                    return null;
            }
        }
        return seconds.intValue();
    }

    private String generatePathForLocalTarget(FilePolicy filePolicy, FileShare fileShare, FileDeviceInputOutput args) {
        String policyPath = "";
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        String[] fsPathParts = new String[3];
        switch (applyLevel) {
            case vpool:
                String vpool = args.getVPoolNameWithNoSpecialCharacters();
                fsPathParts = fileShare.getNativeId().split(vpool);
                policyPath = fsPathParts[0] + vpool + "_localTarget" + fsPathParts[1];
                break;
            case project:
                String project = args.getProjectNameWithNoSpecialCharacters();
                fsPathParts = fileShare.getNativeId().split(project);
                policyPath = fsPathParts[0] + project + "_localTarget" + fsPathParts[1];
                break;
            case file_system:
                policyPath = fileShare.getNativeId() + "_localTarget";
                break;
            default:
                _log.error("Not a valid policy apply level: " + applyLevel);
        }
        return policyPath;
    }

    /**
     * 
     * @param dbClient
     * @param project
     * @param storageSystem
     * @return
     */
    public void updateLocalTargetFileSystemPath(StorageSystem system, FileDeviceInputOutput args) {
        VirtualPool vpool = args.getVPool();
        Project project = args.getProject();
        FileShare fs = args.getFs();

        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            List<FilePolicy> replicationPolicies = FileOrchestrationUtils.getReplicationPolices(_dbClient, vpool, project, null);
            if (replicationPolicies != null && !replicationPolicies.isEmpty()) {
                if (replicationPolicies.size() > 1) {
                    _log.warn("More than one replication policy found {}", replicationPolicies.size());
                } else {
                    FilePolicy replPolicy = replicationPolicies.get(0);
                    if (replPolicy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.LOCAL.name())) {
                        // For local replication, the path should be different
                        // add localTaget to file path at directory level where the policy is applied!!!
                        String mountPath = generatePathForLocalTarget(replPolicy, fs, args);
                        // replace extra forward slash with single one
                        mountPath = mountPath.replaceAll("/+", "/");
                        _log.info("Mount path to mount the Isilon File System {}", mountPath);
                        args.setFsMountPath(mountPath);
                        args.setFsNativeGuid(args.getFsMountPath());
                        args.setFsNativeId(args.getFsMountPath());
                        args.setFsPath(args.getFsMountPath());
                    }
                }
            } else if (fs.getLabel().contains("-localTarget")) {
                String mountPath = fs.getNativeId() + "_localTarget";
                // replace extra forward slash with single one
                mountPath = mountPath.replaceAll("/+", "/");
                _log.info("Mount path to mount the Isilon File System {}", mountPath);
                args.setFsMountPath(mountPath);
                args.setFsNativeGuid(args.getFsMountPath());
                args.setFsNativeId(args.getFsMountPath());
                args.setFsPath(args.getFsMountPath());
            }
        }
        return;
    }

    @Override
    public BiosCommandResult checkFilePolicyExistsOrCreate(StorageSystem storageObj, FileDeviceInputOutput args) {

        FilePolicy filePolicy = args.getFileProtectionPolicy();
        BiosCommandResult result = null;

        try {
            IsilonApi isi = getIsilonDevice(storageObj);
            String clusterName = isi.getClusterConfig().getName();

            String filePolicyBasePath = getFilePolicyPath(storageObj, filePolicy.getApplyAt(), args);
            checkAppliedResourceNamePartOfFilePolicyPath(filePolicyBasePath, filePolicy, args);

            String snapshotPolicyScheduleName = FileOrchestrationUtils
                    .generateNameForSnapshotIQPolicy(clusterName, filePolicy, null, args, filePolicyBasePath);

            IsilonSnapshotSchedule isilonSnapshotSchedule = getEquivalentIsilonSnapshotSchedule(isi, filePolicyBasePath);
            if (isilonSnapshotSchedule != null) {

                String filePolicySnapshotSchedule = getIsilonPolicySchedule(filePolicy);
                _log.info("Comparing snapshot schedule between CoprHD policy: {} and Isilon policy: {}.", filePolicySnapshotSchedule,
                        isilonSnapshotSchedule.getSchedule());
                if (isilonSnapshotSchedule.getSchedule().equalsIgnoreCase(filePolicySnapshotSchedule)) {
                    // Verify the policy was mapped to FileStorageResource
                    if (null == FileOrchestrationUtils.findPolicyStorageResourceByNativeId(_dbClient, storageObj,
                            filePolicy, args, filePolicyBasePath)) {
                        _log.info("Isilon policy found for {}, creating policy storage resouce to further management",
                                filePolicy.getFilePolicyName());
                        FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, storageObj, filePolicy,
                                args, filePolicyBasePath, isilonSnapshotSchedule.getName(),
                                isilonSnapshotSchedule.getId().toString(), null, null, null);
                    }
                    result = BiosCommandResult.createSuccessfulResult();
                } else {
                    _log.info("Snapshot schedule differs between Isilon policy and CoprHD file policy. So, create policy in Isilon...");
                    // Create snapshot policy.
                    createIsilonSnapshotPolicySchedule(storageObj, filePolicy, filePolicyBasePath,
                            snapshotPolicyScheduleName, args, filePolicyBasePath);
                    result = BiosCommandResult.createSuccessfulResult();
                }
            } else {
                // Create snapshot policy.
                createIsilonSnapshotPolicySchedule(storageObj, filePolicy, filePolicyBasePath,
                        snapshotPolicyScheduleName, args, filePolicyBasePath);
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (IsilonException e) {
            _log.error("Assigning file policy failed.", e);
            result = BiosCommandResult.createErrorResult(e);
        }
        return result;
    }

    private void createIsilonSnapshotPolicySchedule(StorageSystem storageObj, FilePolicy filePolicy,
            String path, String snapshotSchedulePolicyName, FileDeviceInputOutput args, String filePolicyBasePath) {

        String pattern = snapshotSchedulePolicyName + "_%Y-%m-%d_%H-%M";
        String scheduleValue = getIsilonPolicySchedule(filePolicy);
        Integer expireValue = getIsilonSnapshotExpireValue(filePolicy);

        _log.info("File Policy : {} creation started", filePolicy.toString());
        try {
            IsilonApi isi = getIsilonDevice(storageObj);
            isi.createDir(path, true);
            String scheduleId = isi.createSnapshotSchedule(snapshotSchedulePolicyName, path, scheduleValue, pattern, expireValue);
            _log.info("Isilon File Policy {} created successfully.", snapshotSchedulePolicyName);
            FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, storageObj, filePolicy, args, filePolicyBasePath,
                    snapshotSchedulePolicyName, scheduleId, null, null, null);
        } catch (IsilonException e) {
            throw e;
        }
    }

    @Override
    public BiosCommandResult checkFileReplicationPolicyExistsOrCreate(StorageSystem sourceStorageObj,
            StorageSystem targetStorageObj, FileDeviceInputOutput sourceSytemArgs, FileDeviceInputOutput targetSytemArgs) {

        FilePolicy filePolicy = sourceSytemArgs.getFileProtectionPolicy();

        // Source Path
        String sourcePath = getFilePolicyPath(sourceStorageObj, filePolicy.getApplyAt(), sourceSytemArgs);
        String targetPath = getFilePolicyPath(targetStorageObj, filePolicy.getApplyAt(), targetSytemArgs);

        if (FileReplicationType.LOCAL.name().equalsIgnoreCase(filePolicy.getFileReplicationType())) {
            targetPath = targetPath + "_localTarget";
        }
        // Policy Name

        BiosCommandResult result = null;

        try {
            IsilonApi sourceIsi = getIsilonDevice(sourceStorageObj);
            IsilonApi targetIsi = getIsilonDevice(targetStorageObj);
            String sourceClusterName = sourceIsi.getClusterConfig().getName();
            String targetClusterName = targetIsi.getClusterConfig().getName();
            checkAppliedResourceNamePartOfFilePolicyPath(sourcePath, filePolicy, sourceSytemArgs);

            String policyName = FileOrchestrationUtils.generateNameForSyncIQPolicy(sourceClusterName, targetClusterName, filePolicy,
                    null, sourceSytemArgs, sourcePath);

            ArrayList<IsilonSyncPolicy> isiReplicationPolicies = sourceIsi.getReplicationPolicies().getList();
            IsilonSyncPolicy isilonReplicationSchedule = checkForReplicationPolicyOnIsilon(isiReplicationPolicies,
                    filePolicy, sourcePath, targetPath);

            if (isilonReplicationSchedule != null) {
                boolean validPolicy = validateIsilonReplicationPolicy(isilonReplicationSchedule, filePolicy, targetPath,
                        targetStorageObj, sourceStorageObj);
                if (validPolicy) {
                    // Verify the policy was mapped to FileStorageResource
                    if (null == FileOrchestrationUtils.findPolicyStorageResourceByNativeId(_dbClient, sourceStorageObj,
                            filePolicy, sourceSytemArgs, sourcePath)) {
                        _log.info("Isilon policy found for {}, creating policy storage resouce to further management",
                                filePolicy.getFilePolicyName());
                        FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, sourceStorageObj, filePolicy,
                                sourceSytemArgs, sourcePath, isilonReplicationSchedule.getName(),
                                isilonReplicationSchedule.getId(), targetStorageObj, targetSytemArgs.getvNAS(), targetPath);
                    }
                    result = BiosCommandResult.createSuccessfulResult();
                } else {
                    throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                            filePolicy.getApplyAt(),
                            "File policy and Isilon syncIQ policy differs for path: "
                                    + sourcePath);
                }
            } else {
                // Before creating SyncIQ policy between source and target paths
                // verify that is there any data present on the target
                // to avoid DL
                if (targetIsi.existsDir(targetPath) && targetIsi.fsDirHasData(targetPath)) {
                    // Fail to assign policy to target which has data in it!!!
                    String errMsg = String.format("Target %s:%s directory has content in it", targetClusterName, targetPath);
                    _log.error("Unable create policy due to, {}", errMsg);
                    throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                            filePolicy.getApplyAt(), errMsg);
                }

                // Create replication sync policy.
                createIsilonSyncPolicy(sourceStorageObj, targetStorageObj, filePolicy, sourcePath, targetPath,
                        policyName, sourceSytemArgs, targetSytemArgs);

                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (IsilonException e) {
            _log.error("Assigning file policy failed.", e);
            result = BiosCommandResult.createErrorResult(e);
        }
        return result;
    }

    private String truncatePathUptoApplyAtLevel(String policyPath, String applyAt, FileDeviceInputOutput args) {

        if (StringUtils.isNotEmpty(applyAt)) {
            FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(applyAt);
            switch (applyLevel) {
                case vpool:
                    String vpool = getNameWithNoSpecialCharacters(args.getVPool().getLabel(), args);
                    if (policyPath.contains(vpool)) {
                        policyPath = policyPath.split(vpool)[0] + vpool;
                    }
                    break;
                case project:
                    String project = getNameWithNoSpecialCharacters(args.getProject().getLabel(), args);
                    if (policyPath.contains(project)) {
                        policyPath = policyPath.split(project)[0] + project;
                    }
                    break;
                case file_system:
                    // Truncate not required
                    break;
                default:
                    _log.error("Not a valid policy applied at level {} ", applyLevel);
            }
        }
        return policyPath;

    }

    /**
     * This method verify the target file system has some data or not
     * 
     * @param targetStorage
     * @param targetPath
     * @return True, if it has some data in it; false, otherwise
     */
    private boolean isTargetDirectoryEmpty(StorageSystem targetStorage, String targetPath) {
        IsilonApi isi = getIsilonDevice(targetStorage);
        // verify the target directory has some data in it or not!!
        if (!isi.fsDirHasData(targetPath)) {
            return true;
        }
        return false;

    }

    private String createIsilonSyncPolicy(StorageSystem storageObj, StorageSystem targetStorage,
            FilePolicy filePolicy, String sourcePath, String targetPath, String syncPolicyName,
            FileDeviceInputOutput sourceSystemArgs, FileDeviceInputOutput targetSystemArgs) {

        String scheduleValue = getIsilonPolicySchedule(filePolicy);

        _log.info("File replication policy : {} creation started", filePolicy.toString());

        try {
            VirtualNAS targetVNas = targetSystemArgs.getvNAS();
            URI targetVNasURI = null;
            if (targetVNas != null) {
                targetVNasURI = targetVNas.getId();
            }

            String targetHost = FileOrchestrationUtils.getTargetHostPortForReplication(_dbClient, targetStorage.getId(),
                    targetSystemArgs.getVarray().getId(), targetVNasURI);

            IsilonApi isi = getIsilonDevice(storageObj);
            isi.createDir(sourcePath, true);
            IsilonSyncPolicy replicationPolicy = new IsilonSyncPolicy(syncPolicyName, sourcePath, targetPath, targetHost,
                    Action.sync);
            if (scheduleValue != null && !scheduleValue.isEmpty()) {
                replicationPolicy.setSchedule(scheduleValue);
            }
            if (filePolicy.getFilePolicyDescription() != null) {
                replicationPolicy.setDescription(filePolicy.getFilePolicyDescription());
            }
            if (filePolicy.getNumWorkerThreads() != null && filePolicy.getNumWorkerThreads() > 0) {
                replicationPolicy.setWorkersPerNode(filePolicy.getNumWorkerThreads().intValue());
            }
            replicationPolicy.setEnabled(true);
            replicationPolicy.setSchedule(scheduleValue);
            String scheduleId;
            if (VersionChecker.verifyVersionDetails(ONEFS_V8, storageObj.getFirmwareVersion()) >= 0) {
                IsilonSyncPolicy8Above replicationPolicyCopy = new IsilonSyncPolicy8Above();
                replicationPolicyCopy = replicationPolicyCopy.copy(replicationPolicy);
                if (filePolicy.getPriority() != null) {
                    replicationPolicyCopy.setPriority(FilePolicyPriority.valueOf(filePolicy.getPriority()).ordinal());
                }
                scheduleId = isi.createReplicationPolicy8above(replicationPolicyCopy);
            } else {
                scheduleId = isi.createReplicationPolicy(replicationPolicy);
            }

            FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, storageObj,
                    filePolicy, sourceSystemArgs, sourcePath, syncPolicyName, scheduleId, targetStorage, targetSystemArgs.getvNAS(),
                    targetPath);

            return scheduleId;
        } catch (IsilonException e) {
            throw e;
        }
    }

    private boolean isValidTargetHostOnExistingPolicy(String existingPolicyTargetHost, StorageSystem system) {
        if (existingPolicyTargetHost != null && !existingPolicyTargetHost.isEmpty()) {
            // target cluster IP address is matching????
            if (existingPolicyTargetHost.equalsIgnoreCase(system.getIpAddress())) {
                return true;
            }
            IsilonApi isi = getIsilonDevice(system);
            String targetClusterName = isi.getClusterConfig().getName();
            // target cluster name is matching????
            if (existingPolicyTargetHost.equalsIgnoreCase(targetClusterName)) {
                return true;
            }
            // target cluster smart connect zone is matching???
            for (com.emc.storageos.db.client.model.StoragePort port : FileOrchestrationUtils
                    .getStorageSystemPorts(_dbClient, system)) {
                if (existingPolicyTargetHost.equalsIgnoreCase(port.getPortName())) {
                    return true;
                } else if (existingPolicyTargetHost.equalsIgnoreCase(port.getPortNetworkId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean validateIsilonReplicationPolicy(IsilonSyncPolicy isiMatchedPolicy, FilePolicy filePolicy,
            String targetPath, StorageSystem targetSystem, StorageSystem sourceSystem) {
        _log.info("Comparing filepolicy: {} with SyncIQ policy: {}", filePolicy.getFilePolicyName(), isiMatchedPolicy.getName());

        if (isiMatchedPolicy != null) {
            // replication type validation
            if (!isiMatchedPolicy.getAction().equals(IsilonSyncPolicy.Action.sync)) {
                _log.error("Isilon policy replication type is not valid: {}", isiMatchedPolicy.getAction().name());
                return false;
            }

            // Verify the remote/local cluster
            if (filePolicy.getFileReplicationType().equalsIgnoreCase(FilePolicy.FileReplicationType.REMOTE.name())) {
                if (!isValidTargetHostOnExistingPolicy(isiMatchedPolicy.getTargetHost(), targetSystem)) {
                    _log.error("Target host is not matching for REMOTE replication.");
                    return false;
                }
            } else {
                if (!isValidTargetHostOnExistingPolicy(isiMatchedPolicy.getTargetHost(), sourceSystem)) {
                    _log.error("Target host is not matching for LOCAL replication.");
                    return false;
                }
            }
            // schedule validation
            String viprSchedule = getIsilonPolicySchedule(filePolicy);
            String isiSchedule = StringUtils.substringBefore(isiMatchedPolicy.getSchedule(), " between");
            if (!viprSchedule.equalsIgnoreCase(isiSchedule)) {
                _log.error("File policy schedule: {} is different compared to isilon SyncIQ schedule: {}", viprSchedule, isiSchedule);
                return false;
            }

            // target path validation
            if (!isiMatchedPolicy.getTargetPath().equals(targetPath)) {
                _log.error("Target path: {} is different compared to SyncIQ policy path: {}", targetPath, isiMatchedPolicy);
                return false;
            }
        }
        return true;
    }

    private static IsilonSyncPolicy checkForReplicationPolicyOnIsilon(ArrayList<IsilonSyncPolicy> isiPolicies, FilePolicy filePolicy,
            String sourceRootPath, String targetPath) {
        IsilonSyncPolicy isiMatchedPolicy = null;

        // Get the replication policies applied at directory path @source cluster
        for (IsilonSyncPolicy isiPolicy : isiPolicies) {
            if (isiPolicy.getSourceRootPath().equals(sourceRootPath) && isiPolicy.getTargetPath().equals(targetPath)) {
                isiMatchedPolicy = isiPolicy;
                break;
            }
        }

        return isiMatchedPolicy;
    }

    private String getFilePolicyPath(StorageSystem storageObj, String applyAt, FileDeviceInputOutput args) {
        String customPath = getCustomPath(storageObj, args);
        String filePolicyBasePath = null;
        VirtualNAS vNAS = args.getvNAS();
        if (vNAS != null) {
            String vNASPath = vNAS.getBaseDirPath();
            if (vNASPath != null && !vNASPath.trim().isEmpty()) {
                filePolicyBasePath = vNASPath + FW_SLASH + customPath;
            } else {
                filePolicyBasePath = IFS_ROOT + FW_SLASH + getSystemAccessZoneNamespace() + FW_SLASH + customPath;
            }

        } else {
            filePolicyBasePath = IFS_ROOT + FW_SLASH + getSystemAccessZoneNamespace() + FW_SLASH + customPath;
        }

        filePolicyBasePath = filePolicyBasePath.replaceAll("/+", "/").replaceAll("/$", "");
        filePolicyBasePath = truncatePathUptoApplyAtLevel(filePolicyBasePath, applyAt, args);

        _log.info("Computed file policy path: {}", filePolicyBasePath);
        return filePolicyBasePath;
    }

    private void checkAppliedResourceNamePartOfFilePolicyPath(String filePolicyBasePath,
            FilePolicy filePolicy, FileDeviceInputOutput args) {

        FilePolicyApplyLevel appliedAt = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        String resourceName = null;
        switch (appliedAt) {
            case project:
                if (args.getProject() != null) {
                    resourceName = args.getProjectNameWithNoSpecialCharacters().replaceAll("_", "");
                    if (!filePolicyBasePath.contains(resourceName)) {
                        _log.error("File policy base path does not contain project: {}", resourceName);
                        throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                                filePolicy.getApplyAt(),
                                "File policy base path does not contain project: " + resourceName);
                    }
                } else {
                    throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                            filePolicy.getApplyAt(), "No project was provided in the input.");
                }
                break;
            case vpool:
                if (args.getVPool() != null) {
                    resourceName = args.getVPoolNameWithNoSpecialCharacters().replaceAll("_", "");
                    if (!filePolicyBasePath.contains(resourceName)) {
                        _log.error("File policy base path does not contain vpool: {}", resourceName);
                        throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                                filePolicy.getApplyAt(),
                                "File policy base path does not contain vpool: " + resourceName);
                    }
                } else {
                    throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                            filePolicy.getApplyAt(), "No vpool was provided in the input.");
                }
                break;
            case file_system:
                if (args.getFs() != null) {
                    resourceName = args.getFsLabel();
                    if (!filePolicyBasePath.contains(resourceName)) {
                        _log.error("File policy base path does not contain fileshare: {}", resourceName);
                        throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                                filePolicy.getApplyAt(),
                                "File policy base path does not contain fileshare: " + resourceName);
                    }
                } else {
                    throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                            filePolicy.getApplyAt(), "No fileshare was provided in the input.");
                }
                break;

            default:
                break;
        }
    }

    public static FilePolicy getReplicationPolicyAppliedOnFS(FileShare fs, DbClient dbClient) {
        StringSet existingFSPolicies = fs.getFilePolicies();
        List<URI> existingFSPolicyURIs = new ArrayList<>();
        for (String filePolicyURI : existingFSPolicies) {
            existingFSPolicyURIs.add(URI.create(filePolicyURI));
        }
        Iterator<FilePolicy> iterator = dbClient.queryIterativeObjects(FilePolicy.class, existingFSPolicyURIs, true);
        while (iterator.hasNext()) {
            FilePolicy fp = iterator.next();
            if (fp.getFilePolicyType().equals(FilePolicy.FilePolicyType.file_replication.name())) {
                _log.info("Found replication policy :{}  applied to the file system:  {}.",
                        fp.toString(), fs.getId());
                return fp;
            }
        }
        return null;
    }

    public static PolicyStorageResource getEquivalentPolicyStorageResource(FileShare fs, DbClient dbClient) {
        FilePolicy fp = getReplicationPolicyAppliedOnFS(fs, dbClient);
        if (fp != null) {
            StringSet policyStrResources = fp.getPolicyStorageResources();
            List<URI> policyStrURIs = new ArrayList<>();
            for (String policyStrResource : policyStrResources) {
                policyStrURIs.add(URI.create(policyStrResource));
            }
            Iterator<PolicyStorageResource> iterator = dbClient.queryIterativeObjects(PolicyStorageResource.class, policyStrURIs, true);
            while (iterator.hasNext()) {
                PolicyStorageResource policyRes = iterator.next();
                if (policyRes.getAppliedAt().equals(fs.getId()) && policyRes.getStorageSystem().equals(fs.getStorageDevice())) {
                    _log.info("Found replication policy:{} corresponding storage resource: {}  applied to the file system: {}.",
                            fp.getLabel(), policyRes.toString(), fs.getId());
                    return policyRes;
                }
            }
        }
        return null;
    }

    @Override
    public BiosCommandResult checkFilePolicyPathHasResourceLabel(StorageSystem system, FileDeviceInputOutput args) {

        _log.info("Inside checkFilePolicyPathHasResourceLabel()");

        try {
            FilePolicy filePolicy = args.getFileProtectionPolicy();
            String filePolicyBasePath = getFilePolicyPath(system, filePolicy.getApplyAt(), args);
            checkAppliedResourceNamePartOfFilePolicyPath(filePolicyBasePath, filePolicy, args);
            _log.info("checkFilePolicyPathHasResourceLabel successful.");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("checkFilePolicyPathHasResourceLabel failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }

    }

    /**
     * It search all the provider configured in NASServer and gives sid for the user/group and Domain
     * if checkUidRange is enable and uid value is between 1,000,000-2,000,000 return sid,.Otherwise uid or gid
     * 
     * @param isi Isilon Api to connect with Isilon
     * @param nas - NASServer object to get all the provider info.
     * @param domain -domain should be in FQDN format
     * @param user name of the user
     * @param type can be user or group
     * @param checkUidRangeEnable to enable the uid range check
     * @return sidOrUid if found or else empty String
     */
    private String getIdForDomainUserOrGroup(IsilonApi isi, NASServer nas, String domain, String user, String type,
            boolean checkUidRangeEnable) {

        // we can get all auth providers and zone name from NASServer
        String sidOrUid = "";
        boolean sidOrUidfound = false;
        try {
            String zone = nas.getNasName();
            List<String> authProviders = new ArrayList<String>();
            CifsServerMap cifsServersMap = nas.getCifsServersMap();

            authProviders = getAuthProviderListFromCifsServerMap(cifsServersMap);
            _log.info("Auth providers for NASServer {} are {} ", nas.getNasName(), authProviders);
            for (String provider : authProviders) {

                if ("user".equals(type)) {
                    // no need of resume token as we are expecting only one result.
                    List<IsilonUser> userDetails = isi.getUsersDetail(zone, provider, domain, user, "");
                    if (!CollectionUtils.isEmpty(userDetails)) {
                        IsilonIdentity sid = userDetails.get(0).getSid();
                        sidOrUid = sid.getId();
                        sidOrUidfound = true;
                        // Range check is only done for nfsacl, should be true for it.
                        if (checkUidRangeEnable) {
                            // For gid, check what range it�s in. If it�s 1,000,000-2,000,000,
                            // it�s generated by OneFS and you should use the SID. Otherwise you should use the unix gid.
                            IsilonIdentity uid = userDetails.get(0).getUid();
                            if (isUidInRange(uid)) {
                                _log.debug("using uid {} instead of sid {} ", uid.getId(), sidOrUid);
                                sidOrUid = uid.getId();
                            }
                        }
                        _log.info("For user name {} and domain {} sid/uid is {}", user, domain, sidOrUid);

                        break;
                    }
                } else {
                    List<IsilonGroup> groupDetails = isi.getGroupsDetail(zone, provider, domain, user, "");
                    // no need of resume token as we are expecting only one result.
                    if (!CollectionUtils.isEmpty(groupDetails)) {
                        IsilonIdentity id = groupDetails.get(0).getSid();
                        sidOrUid = id.getId();
                        sidOrUidfound = true;
                        if (checkUidRangeEnable) {
                            // For gid, check what range it�s in. If it�s 1,000,000-2,000,000,
                            // it�s generated by OneFS and you should use the SID. Otherwise you should use the unix gid.
                            IsilonIdentity gid = groupDetails.get(0).getGid();
                            if (isUidInRange(gid)) {
                                _log.debug("using gid {} instead of sid {} ", gid.getId(), sidOrUid);
                                sidOrUid = gid.getId();
                            }
                        }
                        _log.info("For group name {} and domain {} sid is {}", user, domain, sidOrUid);
                        break;
                    }

                }
            }
        } catch (IsilonException e) {
            _log.error("Error while finding sid/uid for name {} and domain {} ", user, domain, e);
        }
        if (sidOrUidfound) {
            _log.info("Sid/uid for user name {}, type {} and domain {} is {}", user, type, domain, sidOrUid);

        } else {
            _log.error("No sid/uid found for user name {}, type {} and domain {} ", user, type, domain);

        }

        return sidOrUid;

    }

    /**
     * If range is not 1,000,000-2,000,000 return true
     * 
     * @param uidIdentity
     * @return
     */
    private boolean isUidInRange(IsilonIdentity uidIdentity) {
        boolean inRange = false;
        try {
            String ids = uidIdentity.getId();
            _log.debug("uid for acl is {} ", ids);
            String[] uid = new String[2];
            uid = ids.split(COLON);
            if (uid.length > 1) {
                Integer idvalue = Integer.parseInt(uid[1]);
                if (idvalue < 1000000 || idvalue > 2000000) {
                    inRange = true;
                }
            }
        } catch (Exception e) {
            // if exception while getting range, then return false.
            _log.warn("Not able to dermine uid range {}", uidIdentity.getId());

        }

        return inRange;

    }

    /**
     * Get the details for all provider form cifsServersMap
     * 
     * @param cifsServersMap
     * @return
     */
    private List<String> getAuthProviderListFromCifsServerMap(CifsServerMap cifsServersMap) {
        /*
         * ads Manage Active Directory Service providers.
         * ldap Manage LDAP authentication providers.
         * nis Manage NIS authentication providers.
         * example for a auth prover is lsa-activedirectory-provider:PROVISIONING.BOURNE.LOCAL
         */
        List<String> authProvider = new ArrayList<>();
        NasCifsServer adProvider = cifsServersMap.get(LSA_AD_PROVIDER);
        if (adProvider != null) {
            String adProvderString = adProvider.getName() + COLON + adProvider.getDomain();
            authProvider.add(adProvderString);
        }
        NasCifsServer ldapProvider = cifsServersMap.get(LSA_LDAP_PROVIDER);
        if (ldapProvider != null) {
            String ldapProvderString = ldapProvider.getName() + COLON + ldapProvider.getDomain();
            authProvider.add(ldapProvderString);
        }
        NasCifsServer nisProvider = cifsServersMap.get(LSA_NIS_PROVIDER);
        if (nisProvider != null) {
            String nisProvderString = nisProvider.getName() + COLON + nisProvider.getDomain();
            authProvider.add(nisProvderString);
        }
        NasCifsServer localProvider = cifsServersMap.get(LSA_LOCAL_PROVIDER);
        if (localProvider != null) {
            String localProviderString = localProvider.getName() + COLON + localProvider.getDomain();
            authProvider.add(localProviderString);
        }
        NasCifsServer fileProvider = cifsServersMap.get(LSA_FILE_PROVIDER);
        if (fileProvider != null) {
            String fileProviderString = fileProvider.getName() + COLON + fileProvider.getDomain();
            authProvider.add(fileProviderString);
        }
        return authProvider;
    }

    private BiosCommandResult doApplyFileReplicationPolicy(FilePolicy filePolicy, FileDeviceInputOutput args, FileShare fs,
            StorageSystem storageObj) {
        IsilonApi isi = getIsilonDevice(storageObj);
        FileShare targetFS = null;
        String sourcePath = generatePathForPolicy(filePolicy, fs, args);
        String scheduleValue = getIsilonPolicySchedule(filePolicy);
        String targetPath = null;
        String targetHost = null;
        StorageSystem targetSystem = null;
        NASServer targetNasServer = null;
        if (fs.getPersonality() != null && PersonalityTypes.SOURCE.name().equalsIgnoreCase(fs.getPersonality())) {
            String targetFs = fs.getMirrorfsTargets().iterator().next();
            targetFS = _dbClient.queryObject(FileShare.class, URI.create(targetFs));
            targetPath = generatePathForPolicy(filePolicy, targetFS, args);
            // _localTarget suffix is not needed for policy at file system level
            // as the suffix already present in target file system native id
            // Add the suffix only for local replication policy at higher level
            if (filePolicy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.LOCAL.name())
                    && !FilePolicyApplyLevel.file_system.name().equalsIgnoreCase(filePolicy.getApplyAt())) {
                targetPath = targetPath + "_localTarget";
            }
            // Get the target smart connect zone!!
            targetHost = FileOrchestrationUtils.getTargetHostPortForReplication(_dbClient, targetFS);
            targetSystem = _dbClient.queryObject(StorageSystem.class, targetFS.getStorageDevice());
            if (targetFS.getVirtualNAS() != null) {
                targetNasServer = _dbClient.queryObject(VirtualNAS.class, targetFS.getVirtualNAS());
            }
        }
        IsilonApi isiApiOfTarget = getIsilonDevice(targetSystem);
        String targetClusterName = isiApiOfTarget.getClusterConfig().getName();
        String sourceClustername = isi.getClusterConfig().getName();
        String policyName = FileOrchestrationUtils.generateNameForSyncIQPolicy(sourceClustername, targetClusterName,
                filePolicy, fs, args, sourcePath);

        IsilonSyncPolicy isiSynIQPolicy = getEquivalentIsilonSyncIQPolicy(isi, sourcePath);
        PolicyStorageResource policyStorageResource = null;
        if (isiSynIQPolicy != null) {
            boolean validPolicy = validateIsilonReplicationPolicy(isiSynIQPolicy, filePolicy, targetPath,
                    targetSystem, storageObj);
            if (validPolicy) {
                _log.info("File Policy {} is already applied and running.", filePolicy.toString());
                // Verify the policy was mapped to FileStorageResource
                if (null == FileOrchestrationUtils.findPolicyStorageResourceByNativeId(_dbClient, storageObj,
                        filePolicy, args, sourcePath)) {
                    _log.info("Isilon policy found for {}, creating policy storage resouce to further management",
                            filePolicy.getFilePolicyName());
                    // update the policy object in DB
                    policyStorageResource = FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, storageObj,
                            filePolicy,
                            args, sourcePath, isiSynIQPolicy.getName(), isiSynIQPolicy.getId(), targetSystem, targetNasServer, targetPath);
                    // for existing policy's on device
                    // label - label is generated from ViPR
                    policyStorageResource.setLabel(policyName);
                    _dbClient.updateObject(policyStorageResource);
                }
                return BiosCommandResult.createSuccessfulResult();
            } else {
                throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                        filePolicy.getApplyAt(), "File policy and Isilon syncIQ policy differs for path: "
                                + sourcePath);
            }
        } else {

            IsilonSyncPolicy policy = new IsilonSyncPolicy(policyName, sourcePath, targetPath, targetHost,
                    IsilonSyncPolicy.Action.sync);
            IsilonSyncPolicy8Above policycopy = new IsilonSyncPolicy8Above();
            if (scheduleValue != null && !scheduleValue.isEmpty()) {
                policy.setSchedule(scheduleValue);
            }
            if (filePolicy.getFilePolicyDescription() != null) {
                policy.setDescription(filePolicy.getFilePolicyDescription());
            }
            if (filePolicy.getNumWorkerThreads() != null && filePolicy.getNumWorkerThreads() > 0) {
                policy.setWorkersPerNode(filePolicy.getNumWorkerThreads().intValue());
            }
            policy.setEnabled(true);
            String policyId = null;
            if (VersionChecker.verifyVersionDetails(ONEFS_V8, storageObj.getFirmwareVersion()) >= 0) {
                if (filePolicy.getPriority() != null) {
                    policycopy = policycopy.copy(policy);
                    policycopy.setPriority(FilePolicyPriority.valueOf(filePolicy.getPriority()).ordinal());
                }
                policyId = isi.createReplicationPolicy8above(policycopy);
            } else {
                policyId = isi.createReplicationPolicy(policy);
            }

            if (policyId != null) {
                _log.info("Isilon File Policy {} created successfully with id {}", policyName, policyId);
                // update the policy object in DB
                FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, storageObj, filePolicy, args,
                        sourcePath, policyName, policyId,
                        targetSystem, targetNasServer, targetPath);
                return BiosCommandResult.createSuccessfulResult();
            }
        }
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * Method to check if given target directory has content
     * 
     * @param targetPath
     * @param targetSystem
     * @return
     */
    private boolean isTargetDirExists(String targetPath, StorageSystem targetSystem) {
        IsilonApi isi = getIsilonDevice(targetSystem);
        if (isi.existsDir(targetPath) && isi.fsDirHasData(targetPath)) {
            return true;
        }
        return false;
    }

    private BiosCommandResult doApplyFileSnapshotPolicy(FilePolicy filePolicy, FileDeviceInputOutput args, FileShare fs,
            StorageSystem storageObj) {
        IsilonApi isi = getIsilonDevice(storageObj);
        String path = generatePathForPolicy(filePolicy, fs, args);
        String clusterName = isi.getClusterConfig().getName();
        String snapshotScheduleName = FileOrchestrationUtils.generateNameForSnapshotIQPolicy(clusterName, filePolicy, fs, args, path);
        IsilonSnapshotSchedule isiSnapshotSchedule = getEquivalentIsilonSnapshotSchedule(isi, path);
        if (isiSnapshotSchedule != null) {
            String filePolicySnapshotSchedule = getIsilonPolicySchedule(filePolicy);
            _log.info("Comparing snapshot schedule between CoprHD policy: {} and Isilon policy: {}.", filePolicySnapshotSchedule,
                    isiSnapshotSchedule.getSchedule());
            if (isiSnapshotSchedule.getSchedule() != null && isiSnapshotSchedule.getSchedule().equalsIgnoreCase(filePolicySnapshotSchedule)) {
                // Verify the policy was mapped to FileStorageResource
                if (null == FileOrchestrationUtils.findPolicyStorageResourceByNativeId(_dbClient, storageObj,
                        filePolicy, args, path)) {
                    _log.info("Isilon snapshot policy found for {}, creating policy storage resouce to further management",
                            filePolicy.getFilePolicyName());
                    PolicyStorageResource policyResource = FileOrchestrationUtils.updatePolicyStorageResource(_dbClient, storageObj,
                            filePolicy,
                            args, path, isiSnapshotSchedule.getName(), isiSnapshotSchedule.getId().toString(), null, null, null);
                    // for existing policy vipr generated label
                    policyResource.setLabel(filePolicySnapshotSchedule);
                    _dbClient.updateObject(policyResource);
                    _log.info("File Policy {} is already applied and running.", filePolicy.getFilePolicyName());
                }
                return BiosCommandResult.createSuccessfulResult();
            } else {
                _log.info("Snapshot schedule differs between Isilon policy and CoprHD file policy. So, create policy in Isilon...");
                // Create snapshot policy.
                createIsilonSnapshotPolicySchedule(storageObj, filePolicy, path,
                        snapshotScheduleName, args, path);
                return BiosCommandResult.createSuccessfulResult();
            }
        } else {
            // Create snapshot policy.
            createIsilonSnapshotPolicySchedule(storageObj, filePolicy, path,
                    snapshotScheduleName, args, path);
            return BiosCommandResult.createSuccessfulResult();
        }
    }
    @Override
    public BiosCommandResult doCheckFSDependencies(StorageSystem storage, FileDeviceInputOutput args) {
        _log.info("Checking file system {} has dependencies in storage array: {}", args.getFsName(), storage.getLabel());
        boolean hasDependency = true;
        String vnasName = null;
        VirtualNAS vNas = args.getvNAS();
        if (vNas != null) {
            vnasName = vNas.getNasName();
        }
        try {
            String fsMountPath = args.getFsMountPath();
            hasDependency = doesNFSExportExistsForFSPath(storage, vnasName, fsMountPath);

            if (!hasDependency) {
                hasDependency = doesCIFSShareExistsForFSPath(storage, vnasName, fsMountPath);
            }

            if (!hasDependency) {
                hasDependency = doesSnapshotExistsForFSPath(storage, vnasName, fsMountPath);
            }

            if (hasDependency) {
                _log.error("File system has dependencies on array: {}", args.getFsName());
                DeviceControllerException e = DeviceControllerException.exceptions.fileSystemHasDependencies(fsMountPath);
                return BiosCommandResult.createErrorResult(e);
            }
            _log.info("File system has no dependencies on array: {}", args.getFsName());
            return BiosCommandResult.createSuccessfulResult();

        } catch (IsilonException e) {
            _log.error("Checking FS dependencies failed.", e);
            throw e;
        }
    }

    private boolean doesCIFSShareExistsForFSPath(final StorageSystem storageSystem,
            String isilonAccessZone, String path) {
        String resumeToken = null;
        URI storageSystemId = storageSystem.getId();
        _log.info("Checking CIFS share for path {}  on Isilon storage system: {} in access zone {} - start", path,
                storageSystem.getLabel(), isilonAccessZone);

        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            do {
                IsilonApi.IsilonList<IsilonSMBShare> isilonShares = isilonApi.listShares(resumeToken, isilonAccessZone);
                List<IsilonSMBShare> isilonSMBShareList = isilonShares.getList();
                for (IsilonSMBShare share : isilonSMBShareList) {
                    if (share.getPath().equals(path)) {
                        _log.info("Found CIFS share with path {} and name {} on Ision: {} in access zone: {}",
                                path, share.getName(), storageSystem.getLabel(), isilonAccessZone);
                        return true;
                    }
                }
                resumeToken = isilonShares.getToken();
            } while (resumeToken != null);
            _log.info("CIFS share not found with path {} on Ision: {} in access zone: {}",
                    path, storageSystem.getLabel(), isilonAccessZone);
            return false;
        } catch (IsilonException ie) {
            _log.error("doesCIFSShareExistForFSPath failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("doesCIFSShareExistForFSPath failed. Storage system: "
                    + storageSystemId);
            ice.initCause(ie);
            throw ice;
        } catch (Exception e) {
            _log.error("doesCIFSShareExistForFSPath failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("doesCIFSShareExistForFSPath failed. Storage system: "
                    + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private boolean doesNFSExportExistsForFSPath(StorageSystem storageSystem,
            String isilonAccessZone, String path) throws IsilonCollectionException {

        URI storageSystemId = storageSystem.getId();
        String resumeToken = null;
        try {
            _log.info("Checking NFS export for path {}  on Isilon storage system: {} in access zone {} - start",
                    path, storageSystem.getLabel(), isilonAccessZone);
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            do {
                IsilonApi.IsilonList<IsilonExport> isilonExports = isilonApi.listExports(resumeToken,
                        isilonAccessZone);
                List<IsilonExport> exports = isilonExports.getList();

                for (IsilonExport exp : exports) {
                    if (exp.getPaths() == null || exp.getPaths().isEmpty()) {
                        _log.info("Ignoring export {} as it is not having any path", exp);
                        continue;
                    }
                    // Ignore Export with multiple paths
                    if (exp.getPaths().size() > 1) {
                        _log.info("Isilon Export: {} has multiple paths. So ingnore it.", exp);
                        continue;
                    }
                    String exportPath = exp.getPaths().get(0);
                    if (exportPath.equals(path)) {
                        _log.info("Found NFS export with path {} on Ision: {} in access zone: {}",
                                path, storageSystem.getLabel(), isilonAccessZone);
                        return true;
                    }
                }
                resumeToken = isilonExports.getToken();
            } while (resumeToken != null);
            _log.info("NFS export not found with path {} on Ision: {} in access zone: {}",
                    path, storageSystem.getLabel(), isilonAccessZone);
            return false;
        } catch (IsilonException ie) {
            _log.error("doesNFSExportExistsForFSPath failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("doesNFSExportExistsForFSPath failed. Storage system: "
                    + storageSystemId);
            ice.initCause(ie);
            throw ice;
        } catch (Exception e) {
            _log.error("doesNFSExportExistsForFSPath failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("doesNFSExportExistsForFSPath failed. Storage system: "
                    + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private boolean doesSnapshotExistsForFSPath(StorageSystem storageSystem,
            String isilonAccessZone, String path) throws IsilonCollectionException {

        URI storageSystemId = storageSystem.getId();
        String resumeToken = null;
        try {
            _log.info("Checking snapshots for path {}  on Isilon storage system: {} in access zone {} - start",
                    path, storageSystem.getLabel(), isilonAccessZone);
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            do {
                IsilonList<IsilonSnapshot> isilonSnapshots = isilonApi.listSnapshots(resumeToken, path);
                List<IsilonSnapshot> snpashots = isilonSnapshots.getList();

                for (IsilonSnapshot snapshot : snpashots) {
                    if (snapshot.getPath() == null || snapshot.getPath().isEmpty()) {
                        _log.info("Ignoring snapshot {} as it is not having any path", snapshot);
                        continue;
                    }

                    if (snapshot.getPath().equals(path)) {
                        _log.info("Found snapshot on path {} in Ision: {}",
                                path, storageSystem.getLabel());
                        return true;
                    }
                }
                resumeToken = isilonSnapshots.getToken();
            } while (resumeToken != null);
            _log.info("Snapshots not found with path {} on Ision: {} in access zone: {}",
                    path, storageSystem.getLabel(), isilonAccessZone);
            return false;
        } catch (IsilonException ie) {
            _log.error("doesSnapshotExistsForFSPath failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("doesSnapshotExistsForFSPath failed. Storage system: "
                    + storageSystemId);
            ice.initCause(ie);
            throw ice;
        } catch (Exception e) {
            _log.error("doesSnapshotExistsForFSPath failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("doesSnapshotExistsForFSPath failed. Storage system: "
                    + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }
}
