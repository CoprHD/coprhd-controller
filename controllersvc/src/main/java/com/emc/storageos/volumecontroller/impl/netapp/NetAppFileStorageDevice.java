/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.netapp;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.netapp.NetAppException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.FileSystemConstants;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.iwave.ext.netapp.model.CifsAccess;
import com.iwave.ext.netapp.model.CifsAcl;

public class NetAppFileStorageDevice implements FileStorageDevice {
    private static final Logger _log = LoggerFactory
            .getLogger(NetAppFileStorageDevice.class);

    private DbClient _dbClient;

    private static int BYTESPERMB = 1048576;
    private static final String VOL_ROOT = "/vol/";
    private static final String VOL_ROOT_NO_SLASH = "/vol";
    private static final String UNIX_QTREE_SETTING = "unix";
    private static final String NTFS_QTREE_SETTING = "ntfs";
    private static final String RO_PERM = "ro";
    private static final String RW_PERM = "rw";
    private static final String ROOT_PERM = "root";
    private static final String RO_HOSTS = "roHosts";
    private static final String ROOT_HOSTS = "rootHosts";
    private static final String RW_HOSTS = "rwHosts";
    private static final int QUOTA_DIR_MAX_PATH = 100;
    private static final int QUOTA_DIR_MAX_NAME = 64;

