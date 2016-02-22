/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.provisioning;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.Checkpoint;
import com.emc.nas.vnxfile.xmlapi.TreeQuota;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.vnx.xmlapi.VNXCifsServer;
import com.emc.storageos.vnx.xmlapi.VNXException;
import com.emc.storageos.vnx.xmlapi.VNXFileExport;
import com.emc.storageos.vnx.xmlapi.VNXFileSshApi;
import com.emc.storageos.vnx.xmlapi.VNXFileSystem;
import com.emc.storageos.vnx.xmlapi.VNXQuotaTree;
import com.emc.storageos.vnx.xmlapi.VNXSnapshot;
import com.emc.storageos.vnx.xmlapi.XMLApiResult;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileDiscExecutor;

/**
 * VNXFileCommApi is responsible for communicating with the VNX File Control Station to perform
 * provisioning.
 */
public class VNXFileCommApi {

    private static final Logger _log = LoggerFactory.getLogger(VNXFileCommApi.class);

    private static final String PROV_FSCR_FILE = "vnxfile-prov-file";
    private static final String PROV_FSDEL_FILE = "vnxfile-prov-del-file";
    private static final String PROV_FS_FORCE_DEL_FILE = "vnxfile-prov-force-del-file";

    private static final String PROV_CREATE_SNAP = "vnxfile-prov-cr-snap";
    private static final String PROV_FILE_EXPORT = "vnxfile-prov-file-export";
    private static final String PROV_FILE_EXPORT_MOUNT = "vnxfile-prov-file-export-mount";
    private static final String PROV_DELETE_SNAP = "vnxfile-prov-delete-snap";
    private static final String PROV_FILE_UNEXPORT = "vnxfile-prov-file-unexport";
    private static final String PROV_FILE_UNEXPORT_UNMOUNT = "vnxfile-prov-file-unexport-unmount";
    private static final String PROV_FILE_EXPAND = "vnxfile-prov-file-expand";
    private static final String PROV_FILE_DELSHARE = "vnxfile-prov-file-delshare";
    private static final String PROV_FILE_MOUNT_EXPAND = "vnxfile-prov-file-mount-expand";
    private static final String PROV_SNAP_RESTORE = "vnxfile-prov-snap-restore";
    private static final String PROV_FSIDQUERY_FILE = "vnxfile-prov-filesysid-query";
    private static final String PROV_FSIDQUERY_FILE_DELETE = "vnxfile-prov-filesysid-delete-query";
    private static final String PROV_CIFS_SERVERS = "vnxfile-prov-cifsserver-query";

    private static final String PROV_FILE_QUOTA_DIR_CREATE = "vnxfile-prov-quota-dir-create";
    private static final String PROV_FILE_QUOTA_DIR_MODIFY = "vnxfile-prov-quota-dir-modify";
    private static final String PROV_FILE_QUOTA_DIR_DELETE = "vnxfile-prov-quota-dir-delete";

    private static final String PROV_FILE_QUOTA_DIR_CREATE_MOUNT = "vnxfile-prov-mount-quota-dir-create";
    private static final String PROV_FILE_QUOTA_DIR_MODIFY_MOUNT = "vnxfile-prov-mount-quota-dir-modify";
    private static final String PROV_FILE_QUOTA_DIR_DELETE_MOUNT = "vnxfile-prov-mount-quota-dir-delete";

    public static final String WORM_ATTRIBUTE = "WORM";
    public static final String WORM_DEF = "off";
    public static final String AUTO_EXTEND_ENABLED_ATTRIBUTE = "AutoExtendEnabled";
    public static final String AUTO_EXTEND_ENABLED_DEF = "false";
    public static final String AUTO_EXTEND_HWM_ATTRIBUTE = "AutoExtendHWM";
    public static final String AUTO_EXTEND_MAX_SIZE_ATTRIBUTE = "AutoExtendMaxSize";
    public static final String AUTO_EXTEND_HWM_DEF = "90";
    public static final String FILE_SYSTEM_TYPE_ATTRIBUTE = "FileSystemType";
    public static final String FILE_SYSTEM_TYPE_DEF = "uxfs";
    public static final String THIN_PROVISIONED_ATTRIBUTE = "ThinProvisioned";
    public static final String THIN_PROVISIONED_DEF = "false";
    public static final String THIN_PROVISIONED_FS_SIZE_MB = "1024";

    private static final String SERVER_URI = "/servlets/CelerraManagementServices";

    private VNXFileDiscExecutor _provExecutor;
    private NamespaceList _provNamespaces;

    private DbClient _dbClient;

    private String _thinFsAllocPercentage;

    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public String getThinFsAllocPercentage() {
        return _thinFsAllocPercentage;
    }

    public void setThinFsAllocPercentage(String thinFsAllocPercentage) {
        _thinFsAllocPercentage = thinFsAllocPercentage;
    }

    private final VNXFileSshApi sshApi = new VNXFileSshApi();

    public VNXFileCommApi() {

    }

