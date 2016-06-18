/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.VNXeUtils;
import com.emc.storageos.vnxe.models.AccessEnum;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeFSSupportedProtocolEnum;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateFileSystemSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateShareJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeDeleteFileSystemSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeDeleteShareJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeExpandFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeExportFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeFSSnapshotTaskCompleter;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeFileTaskCompleter;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeModifyExportJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeRestoreFileSystemSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeUnexportFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityCreateFileSystemQuotaDirectoryJob;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityDeleteFileSystemQuotaDirectoryJob;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityQuotaDirectoryTaskCompleter;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityUpdateFileSystemQuotaDirectoryJob;

public class VNXUnityFileStorageDevice extends VNXUnityOperations
        implements FileStorageDevice {

    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityFileStorageDevice.class);

    private NameGenerator nameGenerator;

    public NameGenerator getNameGenerator() {
        return nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage,
            FileDeviceInputOutput fileInOut) throws ControllerException {
        _logger.info("creating file system: ", fileInOut.getFsName());
        Long fsSize = fileInOut.getFsCapacity();
        if (fsSize < 1) {
            // Invalid size throw an error
            _logger.error("doCreateFS failed : FileSystem size in bytes is not valid {}", fileInOut.getFsCapacity());
            ServiceError error = DeviceControllerErrors.vnxe.unableToCreateFileSystem("FileSystem size in bytes is not valid");
            return BiosCommandResult.createErrorResult(error);
        }
        VNXeFileTaskCompleter completer = null;
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        try {
            FileShare fs = fileInOut.getFs();
            URI port = fs.getStoragePort();
            if (port == null) {
                _logger.error("No storageport uri found in the fs");
                ServiceError error = DeviceControllerErrors.vnxe.unableToCreateFileSystem("No storageport uri found in the fs");
                return BiosCommandResult.createErrorResult(error);
            }
            StoragePort portObj = dbClient.queryObject(StoragePort.class, port);
            URI haDomainUri = portObj.getStorageHADomain();
            StorageHADomain haDomainObj = dbClient.queryObject(StorageHADomain.class, haDomainUri);
            StringSet protocols = fs.getProtocol();
            if (protocols.contains(StorageProtocol.File.NFS_OR_CIFS.name())) {
                /*
                 * the protocol is set to NFS_OR_CIFS, only if virtual pool's protocol is not set
                 * and the pool's protocol is set to NFS_OR_CIFS, since pool's protocol is set based on
                 * storageHADomain's protocol, setting the protocols to the selected StorageHADomain.
                 */
                protocols = haDomainObj.getFileSharingProtocols();
            }
            VNXeFSSupportedProtocolEnum protocolEnum = null;
            if (protocols.contains(StorageProtocol.File.NFS.name())
                    && protocols.contains(StorageProtocol.File.CIFS.name())) {
                protocolEnum = VNXeFSSupportedProtocolEnum.NFS_CIFS;
            } else if (protocols.contains(StorageProtocol.File.NFS.name())) {
                protocolEnum = VNXeFSSupportedProtocolEnum.NFS;
            } else if (protocols.contains(StorageProtocol.File.CIFS.name())) {
                protocolEnum = VNXeFSSupportedProtocolEnum.CIFS;
            } else {
                _logger.error("The protocol is not supported: " + protocols);
                ServiceError error = DeviceControllerErrors.vnxe.unableToCreateFileSystem("The protocol is not supported:" + protocols);
                return BiosCommandResult.createErrorResult(error);
            }
            job = apiClient.createFileSystem(fileInOut.getFsName(),
                    fsSize,
                    fileInOut.getPoolNativeId(),
                    haDomainObj.getSerialNumber(),
                    fileInOut.getThinProvision(),
                    protocolEnum);

            if (job != null) {
                _logger.info("opid:" + fileInOut.getOpId());
                completer = new VNXeFileTaskCompleter(FileShare.class, fileInOut.getFsId(), fileInOut.getOpId());
                if (fileInOut.getFs() == null) {
                    _logger.error("Could not find the fs object");
                }
                VNXeCreateFileSystemJob createFSJob = new VNXeCreateFileSystemJob(job.getId(), storage.getId(),
                        completer, fileInOut.getPoolId());
                ControllerServiceImpl.enqueueJob(new QueueJob(createFSJob));
            } else {
                _logger.error("No job returned from creatFileSystem");
                ServiceError error = DeviceControllerErrors.vnxe.unableToCreateFileSystem("No Job returned from createFileSystem");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("Create file system got an exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Create file system got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateFileSystem", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create filesystem job submitted - Array:%s, Pool:%s, fileSystem: %s", storage.getSerialNumber(),
                fileInOut.getPoolNativeId(), fileInOut.getFsName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public boolean doCheckFSExists(StorageSystem storage,
            FileDeviceInputOutput fileInOut) throws ControllerException {
        _logger.info("checking file system existence on array: ", fileInOut.getFsName());
        boolean isFSExists = true;
        try {
            String name = fileInOut.getFsName();
            VNXeApiClient apiClient = getVnxUnityClient(storage);
            VNXeFileSystem fs = apiClient.getFileSystemByFSName(name);
            if (fs != null && (fs.getName().equals(name))) {
                isFSExists = true;
            } else {
                isFSExists = false;
            }
        } catch (Exception e) {
            _logger.error("Querying File System failed with exception:", e);
        }
        return isFSExists;
    }

    /*
     * To get around the KH API delete file system async issues, using sync call for now.
     */
    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage,
            FileDeviceInputOutput fileInOut) throws ControllerException {
        _logger.info("deleting file system: ", fileInOut.getFsName());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        BiosCommandResult result = null;
        try {
            apiClient.deleteFileSystemSync(fileInOut.getFsNativeId(), fileInOut.getForceDelete());
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Deleted filesystem - Array:%s, fileSystem: %s", storage.getSerialNumber(),
                    fileInOut.getFsName()));
            _logger.info(logMsgBuilder.toString());
            result = BiosCommandResult.createSuccessfulResult();

        } catch (VNXeException e) {
            _logger.error("Delete file system got the exception", e);
            result = BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Delete file system got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", ex.getMessage());
            result = BiosCommandResult.createErrorResult(error);
        }
        return result;

    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage,
            FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {

        _logger.info("exporting the file system: " + args.getFsName());
        if (args.getFileObjExports() == null || args.getFileObjExports().isEmpty()) {
            args.initFileObjExports();

        }

        for (FileExport exp : exportList) {
            VNXeApiClient apiClient = getVnxUnityClient(storage);
            String fsId = args.getFs().getNativeId();
            String permission = exp.getPermissions();

            String path = "/";
            String subdirName = "";
            String mountPathArg = exp.getMountPath();
            String comments = exp.getComments();
            VNXeCommandJob job = null;
            VNXeFileTaskCompleter completer = null;
            String exportKey = exp.getFileExportKey();
            FileShareExport newExport = new FileShareExport(exp);

            try {
                AccessEnum access = null;
                List<String> roClients = null;
                List<String> rwClients = null;
                List<String> rootClients = null;
                FileExport existingExport = null;
                if (args.getFileOperation()) {

                    FSExportMap exportMap = args.getFileObjExports();
                    existingExport = exportMap.get(exportKey);
                } else {
                    FSExportMap exportMap = args.getSnapshotExports();
                    existingExport = exportMap.get(exportKey);
                }
                if (existingExport != null) {
                    if (permission.equalsIgnoreCase(FileShareExport.Permissions.rw.name())) {
                        access = AccessEnum.READWRITE;
                        if (existingExport.getClients() != null && !existingExport.getClients().isEmpty()) {
                            if (rwClients == null) {
                                rwClients = new ArrayList<String>();
                            }
                            rwClients.addAll(existingExport.getClients());
                        }
                    } else if (permission.equalsIgnoreCase(FileShareExport.Permissions.ro.name())) {
                        access = AccessEnum.READ;
                        if (existingExport.getClients() != null && !existingExport.getClients().isEmpty()) {
                            if (roClients == null) {
                                roClients = new ArrayList<String>();
                            }
                            roClients.addAll(existingExport.getClients());
                        }
                    } else if (permission.equalsIgnoreCase(FileShareExport.Permissions.root.name())) {
                        access = AccessEnum.ROOT;
                        if (existingExport.getClients() != null && !existingExport.getClients().isEmpty()) {
                            if (rootClients == null) {
                                rootClients = new ArrayList<String>();
                            }
                            rootClients.addAll(existingExport.getClients());
                        }
                    }
                }

                if (permission.equalsIgnoreCase(FileShareExport.Permissions.rw.name())) {
                    access = AccessEnum.READWRITE;
                    if (exp.getClients() != null && !exp.getClients().isEmpty()) {
                        if (rwClients == null) {
                            rwClients = new ArrayList<String>();
                        }
                        rwClients.addAll(exp.getClients());
                    }
                } else if (permission.equalsIgnoreCase(FileShareExport.Permissions.ro.name())) {
                    access = AccessEnum.READ;
                    if (exp.getClients() != null && !exp.getClients().isEmpty()) {
                        if (roClients == null) {
                            roClients = new ArrayList<String>();
                        }
                        roClients.addAll(exp.getClients());
                    }
                } else if (permission.equalsIgnoreCase(FileShareExport.Permissions.root.name())) {
                    access = AccessEnum.ROOT;
                    if (exp.getClients() != null && !exp.getClients().isEmpty()) {
                        if (rootClients == null) {
                            rootClients = new ArrayList<String>();
                        }
                        rootClients.addAll(exp.getClients());
                    }
                }

                if (args.getFileOperation()) {
                    String mountPathFs = args.getFsMountPath();

                    if (!mountPathArg.equals(mountPathFs)) {
                        // subdirectory specified.
                        subdirName = mountPathArg.substring(mountPathFs.length() + 1);
                        path += subdirName;
                    }

                    String shareName = VNXeUtils.buildNfsShareName(fsId, subdirName);

                    job = apiClient.exportFileSystem(fsId, roClients, rwClients, rootClients, access, path, shareName, null, comments);
                    if (job != null) {
                        completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());

                        VNXeExportFileSystemJob exportFSJob = new VNXeExportFileSystemJob(job.getId(), storage.getId(),
                                completer, newExport, shareName, true);
                        ControllerServiceImpl.enqueueJob(new QueueJob(exportFSJob));
                    } else {
                        _logger.error("No job returned from exportFileSystem");
                        ServiceError error = DeviceControllerErrors.vnxe.jobFailed("exportFileSystem",
                                "No Job returned from exportFileSystem");
                        return BiosCommandResult.createErrorResult(error);
                    }
                } else {
                    String snapId = args.getSnapNativeId();
                    String shareName = VNXeUtils.buildNfsShareName(snapId, path);
                    job = apiClient.createNfsShareForSnap(snapId, roClients, rwClients, rootClients, access, path, shareName, comments);
                    if (job != null) {
                        completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());

                        VNXeExportFileSystemJob exportFSJob = new VNXeExportFileSystemJob(job.getId(), storage.getId(),
                                completer, newExport, shareName, false);
                        ControllerServiceImpl.enqueueJob(new QueueJob(exportFSJob));
                    } else {
                        _logger.error("No job returned from exportFileSystem");
                        ServiceError error = DeviceControllerErrors.vnxe.jobFailed("exportFileSystem",
                                "No Job returned from exportFileSystem");
                        return BiosCommandResult.createErrorResult(error);
                    }
                }
            } catch (VNXeException e) {
                _logger.error("Export file system got the exception", e);
                if (completer != null) {
                    completer.error(dbClient, e);
                }
                return BiosCommandResult.createErrorResult(e);
            } catch (Exception ex) {
                _logger.error("export file system got the exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("exportFileSystem", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
                return BiosCommandResult.createErrorResult(error);
            }
            _logger.info("Export job submitted");
        }
        return BiosCommandResult.createPendingResult();

    }

    @Override
    public BiosCommandResult doShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare) throws ControllerException {
        _logger.info("creating smbShare: " + smbFileShare.getName());

        VNXeApiClient apiClient = getVnxUnityClient(storage);
        String permission = smbFileShare.getPermission();
        String shareName = smbFileShare.getName();
        String path = "/";
        VNXeCommandJob job = null;
        VNXeFileTaskCompleter completer = null;
        FileSMBShare newShare = new FileSMBShare(smbFileShare);
        String absolutePath = smbFileShare.getPath();
        newShare.setStoragePortNetworkId(smbFileShare.getStoragePortNetworkId());
        newShare.setStoragePortName(smbFileShare.getStoragePortName());

        try {
            if (args.getFileOperation()) {

                if (newShare.isSubDirPath()) {
                    String basePath = args.getFsPath();
                    /*
                     * The below line will allow us to get the relative path of subdir
                     * For example: absolutePath = /vnxeShare1/subdir1
                     * Then, the below line will assign path = subdir
                     * VNXe takes the relative path of the sub-directory. Not the absolute path
                     */
                    path = "/" + new File(basePath).toURI().relativize(new File(absolutePath).toURI()).getPath();
                }

                String fsNativeId = args.getFs().getNativeId();
                _logger.info("Creating CIFS share for path {}", path);
                job = apiClient.createCIFSShare(fsNativeId, shareName, permission, path);
                if (job != null) {
                    newShare.setNetBIOSName(apiClient.getNetBios());
                    completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                    VNXeCreateShareJob createShareJob = new VNXeCreateShareJob(job.getId(), storage.getId(),
                            completer, newShare, true);
                    ControllerServiceImpl.enqueueJob(new QueueJob(createShareJob));
                } else {
                    _logger.error("No job returned from creaetCifsShare");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("createShare", "No Job returned");
                    return BiosCommandResult.createErrorResult(error);
                }
            } else {
                // create share for a snapshot
                if (newShare.isSubDirPath()) {
                    String basePath = args.getSnapshotPath();
                    /*
                     * The below line will allow us to get the relative path of subdir
                     * For example: absolutePath = /vnxeShare1/subdir1
                     * Then, the below line will assign path = subdir
                     * VNXe takes the relative path of the sub-directory. Not the absolute path
                     */
                    path = "/" + new File(basePath).toURI().relativize(new File(absolutePath).toURI()).getPath();
                }
                String fsNativeId = args.getFs().getNativeId();
                String snapId = args.getSnapNativeId();
                job = apiClient.createCifsShareForSnap(snapId, shareName, permission, path, fsNativeId);
                if (job != null) {
                    newShare.setNetBIOSName(apiClient.getNetBios());
                    completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());

                    VNXeCreateShareJob createShareJob = new VNXeCreateShareJob(job.getId(), storage.getId(),
                            completer, newShare, false);
                    ControllerServiceImpl.enqueueJob(new QueueJob(createShareJob));
                } else {
                    _logger.error("No job returned from creaetCifsShare");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("createShare", "No Job returned");
                    return BiosCommandResult.createErrorResult(error);
                }

            }
        } catch (VNXeException e) {
            _logger.error("Create share got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("create share got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("create share", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create share job submitted - Array:%s, share: %s", storage.getSerialNumber(),
                smbFileShare.getName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare) throws ControllerException {
        _logger.info(String.format(String.format("Deleting smbShare: %s, nativeId: %s",
                smbFileShare.getName(), smbFileShare.getNativeId())));

        VNXeApiClient apiClient = getVnxUnityClient(storage);

        String shareId = smbFileShare.getNativeId();
        VNXeCommandJob job = null;
        VNXeFileTaskCompleter completer = null;
        boolean isFile = args.getFileOperation();
        FileSMBShare newShare = new FileSMBShare(smbFileShare);
        try {
            if (isFile) {
                String fsId = args.getFs().getNativeId();
                job = apiClient.removeCifsShare(shareId, fsId);
            } else {
                job = apiClient.deleteCifsShareForSnapshot(shareId);
            }
            if (job != null) {
                if (isFile) {
                    completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                } else {
                    completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                }
                VNXeDeleteShareJob deleteShareJob = new VNXeDeleteShareJob(job.getId(), storage.getId(),
                        completer, newShare, isFile);
                ControllerServiceImpl.enqueueJob(new QueueJob(deleteShareJob));
            } else {
                _logger.error("No job returned from deleteCifsShare");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("deleteShare", "No Job returned");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (VNXeException e) {
            _logger.error("Create share got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("delete share got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("create share", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete share job submitted - Array:%s, share: %s", storage.getSerialNumber(),
                smbFileShare.getName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        return null;
    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage,
            FileDeviceInputOutput args, List<FileExport> exportList) throws ControllerException {
        _logger.info("unexporting the file system: " + args.getFsName());

        boolean isFile = args.getFileOperation();
        for (FileExport exp : exportList) {
            VNXeApiClient apiClient = getVnxUnityClient(storage);
            String vnxeShareId = exp.getIsilonId();
            VNXeCommandJob job = null;
            VNXeFileTaskCompleter completer = null;

            try {
                if (isFile) {
                    String fsId = args.getFs().getNativeId();
                    job = apiClient.removeNfsShare(vnxeShareId, fsId);
                } else {
                    job = apiClient.deleteNfsShareForSnapshot(vnxeShareId);
                }
                if (job != null) {
                    if (isFile) {
                        completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                    } else {
                        completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                    }

                    FileShareExport export = new FileShareExport(exp);
                    VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                            completer, export, export.getPath(), isFile);
                    ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                } else {
                    _logger.error("No job returned from exportFileSystem");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", "No Job returned from deleteFileSystem");
                    return BiosCommandResult.createErrorResult(error);
                }
            } catch (VNXeException e) {
                _logger.error("Unexport file system got the exception", e);
                if (completer != null) {
                    completer.error(dbClient, e);
                }
                return BiosCommandResult.createErrorResult(e);
            } catch (Exception ex) {
                _logger.error("Delete file system got the exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
                return BiosCommandResult.createErrorResult(error);
            }
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Unexport filesystem job submitted - Array:%s, fileSystem: %s", storage.getSerialNumber(),
                    args.getFsName()));
            _logger.info(logMsgBuilder.toString());
        }
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(false);
        result.setCommandStatus(Operation.Status.error.name());
        result.setMessage("Modify FS NOT supported for VNXe.");
        return result;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _logger.info("expanding file system: ", args.getFsName());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        VNXeFileTaskCompleter completer = null;
        try {
            job = apiClient.expandFileSystem(args.getFsNativeId(), args.getNewFSCapacity());
            if (job != null) {
                completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                VNXeExpandFileSystemJob expandFSJob = new VNXeExpandFileSystemJob(job.getId(), storage.getId(),
                        completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(expandFSJob));
            } else {
                _logger.error("No job returned from expandFileSystem");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                        "expandFileSystem", "No Job returned from expandFileSystem");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("Expand file system got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Expand file system got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("ExpandFileSystem", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Expand filesystem job submitted - Array:%s, fileSystem: %s, new size: %d", storage.getSerialNumber(),
                args.getFsName(), args.getNewFSCapacity()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _logger.info("creating file system {} snap {} ", args.getFsName(), args.getSnapshotLabel());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        VNXeFSSnapshotTaskCompleter completer = null;
        try {
            job = apiClient.createFileSystemSnap(args.getFsNativeId(), args.getSnapshotName());
            if (job != null) {
                completer = new VNXeFSSnapshotTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                VNXeCreateFileSystemSnapshotJob snapJob = new VNXeCreateFileSystemSnapshotJob(job.getId(), storage.getId(),
                        completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(snapJob));
            } else {
                _logger.error("No job returned from createFileSystemSnap");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                        "snapshotFileSystem", "No Job returned from createFileSystemSnap");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("Create file system snapshot got an exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Create file system snapshot got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateFileSystemSnapshot", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create filesystem snapshot job submitted - Array:%s, fileSystem: %s", storage.getSerialNumber(),
                args.getFsName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _logger.info("restoring file system {} snap {} ", args.getFsName(), args.getSnapshotLabel());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        VNXeFSSnapshotTaskCompleter completer = null;
        try {
            job = apiClient.restoreFileSystemSnap(args.getSnapNativeId());
            if (job != null) {
                completer = new VNXeFSSnapshotTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                VNXeRestoreFileSystemSnapshotJob snapJob = new VNXeRestoreFileSystemSnapshotJob(job.getId(), storage.getId(),
                        completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(snapJob));
            } else {
                _logger.error("No job returned from restoreFileSystemSnap");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                        "restoreSnapshotFileSystem", "No Job returned from restoreFileSystemSnap");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("Restore file system snapshot got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Restore file system snpashot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("RestoreFileSystemSnapshot", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Restore filesystem snapshot job submitted - Array:%s, fileSystem: %s, snapshot: %s",
                storage.getSerialNumber(), args.getFsName(), args.getSnapshotLabel()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _logger.info("Deleting file system {} snapshot {} ", args.getFsName(), args.getSnapshotLabel());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        VNXeFileTaskCompleter completer = null;
        try {
            job = apiClient.deleteFileSystemSnap(args.getSnapNativeId());
            if (job != null) {
                completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                VNXeDeleteFileSystemSnapshotJob snapJob = new VNXeDeleteFileSystemSnapshotJob(job.getId(), storage.getId(),
                        completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(snapJob));
            } else {
                _logger.error("No job returned from deleteFileSystemSnap");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                        "snapshotFileSystem", "No Job returned from deleteFileSystemSnap");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (VNXeException e) {
            _logger.error("Delete file system snapshot got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Delete file system snpashot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystemSnapshot", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete filesystem snapshot job submitted - Array:%s, fileSystem: %s, snapshot: %s", storage.getSerialNumber(),
                args.getFsName(), args.getSnapshotName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public void doConnect(StorageSystem storage) throws ControllerException {
        try {
            _logger.info("doConnect {} - start", storage.getId());
            VNXeApiClient client = getVnxUnityClient(storage);
            client.getStorageSystem();
            String msg = String.format("doConnect %1$s - complete", storage.getId());
            _logger.info(msg);
        } catch (VNXeException e) {
            _logger.error("doConnect failed.", e);
            throw DeviceControllerException.exceptions.connectStorageFailed(e);
        }
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        try {
            _logger.info("doConnect {} - start", storage.getId());
            VNXeApiClient client = getVnxUnityClient(storage);
            client.logout();
            String msg = String.format("doDisconnect %1$s - complete", storage.getId());
            _logger.info(msg);

        } catch (VNXeException e) {
            _logger.error("doDisconnect failed.", e);
            throw DeviceControllerException.exceptions.disconnectStorageFailed(e);
        }
    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput fd, List<String> snapshots) throws ControllerException {
        _logger.info("getFSSnapshotList {} - start", fd.getFsId());
        VNXeApiClient client = getVnxUnityClient(storage);
        try {

            List<VNXeFileSystemSnap> snaps = client.getFileSystemSnaps(fd.getFsNativeId());
            for (VNXeFileSystemSnap snap : snaps) {
                snapshots.add(snap.getName());
            }
            return BiosCommandResult.createSuccessfulResult();
        } catch (VNXeException e) {
            _logger.error("getFSSnapshotList failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
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
        List<ExportRule> newExportsForDelete = new ArrayList<>();
        VNXeFileTaskCompleter completer = null;

        VNXeApiClient apiClient = getVnxUnityClient(storage);

        String subDir = args.getSubDirectory();

        // ALL EXPORTS
        List<ExportRule> exportsToprocess = args.getExistingDBExportRules();
        Map<String, ArrayList<ExportRule>> existingExportsMapped = new HashMap();

        try {
            String exportPath;

            if (!args.getFileOperation()) {
                exportPath = args.getSnapshotPath();
                if (subDir != null
                        && subDir.length() > 0) {
                    exportPath = args.getSnapshotPath() + "/"
                            + subDir;
                }

            } else {
                exportPath = args.getFs().getPath();
                if (subDir != null
                        && subDir.length() > 0) {
                    exportPath = args.getFs().getPath() + "/"
                            + subDir;
                }
            }

            _logger.info("exportPath : {}", exportPath);
            args.setExportPath(exportPath);

            if (exportsToprocess == null) {
                exportsToprocess = new ArrayList<>();
            }
            _logger.info("Number of existng Rules found {}", exportsToprocess.size());

            // Process Exports
            for (ExportRule existingRule : exportsToprocess) {
                ArrayList<ExportRule> exps = existingExportsMapped.get(existingRule.getExportPath());
                if (exps == null) {
                    exps = new ArrayList<>();
                }
                exps.add(existingRule);
                _logger.info("Checking existing export for {} : exps : {}", existingRule.getExportPath(), exps);
                existingExportsMapped.put(existingRule.getExportPath(), exps);
            }

            // Handle Add export Rules
            if (exportAdd != null && !exportAdd.isEmpty()) {
                // Check for existing exports for the export path including subdirectory
                ArrayList<ExportRule> exps = existingExportsMapped.get(exportPath);
                if (exps != null && !exps.isEmpty()) {
                    _logger.error("Adding export rules is not supported as there can be only one export rule for VNX Unity.");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("updateExportRules",
                            "Adding export rule is not supported for VNX unity");
                    return BiosCommandResult.createErrorResult(error);
                }
            }

            // Handle Modified export Rules
            if (!exportsToprocess.isEmpty()) {

                if (subDir != null && !subDir.isEmpty()) {
                    for (ExportRule existingRule : exportsToprocess) {
                        if (existingRule.getExportPath().endsWith("/" + subDir)) {
                            // Filter for a specific Sub Directory export
                            _logger.info(
                                    "Updating all subdir exports rules at ViPR and  sub directory export at device {}",
                                    subDir);
                            processModifyRules(exportModify, existingRule, exportsToRemove, exportsToAdd);
                        } else {
                            exportsToRemove.add(existingRule);
                        }
                    }

                    // Handle Delete export Rules
                    if (exportDelete != null && !exportDelete.isEmpty()) {
                        for (ExportRule existingRule : exportsToprocess) {
                            if (existingRule.getExportPath().endsWith("/" + subDir)) {
                                processDeleteRules(exportDelete, existingRule, exportsToRemove, newExportsForDelete);
                            } else {
                                exportsToRemove.add(existingRule);
                            }
                        }
                        exportsToAdd.addAll(newExportsForDelete);
                    }
                } else {
                    for (ExportRule existingRule : exportsToprocess) {
                        if (existingRule.getExportPath().equalsIgnoreCase(exportPath)) {
                            processModifyRules(exportModify, existingRule, exportsToRemove, exportsToAdd);
                        } else {
                            exportsToRemove.add(existingRule);
                        }
                    }

                    // Handle Delete export Rules
                    if (exportDelete != null && !exportDelete.isEmpty()) {
                        for (ExportRule existingRule : exportsToprocess) {
                            if (existingRule.getExportPath().equalsIgnoreCase(exportPath)) {
                                processDeleteRules(exportDelete, existingRule, exportsToRemove, newExportsForDelete);
                            } else {
                                exportsToRemove.add(existingRule);
                            }
                        }
                        exportsToAdd.addAll(newExportsForDelete);
                    }
                }

                // No of exports found to remove from the list
                _logger.info("No of exports found to remove from the existing exports list {}", exportsToRemove.size());
                exportsToprocess.removeAll(exportsToRemove);
                _logger.info("No of exports found to add to the existing exports list {}", exportsToAdd.size());
                exportsToprocess.addAll(exportsToAdd);
            } else {
                // Handle Add Export Rules
                // This is valid only if no rules to modify exists
                if (exportAdd != null && !exportAdd.isEmpty()) {
                    for (ExportRule newExport : exportAdd) {
                        if (args.getFileObjExports() != null) {
                            Collection<FileExport> expList = args.getFileObjExports().values();
                            Iterator<FileExport> it = expList.iterator();
                            FileExport exp = null;

                            while (it.hasNext()) {
                                FileExport export = it.next();
                                if (export.getPath().equalsIgnoreCase(exportPath)) {
                                    exp = export;
                                }
                            }
                            if (exp != null) {
                                if (exp.getIsilonId() != null) {
                                    newExport.setDeviceExportId(exp.getIsilonId());
                                }
                                if (exp.getNativeId() != null) {
                                    newExport.setDeviceExportId(exp.getNativeId());
                                }
                            }
                        }
                        _logger.info("Add Export Rule {}", newExport);
                        newExport.setExportPath(exportPath);
                        exportsToAdd.add(newExport);
                    }
                }
                exportsToprocess.addAll(exportsToAdd);
            }

            _logger.info("exportPath : {}", exportPath);
            args.setExportPath(exportPath);
            VNXeCommandJob job = null;
            for (ExportRule rule : exportsToprocess) {
                AccessEnum access = null;
                List<String> roHosts = new ArrayList<String>();
                List<String> rwHosts = new ArrayList<String>();
                List<String> rootHosts = new ArrayList<String>();
                String path = "/";
                String subdirName = "";
                String mountPathFs = args.getFsMountPath();
                String shareName = null;
                FileShareExport fsExport = null;
                boolean isDeleteRule = false;

                if (args.getFileObjExports() != null) {
                    Collection<FileExport> expList = args.getFileObjExports().values();
                    Iterator<FileExport> it = expList.iterator();
                    FileExport exp = null;

                    while (it.hasNext()) {
                        FileExport export = it.next();
                        if (export.getPath().equalsIgnoreCase(rule.getExportPath())) {
                            exp = export;
                        }
                    }
                    fsExport = new FileShareExport(exp);
                }
                String mountPathArg = rule.getExportPath();

                if (rule.getReadWriteHosts() != null && !rule.getReadWriteHosts().isEmpty()) {
                    access = AccessEnum.READWRITE;
                    rwHosts.addAll(rule.getReadWriteHosts());
                }
                if (rule.getReadOnlyHosts() != null && !rule.getReadOnlyHosts().isEmpty()) {
                    access = AccessEnum.READ;
                    roHosts.addAll(rule.getReadOnlyHosts());
                }
                if (rule.getRootHosts() != null && !rule.getRootHosts().isEmpty()) {
                    access = AccessEnum.ROOT;
                    rootHosts.addAll(rule.getRootHosts());
                }

                if (newExportsForDelete.contains(rule)) {
                    isDeleteRule = true;
                }
                if (args.getFileOperation()) {
                    if (!mountPathArg.equals(mountPathFs)) {
                        // subdirectory specified.
                        subdirName = mountPathArg.substring(mountPathFs.length() + 1);
                        path += subdirName;
                    }
                    if (isDeleteRule) {
                        job = apiClient.removeNfsShare(rule.getDeviceExportId(), args.getFs().getNativeId());
                        if (job != null) {
                            completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                            VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                                    completer, fsExport, args.getExportPath(), true);
                            ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                        } else {
                            _logger.error("No job returned from unexport FileSystem");
                            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("UnExportFileSystem",
                                    "No Job returned from UnExportFileSystem");
                            return BiosCommandResult.createErrorResult(error);
                        }

                    } else {
                        job = apiClient.exportFileSystem(args.getFs().getNativeId(), roHosts, rwHosts, rootHosts, access, path, null,
                                rule.getDeviceExportId(), null);
                        if (job != null) {
                            completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                            VNXeModifyExportJob modifyExportJob = new VNXeModifyExportJob(job.getId(), storage.getId(),
                                    completer, rule, fsExport, args.getExportPath(), args.getFileOperation(), isDeleteRule, shareName);
                            ControllerServiceImpl.enqueueJob(new QueueJob(modifyExportJob));
                        } else {
                            _logger.error("No job returned from updateExportRules");
                            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("updateExportRules",
                                    "No Job returned from updateExportRules");
                            return BiosCommandResult.createErrorResult(error);
                        }
                    }
                } else {

                    shareName = VNXeUtils.buildNfsShareName(args.getSnapNativeId(), path);

                    if (isDeleteRule) {
                        job = apiClient.deleteNfsShareForSnapshot(rule.getDeviceExportId());
                        if (job != null) {
                            completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                            VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                                    completer, fsExport, rule.getExportPath(), false);
                            ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                        } else {
                            _logger.error("No job returned from unexportFileSystem Snapshot");
                            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("UnExportFileSystem",
                                    "No Job returned from UnExportFileSystem");
                            return BiosCommandResult.createErrorResult(error);
                        }
                    } else {
                        job = apiClient.createNfsShareForSnap(args.getSnapNativeId(), roHosts, rwHosts, rootHosts, access, path, shareName,
                                null);
                        if (job != null) {
                            completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                            VNXeModifyExportJob modifyExportJob = new VNXeModifyExportJob(job.getId(), storage.getId(),
                                    completer, rule, fsExport, args.getExportPath(), args.getFileOperation(), isDeleteRule, shareName);
                            ControllerServiceImpl.enqueueJob(new QueueJob(modifyExportJob));
                        } else {
                            _logger.error("No job returned from updateExportRules");
                            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("updateExportRules",
                                    "No Job returned from updateExportRules");
                            return BiosCommandResult.createErrorResult(error);
                        }
                    }
                }
            }
        } catch (VNXeException e) {
            _logger.error("updateExportRules got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("updateExportRules got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("updateExportRules", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        _logger.info("UpdateExportRules job submitted");
        return BiosCommandResult.createPendingResult();
    }

    private void processModifyRules(List<ExportRule> exportModify, ExportRule existingRule,
            List<ExportRule> exportsToRemove, List<ExportRule> exportsToAdd) {
        for (ExportRule modifiedrule : exportModify) {
            if (modifiedrule.getSecFlavor().equals(
                    existingRule.getSecFlavor())) {
                _logger.info("Modifying Export Rule from {}, To {}",
                        existingRule, modifiedrule);
                // use a separate list to avoid concurrent modification exception for now.
                exportsToRemove.add(existingRule);
                ExportRule modifyRule = new ExportRule();
                copyPropertiesToSave(modifyRule, modifiedrule);
                modifyRule.setDeviceExportId(existingRule.getDeviceExportId());
                modifyRule.setExportPath(existingRule.getExportPath());
                exportsToAdd.add(modifyRule);
                break;
            }
        }
    }

    private void processDeleteRules(List<ExportRule> exportDelete, ExportRule existingRule,
            List<ExportRule> exportsToRemove, List<ExportRule> exportsToAdd) {
        for (ExportRule oldExport : exportDelete) {
            if (oldExport.getSecFlavor().equals(
                    existingRule.getSecFlavor())) {
                _logger.info("Deleting Export Rule {}", existingRule);
                exportsToRemove.add(existingRule);
                ExportRule rule = new ExportRule();
                rule.setSecFlavor("sys");
                rule.setAnon("root");
                rule.setExportPath(existingRule.getExportPath());
                rule.setDeviceExportId(existingRule.getDeviceExportId());
                exportsToAdd.add(rule);
                break;
            }
        }
    }

    private void copyPropertiesToSave(ExportRule dest, ExportRule orig) {

        _logger.info("Origin {}", orig.toString());

        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        if (orig.getReadOnlyHosts() != null && !orig.getReadOnlyHosts().isEmpty()) {
            dest.setReadOnlyHosts(new StringSet(orig.getReadOnlyHosts()));
            _logger.info("Read Only Hosts {}", dest.getReadOnlyHosts());
        }
        if (orig.getReadWriteHosts() != null && !orig.getReadWriteHosts().isEmpty()) {
            dest.setReadWriteHosts(new StringSet(orig.getReadWriteHosts()));
            _logger.info("Read Write Hosts {}", dest.getReadWriteHosts());
        }
        if (orig.getRootHosts() != null && !orig.getRootHosts().isEmpty()) {
            dest.setRootHosts(new StringSet(orig.getRootHosts()));
            _logger.info("Root hosts {}", dest.getRootHosts());
        }
        _logger.info("Dest After {}", dest.toString());
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) {

        List<ExportRule> allExports = args.getExistingDBExportRules();
        String subDir = args.getSubDirectory();
        boolean allDirs = args.isAllDir();
        VNXeCommandJob job = null;
        VNXeFileTaskCompleter completer = null;
        boolean isFile = args.getFileOperation();
        boolean ifRulePresent = false;

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

        _logger.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        _logger.info("Number of existing exports found {}", allExports.size());

        try {

            VNXeApiClient apiClient = getVnxUnityClient(storage);

            if (allDirs) {
                // ALL EXPORTS
                _logger.info(
                        "Deleting all exports specific to filesystem at device and rules from DB including sub dirs rules and exports");
                for (ExportRule rule : allExports) {
                    FileShareExport fsExport = null;
                    if (args.getFileObjExports() != null) {
                        Collection<FileExport> expList = args.getFileObjExports().values();
                        Iterator<FileExport> it = expList.iterator();
                        FileExport exp = null;
                        while (it.hasNext()) {
                            FileExport export = it.next();
                            if (export.getPath().equalsIgnoreCase(rule.getExportPath())) {
                                exp = export;
                            }
                        }
                        fsExport = new FileShareExport(exp);
                    }
                    String vnxeShareId = rule.getDeviceExportId();
                    _logger.info("Delete UnityExport id {} for path {}",
                            rule.getDeviceExportId(), rule.getExportPath());
                    if (isFile) {
                        String fsId = args.getFs().getNativeId();
                        job = apiClient.removeNfsShare(vnxeShareId, fsId);
                    } else {
                        job = apiClient.deleteNfsShareForSnapshot(vnxeShareId);
                    }
                    if (job != null) {
                        if (isFile) {
                            completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                        } else {
                            completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                        }

                        VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                                completer, fsExport, rule.getExportPath(), isFile);
                        ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                    } else {
                        _logger.error("No job returned from exportFileSystem");
                        ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem",
                                "No Job returned from deleteFileSystem");
                        return BiosCommandResult.createErrorResult(error);
                    }
                }
                FileShareExport fsExport = null;
                if (args.getFileObjExports() != null) {
                    Collection<FileExport> expList = args.getFileObjExports().values();
                    Iterator<FileExport> it = expList.iterator();
                    FileExport exp = null;
                    while (it.hasNext()) {
                        exp = it.next();
                        fsExport = new FileShareExport(exp);
                        if (fsExport != null) {
                            String vnxeShareId = fsExport.getIsilonId();
                            _logger.info("Delete UnityExport id {} for path {}",
                                    vnxeShareId, fsExport.getPath());
                            if (isFile) {
                                String fsId = args.getFs().getNativeId();
                                job = apiClient.removeNfsShare(vnxeShareId, fsId);
                            } else {
                                job = apiClient.deleteNfsShareForSnapshot(vnxeShareId);
                            }
                            if (job != null) {
                                if (isFile) {
                                    completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                                } else {
                                    completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                                }

                                VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                                        completer, fsExport, fsExport.getPath(), isFile);
                                ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                            } else {
                                _logger.error("No job returned from exportFileSystem");
                                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem",
                                        "No Job returned from deleteFileSystem");
                                return BiosCommandResult.createErrorResult(error);
                            }
                        }
                    }
                }
            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _logger.info("Deleting all subdir exports rules at ViPR and  sub directory export at device {}", subDir);
                FileShareExport fsExport = null;
                String vnxeShareId = null;
                if (args.getFileObjExports() != null) {
                    Collection<FileExport> expList = args.getFileObjExports().values();
                    Iterator<FileExport> it = expList.iterator();
                    FileExport exp = null;
                    while (it.hasNext()) {
                        FileExport export = it.next();
                        if (export.getPath().equalsIgnoreCase(subDirExportPath)) {
                            exp = export;
                            break;
                        }
                    }
                    fsExport = new FileShareExport(exp);

                }
                for (ExportRule rule : allExports) {
                    _logger.info("Delete UnityExport id for path {} f containing subdirectory {}",
                            rule.getDeviceExportId() + ":" + rule.getExportPath(), subDir);

                    if (rule.getExportPath().equalsIgnoreCase(subDirExportPath)) {
                        ifRulePresent = true;
                        vnxeShareId = rule.getDeviceExportId();
                    }
                }
                if (!ifRulePresent) {
                    if (fsExport != null) {
                        vnxeShareId = fsExport.getIsilonId();
                    }
                }
                _logger.info("Delete UnityExport id {} for path {}",
                        vnxeShareId, subDirExportPath);

                if (isFile) {
                    String fsId = args.getFs().getNativeId();
                    job = apiClient.removeNfsShare(vnxeShareId, fsId);
                } else {
                    job = apiClient.deleteNfsShareForSnapshot(vnxeShareId);
                }
                if (job != null) {
                    if (isFile) {
                        completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                    } else {
                        completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                    }

                    VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                            completer, fsExport, subDirExportPath, isFile);
                    ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                } else {
                    _logger.error("No job returned from exportFileSystem");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", "No Job returned from deleteFileSystem");
                    return BiosCommandResult.createErrorResult(error);
                }

            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                _logger.info("Deleting all export rules  from DB and export at device not included sub dirs");
                FileShareExport fsExport = null;
                String vnxeShareId = null;
                if (args.getFileObjExports() != null) {
                    Collection<FileExport> expList = args.getFileObjExports().values();
                    Iterator<FileExport> it = expList.iterator();
                    FileExport exp = null;
                    while (it.hasNext()) {
                        FileExport export = it.next();
                        if (export.getPath().equalsIgnoreCase(exportPath)) {
                            exp = export;
                            break;
                        }
                    }
                    fsExport = new FileShareExport(exp);
                }
                for (ExportRule rule : allExports) {
                    if (rule.getExportPath().equalsIgnoreCase(exportPath)) {
                        ifRulePresent = true;
                        vnxeShareId = rule.getDeviceExportId();
                    }
                }

                if (!ifRulePresent) {
                    if (fsExport != null) {
                        vnxeShareId = fsExport.getIsilonId();
                    }
                }
                _logger.info("Delete UnityExport id {} for path {}",
                        vnxeShareId, fsExport.getPath());
                if (isFile) {
                    String fsId = args.getFs().getNativeId();
                    job = apiClient.removeNfsShare(vnxeShareId, fsId);
                } else {
                    job = apiClient.deleteNfsShareForSnapshot(vnxeShareId);
                }
                if (job != null) {
                    if (isFile) {
                        completer = new VNXeFileTaskCompleter(FileShare.class, args.getFsId(), args.getOpId());
                    } else {
                        completer = new VNXeFileTaskCompleter(Snapshot.class, args.getSnapshotId(), args.getOpId());
                    }

                    VNXeUnexportFileSystemJob unexportFSJob = new VNXeUnexportFileSystemJob(job.getId(), storage.getId(),
                            completer, fsExport, fsExport.getPath(), isFile);
                    ControllerServiceImpl.enqueueJob(new QueueJob(unexportFSJob));
                } else {
                    _logger.error("No job returned from exportFileSystem");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", "No Job returned from deleteFileSystem");
                    return BiosCommandResult.createErrorResult(error);
                }
            }

        } catch (VNXeException e) {
            _logger.error("Unexport file system got the exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Delete file system got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }

        if (job != null) {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Unexport filesystem job submitted - Array:%s, fileSystem: %s", storage.getSerialNumber(),
                    args.getFsName()));
            _logger.info(logMsgBuilder.toString());
            return BiosCommandResult.createPendingResult();
        } else {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "No export found - Array:%s, fileSystem: %s", storage.getSerialNumber(),
                    args.getFsName()));
            _logger.info(logMsgBuilder.toString());
            return BiosCommandResult.createSuccessfulResult();
        }
    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qd) throws ControllerException {

        _logger.info("creating Quota Directory: ", args.getQuotaDirectoryName());
        VNXUnityQuotaDirectoryTaskCompleter completer = null;
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        try {
            Long softLimit = 0L;
            Long softGrace = 0L;
            Long size = 0L;

            if (qd.getSize() == 0) {
                size = args.getFsCapacity(); // If quota directory has no size specified, inherit it from the parent fs
                                             // for the calculation of limit sizes
            } else {
                size = qd.getSize();
            }
            softLimit = Long.valueOf(qd.getSoftLimit() * size / 100);// conversion from percentage to bytes
                                                                     // using hard limit
            softGrace = Long.valueOf(qd.getSoftGrace() * 24 * 60 * 60); // conversion from days to seconds
            job = apiClient.createQuotaDirectory(args.getFsName(), qd.getName(), qd.getSize(), softLimit, softGrace);

            if (job != null) {
                _logger.info("opid:" + args.getOpId());
                completer = new VNXUnityQuotaDirectoryTaskCompleter(QuotaDirectory.class, args.getQuotaDirectory().getId(), args.getOpId());
                if (args.getQuotaDirectory() == null) {
                    _logger.error("Could not find the quota object");
                }
                VNXUnityCreateFileSystemQuotaDirectoryJob createQuotaJob = new VNXUnityCreateFileSystemQuotaDirectoryJob(job.getId(),
                        storage.getId(), completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(createQuotaJob));
            } else {
                _logger.error("No job returned from createQuotaDirectory");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("No Job returned from createQuotaDirectory");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("Create Quota Directory got an exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Create Quota Directory got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateQuotaDirectory", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create filesystem job submitted - Array:%s, fileSystem: %s", storage.getSerialNumber(), args.getFsName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _logger.info("Deleting file system {} quota directory {} ", args.getFsName(), args.getQuotaDirectoryName());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        VNXUnityQuotaDirectoryTaskCompleter completer = null;
        try {
            job = apiClient.deleteQuotaDirectory(args.getQuotaDirectoryNativeId());
            if (job != null) {
                completer = new VNXUnityQuotaDirectoryTaskCompleter(QuotaDirectory.class, args.getQuotaDirectoryId(), args.getOpId());
                VNXUnityDeleteFileSystemQuotaDirectoryJob quotaJob = new VNXUnityDeleteFileSystemQuotaDirectoryJob(job.getId(),
                        storage.getId(), completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(quotaJob));
            } else {
                _logger.error("No job returned from deleteQuotaDirectory");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                        "DeleteFileSystemQuota", "No Job returned from deleteQuotaDirectory");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("Delete file system quota directory got an exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Delete file system quota directory got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystemQuota", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete filesystem quota job submitted - Array:%s, fileSystem: %s, quota: %s", storage.getSerialNumber(),
                args.getFsName(), args.getQuotaDirectoryName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage, FileDeviceInputOutput args, QuotaDirectory qd)
            throws ControllerException {
        _logger.info("updating Quota Directory: ", args.getQuotaDirectoryName());
        VNXUnityQuotaDirectoryTaskCompleter completer = null;
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        VNXeCommandJob job = null;
        try {
            Long softLimit = 0L;
            Long softGrace = 0L;
            Long size = 0L;

            if (qd.getSize() == 0) {
                size = args.getFsCapacity(); // If quota directory has no size specified, inherit it from the parent fs
                                             // for the calculation of limit sizes
            } else {
                size = qd.getSize();
            }

            softLimit = Long.valueOf(qd.getSoftLimit() * size / 100);// conversion from percentage to bytes
                                                                     // using hard limit
            softGrace = Long.valueOf(qd.getSoftGrace() * 24 * 60 * 60); // conversion from days to seconds
            job = apiClient.updateQuotaDirectory(qd.getNativeId(), qd.getSize(), softLimit, softGrace);

            if (job != null) {
                _logger.info("opid:" + args.getOpId());
                completer = new VNXUnityQuotaDirectoryTaskCompleter(QuotaDirectory.class, args.getQuotaDirectory().getId(), args.getOpId());
                if (args.getQuotaDirectory() == null) {
                    _logger.error("Could not find the quota object");
                }
                VNXUnityUpdateFileSystemQuotaDirectoryJob createQuotaJob = new VNXUnityUpdateFileSystemQuotaDirectoryJob(job.getId(),
                        storage.getId(), completer);
                ControllerServiceImpl.enqueueJob(new QueueJob(createQuotaJob));
            } else {
                _logger.error("No job returned from createQuotaDirectory");
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("No Job returned from createQuotaDirectory");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (VNXeException e) {
            _logger.error("update Quota Directory got an exception", e);
            if (completer != null) {
                completer.error(dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("update Quota Directory got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("UpdateQuotaDirectory", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
            return BiosCommandResult.createErrorResult(error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "update quota directory job submitted - Array:%s, fileSystem: %s, Quota Directory: %s", storage.getSerialNumber(),
                args.getFsName(), args.getQuotaDirectoryName()));
        _logger.info(logMsgBuilder.toString());
        return BiosCommandResult.createPendingResult();
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported("Add or Update CIFS Share ACLs", "Unity"));
    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported("Delete CIFS Share ACLs", "Unity"));
    }

    @Override
    public BiosCommandResult assignFilePolicy(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported("Assign File Policy", "Unity"));
    }

    @Override
    public BiosCommandResult unassignFilePolicy(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported("Unassign File Policy", "Unity"));
    }

    @Override
    public BiosCommandResult listSanpshotByPolicy(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported("List Snapshot By Policy" , "Unity"));
    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported(" Add or Update NFS Share ACLs", "Unity"));
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported("Delete NFS Share ACLs", "Unity"));
    }

}
