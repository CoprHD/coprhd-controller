/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.netappc;

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
import com.emc.storageos.netapp.NetAppException;
import com.emc.storageos.netappc.NetAppCException;
import com.emc.storageos.netappc.NetAppClusterApi;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.FileSystemConstants;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.iwave.ext.netappc.model.CifsAccess;
import com.iwave.ext.netappc.model.CifsAcl;

public class NetAppClusterModeDevice implements FileStorageDevice {
    private static final Logger _log = LoggerFactory
            .getLogger(NetAppClusterModeDevice.class);

    private DbClient _dbClient;

    private static int BYTESPERMB = 1048576;
    private static final String VOL_ROOT = "/vol/";
    private static final String SNAPSHOT = "/.snapshot";
    private static final String RO_PERM = "ro";
    private static final String RW_PERM = "rw";
    private static final String ROOT_PERM = "root";
    private static final String UNIX_SEC_FLAVOR = "sys";
    private static final String DEFAULT_HOSTS = "Default";
    private static final String USER_NONE = "nobody";
    private static final String RO_HOSTS = "roHosts";
    private static final String ROOT_HOSTS = "rootHosts";
    private static final String RW_HOSTS = "rwHosts";

    private static final int QUOTA_DIR_MAX_PATH = 100;
    private static final int QUOTA_DIR_MAX_NAME = 64;

    private enum AclOperation {
        ADD, MODIFY, DELETE, FORCE_ADD, FORCE_DELETE
    };