    public XMLApiResult createFileSystem(final StorageSystem system,
            final String fileSys,
            final String pool,
            final String dataMover,
            final Long size,
            final boolean virtualProvisioning,
            final String nativeFsId,
            final Map autoAtts) throws VNXException {

        _log.info("Create VNX File System: {} on data mover {}", fileSys, dataMover);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);

            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fileSys);
            reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, "/" + fileSys);
            reqAttributeMap.put(VNXFileConstants.POOL_NAME, pool);
            reqAttributeMap.put(VNXFileConstants.FS_INIT_SIZE, size);
            reqAttributeMap.put(VNXFileConstants.MOVER_ID, dataMover);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_VIRTUAL_PROVISIONING, virtualProvisioning);
            _provExecutor.setKeyMap(reqAttributeMap);

            String cmdResult = VNXFileConstants.CMD_SUCCESS;

            // CHeck for FSId provided
            // If provided query the array for the FSId in use
            // If in use error out
            // if not in use create the FS thin or thick
            Boolean foundFSwithId = false;
            if (null != nativeFsId && !nativeFsId.isEmpty()) {
                String fileSysId = nativeFsId;
                _log.info("Query file system query with id {}.", fileSysId);
                _provExecutor.getKeyMap().put(VNXFileConstants.FILESYSTEM_ID, fileSysId);
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FSIDQUERY_FILE));
                cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
                if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                    fileSysId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEM_ID);
                    foundFSwithId = (Boolean) _provExecutor.getKeyMap().get(VNXFileConstants.IS_FILESYSTEM_AVAILABLE_ON_ARRAY);
                }

                if (foundFSwithId) {
                    _log.info("There is a FileSystem exist with the id {} so fail the create.", fileSysId);
                    result.setCommandFailed();
                    result.setMessage("File System creation failed because a File System with exist with id " + nativeFsId);
                    return result;
                } else {
                    _log.info("There is no FileSystem  with the id {} so proceed with create.", fileSysId);
                }
            } else {
                _log.info("FileSystem Id provided is null or empty so we will use system generated id");
            }

            // calculate the thin fs allocation size
            String thinProvFsSizeMBs = THIN_PROVISIONED_FS_SIZE_MB;
            if (virtualProvisioning) {
                thinProvFsSizeMBs = getThinFSAllocSize(size, true).toString();
            }

            // FileSystem doesnt exist on the array, so now create it
            sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
            String createFSCmd = sshApi.formatCreateFS(fileSys, FILE_SYSTEM_TYPE_DEF, thinProvFsSizeMBs,
                    size.toString(), pool, "", virtualProvisioning, nativeFsId);
            _log.info("parsed createFSCmd {}.", createFSCmd);
            result = sshApi.executeSshRetry(VNXFileSshApi.NAS_FS, createFSCmd);
            if (!result.isCommandSuccess()) {
                cmdResult = VNXFileConstants.CMD_FAILURE;
            }

            if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                String fileSysId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEM_ID);

                int fsId = 0;
                if (null == fileSysId || fileSysId.isEmpty()) {
                    // Since there was no error but the file system id was not found, query for id again.
                    _log.info("Second file system create query.");
                    _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FSIDQUERY_FILE));

                    cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
                    if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                        fileSysId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEM_ID);

                        if (null != fileSysId && !fileSysId.isEmpty()) {
                            fsId = Integer.parseInt(fileSysId);
                        }
                    }

                } else {
                    fsId = Integer.parseInt(fileSysId);
                }

                if (0 < fsId) {
                    _log.info("VNX File System create success!  ID: {}", fsId);
                    String fsType = (String) autoAtts.get(FILE_SYSTEM_TYPE_ATTRIBUTE);
                    String worm = (String) autoAtts.get(WORM_ATTRIBUTE);
                    VNXFileSystem newFs = new VNXFileSystem(fileSys, -1, pool, fsType, worm, dataMover, Long.toString(size), autoAtts);

                    result.setCommandSuccess();
                    newFs.setFsId(fsId);
                    result.setObject(newFs);
                } else {
                    result.setCommandFailed();
                    result.setMessage("File System creation failed");
                }
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }

        } catch (Exception e) {
            throw VNXException.exceptions.createFileSystemFailed(e.getMessage());
        }

        return result;
    }

    public boolean checkFileSystemExists(StorageSystem system, String fileId, String fileSys) throws VNXException {

        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();
        boolean isFsAvailable = true;
        try {
            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fileSys);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_ID, fileId);
            _provExecutor.setKeyMap(reqAttributeMap);
            _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FSIDQUERY_FILE));
            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (null != cmdResult && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                isFsAvailable = (Boolean) _provExecutor.getKeyMap().get(VNXFileConstants.IS_FILESYSTEM_AVAILABLE_ON_ARRAY);
            }
        } catch (Exception e) {
            throw VNXException.exceptions.communicationFailed(e.getMessage());
        }
        return isFsAvailable;
    }

    public XMLApiResult createSnapshot(final StorageSystem system,
            final String fsName,
            final String snapshotName,
            final FileShare fileShare) throws VNXException {
        _log.info("Create Snap for file sys : {} snap name : {}", fsName, snapshotName);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {

            // get the data mover
            StorageHADomain dataMover = this.getDataMover(fileShare);
            if (null != dataMover) {
                sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                Map<String, String> existingMounts = sshApi.getFsMountpathMap(dataMover.getAdapterName());
                if (existingMounts.get(fileShare.getName()) == null) {
                    String mountCmdArgs = sshApi.formatMountCmd(dataMover.getAdapterName(), fileShare.getName(), fileShare.getMountPath());
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_MOUNT_CMD, mountCmdArgs);
                    _log.info("filesystem mount is successful for filesystem: {} mount path: {}", fileShare.getName(),
                            fileShare.getMountPath());
                }
            } else {
                Exception e = new Exception(
                        "VNX File snapshot creation failed because suitable Data mover to mount the File System not found");
                throw VNXException.exceptions.createExportFailed("VNX File Snapshot create is Failed", e);
            }

            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            reqAttributeMap.put(VNXFileConstants.SNAPSHOT_NAME, snapshotName);
            _provExecutor.setKeyMap(reqAttributeMap);

            _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_CREATE_SNAP));

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult != null && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                String snapId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.SNAPSHOT_ID);
                String fsysId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEM_ID);
                if (snapId != null) {
                    int fsId = Integer.parseInt(fsysId);
                    int snId = Integer.parseInt(snapId);

                    VNXSnapshot vnxSnap = new VNXSnapshot(snapshotName, -1, fsId);
                    vnxSnap.setId(snId);
                    result.setObject(vnxSnap);
                    result.setCommandSuccess();
                } else {
                    result.setCommandFailed();
                    result.setMessage((String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_MSG));
                }
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_MSG);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }

        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    public XMLApiResult createQuotaDirectory(final StorageSystem system,
            final String fsName, final String quotaDirName, final String securityStyle,
            final Long size, final Boolean oplocks, Boolean isMountRequired) throws VNXException {

        _log.info("Create VNX File System Quota dir: {} on file system {}", quotaDirName,
                fsName);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);

            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            reqAttributeMap.put(VNXFileConstants.QUOTA_DIR_NAME, quotaDirName);
            reqAttributeMap.put(VNXFileConstants.HARD_QUOTA, size);
            reqAttributeMap.put(VNXFileConstants.SECURITY_STYLE, securityStyle);
            reqAttributeMap.put(VNXFileConstants.OPLOCKS, oplocks);
            reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, "/" + fsName);

            _provExecutor.setKeyMap(reqAttributeMap);

            if (isMountRequired) {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_QUOTA_DIR_CREATE_MOUNT));
            } else {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_QUOTA_DIR_CREATE));
            }

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult != null && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                String quotaDirId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.QUOTA_DIR_ID);
                String fsysId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEM_ID);
                if (quotaDirId != null) {
                    int fsId = Integer.parseInt(fsysId);
                    int qdId = Integer.parseInt(quotaDirId);

                    VNXQuotaTree vnxQuotaTree = new VNXQuotaTree(quotaDirName, -1, fsId);
                    vnxQuotaTree.setId(qdId);
                    result.setObject(vnxQuotaTree);
                    result.setCommandSuccess();
                } else {
                    result.setCommandFailed();
                    result.setMessage((String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC));
                }
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }

        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    public XMLApiResult modifyQuotaDirectory(final StorageSystem system,
            final String fsName, final String quotaDirName, final String securityStyle,
            final Long size, final Boolean oplocks, Boolean isMountRequired) throws VNXException {

        _log.info("Modify VNX File System Quota dir: {} on file system {}", quotaDirName,
                fsName);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);

            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            reqAttributeMap.put(VNXFileConstants.QUOTA_DIR_NAME, quotaDirName);
            reqAttributeMap.put(VNXFileConstants.HARD_QUOTA, size);
            reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, "/" + fsName);
            reqAttributeMap.put(VNXFileConstants.SECURITY_STYLE, securityStyle);
            reqAttributeMap.put(VNXFileConstants.OPLOCKS, oplocks);

            _provExecutor.setKeyMap(reqAttributeMap);

            if (isMountRequired) {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_QUOTA_DIR_MODIFY_MOUNT));
            } else {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_QUOTA_DIR_MODIFY));
            }

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult != null && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                String quotaDirId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.QUOTA_DIR_ID);
                String fsysId = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEM_ID);
                if (quotaDirId != null) {
                    int fsId = Integer.parseInt(fsysId);
                    int qdId = Integer.parseInt(quotaDirId);

                    VNXQuotaTree vnxQuotaTree = new VNXQuotaTree(quotaDirName, -1, fsId);
                    vnxQuotaTree.setId(qdId);
                    result.setObject(vnxQuotaTree);
                    result.setCommandSuccess();
                } else {
                    result.setCommandFailed();
                    result.setMessage((String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC));
                }
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }

        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    public XMLApiResult deleteQuotaDirectory(final StorageSystem system,
            final String fsName, final String quotaDirName, final Boolean forceDelete, Boolean isMountRequired) throws VNXException {

        _log.info("Delete VNX File System Quota dir: {} on file system {}", quotaDirName,
                fsName);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);

            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            reqAttributeMap.put(VNXFileConstants.QUOTA_DIR_NAME, quotaDirName);
            reqAttributeMap.put(VNXFileConstants.QTREE_FORCE_DELETE, forceDelete);
            reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, "/" + fsName);

            _provExecutor.setKeyMap(reqAttributeMap);

            if (isMountRequired) {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_QUOTA_DIR_DELETE_MOUNT));
            } else {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_QUOTA_DIR_DELETE));
            }

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult != null && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                result.setCommandSuccess();
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }

        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    public XMLApiResult deleteFileSystem(final StorageSystem system,
            final String fileId,
            final String fileSys,
            final boolean isForceDelete, FileShare fs) throws VNXException {
        _log.info("Delete VNX File System: fs id {}, Force Delete {}", fileId, isForceDelete);
        XMLApiResult result = new XMLApiResult();

        if (null == fileId || null == fileSys || fileId.trim().equals("") || fileSys.trim().equals("")) {
            result.setCommandFailed();
            result.setMessage("Invalid Input Parameters.");
            return result;
        }

        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fileSys);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_ID, fileId);
            _provExecutor.setKeyMap(reqAttributeMap);

            // Before deleting check whether it is available or not on the array - This need to be done as part of
            // deleting un-managed FS.
            _provExecutor.setKeyMap(reqAttributeMap);
            _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FSIDQUERY_FILE_DELETE));
            boolean isFsAvailable = false;
            _log.debug("Listing VNX File File Systems");
            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (null != cmdResult && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                isFsAvailable = (Boolean) _provExecutor.getKeyMap().get(VNXFileConstants.IS_FILESYSTEM_AVAILABLE_ON_ARRAY);
            }

            if (!isFsAvailable) {
                _log.debug("File System **Not found on array which requested to delete.");
                result.setCommandSuccess();
                // No need to set Inactive and persist here as this will be done in upper layer(FileDeviceController)
            }

            if (isForceDelete) {

                // handle snapshots
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FS_FORCE_DEL_FILE));
                cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);

                StorageHADomain dataMover = getDataMover(fs);

                if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {

                    List<Checkpoint> snaps = (List<Checkpoint>) _provExecutor.getKeyMap().get(VNXFileConstants.SNAPSHOTS_LIST);
                    int numSnapshots = (snaps != null) ? snaps.size() : 0;
                    _log.info("Number of Snapshots found {} for a file system {}", numSnapshots, fileId);
                    if (snaps != null && !snaps.isEmpty()) {
                        for (Checkpoint checkpoint : snaps) {
                            _log.info("Deleting Snapshot having name {} - and id {}", checkpoint.getName(), checkpoint.getCheckpoint());

                            String nativeGuid = NativeGUIDGenerator.getNativeGuidforSnapshot(system, system.getSerialNumber(),
                                    checkpoint.getCheckpoint());
                            _log.info("NativeGuid {} built for snapshot {}", nativeGuid, checkpoint.getCheckpoint());
                            Snapshot snapshot = null;

                            List<URI> snapShotUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                    .getSnapshotNativeGuidConstraint(nativeGuid));

                            _log.info("{} Snapshots found with native guid : {} ", snapShotUris.size(), nativeGuid);

                            if (!snapShotUris.isEmpty()) {
                                _log.info("Retriving Snapshot using URI : {} ", snapShotUris.get(0));
                                snapshot = _dbClient.queryObject(Snapshot.class, snapShotUris.get(0));
                            }

                            if (snapshot != null) {
                                result = deleteAllExportsAndShares(system, dataMover, fs, snapshot);

                                XMLApiResult status = doDeleteSnapshot(system, checkpoint.getCheckpoint(), checkpoint.getName(), false);
                                if (!status.isCommandSuccess()) {
                                    String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                                    result.setCommandFailed();
                                    result.setMessage(errMsg);
                                    return result;
                                }
                            }
                        }
                    }

                    // Delete All Quota directories of FileShare.
                    result = deleteAllQuotaDirs(system, dataMover, fs);

                    // Delete Exports/SMB Shares of FileShare
                    result = deleteAllExportsAndShares(system, dataMover, fs, null);

                } else {
                    String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                    result.setCommandFailed();
                    result.setMessage(errMsg);
                    return result;
                }
            }

            if (isFsAvailable) {
                _log.debug("File System found on array which requested to delete. Now, deleting on Array.");

                // First unmount it

                StorageHADomain dataMover = getDataMover(fs);

                if (dataMover != null) {
                    Map<String, String> existingMounts = sshApi.getFsMountpathMap(dataMover.getAdapterName());
                    // is FS mount still exists?
                    if (existingMounts.get(fs.getName()) != null) {
                        // The File system is mounted and we need to unmount it before deleting it
                        String unMountCmd = sshApi.formatUnMountCmd(dataMover.getAdapterName(), fs.getMountPath(), "NFS");
                        _log.info("Unmount FS {}", unMountCmd);
                        sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                        result = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, unMountCmd);
                    }
                } else {
                    _log.info("No need to Unmount FS {} since there is no mount info", fs.getMountPath());
                }

                _provExecutor.setKeyMap(reqAttributeMap);
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FSDEL_FILE));

                cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
                if (null != cmdResult && cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                    result.setCommandSuccess();
                } else {
                    String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                    result.setCommandFailed();
                    result.setMessage(errMsg);
                }
            }
        } catch (Exception e) {
            throw new VNXException("File system delete exception: ", e);
        }

        return result;
    }

    private XMLApiResult deleteAllQuotaDirs(StorageSystem system, StorageHADomain dataMover, FileShare fs) {

        XMLApiResult result = new XMLApiResult();
        result.setCommandSuccess();
        _log.info("deleteAllQuotaDirs for {}", fs.getName());

        try {
            // Process for quota dir delete on this file share.
            List<TreeQuota> quotaDirs = (List<TreeQuota>) _provExecutor.getKeyMap().get(VNXFileConstants.QUOTA_DIR_LIST);
            if (quotaDirs != null && !quotaDirs.isEmpty() && dataMover != null) {
                _log.info("Number of quota dirs found {} for a file system {}", quotaDirs.size(), fs.getName());
                // In the process of delete file system, we are unmounting the FileSystem.
                // In order to delete Quota Directory, file system should be mounted.
                // we just mount the file system temporarily. if it was un-mounted.
                sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                Map<String, String> existingMounts = sshApi.getFsMountpathMap(dataMover.getAdapterName());
                if (existingMounts.get(fs.getName()) == null) {
                    String mountCmdArgs = sshApi.formatMountCmd(dataMover.getAdapterName(), fs.getName(), fs.getMountPath());
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_MOUNT_CMD, mountCmdArgs);
                }

                for (TreeQuota quota : quotaDirs) {
                    if (quota != null) {
                        String quotaDirName = quota.getPath().substring(1); // exclude the "/" in the beginning of the
                                                                            // path.
                        XMLApiResult status = deleteQuotaDirectory(system, fs.getName(), quotaDirName, true, false);
                        if (!status.isCommandSuccess()) {
                            String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                            result.setCommandFailed();
                            result.setMessage(errMsg);
                            return result;
                        }
                    }
                }
            }

            return result;
        } catch (Exception e) {
            throw new VNXException("File system quota directory delete exception: ", e);
        }
    }

    private XMLApiResult deleteAllExportsAndShares(StorageSystem system, StorageHADomain dataMover, FileShare fs, Snapshot snapshot) {

        FSExportMap exports;
        SMBShareMap shares;

        XMLApiResult result = new XMLApiResult();
        result.setCommandSuccess();
        String fileId = fs.getId().toString();
        FileObject fObj = fs;

        _log.info("deleteAllExportsAndShares for {} {}", fs.getName(), snapshot);

        boolean fileOperation = false;
        if (snapshot == null) {
            // FileShare operation
            _log.info("deleteAllExportsAndShares FileShare delete operation");
            exports = fs.getFsExports();
            shares = fs.getSMBFileShares();
            fileOperation = true;
            fObj = fs;
        } else {
            _log.info("deleteAllExportsAndShares Snapshot delete operation");
            exports = snapshot.getFsExports();
            shares = snapshot.getSMBFileShares();
            fObj = snapshot;
            fileId = snapshot.getId().toString();
        }

        int exportsToUnExport = 0;
        Set<String> keys = new HashSet();
        if (exports != null) {
            exportsToUnExport = exports.size();
            keys = exports.keySet();
        }
        int noOfShares = 0;
        if (shares != null) {
            noOfShares = shares.size();
        }

        _log.info("Number of NFS exports {}  SMB Shares found {} for File/Snapshot Id {}",
                new Object[] { exportsToUnExport, noOfShares, fileId });

        // To avoid concurrent modification exceptions
        Set<String> exportKeys = new HashSet();
        exportKeys.addAll(keys);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.setFileOperation(fileOperation);
        args.addFSFileObject(fs);
        if (fileOperation) {
            args.setFileOperation(true);
            args.addFSFileObject(fs);
        } else {
            args.setFileOperation(false);
            args.addFSFileObject(fs);
            args.addSnapshot(snapshot);
        }

        for (String key : exportKeys) {
            FileExport exp = exports.get(key);
            VNXFileExport fileExport = new VNXFileExport(exp.getClients(), exp.getStoragePortName(), exp.getPath(), exp.getSecurityType(),
                    exp.getPermissions(), exp.getRootUserMapping(), exp.getProtocol(), exp.getStoragePort(), exp.getSubDirectory(),
                    exp.getComments());

            fileExport.setStoragePort(fs.getStoragePort().toString());

            boolean deleteMount = false;
            if (exportsToUnExport == 1 && noOfShares == 0 && fileOperation) {
                deleteMount = true;
            }
            XMLApiResult status = doUnexport(system, fileExport, args, deleteMount);
            if (!status.isCommandSuccess()) {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
                return result;
            } else {
                fObj.getFsExports().remove(key);
                _log.info("Export removed : " + key);
                exportsToUnExport--;
            }
            // Persist the object after exports removed
            _dbClient.persistObject(fObj);
        }

        // Now Let Handle SMB/CIFS Shares
        keys = new HashSet<>();
        int noOfSharesToDelete = 0;
        if (shares != null) {
            keys = shares.keySet();
            noOfSharesToDelete = keys.size();
        }

        int noOfExports = 0;
        if (exports != null) {
            noOfExports = exports.size();
        }

        _log.info("Number of CIFS/SMB Shares {}  NFS Exports found {} for File/Snapshot Id {}",
                new Object[] { noOfSharesToDelete, noOfExports, fileId });

        // To avoid concurrent modification exceptions
        Set<String> shareKeys = new HashSet();
        shareKeys.addAll(keys);

        for (String key : shareKeys) {
            SMBFileShare share = shares.get(key);

            _log.info("Delete SMB/CIFS Share {} from FS/Snapshot {}", share.getName(), fileId);

            boolean deleteMount = false;
            if (noOfSharesToDelete == 1 && noOfExports == 0 && fileOperation) {
                deleteMount = true;
            }
            XMLApiResult status = doDeleteShare(system, dataMover, share.getName(), fs.getMountPath(), deleteMount, args);
            if (!status.isCommandSuccess()) {
                _log.info("SMBFileShare deletion failed key {} : {} ", key, share.getName());
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
                return result;
            } else {
                fObj.getSMBFileShares().remove(key);
                _log.info("SMBFileShare removed : " + key);
                noOfSharesToDelete--;
            }
            // Persist the object after SMBShares removed
            _dbClient.persistObject(fObj);
        }

        return result;
    }

    /**
     * Performs an export for a VNX File array. If this is the first export of a file system path,
     * then the path must be mounted on a data mover first. Also, if the root user mapping contains a
     * user account name, then it must be converted into a UID.
     * 
     * @param system
     * @param exports
     * @param fsName
     * @param fsId
     * @param firstExport
     * @return
     * @throws VNXException
     */
    public XMLApiResult doExport(final StorageSystem system,
            StorageHADomain dataMover,
            List<VNXFileExport> exports,
            List<String> newPaths,
            FileObject fileObject,
            String fsId,
            boolean firstExport) throws VNXException {
        VNXFileExport vnxExp = exports.get(0);
        String port = vnxExp.getStoragePortName();
        String storagePortNetworkId = vnxExp.getStoragePort();
        _log.info("Export for {}, data mover {}", fileObject.getLabel(), port + ":" + storagePortNetworkId);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();
        FileShare fs = null;

        if (fileObject instanceof FileShare) {
            fs = _dbClient.queryObject(FileShare.class, fileObject.getId());
        }

        String moverOrVdmId = "";
        String moverOrVdmName = "";
        String parentDMName = "";
        String isVdm = "false";
        try {

            if (null == dataMover) {
                result.setCommandFailed();
                result.setMessage("Export failed:  data mover or vdm not found.");
                return result;
            }

            moverOrVdmId = dataMover.getName();
            moverOrVdmName = dataMover.getAdapterName();

            if (dataMover.getVirtual() != null && dataMover.getVirtual() == true) {
                isVdm = "true";
                parentDMName = getParentMoverName(dataMover.getParentHADomainURI());
            }

            sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());

            Map<String, String> userInfo = sshApi.getUserInfo(parentDMName);

            _log.info("Using Mover {} to export FS mounted at {}", moverOrVdmId + ":" + moverOrVdmName, exports.get(0).getMountPoint());

            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.DATAMOVER_ID, port);
            reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverOrVdmId);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_ID, fsId);
            reqAttributeMap.put(VNXFileConstants.DATAMOVER_NAME, moverOrVdmName);
            reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);
            if (vnxExp.getComment() != null && !vnxExp.getComment().isEmpty()) {
                reqAttributeMap.put(VNXFileConstants.TASK_DESCRIPTION, vnxExp.getComment());
            }

            Set<String> moverIds = new HashSet<String>();
            moverIds.add(port);
            reqAttributeMap.put(VNXFileConstants.MOVERLIST, moverIds);
            _provExecutor.setKeyMap(reqAttributeMap);

            if (firstExport) {
                reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, fs.getMountPath());
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_EXPORT_MOUNT));
            } else {
                reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, vnxExp.getMountPoint());
                _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_FILE_EXPORT));
            }

            List<VNXCifsServer> cifsServers = (List<VNXCifsServer>) _provExecutor.getKeyMap().get(VNXFileConstants.CIFS_SERVERS);

            if (cifsServers == null || cifsServers.isEmpty()) {
                _log.info("No CIFS Servers retrieved for mover {} with id {}", moverOrVdmName, moverOrVdmId);
            } else {
                for (VNXCifsServer cifsServer : cifsServers) {
                    _log.debug("CIFServer:" + cifsServer.toString());
                }
            }

            // Format and issue separate ssh api commands for each new file system and subdirectory
            List<VNXFileExport> newExportEntries = new ArrayList<VNXFileExport>();
            sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
            for (String newPath : newPaths) {
                String netBios = null;
                // Only set netbios for VDM CIFS exports
                if (cifsServers != null && !cifsServers.isEmpty() && dataMover.getVirtual()) {
                    netBios = cifsServers.get(0).getName();
                }
                for (VNXFileExport export : exports) {
                    if (export.getMountPoint().equals(newPath)) {
                        export.setNetBios(netBios);
                        newExportEntries.add(export);
                    }
                }

                _log.info("Export info {} {}", moverOrVdmName, netBios);
              //Check for existance of share by name
                String shareNameCheckData = sshApi.formatCheckShareForExportCmd(moverOrVdmName, newExportEntries, userInfo, netBios);
                XMLApiResult shareNameCheckCommandResult = sshApi.executeSshRetry(VNXFileSshApi.SERVER_EXPORT_CMD, shareNameCheckData);
                if(shareNameCheckCommandResult.isCommandSuccess()) {
                    _log.error("Export command failed for share name {}", newExportEntries.get(0).getExportName());
                    StringBuilder errorMessageBuilder = new StringBuilder();
                    errorMessageBuilder.append("Share by the name ");
                    errorMessageBuilder.append(newExportEntries.get(0).getExportName());
                    errorMessageBuilder.append(" Already exists on mover ");
                    errorMessageBuilder.append(moverOrVdmName);
                    
                    result.setCommandFailed();
                    result.setMessage(errorMessageBuilder.toString());
                    return result;
                }
                String data = sshApi.formatExportCmd(moverOrVdmName, newExportEntries, userInfo, netBios);
                _log.info("Export command {}", data);
                if (data != null) {
                    result = sshApi.executeSshRetry(VNXFileSshApi.SERVER_EXPORT_CMD, data);
                }
                if (!result.isCommandSuccess()) {
                    if (firstExport) {
                        data = sshApi.formatUnMountCmd(moverOrVdmName, fs.getMountPath(), "NFS");
                        XMLApiResult unmountResult = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, data);
                        if (!unmountResult.isCommandSuccess()) {
                            _log.warn("Unmounting the file system {} failed due to {}", fs.getId(), unmountResult.getMessage());
                        } else {
                            _log.info("Unmounted the file system {} successfully", fs.getId());
                        }
                    }
                    return result;
                }
                newExportEntries.clear();
            }
            sshApi.clearConnParams();
        } catch (Exception e) {
            throw VNXException.exceptions.createExportFailed(result.getMessage(), e);
        }

        _log.info("doExport result: " + result.getMessage());
        return result;
    }

    public XMLApiResult doDeleteExport(final StorageSystem system, String exportPath, FileDeviceInputOutput args, boolean deleteMount) {

        XMLApiResult result = new XMLApiResult();
        result.setCommandSuccess();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();
        _log.info("Delete VNX Export : {}", exportPath);

        if (exportPath == null || (exportPath != null && exportPath.isEmpty())) {
            _log.info("Invalid Export Path");
            return result;
        }

        try {
            updateAttributes(reqAttributeMap, system);
            String moverId;
            StorageHADomain dataMover = null;
            String mountPath = "";

            if (args.getFileOperation()) {

                StoragePort storagePort = _dbClient.queryObject(StoragePort.class, args.getFs().getStoragePort());
                URI dataMoverId = storagePort.getStorageHADomain();
                dataMover = _dbClient.queryObject(StorageHADomain.class, dataMoverId);
                moverId = dataMover.getName();
                mountPath = args.getFsMountPath();
                _log.info("Using Mover Id {} to unexport FS mounted at {}", moverId, exportPath);

                // Delete export from storage system.
                sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                if (sshApi.getNFSExportsForPath(dataMover.getAdapterName(), exportPath).containsKey(exportPath)) {
                    // Delete the Export.
                    String data = sshApi.formatDeleteNfsExportCmd(dataMover.getAdapterName(), exportPath);
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data);
                }
                // umount root directory should once, again umount operation on same directory fails
                // we check for any exports and share exists and then run umount operation
                if (result.isCommandSuccess() && getVNXFSDependencies(args.getFs(), false) <= 1) {
                    // Delete the mount
                    String data = sshApi.formatUnMountCmd(dataMover.getAdapterName(), mountPath, "NFS");
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, data);
                }
                sshApi.clearConnParams();
            } else {

                String isVdm = "false";
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, args.getSnapshotId());
                FileShare fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
                StoragePort storagePort = _dbClient.queryObject(StoragePort.class, fileshare.getStoragePort());
                URI dataMoverId = storagePort.getStorageHADomain();
                dataMover = _dbClient.queryObject(StorageHADomain.class, dataMoverId);
                moverId = dataMover.getName();
                _log.info("Using Mover Id {} to unexport FS mounted at {}", moverId, exportPath);

                if (dataMover.getVirtual()) {
                    isVdm = "true";
                }

                // Delete export from storage system.
                reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverId);
                reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, exportPath);
                reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);
                _provExecutor.setKeyMap(reqAttributeMap);

                sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                if (sshApi.getNFSExportsForPath(dataMover.getAdapterName(), exportPath).containsKey(exportPath)) {
                    String data = sshApi.formatDeleteNfsExportCmd(dataMover.getAdapterName(), exportPath);
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data);
                }

                // Delete the Snapshot mount, only if No depending exports, shares for that snapshot.
                if (result.isCommandSuccess() && getVNXFSDependencies(args.getFs(), true) <= 1) {
                    // Delete the mount
                    String data = sshApi.formatUnMountCmd(dataMover.getAdapterName(), exportPath, "NFS");
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, data);
                }
                sshApi.clearConnParams();

            }
        } catch (Exception e) {
            throw new VNXException("File Export Delete Exception: ", e);
        }

        return result;
    }

    public XMLApiResult doUnexport(final StorageSystem system, VNXFileExport fileExport,
            FileDeviceInputOutput args, boolean deleteMount) throws VNXException {
        _log.info("Unexport file sys  mounted at : {}", fileExport.getMountPoint());
        XMLApiResult result = new XMLApiResult();
        result.setCommandSuccess();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);
            String moverId;
            StorageHADomain dataMover = null;
            if (args.getFileOperation()) {
                StoragePort storagePort = _dbClient.queryObject(StoragePort.class, args.getFs().getStoragePort());
                URI dataMoverId = storagePort.getStorageHADomain();
                dataMover = _dbClient.queryObject(StorageHADomain.class, dataMoverId);
                moverId = dataMover.getName();
                String fsMountPath = args.getFsPath();
                _log.info("Using Mover Id {} to unexport FS mounted at {}", moverId, fsMountPath);

                // Retrieve export object from the DB. If there are multiple "ro",
                // "rw", "root", and "access" endpoints, just remove this entry and update
                // export properties on the array and in the DB
                boolean thisEntryFound = false;
                boolean moreEntries = false;
                Set<String> keysToRemove = new HashSet<String>();
                String exportEntryKey = FileExport.exportLookupKey(fileExport.getProtocol(),
                        fileExport.getSecurityType(), fileExport.getPermissions(),
                        fileExport.getRootUserMapping(), fileExport.getMountPoint());
                FileExport export = args.getFileObjExports().get(exportEntryKey);
                if (export != null) {
                    thisEntryFound = true;
                    keysToRemove.add(exportEntryKey);
                }
                Set<String> keys = args.getFileObjExports().keySet();
                for (String key : keys) {
                    if ((fileExport.getMountPoint().equals(args.getFileObjExports().get(key).getPath()))
                            && (!exportEntryKey.equalsIgnoreCase(key))) {
                        moreEntries = true;
                        break;
                    }
                }
                for (String key : keysToRemove) {
                    args.getFsExports().remove(key);
                }
                boolean deleteExportFromDevice = true;
                if ((!thisEntryFound) || (moreEntries)) {
                    // Don't unexport, just update properties
                    deleteExportFromDevice = false;
                }

                if (deleteExportFromDevice) {
                    // Delete export from storage system.
                    String mntPoint = fileExport.getMountPoint();
                    sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());

                    if (sshApi.getNFSExportsForPath(dataMover.getAdapterName(), mntPoint).containsKey(mntPoint)) {
                        String data = sshApi.formatDeleteNfsExportCmd(dataMover.getAdapterName(), mntPoint);
                        result = sshApi.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data);
                    }

                    // As we already removed the export entry from Map, Check for any other dependents.
                    if (result.isCommandSuccess() && getVNXFSDependencies(args.getFs(), false) < 1) {
                        // Delete the mount
                        String data = sshApi.formatUnMountCmd(dataMover.getAdapterName(), fsMountPath, "NFS");
                        result = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, data);
                    }
                    sshApi.clearConnParams();

                } else {
                    // Just update export properties.
                    List<VNXFileExport> vnxExports = new ArrayList<VNXFileExport>();
                    keys = args.getFsExports().keySet();
                    for (String key : keys) {
                        FileExport exp = args.getFileObjExports().get(key);
                        VNXFileExport vnxExp = new VNXFileExport(exp.getClients(), exp.getStoragePortName(),
                                exp.getPath(), exp.getSecurityType(), exp.getPermissions(),
                                exp.getRootUserMapping(), exp.getProtocol(), exp.getStoragePort(), exp.getSubDirectory(),
                                exp.getComments());
                        vnxExports.add(vnxExp);
                    }
                    sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                    String data = sshApi.formatExportCmd(dataMover.getAdapterName(), vnxExports, null, null);
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data);
                    sshApi.clearConnParams();
                    if (result.isCommandSuccess()) {
                        result.setCommandSuccess();
                    } else {
                        result.setCommandFailed();
                    }
                }
            } else {
                String isVdm = "false";
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, args.getSnapshotId());
                FileShare fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
                StoragePort storagePort = _dbClient.queryObject(StoragePort.class, fileshare.getStoragePort());
                URI dataMoverId = storagePort.getStorageHADomain();
                dataMover = _dbClient.queryObject(StorageHADomain.class, dataMoverId);
                moverId = dataMover.getName();
                _log.info("Using Mover Id {} to unexport FS mounted at {}", moverId, fileExport.getMountPoint());

                if (dataMover.getVirtual()) {
                    isVdm = "true";
                }

                // Delete export from storage system.
                reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverId);
                reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, fileExport.getMountPoint());
                reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);
                _provExecutor.setKeyMap(reqAttributeMap);

                sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
                String mntPoint = fileExport.getMountPoint();

                if (sshApi.getNFSExportsForPath(dataMover.getAdapterName(), mntPoint).containsKey(mntPoint)) {
                    String data = sshApi.formatDeleteNfsExportCmd(dataMover.getAdapterName(), mntPoint);
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data);
                }

                if (result.isCommandSuccess() && getVNXFSDependencies(fileshare, true) <= 1) {
                    // Delete the mount
                    String data = sshApi.formatUnMountCmd(dataMover.getAdapterName(), fileExport.getMountPoint(), "NFS");
                    result = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, data);
                }

                sshApi.clearConnParams();
            }
        } catch (Exception e) {
            throw new VNXException("File unexport exception: ", e);
        }

        return result;
    }

    /**
     * Delete a CIFS Share
     * 
     * @param system
     * @param moverOrVdm
     *            data mover the share is on.
     * @param shareName
     *            name of the CIFS share.
     * @return result of the operation.
     */
    public XMLApiResult doDeleteShare(StorageSystem system, StorageHADomain moverOrVdm,
            String shareName, String mountPoint, boolean deleteMount, FileDeviceInputOutput args) {
        _log.info("CommApi: delete share {}", shareName);
        XMLApiResult result = new XMLApiResult();
        result.setCommandSuccess();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);
            if (null == moverOrVdm) {
                result.setCommandFailed();
                result.setMessage("Export failed:  data mover or VDM not found.");
                return result;
            }

            String moverOrVdmName = moverOrVdm.getAdapterName();
            String isVdm = "false";
            String moverOrVdmId = moverOrVdm.getName();

            reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverOrVdmId);
            reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);

            if (moverOrVdm.getVirtual() != null && moverOrVdm.getVirtual() == true) {
                isVdm = "true";
            }

            _log.info("Using Mover {} to Delete share {}", moverOrVdmId + ":" + moverOrVdmName, shareName);

            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverOrVdmId);
            reqAttributeMap.put(VNXFileConstants.DATAMOVER_NAME, moverOrVdmName);
            reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);
            _provExecutor.setKeyMap(reqAttributeMap);

            _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(PROV_CIFS_SERVERS));

            List<VNXCifsServer> cifsServers = (List<VNXCifsServer>) _provExecutor.getKeyMap().get(VNXFileConstants.CIFS_SERVERS);
            for (VNXCifsServer cifsServer : cifsServers) {
                _log.info("CIFServer:" + cifsServer.toString());
            }

            if (cifsServers == null || cifsServers.isEmpty()) {
                _log.info("No CIFS Servers retrieved for mover {} with id {}", moverOrVdmName, moverOrVdmId);
            }

            String netBios = null;
            // Only set netbios for VDM CIFS exports
            if (cifsServers != null && !cifsServers.isEmpty() && moverOrVdm.getVirtual()) {
                netBios = cifsServers.get(0).getName();
            }

            sshApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
            String data = sshApi.formatDeleteShareCmd(moverOrVdmName, shareName, netBios);
            _log.info("doDeleteShare command {}", data);
            result = sshApi.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data);
            FileShare fileShare = null;
            if (!args.getFileOperation()) {
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, args.getSnapshotId());
                fileShare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
            } else {
                fileShare = _dbClient.queryObject(FileShare.class, args.getFileObjId());
            }
            if (result.isCommandSuccess() && getVNXFSDependencies(fileShare, false) <= 1) {
                // FileSystem Mount Point

                // Delete the mount
                data = sshApi.formatUnMountCmd(moverOrVdmName, mountPoint, "CIFS");
                _log.info("Unmount filesystem command {}", data);
                result = sshApi.executeSsh(VNXFileSshApi.SERVER_UNMOUNT_CMD, data);
            }

            sshApi.clearConnParams();
        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    /*
     * getThinFSAllocSize - calculate the allocation size for thin file system
     * based on the allocation percentage set in conf file.
     * and make sure the minimum allocation size to be 1024M or FS size, if fs size less than 1024M.
     */
    private Long getThinFSAllocSize(Long fsSizeMBs, boolean considerMinAlloc) {
        Long allocPer = 10L;
        Long thinFsSizeMin = Long.parseLong(THIN_PROVISIONED_FS_SIZE_MB);
        Long allocSize = thinFsSizeMin;

        String allocPerStr = this.getThinFsAllocPercentage();
        if (allocPerStr != null && !allocPerStr.isEmpty()) {
            _log.info("Allocation percentage from conf file {}", allocPerStr);
            allocPer = Long.parseLong(allocPerStr);
        }

        allocSize = (fsSizeMBs * allocPer) / 100;
        if (!considerMinAlloc) {
            _log.info("getThinFSAllocSize return allocation size {}", allocSize.toString());
            return allocSize;
        }
        // allocation size less than 1GB or file size less than 1GB
        // set the allocation size accordingly!!!
        if (allocSize < thinFsSizeMin) {
            allocSize = thinFsSizeMin;
            if (allocSize > fsSizeMBs) {
                allocSize = fsSizeMBs;
            }
        }
        _log.info("getThinFSAllocSize return allocation size {}", allocSize.toString());
        return allocSize;
    }

    private String getFSSize(final StorageSystem system, String fsName) {
        sshApi.setConnParams(system.getIpAddress(), system.getUsername(),
                system.getPassword());
        String fsSizeInfo = sshApi.getFSSizeInfo(fsName);
        return fsSizeInfo;
    }

    public XMLApiResult expandFS(final StorageSystem system, String fsName, long extendSize, boolean isMountRequired,
            boolean isVirtualProvisioned) throws VNXException {
        _log.info("Expand File System {} : new size requested {}", fsName, extendSize);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        long fsSize = Long.parseLong(getFSSize(system, fsName));

        try {
            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_SIZE, extendSize);
            reqAttributeMap.put(VNXFileConstants.MOUNT_PATH, "/" + fsName);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_VIRTUAL_PROVISIONING, isVirtualProvisioned);
            reqAttributeMap.put(VNXFileConstants.ORIGINAL_FS_SIZE, fsSize);

            // calculate the thin fs allocation size
            Long thinProvFsSizeMBs = Long.parseLong(THIN_PROVISIONED_FS_SIZE_MB);
            if (isVirtualProvisioned) {
                thinProvFsSizeMBs = getThinFSAllocSize(extendSize, false);
            }
            reqAttributeMap.put(VNXFileConstants.THIN_FS_ALLOC_SIZE, thinProvFsSizeMBs);

            _provExecutor.setKeyMap(reqAttributeMap);

            if (isMountRequired) {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList()
                        .get(PROV_FILE_MOUNT_EXPAND));
            } else {
                _provExecutor.execute((Namespace) _provNamespaces.getNsList()
                        .get(PROV_FILE_EXPAND));
            }

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                result.setCommandSuccess();
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }
        } catch (Exception e) {
            throw new VNXException("File system expand exception: ", e);
        }

        return result;
    }

    public XMLApiResult doRestoreSnapshot(final StorageSystem system, String fsId, String fsName, String id, String snapshotName)
            throws VNXException {
        _log.info("Restore Snapshot name :{} : file system : {}", snapshotName, fsName);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_ID, fsId);
            reqAttributeMap.put(VNXFileConstants.SNAPSHOT_NAME, snapshotName);
            reqAttributeMap.put(VNXFileConstants.SNAPSHOT_ID, id);
            _provExecutor.setKeyMap(reqAttributeMap);
            _provExecutor.execute((Namespace) _provNamespaces.getNsList()
                    .get(PROV_SNAP_RESTORE));

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                result.setCommandSuccess();
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }
        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    public XMLApiResult doDeleteSnapshot(final StorageSystem system,
            final String snapId, String snapshotName, boolean deleteBaseline) throws VNXException {
        _log.info("Delete VNX Snapshot id : {}", snapId);
        XMLApiResult result = new XMLApiResult();
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();

        try {
            updateAttributes(reqAttributeMap, system);
            reqAttributeMap.put(VNXFileConstants.SNAPSHOT_NAME, snapshotName);
            reqAttributeMap.put(VNXFileConstants.SNAPSHOT_ID, snapId);
            _provExecutor.setKeyMap(reqAttributeMap);

            _provExecutor.execute((Namespace) _provNamespaces.getNsList().get(
                    PROV_DELETE_SNAP));

            String cmdResult = (String) _provExecutor.getKeyMap().get(VNXFileConstants.CMD_RESULT);
            if (cmdResult.equals(VNXFileConstants.CMD_SUCCESS)) {
                result.setCommandSuccess();
            } else {
                String errMsg = (String) _provExecutor.getKeyMap().get(VNXFileConstants.FAULT_DESC);
                result.setCommandFailed();
                result.setMessage(errMsg);
            }
        } catch (Exception e) {
            throw new VNXException("Failure", e);
        }

        return result;
    }

    private void updateAttributes(final Map<String, Object> reqAttributeMap, final StorageSystem system) {

        reqAttributeMap.put(VNXFileConstants.DEVICETYPE, system.getSystemType());
        reqAttributeMap.put(VNXFileConstants.USERNAME, system.getUsername());
        reqAttributeMap.put(VNXFileConstants.USER_PASS_WORD, system.getPassword());
        reqAttributeMap.put(VNXFileConstants.PORTNUMBER, system.getPortNumber());

        reqAttributeMap.put(VNXFileConstants.URI, getServerUri(system));
        reqAttributeMap.put(VNXFileConstants.AUTHURI, getLoginUri(system));

    }

    private String getLoginUri(final StorageSystem system) {

        try {
            final URI deviceURI = new URI("https", system.getIpAddress(), "/Login", null);
            return deviceURI.toString();
        } catch (URISyntaxException ex) {
            _log.error("Error while creating server uri for IP {}", system.getIpAddress());
        }

        return "";
    }

    private String getServerUri(final StorageSystem system) {

        try {
            final URI deviceURI = new URI("https", system.getIpAddress(), SERVER_URI, null);
            return deviceURI.toString();
        } catch (URISyntaxException ex) {
            _log.error("Error while creating server uri for IP {}", system.getIpAddress());
        }

        return "";
    }

    public void setProvExecutor(VNXFileDiscExecutor discExec) {
        _provExecutor = discExec;
    }

    public VNXFileDiscExecutor getProvExecutor() {
        return _provExecutor;
    }

    public void setProvNamespaces(NamespaceList namespaces) {
        _provNamespaces = namespaces;
    }

    public NamespaceList getProvNamespaces() {
        return _provNamespaces;
    }

    private StorageHADomain getMoverOrVdmName(StorageSystem system, String moverOrVdmId, String portNetworkId) {

        _log.info("getMoverOrVdmName(StorageSystem {}, String {})", system.getId(), moverOrVdmId);
        StorageHADomain matchingMoverOrVdm = null;

        List<StoragePort> ports = CustomQueryUtility.queryActiveResourcesByRelation(
                _dbClient, system.getId(), StoragePort.class, "storageDevice");

        for (StoragePort port : ports) {
            if (port.getPortGroup().equalsIgnoreCase(moverOrVdmId) &&
                    port.getPortNetworkId().equalsIgnoreCase(portNetworkId)) {
                matchingMoverOrVdm = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
                _log.info("getMoverOrVdmName match for Port {} and MoverOrVdm {}",
                        port.getLabel() + ":" + port.getPortNetworkId() + ":" + port.getPortGroup(),
                        matchingMoverOrVdm.getAdapterName() + ":" + matchingMoverOrVdm.getName());
                break;
            }
        }

        _log.info("getMoverOrVdmName return () ", matchingMoverOrVdm);
        return matchingMoverOrVdm;
    }

    private String getParentMoverName(URI parentMoverId) {

        String parentMoverName = null;

        _log.info("getParentMoverName {} ", parentMoverId);

        StorageHADomain matchingMover = _dbClient.queryObject(StorageHADomain.class, parentMoverId);

        if (matchingMover != null) {
            parentMoverName = matchingMover.getAdapterName();
        }
        return parentMoverName;
    }

    private StorageHADomain getDataMover(FileShare fileShare) {
        StorageHADomain dm = null;
        if (fileShare.getStoragePort() != null) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, fileShare.getStoragePort());
            if (port != null) {
                dm = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
            }
        }
        return dm;
    }

    private List<Snapshot> getFSSnapshots(FileShare fs) {
        URI fsId = fs.getId();
        List<Snapshot> snapshots = new ArrayList<Snapshot>();
        URIQueryResultList snapIDList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(fsId), snapIDList);
        while (snapIDList.iterator().hasNext()) {
            URI uri = snapIDList.iterator().next();
            Snapshot snap = _dbClient.queryObject(Snapshot.class, uri);
            if (!snap.getInactive()) {
                snapshots.add(snap);
            }
        }
        return snapshots;
    }

    private List<QuotaDirectory> getFSQuotaDirs(FileShare fs) {
        URI fsId = fs.getId();
        List<QuotaDirectory> quotaDirs = new ArrayList<QuotaDirectory>();
        URIQueryResultList qdIDList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getQuotaDirectoryConstraint(fsId), qdIDList);
        while (qdIDList.iterator().hasNext()) {
            URI uri = qdIDList.iterator().next();
            QuotaDirectory quotaDir = _dbClient.queryObject(QuotaDirectory.class, uri);
            if (!quotaDir.getInactive()) {
                quotaDirs.add(quotaDir);
            }
        }
        return quotaDirs;
    }

    /*
     * Check the dependencies on file share or on snap shot.
     * input :
     * fs: File share object
     * considerSnapshots: consider the snap shot dependencies.
     * return: this function will check and return the number of dependencies like
     * exports, shares, snapshot and quota directories on the given file share.
     */

    private int getVNXFSDependencies(FileShare fs, Boolean considerSnapshots) {

        FSExportMap exports = null;
        SMBShareMap shares = null;

        int totalDependencies = 0;
        // FileShare operation
        if (fs.getFsExports() != null) {
            exports = fs.getFsExports();
        }
        if (fs.getSMBFileShares() != null) {
            shares = fs.getSMBFileShares();
        }

        if (exports != null) {
            totalDependencies += exports.size();
        }
        if (shares != null) {
            totalDependencies += shares.size();
        }

        List<Snapshot> snapshots = getFSSnapshots(fs);
        if (snapshots != null && !snapshots.isEmpty()) {
            totalDependencies += snapshots.size();
        }

        List<QuotaDirectory> quotaDirs = getFSQuotaDirs(fs);
        if (quotaDirs != null && !quotaDirs.isEmpty()) {
            totalDependencies += quotaDirs.size();
        }

        if (considerSnapshots && snapshots != null) {

            totalDependencies = snapshots.size();
            for (Snapshot snap : snapshots) {
                exports = snap.getFsExports();
                shares = snap.getSMBFileShares();
                if (exports != null) {
                    totalDependencies += exports.size();
                }
                if (shares != null) {
                    totalDependencies += shares.size();
                }
            }
        }
        _log.info("FileShare : total dependencies {} ", totalDependencies);
        return totalDependencies;
    }
}