    public NetAppFileStorageDevice() {
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    private String genDetailedMessage(String methodName, String entityName) {
        StringBuilder detailedMessage = new StringBuilder("NetAppFileStorageDevice ");

        detailedMessage.append(methodName);
        detailedMessage.append(" failed for ");
        detailedMessage.append(entityName);

        return detailedMessage.toString();
    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {

        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doCreateFS - start");

            if (null == args.getFsName()) {
                _log.error("NetAppFileStorageDevice::doCreateFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == args.getPoolNativeId()) {
                _log.error("NetAppFileStorageDevice::doCreateFS failed:  PoolNativeId either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_POOL_NATIVE_ID_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == args.getFsCapacity()) {
                _log.error("NetAppFileStorageDevice::doCreateFS failed:  Filesystem capacity is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_CAPACITY_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            String nativeId;
            if (args.getFsName().startsWith(VOL_ROOT)) {
                nativeId = args.getFsName();
            } else {
                nativeId = VOL_ROOT + args.getFsName();
            }
            args.setFsNativeId(nativeId);
            String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    storage.getSystemType(), storage.getSerialNumber(),
                    nativeId);
            args.setFsNativeGuid(fsNativeGuid);

            String portGroup = findVfilerName(args.getFs());
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();
            Long fsSize = args.getFsCapacity() / BYTESPERMB;
            String strFsSize = fsSize.toString() + "m";

            if (!nApi.createFS(args.getFsName(), args.getPoolNativeId(),
                    strFsSize, args.getThinProvision())) {
                _log.error("NetAppFileStorageDevice doCreateFS {} - failed", args.getFsName());

                BiosCommandResult rollbackResult = doDeleteFS(storage, args);
                if (rollbackResult.isCommandSuccess()) {
                    _log.info(
                            "NetAppFileStorageDevice doCreateFS rollback completed failed for fs, {}", args.getFsName());
                } else {
                    _log.error(
                            "NetAppFileStorageDevice doCreateFS rollback failed for fs, {} with {}.",
                            args.getFsName(), rollbackResult.getMessage());
                }
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("NetAppFileStorageDevice doCreateFS {} - complete",
                        args.getFsName());
                // Set FS path and Mount Path information
                args.setFsPath(nativeId);
                args.setFsMountPath(nativeId);
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doCreateFS failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doCreateFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    /**
     * Deleting a file system: - deletes FileSystem and any correponding exports, smb shares
     * 
     * @param StorageSystem storage
     * @param args FileDeviceInputOutput
     * @return BiosCommandResult
     * @throws ControllerException
     */

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doDeleteFS - start");

            if (null == args.getFsName()) {
                _log.error("NetAppFileStorageDevice::doDeletFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileSystem();
                serviceError.setMessage("Filesystem name is either missing or empty");
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            FileShare fileshare = args.getFs();
            // Now get the VFiler from the fileShare
            String portGroup = findVfilerName(fileshare);
            if (null != args.getFsShares() && !args.getFsShares().isEmpty()) {
                if (!netAppDeleteCIFSExports(storage, args.getFsShares(), portGroup)) {
                    _log.info("NetAppFileStorageDevice doDeletFS:netAppDeleteCIFSExports {} - failed", args.getFsName());
                } else {
                    _log.info("NetAppFileStorageDevice doDeletFS:netAppDeleteCIFSExports {} - succeeded", args.getFsName());
                }
            }
            boolean failedStatus = false;
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();
            if (!nApi.deleteFS(args.getFsName())) {
                failedStatus = true;
            }
            if (failedStatus == true) {
                _log.error("NetAppFileStorageDevice doDeletFS {} - failed",
                        args.getFsName());
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("NetAppFileStorageDevice doDeletFS {} - complete",
                        args.getFsName());
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doDeleteFS failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doDeleteFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    /**
     * Checking a file system: - Check if the FS exists on Array or not
     * 
     * @param StorageSystem storage
     * @param args FileDeviceInputOutput
     * @return boolean true if exists else false
     * @throws ControllerException
     */

    @Override
    public boolean doCheckFSExists(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _log.info("checking file system existence on array: ", args.getFsName());
        boolean isFSExists = true;
        try {
            String portGroup = findVfilerName(args.getFs());
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();
            List<String> fs = nApi.listFileSystems();
            if (!fs.isEmpty() && fs.contains(args.getFsName())) {
                isFSExists = true;
            } else {
                isFSExists = false;
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doCheckFSExists failed with an Exception", e);
        }
        return isFSExists;
    }

    private Boolean netAppDeleteNFSExports(StorageSystem storage,
            FSExportMap exportMap) throws NetAppException {

        int failedCount = 0;
        Iterator<Map.Entry<String, FileExport>> it = exportMap.entrySet()
                .iterator();

        while (it.hasNext()) {
            Map.Entry<String, FileExport> entry = it.next();
            String key = entry.getKey();
            FileExport fsExport = entry.getValue();
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).build();
            if (!nApi.deleteNFS(fsExport.getPath())) {
                failedCount++;
            } else {
                exportMap.remove(key);
            }
        }

        if (failedCount > 0) {
            return false;
        } else {
            return true;
        }
    }

    private Boolean netAppDeleteCIFSExports(StorageSystem storage,
            SMBShareMap currentShares, String portGroup) throws NetAppException {

        int failedCount = 0;
        Iterator<Entry<String, SMBFileShare>> it = currentShares.entrySet()
                .iterator();

        List<String> removedShareKeys = new ArrayList<String>();

        while (it.hasNext()) {
            Map.Entry<String, SMBFileShare> entry = it.next();
            String key = entry.getKey();
            SMBFileShare smbFileShare = entry.getValue();
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();
            if (!nApi.deleteShare(smbFileShare.getName())) {
                failedCount++;
            } else {
                removedShareKeys.add(key);
            }
        }

        for (String keys : removedShareKeys) {
            currentShares.remove(keys);
        }

        if (failedCount > 0) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage,
            FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {
        _log.info("NetAppFileStorageDevice doExport - start");
        // Verify inputs.
        validateExportArgs(exportList);
        List<String> rootHosts = new ArrayList<String>();
        List<String> rwHosts = new ArrayList<String>();
        List<String> roHosts = new ArrayList<String>();

        if (args.getFileObjExports() == null
                || args.getFileObjExports().isEmpty()) {
            args.initFileObjExports();
        }

        FSExportMap existingExpMap = args.getFileObjExports();
        List<FileExport> existingExportList = new ArrayList<FileExport>();

        FileExport existingExport = null;
        Iterator<String> it = existingExpMap.keySet().iterator();
        while (it.hasNext()) {
            existingExport = existingExpMap.get(it.next());
            _log.info("Existing export FileExport key : {} ",
                    existingExport.getFileExportKey());
            existingExportList.add(existingExport);
        }

        // If it's a sub-directory no need to take existing hosts.
        boolean isSubDir = checkIfSubDirectory(args.getFsMountPath(), exportList.get(0).getMountPath());
        if (isSubDir) {
            existingExportList = null;
        }

        // TODO: Revisit once new Data Model for Exports is implemented.
        Map<String, List<String>> existingHosts = null;

        if ((null != existingExportList) && !existingExportList.isEmpty()) {
            existingHosts = sortHostsFromCurrentExports(existingExportList);
        }

        if (null != existingHosts) {
            if ((null != existingHosts.get(ROOT_HOSTS))
                    && !existingHosts.get(ROOT_HOSTS).isEmpty()) {
                addNewHostsOnly(rootHosts, existingHosts.get(ROOT_HOSTS));
            }

            if ((null != existingHosts.get(RW_HOSTS))
                    && !existingHosts.get(RW_HOSTS).isEmpty()) {
                addNewHostsOnly(rwHosts, existingHosts.get(RW_HOSTS));
            }

            if ((null != existingHosts.get(RO_HOSTS))
                    && !existingHosts.get(RO_HOSTS).isEmpty()) {
                addNewHostsOnly(roHosts, existingHosts.get(RO_HOSTS));
            }
        }

        BiosCommandResult result = new BiosCommandResult();
        try {
            for (int expCount = 0; expCount < exportList.size(); expCount++) {
                FileExport export = exportList.get(expCount);
                if (!(export.getMountPath().startsWith(VOL_ROOT_NO_SLASH))) {
                    export.setMountPath(VOL_ROOT_NO_SLASH
                            + export.getMountPath());
                }

                FileExport fileExport = new FileExport(export.getClients(),
                        export.getStoragePortName(), export.getMountPoint(),
                        export.getSecurityType(), export.getPermissions(),
                        export.getRootUserMapping(), export.getProtocol(),
                        export.getStoragePort(), export.getPath(),
                        export.getMountPath(), export.getSubDirectory(), export.getComments());

                args.getFileObjExports().put(fileExport.getFileExportKey(),
                        fileExport);
                String portGroup = null;
                FileShare fileshare = null;
                if (args.getFileOperation() == true) {
                    fileshare = args.getFs();
                    portGroup = findVfilerName(fileshare);
                } else {
                    // Get the FS from the snapshot
                    URI snapShotUID = args.getSnapshotId();
                    Snapshot snapshot = _dbClient.queryObject(Snapshot.class, snapShotUID);
                    fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
                    // Now get the VFiler from the fileshare
                    portGroup = findVfilerName(fileshare);
                }
                NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                        storage.getPortNumber(), storage.getUsername(),
                        storage.getPassword()).https(true).vFiler(portGroup).build();

                List<String> endpointsList = export.getClients();
                if (endpointsList == null) {
                    _log.error("NetAppFileStorageDevice::doExport {} failed:  No endpoints specified", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netapp.unableToExportFileSystem();
                    serviceError.setMessage(FileSystemConstants.FS_ERR_NO_ENDPOINTS_SPECIFIED);
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                }

                sortNewEndPoints(rootHosts, rwHosts, roHosts, endpointsList,
                        export.getPermissions());
                String root_user = export.getRootUserMapping();
                String mountPath = export.getMountPath();
                String exportPath = export.getPath();

                if (!nApi.exportFS(exportPath, mountPath, rootHosts, rwHosts,
                        roHosts, root_user, export.getSecurityType())) {
                    _log.error("NetAppFileStorageDevice::doExport {} failed", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netapp.unableToExportFileSystem();
                    serviceError.setMessage(genDetailedMessage("doExport", args.getFsId().toString()));
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                }
                result = BiosCommandResult.createSuccessfulResult();
                if ((args.getFileOperation() == true) && (isSubDir == false)) {
                    nApi.setQtreemode(exportPath, UNIX_QTREE_SETTING);
                }
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doExport failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToExportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doExport failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToExportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        _log.info("NetAppFileStorageDevice::doExport {} - complete", args.getFsId());
        return result;
    }

    private void addNewHostsOnly(List<String> permHosts, List<String> newHosts) {
        if (null != newHosts) {
            for (String newHost : newHosts) {
                if (!(permHosts.contains(newHost))) {
                    permHosts.add(newHost);
                }
            }
        }

    }

    /*
     * We check to see if fsMountPath is same as exportMountPath, if not we know that it's sub-directory
     * Also we need to make sure that fsMountPath is a substring of exportMountPath if it were to be sub-directory.
     */
    private boolean checkIfSubDirectory(String fsMountPath, String exportMountPath) {
        if (exportMountPath.contains(fsMountPath) && !exportMountPath.equals(fsMountPath)) {
            return true;
        } else {
            return false;
        }
    }

    private Map<String, List<String>> sortHostsFromCurrentExports(
            List<FileExport> curExpList) {

        Map<String, List<String>> currentHostsList = new HashMap<String, List<String>>();
        for (FileExport curExport : curExpList) {
            if ((null != curExport.getClients())
                    && !curExport.getClients().isEmpty()) {
                if (curExport.getPermissions().toString().equals(ROOT_PERM)) {
                    currentHostsList.put(ROOT_HOSTS, curExport.getClients());
                } else if (curExport.getPermissions().toString()
                        .equals(RW_PERM)) {
                    currentHostsList.put(RW_HOSTS, curExport.getClients());
                } else if (curExport.getPermissions().toString()
                        .equals(RO_PERM)) {
                    currentHostsList.put(RO_HOSTS, curExport.getClients());
                }

            }
        }

        return currentHostsList;
    }

    private void sortNewEndPoints(List<String> rootHosts, List<String> rwHosts,
            List<String> roHosts, List<String> endPointList, String permission) {
        for (String endPoint : endPointList) {
            if ((null != endPointList) && !endPointList.isEmpty()) {
                if (permission.equals(ROOT_PERM)
                        && !(rootHosts.contains(endPoint))) {
                    rootHosts.add(endPoint);
                } else if (permission.equals(RW_PERM)
                        && !(rwHosts.contains(endPoint))) {
                    rwHosts.add(endPoint);
                } else if (permission.equals(RO_PERM)
                        && !(roHosts.contains(endPoint))) {
                    roHosts.add(endPoint);
                }
            }
        }

    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage,
            FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doUnexport: {} - start", args.getFileObjId());

            for (int expCount = 0; expCount < exportList.size(); expCount++) {
                FileExport export = exportList.get(expCount);

                String portGroup = findVfilerName(args.getFs());
                NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                        storage.getPortNumber(), storage.getUsername(),
                        storage.getPassword()).https(true).vFiler(portGroup).build();

                if (export.getPermissions() == null) {
                    export.setPermissions("ro");
                }

                String mountPath = export.getMountPath();
                String exportPath = export.getPath();

                if (!nApi.unexportFS(exportPath, mountPath)) {
                    _log.error("NetAppFileStorageDevice::doUnexport {} failed", args.getFileObjId());
                    ServiceError serviceError = DeviceControllerErrors.netapp.unableToUnexportFileSystem();
                    serviceError.setMessage(genDetailedMessage("doUnexport", args.getFileObjId().toString()));
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                } else {
                    _log.info("NetAppFileStorageDevice doUnexport {} - completed", args.getFileObjId());
                    result = BiosCommandResult.createSuccessfulResult();
                }
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doUnexport failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToUnexportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doUnexport failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToUnexportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        _log.info("NetAppFileStorageDevice doUnexport {} - complete", args.getFileObjId());
        return result;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {

        BiosCommandResult result = new BiosCommandResult();
        try {

            _log.info("NetAppFileStorageDevice doExpandFS - start");

            long newFsExpandSize = args.getNewFSCapacity();
            String volumeName = args.getFsName();
            if (args.getNewFSCapacity() % BYTESPERMB == 0) {
                newFsExpandSize = newFsExpandSize / BYTESPERMB;
            } else {
                newFsExpandSize = newFsExpandSize / BYTESPERMB + 1;
            }
            _log.info("FileSystem new size translation : {} : {}", args.getNewFSCapacity(), newFsExpandSize);
            String strNewFsSize = String.valueOf(newFsExpandSize) + "m";
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).build();
            if (!nApi.setVolumeSize(volumeName, strNewFsSize)) {
                _log.error("NetAppFileStorageDevice doExpandFS - failed");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToExpandFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("NetAppFileStorageDevice doExpandFS - complete");
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doExpandFS failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToExpandFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doExpandFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToExpandFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    /**
     * Creates FileShare CIFS/SMB shares
     * 
     * @param StorageSystem storage
     * @param SMBFileShare smbFileShare
     * @return BiosCommandResult
     * @throws ControllerException
     */
    @Override
    public BiosCommandResult doShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        // To be in-sync with isilon implementation, currently forceGroup is
        // set to null which will set the group name as "everyone" by default.
        String forceGroup = null;
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doShare - start");
            SMBShareMap smbShareMap = args.getFileObjShares();
            SMBFileShare existingShare = (smbShareMap == null) ? null
                    : smbShareMap.get(smbFileShare.getName());
            Boolean modOrCreateShareSuccess;
            if (existingShare != null) {
                modOrCreateShareSuccess = modifyNtpShare(storage, args, smbFileShare, forceGroup, existingShare);
            } else {
                modOrCreateShareSuccess = createNtpShare(storage, args, smbFileShare, forceGroup);
            }
            if (modOrCreateShareSuccess.booleanValue() == true) {
                _log.info("NetAppFileStorageDevice doShare {} - complete",
                        smbFileShare.getName());
                // Update the collection.
                if (args.getFileObjShares() == null) {
                    args.initFileObjShares();
                }
                // set Mount Point
                smbFileShare.setMountPoint(smbFileShare.getNetBIOSName(), smbFileShare.getStoragePortNetworkId(),
                        smbFileShare.getStoragePortName(), smbFileShare.getName());
                args.getFileObjShares().put(smbFileShare.getName(), smbFileShare);
                result = BiosCommandResult.createSuccessfulResult();
            } else {
                _log.error("NetAppFileStorageDevice doShare {} - failed",
                        smbFileShare.getName());
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileShare();
                result = BiosCommandResult.createErrorResult(serviceError);
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doShare failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doShare failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    /**
     * Deletes CIFS FileShare
     * 
     * @param StorageSystem storage
     * @param FileDeviceInputOutput args
     * @param SMBFileShare smbFileShare
     * @return BiosCommandResult
     * @throws ControllerException
     */
    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doDeleteShare - start");
            FileShare fileshare = null;
            if (args.getFileOperation() == true) {
                fileshare = args.getFs();
            } else {
                URI snapShotUID = args.getSnapshotId();
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, snapShotUID);
                fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
            }
            // Now get the VFiler from the fileShare
            String portGroup = findVfilerName(fileshare);
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();
            SMBShareMap shares = args.getFileObjShares();
            if (shares == null || shares.isEmpty()) {
                _log.error("NetAppFileStorageDevice::doDeleteShare failed: FileShare(s) is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileShare();
                serviceError.setMessage("FileShare(s) is either missing or empty");
                result = BiosCommandResult.createErrorResult(serviceError);
            }
            SMBFileShare fileShare = shares.get(smbFileShare.getName());
            if (fileShare != null) {
                if (!nApi.deleteShare(smbFileShare.getName())) {
                    _log.error("NetAppFileStorageDevice doDeleteShare {} - failed",
                            args.getFileObjId());
                    ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileShare();
                    serviceError.setMessage("Deletion of CIFS File Share failed");
                    result = BiosCommandResult.createErrorResult(serviceError);
                } else {
                    _log.info("NetAppFileStorageDevice doDeleteShare {} - complete",
                            args.getFileObjId());
                    args.getFileObjShares().remove(smbFileShare.getName());
                    args.getFileObjShares().remove(smbFileShare.getNativeId());
                    result = BiosCommandResult.createSuccessfulResult();
                }
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doDeleteShare failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doCreateFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    /**
     * Deletes CIFS FileShares
     * 
     * @param StorageSystem storage
     * @param FileDeviceInputOutput args
     * @return BiosCommandResult
     * @throws ControllerException
     */

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(false);
        result.setCommandStatus(Operation.Status.error.name());
        result.setMessage("Delete shares for multiple SMB is not supported.");
        return result;
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(false);
        result.setCommandStatus(Operation.Status.error.name());
        result.setMessage("Modify FS NOT supported for NetApp.");
        return result;
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doSnapshotFS - start");
            if (null == args.getFsName()) {
                _log.error("NetAppFileStorageDevice::doSnapshotFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateSnapshot();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
            if (null == args.getSnapshotName()) {
                _log.error("NetAppFileStorageDevice::doSnapshotFS failed:  Snapshot name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateSnapshot();
                serviceError.setMessage(FileSystemConstants.FS_ERR_SNAPSHOT_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
            boolean failedStatus = false;
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(), storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).build();
            if (!nApi.createSnapshot(args.getFsName(), args.getSnapshotName())) {
                failedStatus = true;
            }
            if (failedStatus == true) {
                _log.error("NetAppFileStorageDevice doSnapshotFS {} - failed", args.getFsId());
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateSnapshot();
                serviceError.setMessage(genDetailedMessage("doSnapshotFS", args.getFsName()));
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("doSnapshotFS - Snapshot, {}  was successfully created for filesystem, {} ", args.getSnapshotName(),
                        args.getFsName());
                // Set snapshot Path and MountPath information
                args.setSnapshotMountPath(getSnapshotMountPath(args.getFsMountPath(), args.getSnapshotName()));
                args.setSnapshotPath(getSnapshotPath(args.getFsPath(), args.getSnapshotName()));
                args.setSnapNativeId(args.getSnapshotName());
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doSnapshotFS failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateSnapshot();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doSnapshotFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateSnapshot();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doRestoreFS - start");
            if (null == args.getFsName()) {
                _log.error("NetAppFileStorageDevice::doRestoreFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToRestoreFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == args.getSnapshotName()) {
                _log.error("NetAppFileStorageDevice::doRestoreFS failed:  Snapshot name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToRestoreFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_SNAPSHOT_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(), storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).build();
            if (!nApi.restoreSnapshot(args.getFsName(), args.getSnapshotName())) {
                _log.error("NetAppFileStorageDevice doRestoreFS {} - failed", args.getFsName());
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToRestoreFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("doRestoreFS - restore of snapshot, {}  was successfully for filesystem, {} ", args.getSnapshotName(),
                        args.getFsName());
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doRestoreFS failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToRestoreFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doRestoreFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToRestoreFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    // Get FS snapshot list from the array.
    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput args, List<String> dbSnapshots) throws ControllerException {

        if (null == args.getFsName()) {
            throw new DeviceControllerException(
                    "Filesystem name is either missing or empty",
                    new Object[] {});
        }

        _log.info("NetAppFileStorageDevice getFSSnapshotList: {} - start",
                args.getFsName());

        BiosCommandResult result = new BiosCommandResult();
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();

        try {
            List<String> deviceSnapshots = nApi.listSnapshots(args.getFsName());
            if (deviceSnapshots == null) {
                _log.warn("NetAppFileStorageDevice getFSSnapshotList {} - failed",
                        args.getFsId());
                result.setCommandSuccess(false);
                result.setMessage("NetAppFileStorageDevice getFSSnapshotList failed for FS {}"
                        + args.getFsName());
                result.setCommandStatus(Operation.Status.error.name());
            } else {
                for (String deviceSnapshotName : deviceSnapshots) {
                    dbSnapshots.add(deviceSnapshotName);
                }
                _log.info(
                        "NetAppFileStorageDevice getFSSnapshotList - successful for filesystem, {} ",
                        args.getFsName());
                result.setCommandSuccess(true);
                result.setCommandStatus(Operation.Status.ready.name());
                result.setMessage("List of snapshots for FS " + args.getFsName()
                        + " was successfully retreived from device ");
            }
        } catch (NetAppException e) {
            String[] params = { storage.getId().toString(), args.getFsName() };
            throw new DeviceControllerException(
                    "Failed to retrieve list of snapshots from device {1} for filesystem {2}",
                    params);
        }
        return result;
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        if (null == args.getFsName()) {
            throw new DeviceControllerException(
                    "Filesystem name is either missing or empty",
                    new Object[] {});
        }

        if (null == args.getSnapshotName()) {
            throw new DeviceControllerException(
                    "Snapshot name is either missing or empty for filesystem name {0}",
                    new Object[] { args.getFsName() });
        }

        _log.info("NetAppFileStorageDevice doDeleteSnapshot: {},{} - start",
                args.getSnapshotId(), args.getSnapshotName());

        BiosCommandResult result = new BiosCommandResult();
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();

        try {
            if (!nApi.deleteSnapshot(args.getFsName(), args.getSnapshotName())) {
                _log.error(
                        "NetAppFileStorageDevice doDeleteSnapshot {} - failed",
                        args.getFsId());
                result.setCommandSuccess(false);
                result.setMessage("NetAppFileStorageDevice doDeleteSnapshot - failed");
                result.setCommandStatus(Operation.Status.error.name());
            } else {
                _log.info(
                        "doDeleteSnapshot for snapshot {} on filesystem {} successful",
                        args.getSnapshotName(), args.getFsName());
                result.setCommandSuccess(true);
                result.setCommandStatus(Operation.Status.ready.name());
                result.setMessage("Snapshot," + args.getSnapshotName()
                        + " has been successfully deleted");
            }
        } catch (NetAppException e) {
            throw new DeviceControllerException(
                    "Failed to delete snapshot, {0} for filesystem, {1} with: {2}",
                    new Object[] { args.getSnapshotName(), args.getFsName(),
                            e.getMessage() });
        }
        return result;
    }

    @Override
    public void doConnect(StorageSystem storage) {
        // FIX ME

    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        // FIX ME
    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(true);
        return result;
    }

    // Validate the export arguments for the VNX.
    //
    // The root user mapping must be an integer or the value 'nobody'. Since
    // 'nobody' is the default value on the NetApp, it does not need to be sent.
    // The CLI only accepts UIDs (integer values).
    private void validateExportArgs(List<FileExport> exports)
            throws ControllerException {

        String rootUser = "";
        for (FileExport exp : exports) {
            // Validate the root user mapping (specific to VNX)
            _log.info("FileExport:Clients:" + exp.getClients() + ":SPName:"
                    + exp.getStoragePortName() + ":SP:" + exp.getStoragePort()
                    + ":rootusermapping:" + exp.getRootUserMapping() + ":perm:"
                    + exp.getPermissions() + ":protocol:" + exp.getProtocol()
                    + ":security:" + exp.getSecurityType() + ":subDir:"
                    + exp.getPath());

            rootUser = exp.getRootUserMapping();
            try {
                if (!rootUser.equalsIgnoreCase("nobody")
                        && !rootUser.equalsIgnoreCase("root")) {
                    Integer.parseInt(rootUser);
                }
            } catch (NumberFormatException nfe) {
                throw new DeviceControllerException(
                        "Invalid Root User Mapping {0} ",
                        new Object[] { nfe });
            }
        }
    }

    /**
     * create NetApp snapshot path from file share path and snapshot name
     * 
     * @param fsPath mount path of the fileshare
     * @param name snapshot name
     * @return String
     */
    private String getSnapshotPath(String fsPath, String name) {
        return String.format("%1$s/.snapshot/%2$s",
                fsPath, name);
    }

    /**
     * create NetApp snapshot path from file share path and snapshot name
     * 
     * @param fsPath mount path of the fileshare
     * @param name snapshot name
     * @return String
     */
    private String getSnapshotMountPath(String fsPath, String name) {
        return String.format("%1$s/.snapshot/%2$s",
                fsPath, name);
    }

    /**
     * create NetApp share with right permissions
     * 
     * @param StorageSystem mount path of the fileshare
     * @param args containing input/out arguments of filedevice
     * @param smbFileShare smbFileshare object
     * @param forceGroup Name of the group the fileshare belongs.
     * @return
     */
    private Boolean createNtpShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare,
            String forceGroup) throws NetAppException {
        String shareId = null;
        String portGroup = findVfilerName(args.getFs());
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        shareId = smbFileShare.getPath();
        _log.info("NetAppFileStorageDevice doShare for {} with id {}", shareId, args.getFileObjId());
        if (!nApi.doShare(shareId, smbFileShare.getName(),
                smbFileShare.getDescription(), smbFileShare.getMaxUsers(),
                smbFileShare.getPermission(), forceGroup)) {
            _log.info("NetAppFileStorageDevice doShare for {} with id {} - failed",
                    shareId, args.getFileObjId());
            return false;
        } else {
            // share creation is successful,no need to set permission,clear the default one.
            List<CifsAcl> existingAcls = new ArrayList<CifsAcl>();
            CifsAcl defaultAcl = new CifsAcl();
            // By default NetApp share get everyone full access.
            defaultAcl.setUserName("everyone");
            defaultAcl.setAccess(CifsAccess.full);
            existingAcls.add(defaultAcl);
            nApi.deleteCIFSShareAcl(smbFileShare.getName(), existingAcls);
            smbFileShare.setNativeId(shareId);
            if (null != args.getFileObj()) {
                nApi.setQtreemode(args.getFsPath(), NTFS_QTREE_SETTING);
            }
            smbFileShare.setNetBIOSName(nApi.getNetBiosName());
            _log.info("NetAppFileStorageDevice doShare for {} with id {} - complete",
                    shareId, args.getFileObjId());
            return true;
        }
    }

    /**
     * modify NetApp share with right permissions and other parameters
     * 
     * @param StorageSystem mount path of the fileshare
     * @param args containing input/out arguments of filedevice
     * @param smbFileShare existingShare smbFileshare object that needs to be modified.
     * @param forceGroup Name of the group the fileshare belongs.
     * @return
     */
    private Boolean modifyNtpShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare,
            String forceGroup, SMBFileShare existingShare) throws NetAppException {
        String portGroup = findVfilerName(args.getFs());
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // String shareId = args.getFileObj().getPath();
        String shareId = smbFileShare.getPath();
        if (!nApi.modifyShare(shareId, smbFileShare.getName(), smbFileShare.getDescription(),
                smbFileShare.getMaxUsers(), smbFileShare.getPermission(), forceGroup)) {
            _log.info(
                    "NetAppFileStorageDevice doShare (modification) for {} with id {} - failed",
                    shareId, args.getFileObjId());
            return false;
        } else {
            _log.info(
                    "NetAppFileStorageDevice doShare (modification) for {} with id {} - complete",
                    shareId, args.getFileObjId());
            return true;
        }
    }

    /**
     * Return the vFiler name associated with the file system. If a vFiler is not associated with
     * this file system, then it will return null.
     */
    private String findVfilerName(FileShare fs) {
        String portGroup = null;

        URI port = fs.getStoragePort();
        if (port == null) {
            _log.info("No storage port URI to retrieve vFiler name");
        } else {
            StoragePort stPort = _dbClient.queryObject(StoragePort.class, port);
            if (stPort != null) {
                URI haDomainUri = stPort.getStorageHADomain();
                if (haDomainUri == null) {
                    _log.info("No Port Group URI for port {}", port);
                } else {
                    StorageHADomain haDomain = _dbClient.queryObject(StorageHADomain.class, haDomainUri);
                    if (haDomain != null && haDomain.getVirtual() == true) {
                        portGroup = stPort.getPortGroup();
                        _log.debug("using port {} and vFiler {}", stPort.getPortNetworkId(), portGroup);
                    }
                }
            }
        }
        return portGroup;
    }

    /**
     * Return true if qtree name and its path in valid length, otherwise false.
     */
    private Boolean validQuotaDirectoryPath(String volName, String quotaDirName) {
        if (volName == null && quotaDirName == null) {
            _log.info("Invalid volume name and quota directory name ");
            return false;
        } else {
            // NetApp accepts maximum of 64-character as a quota directory name.
            if (quotaDirName.length() > QUOTA_DIR_MAX_NAME) {
                _log.error("quota directory name is too long {}, maximum {} chars", quotaDirName.length(), QUOTA_DIR_MAX_NAME);
                return false;
            }

            String qtreePath = VOL_ROOT + volName + "/" + quotaDirName;
            // each entry in /etc/quotas file is with maximum of 160 characters.
            // "/vol/<volname>/qtreename tree <size> - - - -"
            if (qtreePath.length() > QUOTA_DIR_MAX_PATH) {
                _log.error("quota directory path is too long {}, maximum {} chars", qtreePath.length(), QUOTA_DIR_MAX_PATH);
                return false;

            }
        }
        return true;
    }

    // New Qtree methods
    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qtree) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doCreateQuotaDirectory - start");

            String volName = args.getFsName();  			// Using NetApp terminology here
            String qtreeName = args.getQuotaDirectoryName();
            Boolean oplocks = qtree.getOpLock();
            String securityStyle = qtree.getSecurityStyle();
            Long size = qtree.getSize();

            if (null == volName) {
                _log.error("NetAppFileStorageDevice::doCreateQuotaDirectory failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == qtreeName) {
                _log.error("NetAppFileStorageDevice::doCreateQuotaDirectory failed:  Qtree name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (!validQuotaDirectoryPath(volName, qtreeName)) {
                _log.error("NetAppFileStorageDevice::doCreateQuotaDirectory failed:  Qtree name or path is too long");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_PATH_TOO_LONG);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            String portGroup = findVfilerName(args.getFs());
            _log.info("NetAppFileStorageDevice::NetAppFileStorageDevice - For FS: {}, vFiler: {}", volName, portGroup);
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();

            nApi.createQtree(qtreeName, volName, oplocks, securityStyle, size, portGroup);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doCreateQuotaDirectory failed with a NetAppException", e);
            _log.info("NetAppFileStorageDevice::doCreateQuotaDirectory e.getLocalizedMessage(): {}", e.getLocalizedMessage());
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doCreateQuotaDirectory failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppFileStorageDevice doDeleteQuotaDirectory - start");

            String volName = args.getFsName();  			// Using NetApp terminology here
            String qtreeName = args.getQuotaDirectoryName();

            if (null == volName) {
                _log.error("NetAppFileStorageDevice::doDeleteQuotaDirectory failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == qtreeName) {
                _log.error("NetAppFileStorageDevice::doCreateQuotaDirectory failed:  Qtree name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            String portGroup = findVfilerName(args.getFs());
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();

            nApi.deleteQtree(qtreeName, volName, portGroup);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doDeleteQuotaDirectory failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doDeleteQuotaDirectory failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qtree) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();

        try {
            _log.info("NetAppFileStorageDevice doUpdateQuotaDirectory - start");

            String volName = args.getFsName();  			// Using NetApp terminology here
            String qtreeName = args.getQuotaDirectoryName();
            Boolean oplocks = qtree.getOpLock();
            String securityStyle = qtree.getSecurityStyle();
            Long size = qtree.getSize();

            if (null == volName) {
                _log.error("NetAppFileStorageDevice::doUpdateQuotaDirectory failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToUpdateQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == qtreeName) {
                _log.error("NetAppFileStorageDevice::doUpdateQuotaDirectory failed:  Qtree name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netapp.unableToUpdateQtree();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            String portGroup = findVfilerName(args.getFs());
            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();

            nApi.updateQtree(qtreeName, volName, oplocks, securityStyle, size, portGroup);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetAppException e) {
            _log.error("NetAppFileStorageDevice::doUpdateQuotaDirectory failed with a NetAppException", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppFileStorageDevice::doUpdateQuotaDirectory failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToCreateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();

        List<ExportRule> allExports = args.getExistingDBExportRules();
        String subDir = args.getSubDirectory();
        boolean allDirs = args.isAllDir();
        FileShare fs = args.getFs();

        String exportPath;
        String subDirExportPath = "";
        subDir = args.getSubDirectory();

        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null
                    && subDir.length() > 0) {
                subDirExportPath = args.getSnapshotPath() + "/"
                        + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null
                    && subDir.length() > 0) {
                subDirExportPath = args.getFs().getPath() + "/"
                        + subDir;
            }
        }

        _log.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        _log.info("Number of existing exports found {}", allExports.size());
        try {
            if (allDirs) {

                Set<String> allPaths = new HashSet<String>();
                // ALL EXPORTS
                _log.info("Deleting all exports specific to filesystem at device and rules from DB including sub dirs rules and exports");
                for (ExportRule rule : allExports) {
                    allPaths.add(rule.getExportPath());
                }

                for (String path : allPaths) {
                    _log.info("deleting export path : {} ", path);
                    nApi.deleteNFSExport(path);
                }

            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _log.info("Deleting all subdir exports rules at ViPR and  sub directory export at device {}", subDir);
                for (ExportRule rule : allExports) {
                    if (rule.getExportPath().endsWith("/" + subDir)) {
                        nApi.deleteNFSExport(subDirExportPath);
                        break;
                    }
                }
            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                _log.info("Deleting all export rules  from DB and export at device not included sub dirs");
                nApi.deleteNFSExport(exportPath);
            }

        } catch (NetAppException e) {
            _log.info("Exception:" + e.getMessage());
            throw new DeviceControllerException(
                    "Exception while performing export for {0} ",
                    new Object[] { args.getFsId() });
        }

        _log.info("NetAppFileStorageDevice exportFS {} - complete",
                args.getFsId());
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        return result;
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {

        // Requested Export Rules
        List<ExportRule> exportAdd = args.getExportRulesToAdd();
        List<ExportRule> exportDelete = args.getExportRulesToDelete();
        List<ExportRule> exportModify = args.getExportRulesToModify();

        // To be processed export rules
        List<ExportRule> exportsToRemove = new ArrayList<>();
        List<ExportRule> exportsToAdd = new ArrayList<>();

        String exportPath;
        String subDir = args.getSubDirectory();

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

        // ALL EXPORTS
        List<ExportRule> existingDBExportRule = args.getExistingDBExportRules();
        List<ExportRule> exportsToprocess = new ArrayList<>();
        for (ExportRule rule : existingDBExportRule) {
            if (rule.getExportPath().equalsIgnoreCase(exportPath)) {
                exportsToprocess.add(rule);
            }
        }
        _log.info("Number of existng Rules found {}", exportsToprocess.size());

        // Handle Modified export Rules
        if (exportsToprocess != null && !exportsToprocess.isEmpty()) {
            for (ExportRule existingRule : exportsToprocess) {
                for (ExportRule modifiedrule : exportModify) {
                    if (modifiedrule.getSecFlavor().equals(
                            existingRule.getSecFlavor())) {
                        _log.info("Modifying Export Rule from {}, To {}",
                                existingRule, modifiedrule);
                        // use a separate list to avoid concurrent modification exception for now.
                        exportsToRemove.add(existingRule);
                        exportsToAdd.add(modifiedrule);
                    }
                }
            }

            // Handle Add export Rules
            if (exportAdd != null && !exportAdd.isEmpty()) {
                for (ExportRule newExport : exportAdd) {
                    _log.info("Adding Export Rule {}", newExport);
                    exportsToAdd.add(newExport);
                }
            }

            // Handle Delete export Rules
            if (exportDelete != null && !exportDelete.isEmpty()) {
                for (ExportRule existingRule : exportsToprocess) {
                    for (ExportRule oldExport : exportDelete) {
                        if (oldExport.getSecFlavor().equals(
                                existingRule.getSecFlavor())) {
                            _log.info("Deleting Export Rule {}", existingRule);
                            exportsToRemove.add(existingRule);
                        }
                    }
                }
            }
            // No of exports found to remove from the list
            _log.info("No of exports found to remove from the existing exports list {}", exportsToRemove.size());
            exportsToprocess.removeAll(exportsToRemove);
            _log.info("No of exports found to add to the existing exports list {}", exportsToAdd.size());
            exportsToprocess.addAll(exportsToAdd);

            // Since NetApp will remove the export itself when we removed all the export rules,
            // adding a default rule will keep the export alive.

            if (exportsToprocess.isEmpty() && !exportsToRemove.isEmpty()) {
                // If all exports rules deleted, export will get deleted too. So set back to its defaults
                ExportRule rule = new ExportRule();
                rule.setSecFlavor("sys");
                rule.setAnon("root");
                java.util.Set<String> roHosts = new HashSet<>();
                roHosts.add("Default");
                rule.setReadOnlyHosts(roHosts);
                exportsToprocess.add(rule);
            }

        } else {
            if (exportsToprocess == null) {
                exportsToprocess = new ArrayList<>();
            }
            exportsToprocess.addAll(exportAdd);
            exportsToprocess.addAll(exportModify);
        }

        // if only delete provided with no existing rules -- How do we handle this? [GOPI]

        _log.info("Number of Export Rules to update after processing found {}", exportsToprocess.size());
        BiosCommandResult result = new BiosCommandResult();
        try {

            String portGroup = null;
            if (args.getFileOperation() == true) {
                FileShare fileshare = args.getFs();
                portGroup = findVfilerName(fileshare);
            } else {
                // Get the FS from the snapshot
                URI snapshotUID = args.getSnapshotId();
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, snapshotUID);
                FileShare fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());

                portGroup = findVfilerName(fileshare);
            }

            NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).vFiler(portGroup).build();

            if (!nApi
                    .modifyNFSShare(exportPath, exportsToprocess)) {
                _log.error("NetAppFileStorageDevice updateFSExportRules {} - failed",
                        args.getFsId());
                result.setMessage("NetAppFileStorageDevice updateFSExportRules {} - failed");
                result.setCommandStatus(Operation.Status.error.name());
                return result;
            }

            if ((args.getFileOperation() == true)
                    && args.getSubDirectory() == null) {
                nApi.setQtreemode(exportPath, UNIX_QTREE_SETTING);
            }
        } catch (NetAppException e) {
            _log.info("Exception:" + e.getMessage());
            throw new DeviceControllerException(
                    "Exception while performing export for {0} ",
                    new Object[] { args.getFsId() });
        }

        _log.info("NetAppFileStorageDevice updateFSExportRules {} - complete",
                args.getFsId());
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        return result;
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        List<ShareACL> existingAcls = new ArrayList<ShareACL>();
        existingAcls = args.getExistingShareAcls();

        BiosCommandResult result = new BiosCommandResult();

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();
        try {
            processAclsForShare(nApi, args);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception e) {

            _log.error("NetAppFileStorageDevice::updateShareACLs failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToUpdateCIFSShareAcl();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);

            // if delete or modify fails , revert to old acl
            _log.info("update ACL failed ,going to roll back to existing ACLs");
            rollbackShareACLs(storage, args, existingAcls);
        }

        return result;

    }

    private void processAclsForShare(NetAppApi nApi,
            FileDeviceInputOutput args) {

        // add and modify are same for NetApp call.
        List<ShareACL> aclsToAddModify = new ArrayList<ShareACL>();
        aclsToAddModify.addAll(args.getShareAclsToAdd());
        aclsToAddModify.addAll(args.getShareAclsToModify());
        List<ShareACL> aclsToDelete = args.getShareAclsToDelete();
        String shareName = args.getShareName();
        deleteShareAcl(nApi, shareName, aclsToDelete);
        addShareAcl(nApi, shareName, aclsToAddModify);

    }

    private void addShareAcl(NetAppApi nApi, String shareName, List<ShareACL> aclsToAdd) {

        if (aclsToAdd == null || aclsToAdd.isEmpty()) {
            return;
        }

        List<CifsAcl> acls = new ArrayList<CifsAcl>();
        for (ShareACL newAcl : aclsToAdd) {
            CifsAcl cif_new = new CifsAcl();
            String domain = newAcl.getDomain();

            String userOrGroup = newAcl.getGroup() == null ? newAcl.getUser() : newAcl.getGroup();

            if (domain != null && !domain.isEmpty()) {
                userOrGroup = domain + "\\" + userOrGroup;
            }

            // for netapp api user and group are same.and need to set only user
            cif_new.setUserName(userOrGroup);
            cif_new.setShareName(shareName);
            cif_new.setAccess(getAccessEnum(newAcl.getPermission()));
            acls.add(cif_new);
        }
        nApi.modifyCIFSShareAcl(shareName, acls);

    }

    private CifsAccess getAccessEnum(String permission) throws NetAppException {

        CifsAccess access = null;
        if (permission != null) {
            switch (permission.toLowerCase()) {
                case "read":
                    access = CifsAccess.read;
                    break;

                case "change":
                    access = CifsAccess.change;
                    break;
                case "fullcontrol":
                    access = CifsAccess.full;
                    break;
                default:
                    throw new IllegalArgumentException(permission + " is not a valid permission for Cifs Share");
            }
        }
        return access;
    }

    private void deleteShareAcl(NetAppApi nApi, String shareName, List<ShareACL> aclsToDelete) {

        if (aclsToDelete == null || aclsToDelete.isEmpty()) {
            return;
        }

        List<CifsAcl> acls = new ArrayList<CifsAcl>();
        for (ShareACL newAcl : aclsToDelete) {

            CifsAcl cif_new = new CifsAcl();
            String domain = newAcl.getDomain();
            String userOrGroup = newAcl.getGroup() == null ? newAcl.getUser() : newAcl.getGroup();

            if (domain != null && !domain.isEmpty()) {
                userOrGroup = domain + "\\" + userOrGroup;
            }
            // for netapp api user and group are same.and need to set only user
            cif_new.setUserName(userOrGroup);
            cif_new.setShareName(shareName);
            cif_new.setAccess(getAccessEnum(newAcl.getPermission()));
            acls.add(cif_new);

        }
        nApi.deleteCIFSShareAcl(shareName, acls);

    }

    private BiosCommandResult rollbackShareACLs(StorageSystem storage, FileDeviceInputOutput args,
            List<ShareACL> existingList) {

        BiosCommandResult result = new BiosCommandResult();

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();

        try {
            // We can have multiple ace added/modified in one put call ,some of them can fail due to some reason.
            // In case of failure, to make it consistent in vipr db and NetApp share, delete all currently
            // added and modified ace and revert it to old acl.
            _log.info("NetAppFileStorageDevice::Rolling back update ACL by trying delete ACL for share {}", args.getShareName());
            List<ShareACL> aclsToClear = new ArrayList<ShareACL>();
            aclsToClear.addAll(args.getShareAclsToAdd());
            aclsToClear.addAll(args.getShareAclsToModify());
            forceDeleteShareAcl(nApi, args.getShareName(), aclsToClear);
            _log.info("NetAppFileStorageDevice::Adding back old ACL to Share {}", args.getShareName());
            forceAddShareAcl(nApi, args.getShareName(), existingList);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception e) {

            _log.error("NetAppFileStorageDevice::Roll Back of ACL failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToUpdateCIFSShareAcl();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);

        }

        return result;
    }

    private void forceDeleteShareAcl(NetAppApi nApi, String shareName, List<ShareACL> aclsToDelete) {

        if (aclsToDelete == null || aclsToDelete.isEmpty()) {
            return;
        }

        List<CifsAcl> acls = new ArrayList<CifsAcl>();
        for (ShareACL newAcl : aclsToDelete) {

            CifsAcl cif_new = new CifsAcl();
            String domain = newAcl.getDomain();
            String userOrGroup = newAcl.getGroup() == null ? newAcl.getUser() : newAcl.getGroup();

            if (domain != null && !domain.isEmpty()) {
                userOrGroup = domain + "\\" + userOrGroup;
            }
            // for netapp api user and group are same.and need to set only user
            cif_new.setUserName(userOrGroup);
            cif_new.setShareName(shareName);
            cif_new.setAccess(getAccessEnum(newAcl.getPermission()));
            acls.add(cif_new);

        }
        for (CifsAcl cifsAcl : acls) {
            try {
                List<CifsAcl> singleACL = new ArrayList<CifsAcl>();
                singleACL.add(cifsAcl);
                nApi.deleteCIFSShareAcl(shareName, singleACL);
            } catch (Exception e) {

                _log.error("NetAppFileStorageDevice:: Force delete of ACL for user [" + cifsAcl.getUserName()
                        + "] failed with an Exception", e);
            }
        }

    }

    private void forceAddShareAcl(NetAppApi nApi, String shareName, List<ShareACL> aclsToAdd) {

        if (aclsToAdd == null || aclsToAdd.isEmpty()) {
            return;
        }

        List<CifsAcl> acls = new ArrayList<CifsAcl>();
        for (ShareACL newAcl : aclsToAdd) {
            CifsAcl cif_new = new CifsAcl();
            String domain = newAcl.getDomain();

            String userOrGroup = newAcl.getGroup() == null ? newAcl.getUser() : newAcl.getGroup();

            if (domain != null && !domain.isEmpty()) {
                userOrGroup = domain + "\\" + userOrGroup;
            }

            // for netapp api user and group are same.and need to set only user
            cif_new.setUserName(userOrGroup);
            cif_new.setShareName(shareName);
            cif_new.setAccess(getAccessEnum(newAcl.getPermission()));
            acls.add(cif_new);
        }

        for (CifsAcl cifsAcl : acls) {
            try {
                List<CifsAcl> singleACL = new ArrayList<CifsAcl>();
                singleACL.add(cifsAcl);
                nApi.modifyCIFSShareAcl(shareName, singleACL);
            } catch (Exception e) {
                _log.error("NetAppFileStorageDevice:: Force add of ACL for user [" + cifsAcl.getUserName() + "] failed with an Exception",
                        e);
            }
        }

    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        BiosCommandResult result = new BiosCommandResult();
        List<ShareACL> existingAcls = new ArrayList<ShareACL>();
        existingAcls = args.getExistingShareAcls();

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();

        try {
            deleteShareAcl(nApi, args.getShareName(), existingAcls);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception e) {

            _log.error("NetAppFileStorageDevice::Delete All ACL failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netapp.unableToDeleteCIFSShareAcl();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);

        }

        return result;

    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.netapp.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.netapp.operationNotSupported());
    }

}