    public NetAppClusterModeDevice() {
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    private String genDetailedMessage(String methodName, String entityName) {
        StringBuilder detailedMessage = new StringBuilder("NetAppClusterModeDevice ");

        detailedMessage.append(methodName);
        detailedMessage.append(" failed for ");
        detailedMessage.append(entityName);

        return detailedMessage.toString();
    }

    private String genDetailedMessage(String methodName, String entityName, String reason) {
        StringBuilder detailedMessage = new StringBuilder("NetAppClusterModeDevice ");

        detailedMessage.append(methodName);
        detailedMessage.append(" failed for ");
        detailedMessage.append(entityName);
        detailedMessage.append(" : ");
        detailedMessage.append(reason);

        return detailedMessage.toString();
    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doCreateFS - start");

            if (null == args.getFsName()) {
                _log.error("NetAppClusterModeDevice::doCreateFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == args.getPoolNativeId()) {
                _log.error("NetAppClusterModeDevice::doCreateFS failed:  PoolNativeId either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_POOL_NATIVE_ID_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == args.getFsCapacity()) {
                _log.error("NetAppClusterModeDevice::doCreateFS failed:  Filesystem capacity is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_CAPACITY_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            String nativeId = "/" + args.getFsName();
            args.setFsNativeId(nativeId);
            String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    storage.getSystemType(), storage.getSerialNumber(),
                    nativeId);
            args.setFsNativeGuid(fsNativeGuid);

            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            Long fsSize = args.getFsCapacity() / BYTESPERMB;
            String strFsSize = fsSize.toString() + "m";

            if (!ncApi.createFS(args.getFsName(), args.getPoolNativeId(),
                    strFsSize, args.getThinProvision())) {
                _log.error("NetAppClusterModeDevice doCreateFS {} - failed", args.getFsName());

                BiosCommandResult rollbackResult = doDeleteFS(storage, args);
                if (rollbackResult.isCommandSuccess()) {
                    _log.info(
                            "NetAppClusterModeDevice doCreateFS rollback completed failed for fs, {}", args.getFsName());
                } else {
                    _log.error(
                            "NetAppClusterModeDevice doCreateFS rollback failed for fs, {} with {}.",
                            args.getFsName(), rollbackResult.getMessage());
                }
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("NetAppClusterModeDevice doCreateFS {} - complete",
                        args.getFsName());
                // Set FS path and Mount Path information
                args.setFsPath(nativeId);
                args.setFsMountPath(nativeId);
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doCreateFS failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doCreateFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doDeleteFS - start");

            if (null == args.getFsName()) {
                _log.error("NetAppClusterModeDevice::doDeletFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileSystem();
                serviceError.setMessage("Filesystem name is either missing or empty");
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            // Now get the SVM from the fileShare
            String portGroup = findSVMName(args.getFs());
            if (null != args.getFsShares() && !args.getFsShares().isEmpty()) {
                if (!netAppDeleteCIFSExports(storage, args.getFsShares(), portGroup)) {
                    _log.info("NetAppClusterModeDevice doDeletFS:netAppDeleteCIFSExports {} - failed", args.getFsName());
                } else {
                    _log.info("NetAppClusterModeDevice doDeletFS:netAppDeleteCIFSExports {} - succeeded", args.getFsName());
                }
            }
            boolean failedStatus = false;
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            if (!ncApi.deleteFS(args.getFsName())) {
                failedStatus = true;
            }
            if (failedStatus == true) {
                _log.error("NetAppClusterModeDevice doDeletFS {} - failed",
                        args.getFsName());
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("NetAppClusterModeDevice doDeletFS {} - complete",
                        args.getFsName());
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doDeleteFS failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doDeleteFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileSystem();
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
            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            List<String> fs = ncApi.listFileSystems();
            if (!fs.isEmpty() && fs.contains(args.getFsName())) {
                isFSExists = true;
            } else {
                isFSExists = false;
            }
        } catch (NetAppException e) {
            _log.error("NetAppClusterModeDevice::doCheckFSExists failed with an Exception", e);
        }
        return isFSExists;
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage,
            FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {
        _log.info("NetAppClusterModeDevice doExport - start");
        // Verify inputs.
        validateExportArgs(exportList);
        List<String> rootHosts = new ArrayList<String>();
        List<String> rwHosts = new ArrayList<String>();
        List<String> roHosts = new ArrayList<String>();
        BiosCommandResult result = new BiosCommandResult();

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
            if (existingExport.getMountPath().equals(args.getFsMountPath())) {
                _log.info("Existing export FileExport key : {} ",
                        existingExport.getFileExportKey());
                existingExportList.add(existingExport);
            }
        }

        // If it's a sub-directory no need to take existing hosts.
        boolean isSubDir = checkIfSubDirectory(args.getFsMountPath(), exportList.get(0).getMountPath());
        if (isSubDir) {
            existingExportList = null;
            if (exportList.get(0).getMountPath().contains(SNAPSHOT)) {
                _log.error("NetAppClusterModeDevice::doExport {} : Snapshot export is not Supported", args.getSnapshotId());
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportSnapshot();
                serviceError
                        .setMessage(genDetailedMessage("doExport", args.getSnapshotId().toString(), "Snapshot export is not Supported"));
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
        }

        // TODO: Revisit once new Data Model for Exports is implemented.
        Map<String, List<String>> existingHosts = null;

        if ((null != existingExportList) && (!existingExportList.isEmpty())) {
            existingHosts = sortHostsFromCurrentExports(existingExportList);
        }

        if (null != existingHosts) {
            if ((null != existingHosts.get(ROOT_HOSTS))
                    && (!existingHosts.get(ROOT_HOSTS).isEmpty())) {
                addNewHostsOnly(rootHosts, existingHosts.get(ROOT_HOSTS));
            }

            if ((null != existingHosts.get(RW_HOSTS))
                    && (!existingHosts.get(RW_HOSTS).isEmpty())) {
                addNewHostsOnly(rwHosts, existingHosts.get(RW_HOSTS));
            }

            if ((null != existingHosts.get(RO_HOSTS))
                    && (!existingHosts.get(RO_HOSTS).isEmpty())) {
                addNewHostsOnly(roHosts, existingHosts.get(RO_HOSTS));
            }
        }

        try {
            for (int expCount = 0; expCount < exportList.size(); expCount++) {
                FileExport export = exportList.get(expCount);

                FileExport fileExport = new FileExport(export.getClients(),
                        export.getStoragePortName(), export.getMountPoint(),
                        export.getSecurityType(), export.getPermissions(),
                        export.getRootUserMapping(), export.getProtocol(),
                        export.getStoragePort(), export.getPath(),
                        export.getMountPath(), export.getSubDirectory(), export.getComments());

                String portGroup = null;
                FileShare fileshare = null;
                if (args.getFileOperation() == true) {
                    fileshare = args.getFs();
                    portGroup = findSVMName(fileshare);
                } else {
                    _log.error("NetAppClusterModeDevice::doExport {} : Snapshot export is not Supported", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportSnapshot();
                    serviceError.setMessage(genDetailedMessage("doExport", args.getSnapshotId().toString(),
                            "Snapshot export is not Supported"));
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                }
                NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                        storage.getPortNumber(), storage.getUsername(),
                        storage.getPassword()).https(true).svm(portGroup).build();

                List<String> endpointsList = export.getClients();
                if (endpointsList == null) {
                    _log.error("NetAppClusterModeDevice::doExport {} failed:  No endpoints specified", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
                    serviceError.setMessage(FileSystemConstants.FS_ERR_NO_ENDPOINTS_SPECIFIED);
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                }
                sortNewEndPoints(rootHosts, rwHosts, roHosts, endpointsList,
                        export.getPermissions());
                String root_user = export.getRootUserMapping();
                String mountPath = export.getMountPath();
                String exportPath = export.getPath();
                String fsName = fileshare.getName();
                String qtreeName = null;
                if (isSubDir) {
                    if (ncApi.isQtree(fileshare.getName(), export.getSubDirectory())) {
                        qtreeName = export.getSubDirectory();
                        exportPath = constructQtreePath(fileshare.getName(), export.getSubDirectory());
                    } else {
                        _log.error("NetAppClusterModeDevice::doExport {} : Sub-directory export is not Supported", args.getFsId());
                        ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
                        serviceError.setMessage(genDetailedMessage("doExport", args.getFsId().toString(),
                                "Sub-directory export is not Supported"));
                        result = BiosCommandResult.createErrorResult(serviceError);
                        return result;
                    }
                }
                if (!ncApi.exportFS(fsName, qtreeName, exportPath, mountPath, rootHosts, rwHosts,
                        roHosts, root_user, export.getSecurityType())) {
                    _log.error("NetAppClusterModeDevice::doExport {} failed", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
                    serviceError.setMessage(genDetailedMessage("doExport", args.getFsId().toString()));
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                }
                args.getFileObjExports().put(fileExport.getFileExportKey(),
                        fileExport);
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doExport failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doExport failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        _log.info("NetAppClusterModeDevice::doExport {} - complete", args.getFsId());
        return result;
    }

    // Validate the export arguments for the NetApp Cluster-mode
    //
    // The root user mapping must be an integer or the value 'nobody'. Since
    // 'nobody' is the default value on the NetApp, it does not need to be sent.
    // The CLI only accepts UIDs (integer values).
    private void validateExportArgs(List<FileExport> exports)
            throws ControllerException {

        String rootUser = "";
        for (FileExport exp : exports) {
            // Validate the root user mapping
            _log.info("FileExport:Clients:" + exp.getClients() + ":SPName:"
                    + exp.getStoragePortName() + ":SP:" + exp.getStoragePort()
                    + ":rootusermapping:" + exp.getRootUserMapping() + ":perm:"
                    + exp.getPermissions() + ":protocol:" + exp.getProtocol()
                    + ":security:" + exp.getSecurityType() + ":subDir:"
                    + exp.getPath());

            rootUser = exp.getRootUserMapping();
            try {
                if (!rootUser.equalsIgnoreCase(USER_NONE)
                        && !rootUser.equalsIgnoreCase(ROOT_PERM)) {
                    Integer.parseInt(rootUser);
                }
            } catch (NumberFormatException nfe) {
                throw new DeviceControllerException(
                        "Invalid Root User Mapping {0} ",
                        new Object[] { nfe });
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

    private String getSubDirectory(String fsMountPath, String exportMountPath) {
        if (exportMountPath.contains(fsMountPath) && !exportMountPath.equals(fsMountPath)) {
            String[] pathString = exportMountPath.split("/");
            return pathString[pathString.length - 1];
        } else {
            return "";
        }
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

    private Map<String, List<String>> sortHostsFromCurrentExports(
            List<FileExport> curExpList) {

        Map<String, List<String>> currentHostsList = new HashMap<String, List<String>>();
        for (FileExport curExport : curExpList) {
            if ((null != curExport.getClients())
                    && (!curExport.getClients().isEmpty())) {
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
            if ((null != endPointList) && (!endPointList.isEmpty())) {
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
    public BiosCommandResult doShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        // To be in-sync with isilon implementation, currently forceGroup is
        // set to null which will set the group name as "everyone" by default.
        String forceGroup = null;
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doShare - start");
            SMBShareMap smbShareMap = args.getFileObjShares();
            SMBFileShare existingShare = (smbShareMap == null) ? null
                    : smbShareMap.get(smbFileShare.getName());
            Boolean modOrCreateShareSuccess = false;
            if (existingShare != null) {
                modOrCreateShareSuccess = modifyNtpShare(storage, args, smbFileShare, forceGroup, existingShare);
            } else if (existingShare == null) {
                modOrCreateShareSuccess = createNtpShare(storage, args, smbFileShare, forceGroup);
            }
            if (modOrCreateShareSuccess.booleanValue() == true) {
                _log.info("NetAppClusterModeDevice doShare {} - complete",
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
                _log.error("NetAppClusterModeDevice doShare {} - failed",
                        smbFileShare.getName());
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileShare();
                result = BiosCommandResult.createErrorResult(serviceError);
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doShare failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doShare failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doDeleteShare - start");
            FileShare fileshare = null;
            if (args.getFileOperation() == true) {
                fileshare = args.getFs();
            } else {
                URI snapShotUID = args.getSnapshotId();
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, snapShotUID);
                fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
            }
            // Now get the VFiler from the fileShare
            String portGroup = findSVMName(fileshare);
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            SMBShareMap shares = args.getFileObjShares();
            if (shares == null || shares.isEmpty()) {
                _log.error("NetAppClusterModeDevice::doDeleteShare failed: FileShare(s) is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileShare();
                serviceError.setMessage("FileShare(s) is either missing or empty");
                result = BiosCommandResult.createErrorResult(serviceError);
            }
            SMBFileShare fileShare = shares.get(smbFileShare.getName());
            if (fileShare != null) {
                if (!ncApi.deleteShare(smbFileShare.getName())) {
                    _log.error("NetAppClusterModeDevice doDeleteShare {} - failed",
                            args.getFileObjId());
                    ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileShare();
                    serviceError.setMessage("Deletion of CIFS File Share failed");
                    result = BiosCommandResult.createErrorResult(serviceError);
                } else {
                    _log.info("NetAppClusterModeDevice doDeleteShare {} - complete",
                            args.getFileObjId());
                    args.getFileObjShares().remove(smbFileShare.getName());
                    args.getFileObjShares().remove(smbFileShare.getNativeId());
                    result = BiosCommandResult.createSuccessfulResult();
                }
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doDeleteShare failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doDeleteShare failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteFileShare();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

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
    public BiosCommandResult doUnexport(StorageSystem storage,
            FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doUnexport: {} - start", args.getFileObjId());

            if (!args.getFileOperation()) {
                _log.error("NetAppClusterModeDevice::doUnexport {} : Snapshot unexport is not Supported", args.getSnapshotId());
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToUnexportSnapshot();
                serviceError.setMessage(genDetailedMessage("doUnExport", args.getSnapshotId().toString(),
                        "Snapshot unexport is not Supported"));
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            for (int expCount = 0; expCount < exportList.size(); expCount++) {
                FileExport export = exportList.get(expCount);

                String portGroup = findSVMName(args.getFs());
                NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                        storage.getPortNumber(), storage.getUsername(),
                        storage.getPassword()).https(true).svm(portGroup).build();

                if (export.getPermissions() == null) {
                    export.setPermissions(RO_PERM);
                }

                String mountPath = export.getMountPath();
                String exportPath = export.getPath();

                if (!ncApi.unexportFS(exportPath, mountPath)) {
                    _log.error("NetAppClusterModeDevice::doUnexport {} failed", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netappc.unableToUnexportFileSystem();
                    serviceError.setMessage(genDetailedMessage("doUnexport", args.getFsId().toString()));
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                } else {
                    _log.info("NetAppClusterModeDevice doUnexport {} - completed", args.getFsId());
                    result = BiosCommandResult.createSuccessfulResult();
                }
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doUnexport failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUnexportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doUnexport failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUnexportFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        _log.info("NetAppClusterModeDevice doUnexport {} - complete", args.getFileObjId());
        return result;
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(false);
        result.setCommandStatus(Operation.Status.error.name());
        result.setMessage("Modify FS NOT supported for NetAppC.");
        return result;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {

            _log.info("NetAppClusterModeDevice doExpandFS - start");

            long newFsExpandSize = args.getNewFSCapacity();
            String volumeName = args.getFsName();
            if (args.getNewFSCapacity() % BYTESPERMB == 0) {
                newFsExpandSize = newFsExpandSize / BYTESPERMB;
            } else {
                newFsExpandSize = newFsExpandSize / BYTESPERMB + 1;
            }
            _log.info("FileSystem new size translation : {} : {}", args.getNewFSCapacity(), newFsExpandSize);
            String strNewFsSize = String.valueOf(newFsExpandSize) + "m";
            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi nApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            if (!nApi.setVolumeSize(volumeName, strNewFsSize)) {
                _log.error("NetAppClusterModeDevice doExpandFS - failed");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToExpandFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("NetAppClusterModeDevice doExpandFS - complete");
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doExpandFS failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToExpandFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doExpandFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToExpandFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doSnapshotFS - start");
            if (null == args.getFsName()) {
                _log.error("NetAppClusterModeDevice::doSnapshotFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateSnapshot();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
            if (null == args.getSnapshotName()) {
                _log.error("NetAppClusterModeDevice::doSnapshotFS failed:  Snapshot name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateSnapshot();
                serviceError.setMessage(FileSystemConstants.FS_ERR_SNAPSHOT_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
            boolean failedStatus = false;
            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(), storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            if (!ncApi.createSnapshot(args.getFsName(), args.getSnapshotName())) {
                failedStatus = true;
            }
            if (failedStatus == true) {
                _log.error("NetAppClusterModeDevice doSnapshotFS {} - failed", args.getFsId());
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateSnapshot();
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
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doSnapshotFS failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateSnapshot();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doSnapshotFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateSnapshot();
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
            _log.info("NetAppClusterModeDevice doRestoreFS - start");
            if (null == args.getFsName()) {
                _log.error("NetAppClusterModeDevice::doRestoreFS failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToRestoreFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == args.getSnapshotName()) {
                _log.error("NetAppClusterModeDevice::doRestoreFS failed:  Snapshot name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToRestoreFileSystem();
                serviceError.setMessage(FileSystemConstants.FS_ERR_SNAPSHOT_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }
            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(), storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            if (!ncApi.restoreSnapshot(args.getFsName(), args.getSnapshotName())) {
                _log.error("NetAppClusterModeDevice doRestoreFS {} - failed", args.getFsName());
                ServiceError serviceError = DeviceControllerErrors.netappc.unableToRestoreFileSystem();
                result = BiosCommandResult.createErrorResult(serviceError);
            } else {
                _log.info("doRestoreFS - restore of snapshot, {}  was successfully for filesystem, {} ", args.getSnapshotName(),
                        args.getFsName());
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doRestoreFS failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToRestoreFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doRestoreFS failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToRestoreFileSystem();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput args, List<String> dbSnapshots)
            throws ControllerException {
        if (null == args.getFsName()) {
            throw new DeviceControllerException(
                    "Filesystem name is either missing or empty",
                    new Object[] {});
        }

        _log.info("NetAppClusterModeDevice getFSSnapshotList: {} - start",
                args.getFsName());

        BiosCommandResult result = new BiosCommandResult();
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(), storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        try {
            List<String> deviceSnapshots = ncApi.listSnapshots(args.getFsName());
            if (deviceSnapshots == null) {
                _log.warn("NetAppClusterModeDevice getFSSnapshotList {} - failed",
                        args.getFsId());
                result.setCommandSuccess(false);
                result.setMessage("NetAppClusterModeDevice getFSSnapshotList failed for FS {}"
                        + args.getFsName());
                result.setCommandStatus(Operation.Status.error.name());
            } else {
                for (String deviceSnapshotName : deviceSnapshots) {
                    dbSnapshots.add(deviceSnapshotName);
                }
                _log.info(
                        "NetAppClusterModeDevice getFSSnapshotList - successful for filesystem, {} ",
                        args.getFsName());
                result.setCommandSuccess(true);
                result.setCommandStatus(Operation.Status.ready.name());
                result.setMessage("List of snapshots for FS " + args.getFsName()
                        + " was successfully retreived from device ");
            }
        } catch (NetAppCException e) {
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

        _log.info("NetAppClusterModeDevice doDeleteSnapshot: {},{} - start",
                args.getSnapshotId(), args.getSnapshotName());

        BiosCommandResult result = new BiosCommandResult();
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        try {
            if (!ncApi.deleteSnapshot(args.getFsName(), args.getSnapshotName())) {
                _log.error(
                        "NetAppClusterModeDevice doDeleteSnapshot {} - failed",
                        args.getFsId());
                result.setCommandSuccess(false);
                result.setMessage("NetAppClusterModeDevice doDeleteSnapshot - failed");
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
        } catch (NetAppCException e) {
            throw new DeviceControllerException(
                    "Failed to delete snapshot, {0} for filesystem, {1} with: {2}",
                    new Object[] { args.getSnapshotName(), args.getFsName(),
                            e.getMessage() });
        }
        return result;
    }

    @Override
    public void doConnect(StorageSystem storage) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doDisconnect(StorageSystem storage) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        // TODO Auto-generated method stub
        return null;
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

    public BiosCommandResult validateQuotaDirectoryParams(String volName, String qtreeName, String method) throws ControllerException {

        BiosCommandResult result = new BiosCommandResult();
        result = BiosCommandResult.createSuccessfulResult();

        ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateQtree();
        if (method.equals("doCreateQuotaDirectory")) {
            serviceError = DeviceControllerErrors.netappc.unableToCreateQtree();
        } else if (method.equals("doDeleteQuotaDirectory")) {
            serviceError = DeviceControllerErrors.netappc.unableToDeleteQtree();
        } else {
            serviceError = DeviceControllerErrors.netappc.unableToUpdateQtree();
        }

        if (null == volName) {
            _log.error("NetAppClusterModeDevice:: {} failed:  Filesystem name is either missing or empty", method);
            serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
            result = BiosCommandResult.createErrorResult(serviceError);
            return result;
        }

        if (null == qtreeName) {
            _log.error("NetAppClusterModeDevice::{} failed:  Qtree name is either missing or empty", method);
            serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
            result = BiosCommandResult.createErrorResult(serviceError);
            return result;
        }

        if (method.equals("doCreateQuotaDirectory") && !validQuotaDirectoryPath(volName, qtreeName)) {
            _log.error("NetAppClusterModeDevice::{} failed:  Qtree name or path is too long", method);
            serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_PATH_TOO_LONG);
            result = BiosCommandResult.createErrorResult(serviceError);
            return result;
        }

        return result;

    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qtree)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        try {
            _log.info("NetAppClusterModeDevice doCreateQuotaDirectory - start");

            String volName = args.getFsName();  			// Using NetApp terminology here
            String qtreeName = args.getQuotaDirectoryName();
            Boolean oplocks = qtree.getOpLock();
            String securityStyle = qtree.getSecurityStyle();
            Long size = qtree.getSize();
            String method = new String("doCreateQuotaDirectory");

            result = validateQuotaDirectoryParams(volName, qtreeName, method);
            if (!result.isCommandSuccess()) {
                return result;
            }

            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();

            ncApi.createQtree(qtreeName, volName, oplocks, securityStyle, size, portGroup);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doCreateQuotaDirectory failed with a NetAppCException", e);
            _log.info("NetAppClusterModeDevice::doCreateQuotaDirectory e.getLocalizedMessage(): {}", e.getLocalizedMessage());
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doCreateQuotaDirectory failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToCreateQtree();
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
            _log.info("NetAppClusterModeDevice doDeleteQuotaDirectory - start");

            String volName = args.getFsName();  			// Using NetApp terminology here
            String qtreeName = args.getQuotaDirectoryName();
            String method = new String("doDeleteQuotaDirectory");

            result = validateQuotaDirectoryParams(volName, qtreeName, method);
            if (!result.isCommandSuccess()) {
                return result;
            }

            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();

            ncApi.deleteQtree(qtreeName, volName, portGroup);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doDeleteQuotaDirectory failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doDeleteQuotaDirectory failed with an Exception ", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qtree)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();

        try {
            _log.info("NetAppClusterModeDevice doUpdateQuotaDirectory - start");

            String volName = args.getFsName();  			// Using NetApp terminology here
            String qtreeName = args.getQuotaDirectoryName();
            Boolean oplocks = qtree.getOpLock();
            String securityStyle = qtree.getSecurityStyle();
            Long size = qtree.getSize();
            String method = new String("doUpdateQuotaDirectory");

            result = validateQuotaDirectoryParams(volName, qtreeName, method);
            if (!result.isCommandSuccess()) {
                return result;
            }

            String portGroup = findSVMName(args.getFs());
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();

            ncApi.updateQtree(qtreeName, volName, oplocks, securityStyle, size, portGroup);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetAppCException e) {
            _log.error("NetAppClusterModeDevice::doUpdateQuotaDirectory failed with a NetAppCException", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUpdateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        } catch (Exception e) {
            _log.error("NetAppClusterModeDevice::doUpdateQuotaDirectory failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUpdateQtree();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);
        }
        return result;
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args) {
        // Requested Export Rules
        List<ExportRule> exportAdd = args.getExportRulesToAdd();
        List<ExportRule> exportDelete = args.getExportRulesToDelete();
        List<ExportRule> exportModify = args.getExportRulesToModify();

        // To be processed export rules
        List<ExportRule> exportsToRemove = new ArrayList<>();
        List<ExportRule> exportsToAdd = new ArrayList<>();
        List<ExportRule> exportsRemove = new ArrayList<>();
        // ALL EXPORTS
        List<ExportRule> exportsToprocess = args.getExistingDBExportRules();
        String fsName = "";
        if (exportsToprocess == null) {
            exportsToprocess = new ArrayList<>();
        }
        _log.info("Number of existng Rules found {}", exportsToprocess.size());

        String exportPath;
        String subDir = args.getSubDirectory();
        BiosCommandResult result = new BiosCommandResult();

        if (!args.getFileOperation()) {
            _log.error("NetAppClusterModeDevice::updateExportRules {} : Snapshot export/unexport is not Supported", args.getSnapshotId());
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUnexportSnapshot();
            serviceError.setMessage(genDetailedMessage("updateExportRules", args.getSnapshotId().toString(),
                    "Snapshot export/unexport is not Supported"));
            result = BiosCommandResult.createErrorResult(serviceError);
            return result;
        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null
                    && subDir.length() > 0) {
                exportPath = args.getFs().getPath() + "/"
                        + subDir;
            }
        }

        // if only delete provided with no existing rules -- How do we handle this?

        _log.info("Number of Export Rules to update after processing found {}", exportsToprocess.size());
        try {

            String portGroup = null;
            if (args.getFileOperation() == true) {
                FileShare fileshare = args.getFs();
                fsName = fileshare.getName();
                portGroup = findSVMName(fileshare);
            }

            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();

            String qtreePath = "";
            String qtreeName = null;
            if (args.getFileOperation()) {
                qtreePath = exportPath;
                if (subDir != null
                        && subDir.length() > 0) {
                    if (ncApi.isQtree(args.getFsName(), subDir)) {
                        qtreeName = subDir;
                        qtreePath = constructQtreePath(args.getFsName(), subDir);
                    } else {
                        _log.error("NetAppClusterModeDevice::updateExportRules {} : Sub-directory export/unexport is not Supported",
                                args.getFsId());
                        ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
                        serviceError.setMessage(genDetailedMessage("updateExportRules", args.getFsId().toString(),
                                "Sub-directory export/unexport is not Supported"));
                        result = BiosCommandResult.createErrorResult(serviceError);
                        return result;
                    }
                }
            }

            _log.info("exportPath : {}", exportPath);
            args.setExportPath(exportPath);

            // Handle Modified export Rules
            if (!exportsToprocess.isEmpty()) {
                for (ExportRule existingRule : exportsToprocess) {
                    if (existingRule.getExportPath().equalsIgnoreCase(exportPath)) {
                        for (ExportRule modifiedrule : exportModify) {
                            if (modifiedrule.getSecFlavor().equals(
                                    existingRule.getSecFlavor())) {
                                _log.info("Modifying Export Rule from {}, To {}",
                                        existingRule, modifiedrule);
                                if (!ncApi.modifyNFSShare(fsName, qtreeName, qtreePath, existingRule, modifiedrule)) {
                                    _log.error("NetAppClusterModeDevice updateFSExportRules {} - failed",
                                            args.getFsId());
                                    result.setMessage("NetAppClusterModeDevice updateFSExportRules {} - failed");
                                    result.setCommandStatus(Operation.Status.error.name());
                                    return result;
                                }
                            }
                        }
                    }
                }

                // Handle Add export Rules
                if (exportAdd != null && !exportAdd.isEmpty()) {
                    for (ExportRule newExport : exportAdd) {
                        _log.info("Adding Export Rule {}", newExport);
                        if (!ncApi.addNFSShare(fsName, qtreeName, qtreePath, newExport)) {
                            _log.error("NetAppClusterModeDevice updateFSExportRules {} - failed",
                                    args.getFsId());
                            result.setMessage("NetAppClusterModeDevice updateFSExportRules {} - failed");
                            result.setCommandStatus(Operation.Status.error.name());
                            return result;
                        }
                    }
                }

                // Handle Delete export Rules
                if (exportDelete != null && !exportDelete.isEmpty()) {
                    for (ExportRule existingRule : exportsToprocess) {
                        if (existingRule.getExportPath().equalsIgnoreCase(exportPath)) {
                            exportsToRemove.add(existingRule);
                            for (ExportRule oldExport : exportDelete) {
                                if (oldExport.getSecFlavor().equals(
                                        existingRule.getSecFlavor())) {
                                    _log.info("Deleting Export Rule {}", existingRule);
                                    exportsRemove.add(existingRule);
                                    if (!ncApi.deleteNFSShare(fsName, qtreeName, existingRule, qtreePath)) {
                                        _log.error("NetAppClusterModeDevice updateFSExportRules {} - failed",
                                                args.getFsId());
                                        result.setMessage("NetAppClusterModeDevice updateFSExportRules {} - failed");
                                        result.setCommandStatus(Operation.Status.error.name());
                                        return result;
                                    }
                                }
                            }
                        }
                    }
                }
                // No of exports found to remove from the list
                _log.info("No of exports found to remove from the existing exports list {}", exportsRemove.size());
                exportsToRemove.removeAll(exportsRemove);
                _log.info("No of exports found to add to the existing exports list {}", exportsToAdd.size());

                // delete the export if no export rule left.
                // If we delete filesystem without deleting export policy. Export policy will not get cleared on Array.
                if (exportsToRemove.isEmpty() && !exportsRemove.isEmpty()) {
                    ncApi.deleteNFSExport(qtreePath);
                }
            }
        } catch (NetAppCException e) {
            _log.info("Exception:" + e.getMessage());
            throw new DeviceControllerException(
                    "Exception while performing export for {0} - {1} ",
                    new Object[] { args.getFsId(), e.getMessage() });
        }

        _log.info("NetAppClusterModeDevice updateFSExportRules {} - complete",
                args.getFsId());
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        return result;
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) {
        BiosCommandResult result = new BiosCommandResult();
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        List<ExportRule> allExports = args.getExistingDBExportRules();
        String subDir = args.getSubDirectory();
        boolean allDirs = args.isAllDir();

        String exportPath;
        String qtreePath = "";

        if (!args.getFileOperation()) {
            _log.error("NetAppClusterModeDevice::doUnexport {} : Snapshot unexport is not Supported", args.getSnapshotId());
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUnexportSnapshot();
            serviceError
                    .setMessage(genDetailedMessage("doUnExport", args.getSnapshotId().toString(), "Snapshot unexport is not Supported"));
            result = BiosCommandResult.createErrorResult(serviceError);
            return result;
        } else {
            exportPath = args.getFs().getPath();
            qtreePath = exportPath;
            if (subDir != null
                    && subDir.length() > 0) {
                exportPath = args.getFs().getPath() + "/"
                        + subDir;
                if (ncApi.isQtree(args.getFsName(), subDir)) {
                    qtreePath = constructQtreePath(args.getFsName(), subDir);
                } else {
                    _log.error("NetAppClusterModeDevice::doUnexport {} : Sub-directory unexport is not Supported", args.getFsId());
                    ServiceError serviceError = DeviceControllerErrors.netappc.unableToExportFileSystem();
                    serviceError.setMessage(genDetailedMessage("doUnExport", args.getFsId().toString(),
                            "Sub-directory unexport is not Supported"));
                    result = BiosCommandResult.createErrorResult(serviceError);
                    return result;
                }
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
                    boolean isSubDir = checkIfSubDirectory(args.getFsMountPath(), path);
                    if (isSubDir) {
                        subDir = getSubDirectory(args.getFsMountPath(), path);
                        if (ncApi.isQtree(args.getFsName(), subDir)) {
                            path = constructQtreePath(args.getFsName(), subDir);
                        }
                    }
                    _log.info("deleting export path : {} ", path);
                    ncApi.deleteNFSExport(path);
                }

            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _log.info("Deleting all subdir exports rules at ViPR and  sub directory export at device {}", subDir);
                for (ExportRule rule : allExports) {
                    if (rule.getExportPath().endsWith("/" + subDir)) {
                        ncApi.deleteNFSExport(qtreePath);
                        break;
                    }
                }
            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                _log.info("Deleting all export rules  from DB and export at device not included sub dirs");
                ncApi.deleteNFSExport(qtreePath);
            }

        } catch (NetAppCException e) {
            _log.info("Exception:" + e.getMessage());
            throw new DeviceControllerException(
                    "Exception while performing export for {0} ",
                    new Object[] { args.getFsId() });
        }

        _log.info("NetAppClusterModeDevice unexportFS {} - complete",
                args.getFsId());
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        return result;
    }

    /**
     * Return the svm name associated with the file system. If a svm is not associated with
     * this file system, then it will return null.
     */
    private String findSVMName(FileShare fs) {
        String portGroup = null;

        URI port = fs.getStoragePort();
        if (port == null) {
            _log.info("No storage port URI to retrieve svm name");
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
                        _log.debug("using port {} and svm {}", stPort.getPortNetworkId(), portGroup);
                    }
                }
            }
        }
        return portGroup;
    }

    /**
     * create NetAppC share with right permissions
     * 
     * @param StorageSystem mount path of the fileshare
     * @param args containing input/out arguments of filedevice
     * @param smbFileShare smbFileshare object
     * @param forceGroup Name of the group the fileshare belongs.
     * @return
     */
    private Boolean createNtpShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare,
            String forceGroup) throws NetAppCException {
        String shareId = null;
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();
        shareId = smbFileShare.getPath();
        _log.info("NetAppClusterModeDevice doShare for {} with id {}", shareId, args.getFileObjId());
        if (!ncApi.doShare(shareId, smbFileShare.getName(),
                smbFileShare.getDescription(), smbFileShare.getMaxUsers(),
                smbFileShare.getPermission(), forceGroup)) {
            _log.info("NetAppClusterModeDevice doShare for {} with id {} - failed",
                    shareId, args.getFileObjId());
            return false;
        } else {
            // Since share creation is successful, now update with right
            // permission.
            if (!ncApi.modifyShare(shareId,
                    smbFileShare.getName(), smbFileShare.getDescription(),
                    smbFileShare.getMaxUsers(),
                    smbFileShare.getPermission(), forceGroup)) {
                // Cleanup the share if permission update is
                // unsuccessful.
                doDeleteShare(storage, args, smbFileShare);
                _log.info("NetAppClusterModeDevice doShare for {} with id {} - failed",
                        shareId, args.getFileObjId());
                return false;
            } else {
                smbFileShare.setNativeId(shareId);

                // share creation is successful,no need to set permission,clear the default one.
                List<CifsAcl> existingAcls = new ArrayList<CifsAcl>();
                CifsAcl defaultAcl = new CifsAcl();
                // By default NetApp share get everyone full access.
                defaultAcl.setUserName("everyone");
                defaultAcl.setAccess(CifsAccess.full);
                existingAcls.add(defaultAcl);
                ncApi.deleteCIFSShareAcl(smbFileShare.getName(), existingAcls);
                smbFileShare.setNetBIOSName(ncApi.getNetBiosName());
                _log.info("NetAppClusterModeDevice doShare for {} with id {} - complete",
                        shareId, args.getFileObjId());
                return true;
            }
        }
    }

    /**
     * modify NetAppC share with right permissions and other parameters
     * 
     * @param StorageSystem mount path of the fileshare
     * @param args containing input/out arguments of filedevice
     * @param smbFileShare existingShare smbFileshare object that needs to be modified.
     * @param forceGroup Name of the group the fileshare belongs.
     * @return
     */
    private Boolean modifyNtpShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare,
            String forceGroup, SMBFileShare existingShare) throws NetAppCException {
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();
        String shareId = smbFileShare.getPath();
        if (!ncApi.modifyShare(shareId, smbFileShare.getName(), smbFileShare.getDescription(),
                smbFileShare.getMaxUsers(), smbFileShare.getPermission(), forceGroup)) {
            _log.info(
                    "NetAppClusterModeDevice doShare (modification) for {} with id {} - failed",
                    shareId, args.getFileObjId());
            return false;
        } else {
            _log.info(
                    "NetAppClusterModeDevice doShare (modification) for {} with id {} - complete",
                    shareId, args.getFileObjId());
            return true;
        }
    }

    private Boolean netAppDeleteCIFSExports(StorageSystem storage,
            SMBShareMap currentShares, String portGroup) throws NetAppCException {

        int failedCount = 0;
        Iterator<Entry<String, SMBFileShare>> it = currentShares.entrySet()
                .iterator();

        List<String> removedShareKeys = new ArrayList<String>();

        while (it.hasNext()) {
            Map.Entry<String, SMBFileShare> entry = it.next();
            String key = entry.getKey();
            SMBFileShare smbFileShare = entry.getValue();
            NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword()).https(true).svm(portGroup).build();
            if (!ncApi.deleteShare(smbFileShare.getName())) {
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

    /**
     * create NetAppC snapshot path from file share path and snapshot name
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
     * create NetAppC snapshot path from file share path and snapshot name
     * 
     * @param fsPath mount path of the fileshare
     * @param name snapshot name
     * @return String
     */
    private String getSnapshotMountPath(String fsPath, String name) {
        return String.format("%1$s/.snapshot/%2$s",
                fsPath, name);
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {
        List<ShareACL> existingAcls = new ArrayList<ShareACL>();
        existingAcls = args.getExistingShareAcls();
        String portGroup = findSVMName(args.getFs());
        BiosCommandResult result = new BiosCommandResult();

        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();
        try {

            processAclsForShare(ncApi, args);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception e) {

            _log.error("NetAppClusterModeDevice::updateShareACLs failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUpdateCIFSShareAcl();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);

            // if delete or modify fails , revert to old acl
            rollbackShareACLs(storage, args, existingAcls);
        }

        return result;
    }

    private void processAclsForShare(NetAppClusterApi ncApi,
            FileDeviceInputOutput args) {
        String ShareName = args.getShareName();
        List<ShareACL> aclsToAdd = new ArrayList<ShareACL>();
        List<ShareACL> aclsToModify = new ArrayList<ShareACL>();
        List<ShareACL> aclsToDelete = new ArrayList<ShareACL>();
        aclsToAdd.addAll(args.getShareAclsToAdd());
        aclsToModify.addAll(args.getShareAclsToModify());
        aclsToDelete.addAll(args.getShareAclsToDelete());
        if (!aclsToAdd.isEmpty()) {
            updateShareAcl(ncApi, ShareName, aclsToAdd, AclOperation.ADD);
        }
        if (!aclsToModify.isEmpty()) {
            updateShareAcl(ncApi, ShareName, aclsToModify, AclOperation.MODIFY);
        }
        if (!aclsToDelete.isEmpty()) {
            updateShareAcl(ncApi, ShareName, aclsToDelete, AclOperation.DELETE);
        }

    }

    /**
     * updateShareAcl is method to perform add, modify and delete acl operation based
     * on the param action
     * 
     * @param ncApi
     * @param shareName
     * @param inputAcls
     * @param action
     */
    private void updateShareAcl(NetAppClusterApi ncApi, String shareName, List<ShareACL> inputAcls, AclOperation action) {

        if (inputAcls.isEmpty()) {
            return;
        }

        List<CifsAcl> acls = new ArrayList<CifsAcl>();
        for (ShareACL newAcl : inputAcls) {
            CifsAcl cif_new = new CifsAcl();
            String domain = newAcl.getDomain();

            String userOrGroup = newAcl.getGroup() == null ? newAcl.getUser() : newAcl.getGroup();

            if (domain != null && !domain.isEmpty()) {
                userOrGroup = domain + "\\" + userOrGroup;
            }

            cif_new.setUserName(userOrGroup);
            cif_new.setAccess(getAccessEnum(newAcl.getPermission()));
            acls.add(cif_new);
        }

        switch (action) {
            case ADD:
                ncApi.addCIFSShareAcl(shareName, acls);
                break;
            case MODIFY:
                ncApi.modifyCIFSShareAcl(shareName, acls);
                break;
            case DELETE:
                ncApi.deleteCIFSShareAcl(shareName, acls);
                break;

            case FORCE_ADD:

                for (CifsAcl cifsAcl : acls) {
                    try {
                        List<CifsAcl> singleACL = new ArrayList<CifsAcl>();
                        singleACL.add(cifsAcl);
                        ncApi.addCIFSShareAcl(shareName, singleACL);
                    } catch (Exception e) {

                        _log.error("NetAppClusterModeDevice:: Force add of ACL for user [" + cifsAcl.getUserName()
                                + "] failed with an Exception", e);
                    }
                }

                break;

            case FORCE_DELETE:
                for (CifsAcl cifsAcl : acls) {
                    try {
                        List<CifsAcl> singleACL = new ArrayList<CifsAcl>();
                        singleACL.add(cifsAcl);
                        ncApi.deleteCIFSShareAcl(shareName, singleACL);
                    } catch (Exception e) {

                        _log.error("NetAppClusterModeDevice:: Force delete of ACL for user [" + cifsAcl.getUserName()
                                + "] failed with an Exception", e);
                    }
                }

                break;

            default:
                throw new IllegalArgumentException(action + " is not a valid action for acl");

        }

    }

    private CifsAccess getAccessEnum(String permission) throws NetAppCException {

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

    private BiosCommandResult rollbackShareACLs(StorageSystem storage, FileDeviceInputOutput args,
            List<ShareACL> existingList) {

        BiosCommandResult result = new BiosCommandResult();
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        try {
            // We can have multiple ace added/modified in one put call ,some of them can fail due to some reason.
            // In case of failure, to make it consistent in vipr db and NetApp share, delete all currently
            // added and modified ace and revert it to old acl list
            _log.info("NetAppClusterModeDevice::Rolling back update ACL by trying delete ACL for share {}", args.getShareName());
            List<ShareACL> aclsToClear = new ArrayList<ShareACL>();
            aclsToClear.addAll(args.getShareAclsToAdd());
            aclsToClear.addAll(args.getShareAclsToModify());
            updateShareAcl(ncApi, args.getShareName(), aclsToClear, AclOperation.FORCE_DELETE);
            _log.info("NetAppClusterModeDevice::Adding back old ACL to Share {}", args.getShareName());
            updateShareAcl(ncApi, args.getShareName(), existingList, AclOperation.FORCE_ADD);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception e) {

            _log.error("NetAppClusterModeDevice::Roll Back of ACL failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToUpdateCIFSShareAcl();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);

        }

        return result;

    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        BiosCommandResult result = new BiosCommandResult();
        List<ShareACL> existingAcls = new ArrayList<ShareACL>();
        existingAcls = args.getExistingShareAcls();
        String portGroup = findSVMName(args.getFs());
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        try {
            updateShareAcl(ncApi, args.getShareName(), existingAcls, AclOperation.DELETE);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception e) {

            _log.error("NetAppClusterModeDevice::Delete All ACL failed with an Exception", e);
            ServiceError serviceError = DeviceControllerErrors.netappc.unableToDeleteCIFSShareAcl();
            serviceError.setMessage(e.getLocalizedMessage());
            result = BiosCommandResult.createErrorResult(serviceError);

        }
        return result;

    }

    private String constructQtreePath(String volumeName, String qtreeName) throws NetAppCException {

        String qtreePath = null;
        if (volumeName.contains(VOL_ROOT)) {
            if (volumeName.endsWith("/")) {
                // i.e. volume name is something like /vol/lookAtMe/
                qtreePath = volumeName + qtreeName;
            } else {
                // i.e. volume name is something like /vol/lookAtMe
                qtreePath = volumeName + "/" + qtreeName;
            }
        } else {
            // i.e. volume name is something like "lookAtMe"
            qtreePath = "/vol/" + volumeName + "/" + qtreeName;
        }

        _log.info("NetAppClusterApi::createQtree -> qtreePath = {}", qtreePath);
        return qtreePath;

    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.netappc.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.netappc.operationNotSupported());
    }
}
