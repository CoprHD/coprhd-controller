/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.VNXeUtils;
import com.emc.storageos.vnxe.models.AccessEnum;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeFSSupportedProtocolEnum;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateFileSystemSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateShareJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateVolumesJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeDeleteFileSystemSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeDeleteShareJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeDeleteVolumesJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeExpandFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeExpandVolumeJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeExportFileSystemJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeFSSnapshotTaskCompleter;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeFileTaskCompleter;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeModifyExportJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeRestoreFileSystemSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeUnexportFileSystemJob;

public class VNXeStorageDevice extends VNXeOperations
        implements FileStorageDevice, BlockStorageDevice {

    private static final Logger _logger = LoggerFactory.getLogger(VNXeStorageDevice.class);

    private SnapshotOperations _snapshotOperations;
    private NameGenerator nameGenerator;

    public NameGenerator getNameGenerator() {
        return nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    private ExportMaskOperations exportMaskOperationsHelper;

    public ExportMaskOperations getExportMaskOperationsHelper() {
        return exportMaskOperationsHelper;
    }

    public void setExportMaskOperationsHelper(
            ExportMaskOperations exportMaskOperationsHelper) {
        this.exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    public void setSnapshotOperations(final SnapshotOperations snapshotOperations) {
        _snapshotOperations = snapshotOperations;
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
        VNXeApiClient apiClient = getVnxeClient(storage);
        VNXeCommandJob job = null;
        try {
            FileShare fs = fileInOut.getFs();
            URI port = fs.getStoragePort();
            if (port == null) {
                _logger.error("No storageport uri found in the fs");
                ServiceError error = DeviceControllerErrors.vnxe.unableToCreateFileSystem("No storageport uri found in the fs");
                return BiosCommandResult.createErrorResult(error);
            }
            StoragePort portObj = _dbClient.queryObject(StoragePort.class, port);
            URI haDomainUri = portObj.getStorageHADomain();
            StorageHADomain haDomainObj = _dbClient.queryObject(StorageHADomain.class, haDomainUri);
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
                _logger.error("protocol is not support: " + protocols);
                ServiceError error = DeviceControllerErrors.vnxe.unableToCreateFileSystem("protocol is not support:" + protocols);
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
            _logger.error("Create file system got the exception", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Create file system got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateFileSystem", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
            VNXeApiClient apiClient = getVnxeClient(storage);
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
     * @Override
     * public BiosCommandResult doDeleteFS(StorageSystem storage,
     * FileDeviceInputOutput fileInOut) throws ControllerException {
     * _logger.info("deleting file system: ", fileInOut.getFsName());
     * VNXeApiClient apiClient = getVnxeClient(storage);
     * VNXeCommandJob job = null;
     * VNXeFileTaskCompleter completer = null;
     * try {
     * job = apiClient.deleteFileSystem(fileInOut.getFsNativeId(), fileInOut.getForceDelete());
     * if (job != null) {
     * completer = new VNXeFileTaskCompleter(FileShare.class, fileInOut.getFsId(), fileInOut.getOpId(),
     * OperationTypeEnum.DELETE_FILE_SYSTEM);
     * VNXeDeleteFileSystemJob deleteFSJob = new VNXeDeleteFileSystemJob(job.getId(), storage.getId(),
     * completer, fileInOut.getForceDelete());
     * ControllerServiceImpl.enqueueJob(new QueueJob(deleteFSJob));
     * } else {
     * _logger.error("No job returned from deleteFileSystem");
     * ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", "No Job returned from deleteFileSystem");
     * return BiosCommandResult.createErrorResult(error);
     * }
     * 
     * }catch (VNXeException e) {
     * _logger.error("Delete file system got the exception", e);
     * if (completer != null) {
     * completer.error(_dbClient, e);
     * }
     * return BiosCommandResult.createErrorResult(e);
     * } catch (Exception ex) {
     * _logger.error("Delete file system got the exception", ex);
     * ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", ex.getMessage());
     * if (completer != null) {
     * completer.error(_dbClient, error);
     * }
     * return BiosCommandResult.createErrorResult(error);
     * }
     * StringBuilder logMsgBuilder = new StringBuilder(String.format(
     * "Delete filesystem job submitted - Array:%s, fileSystem: %s", storage.getSerialNumber(),
     * fileInOut.getFsName()));
     * _logger.info(logMsgBuilder.toString());
     * return BiosCommandResult.createPendingResult();
     * }
     */

    /*
     * To get around the KH API delete file system async issues, using sync call for now.
     */
    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage,
            FileDeviceInputOutput fileInOut) throws ControllerException {
        _logger.info("deleting file system: ", fileInOut.getFsName());
        VNXeApiClient apiClient = getVnxeClient(storage);
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
            VNXeApiClient apiClient = getVnxeClient(storage);
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
                    completer.error(_dbClient, e);
                }
                return BiosCommandResult.createErrorResult(e);
            } catch (Exception ex) {
                _logger.error("export file system got the exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("exportFileSystem", ex.getMessage());
                if (completer != null) {
                    completer.error(_dbClient, error);
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

        VNXeApiClient apiClient = getVnxeClient(storage);
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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("create share got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("create share", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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

        VNXeApiClient apiClient = getVnxeClient(storage);

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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("delete share got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("create share", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
            VNXeApiClient apiClient = getVnxeClient(storage);
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
                    completer.error(_dbClient, e);
                }
                return BiosCommandResult.createErrorResult(e);
            } catch (Exception ex) {
                _logger.error("Delete file system got the exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", ex.getMessage());
                if (completer != null) {
                    completer.error(_dbClient, error);
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _logger.info("expanding file system: ", args.getFsName());
        VNXeApiClient apiClient = getVnxeClient(storage);
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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Expand file system got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("ExpandFileSystem", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
        VNXeApiClient apiClient = getVnxeClient(storage);
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
            _logger.error("Create file system snapshot got the exception", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Create file system snpashot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateFileSystemSnapshot", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
        VNXeApiClient apiClient = getVnxeClient(storage);
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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Restore file system snpashot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("RestoreFileSystemSnapshot", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
        VNXeApiClient apiClient = getVnxeClient(storage);
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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Delete file system snpashot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystemSnapshot", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
            VNXeApiClient client = getVnxeClient(storage);
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
            VNXeApiClient client = getVnxeClient(storage);
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
        VNXeApiClient client = getVnxeClient(storage);
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
    public void doCreateVolumes(StorageSystem storage, StoragePool storagePool,
            String opId, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("creating volumes, array: {}, pool : {}", storage.getSerialNumber(),
                storagePool.getNativeId());
        VNXeApiClient apiClient = getVnxeClient(storage);
        List<String> jobs = new ArrayList<String>();
        boolean opFailed = false;
        try {
            boolean isCG = false;
            Volume vol = volumes.get(0);
            if (vol.getConsistencyGroup() != null) {
                isCG = true;
            }
            List<String> volNames = new ArrayList<String>();
            String autoTierPolicyName = null;
            for (Volume volume : volumes) {
                String tenantName = "";
                try {
                    TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant()
                            .getURI());
                    tenantName = tenant.getLabel();
                } catch (DatabaseException e) {
                    _logger.error("Error lookup TenantOrb object", e);
                }
                String label = nameGenerator.generate(tenantName, volume.getLabel(), volume.getId()
                        .toString(), '-', VNXeConstants.MAX_NAME_LENGTH);
                autoTierPolicyName = ControllerUtils.getAutoTieringPolicyName(volume.getId(), _dbClient);
                if (autoTierPolicyName.equals(Constants.NONE)) {
                    autoTierPolicyName = null;
                }

                volume.setNativeGuid(label);
                _dbClient.persistObject(volume);
                if (!isCG) {
                    VNXeCommandJob job = apiClient.createLun(label, storagePool.getNativeId(), volume.getCapacity(),
                            volume.getThinlyProvisioned(), autoTierPolicyName);
                    jobs.add(job.getId());
                } else {
                    volNames.add(label);
                }

            }
            if (isCG) {

                URI cg = vol.getConsistencyGroup();
                BlockConsistencyGroup cgObj = _dbClient.queryObject(BlockConsistencyGroup.class, cg);

                String cgId = cgObj.getCgNameOnStorageSystem(storage.getId());
                VNXeCommandJob job = apiClient.createLunsInLunGroup(volNames, storagePool.getNativeId(), vol.getCapacity(),
                        vol.getThinlyProvisioned(), autoTierPolicyName, cgId);
                jobs.add(job.getId());
            }
            VNXeCreateVolumesJob createVolumesJob = new VNXeCreateVolumesJob(jobs, storage.getId(),
                    taskCompleter, storagePool.getId(), isCG);

            ControllerServiceImpl.enqueueJob(new QueueJob(createVolumesJob));
        } catch (VNXeException e) {
            _logger.error("Create volumes got the exception", e);
            opFailed = true;
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _logger.error("Create volumes got the exception", ex);
            opFailed = true;
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateVolumes", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }
        if (opFailed) {
            for (Volume vol : volumes) {
                vol.setInactive(true);
                _dbClient.persistObject(vol);
            }
        }

    }

    @Override
    public void doCreateMetaVolume(StorageSystem storage,
            StoragePool storagePool, Volume volume,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeRecommendation recommendation,
            VolumeCreateCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doExpandVolume(StorageSystem storage, StoragePool pool,
            Volume volume, Long size, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info(String.format("Expand Volume Start - Array: %s, Pool: %s, Volume: %s, New size: %d",
                storage.getSerialNumber(), pool.getNativeGuid(), volume.getLabel(), size));

        String consistencyGroupId = null;
        URI consistencyGroupURI = volume.getConsistencyGroup();
        if (consistencyGroupURI != null) {
            BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                    consistencyGroupURI);
            if (consistencyGroup != null) {
                consistencyGroupId = consistencyGroup.getCgNameOnStorageSystem(storage.getId());
            }
        }

        try {
            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeCommandJob commandJob = apiClient.expandLun(volume.getNativeId(), size, consistencyGroupId);
            VNXeExpandVolumeJob expandVolumeJob = new VNXeExpandVolumeJob(commandJob.getId(), storage.getId(), taskCompleter);
            ControllerServiceImpl.enqueueJob(new QueueJob(expandVolumeJob));

        } catch (VNXeException e) {
            _logger.error("Expand volume got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _logger.error("Expand volume got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("ExpandVolume", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    /*
     * @Override
     * public void doExpandAsMetaVolume(StorageSystem storageSystem,
     * StoragePool storagePool, Volume volume, long size,
     * MetaVolumeRecommendation recommendation,
     * TaskCompleter volumeCompleter) throws DeviceControllerException {
     * // TODO Auto-generated method stub
     * 
     * }
     */

    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
            List<Volume> volumes, TaskCompleter completer)
                    throws DeviceControllerException {
        _logger.info("deleting volumes, array: {}", storageSystem.getSerialNumber());
        VNXeApiClient apiClient = getVnxeClient(storageSystem);
        List<String> jobs = new ArrayList<String>();
        Map<String, List<String>> consistencyGroupMap = new HashMap<String, List<String>>();
        try {
            for (Volume volume : volumes) {
                if (volume.getConsistencyGroup() != null) {
                    BlockConsistencyGroup consistencyGroupObj = _dbClient.queryObject(BlockConsistencyGroup.class,
                            volume.getConsistencyGroup());
                    List<String> lunIds = consistencyGroupMap.get(consistencyGroupObj.getCgNameOnStorageSystem(storageSystem.getId()));
                    if (lunIds == null) {
                        lunIds = new ArrayList<String>();
                        consistencyGroupMap.put(consistencyGroupObj.getCgNameOnStorageSystem(storageSystem.getId()), lunIds);
                    }
                    lunIds.add(volume.getNativeId());
                } else {
                    VNXeCommandJob job = apiClient.deleteLun(volume.getNativeId(), true);
                    jobs.add(job.getId());
                }
            }

            for (String consistencyGroup : consistencyGroupMap.keySet()) {
                List<String> lunIDs = consistencyGroupMap.get(consistencyGroup);
                VNXeCommandJob job = apiClient.deleteLunsFromLunGroup(consistencyGroup, lunIDs);
                jobs.add(job.getId());
            }

            VNXeDeleteVolumesJob deleteVolumesJob = new VNXeDeleteVolumesJob(jobs, storageSystem.getId(),
                    completer);

            ControllerServiceImpl.enqueueJob(new QueueJob(deleteVolumesJob));

        } catch (VNXeException e) {
            _logger.error("Delete volumes got the exception", e);
            completer.error(_dbClient, e);

        } catch (Exception ex) {
            _logger.error("Delete volumes got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteVolumes", ex.getMessage());
            completer.error(_dbClient, error);

        }

    }

    @Override
    public void doExportGroupCreate(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumeMap,
            List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doExportGroupCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumeMap, _dbClient);
        exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray,
                targets, initiators, taskCompleter);
        _logger.info("{} doExportGroupCreate END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportGroupDelete(StorageSystem storage,
            ExportMask exportMask, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doExportGroupDelete START ...", storage.getSerialNumber());
        List<URI> volumes = new ArrayList<URI>();
        StringMap maskVolumes = exportMask.getVolumes();

        if (maskVolumes != null && !maskVolumes.isEmpty()) {
            for (String volURI : maskVolumes.keySet()) {
                volumes.add(URI.create(volURI));
            }
        }
        exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(),
                volumes, new ArrayList<URI>(), new ArrayList<Initiator>(),
                taskCompleter);
        _logger.info("{} doExportGroupDelete END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask,
            URI volume, Integer lun, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, _dbClient);
        exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _logger.info("{} doExportAddVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddVolumes(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumes, _dbClient);
        exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _logger.info("{} doExportAddVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage,
            ExportMask exportMask, URI volume, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(),
                Arrays.asList(volume), taskCompleter);
        _logger.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage,
            ExportMask exportMask, List<URI> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), volumes,
                taskCompleter);
        _logger.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddInitiator(StorageSystem storage,
            ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        _logger.info("{} doExportAddInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddInitiators(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), initiators, targets,
                taskCompleter);
        _logger.info("{} doExportAddInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage,
            ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        _logger.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                initiators, targets, taskCompleter);
        _logger.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doCreateSingleSnapshot(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doCreateSingleSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = _dbClient
                .queryObject(BlockSnapshot.class, snapshotList);
        URI snapshot = snapshots.get(0).getId();
        _snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                readOnly, taskCompleter);
        _logger.info("{} doCreateSingleSnapshot END ...", storage.getSerialNumber());
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
                    throws DeviceControllerException {

        _logger.info("{} doCreateSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = _dbClient
                .queryObject(BlockSnapshot.class, snapshotList);
        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
            _snapshotOperations.createGroupSnapshots(storage, snapshotList, createInactive, readOnly, taskCompleter);
        } else {
            URI snapshot = snapshots.get(0).getId();
            _snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                    readOnly, taskCompleter);

        }
        _logger.info("{} doCreateSnapshot END ...", storage.getSerialNumber());
    }

    @Override
    public void doActivateSnapshot(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doActivateSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotList);
        URI snapshot = snapshots.get(0).getId();
        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
            _snapshotOperations.activateGroupSnapshots(storage, snapshot, taskCompleter);
        } else {
            _snapshotOperations.activateSingleVolumeSnapshot(storage, snapshot, taskCompleter);

        }
        _logger.info("{} doDeleteSnapshot END ...", storage.getSerialNumber());

    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} doDeleteSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));

        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
            _snapshotOperations.deleteGroupSnapshots(storage, snapshot, taskCompleter);
        } else {
            _snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);

        }
        _logger.info("{} doDeleteSnapshot END ...", storage.getSerialNumber());

    }

    @Override
    public void doDeleteSelectedSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} doDeleteSelectedSnapshot START ...", storage.getSerialNumber());
        try {
            _snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to delete snapshot(s) on array %s",
                    storage.getSerialNumber());
            _logger.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _logger.info("{} doDeleteSelectedSnapshot END ...", storage.getSerialNumber());
    }

    @Override
    public void doRestoreFromSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("{} doRestoreFromSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));

        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
            _snapshotOperations.restoreGroupSnapshots(storage, volume, snapshot, taskCompleter);
        } else {
            _snapshotOperations.restoreSingleVolumeSnapshot(storage, volume, snapshot, taskCompleter);

        }
        _logger.info("{} doRestoreFromSnapshot END ...", storage.getSerialNumber());

    }

    @Override
    public void doCreateMirror(StorageSystem storage, URI mirror,
            Boolean createInactive, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doFractureMirror(StorageSystem storage, URI mirror,
            Boolean sync, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doDetachMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResumeNativeContinuousCopy(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doEstablishVolumeNativeContinuousCopyGroupRelation(
            StorageSystem storage, URI sourceVolume, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doEstablishVolumeSnapshotGroupRelation(
            StorageSystem storage, URI sourceVolume, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCreateClone(StorageSystem storageSystem, URI sourceVolume,
            URI cloneVolume, Boolean createInactive, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doDetachClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage,
            URI consistencyGroup, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("creating consistency group, array: {}", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroupObj = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroup);
        VNXeApiClient apiClient = getVnxeClient(storage);

        String tenantName = "";
        try {
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, consistencyGroupObj.getTenant()
                    .getURI());
            tenantName = tenant.getLabel();
        } catch (DatabaseException e) {
            _logger.error("Error lookup TenantOrb object", e);
        }
        String label = nameGenerator.generate(tenantName, consistencyGroupObj.getLabel(),
                consistencyGroupObj.getId().toString(), '-', VNXeConstants.MAX_NAME_LENGTH);
        try {
            VNXeCommandResult result = apiClient.createLunGroup(label);
            if (result.getStorageResource() != null) {
                consistencyGroupObj.addSystemConsistencyGroup(storage.getId().toString(),
                        result.getStorageResource().getId());
                consistencyGroupObj.addConsistencyGroupTypes(Types.LOCAL.name());
                if (NullColumnValueGetter.isNullURI(consistencyGroupObj.getStorageController())) {
                    consistencyGroupObj.setStorageController(storage.getId());
                }
                _dbClient.persistObject(consistencyGroupObj);
                taskCompleter.ready(_dbClient);
            } else {
                _logger.error("No storage resource Id returned");
                consistencyGroupObj.setInactive(true);
                _dbClient.persistObject(consistencyGroupObj);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateConsistencyGroup failed");
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            _logger.error("Exception caught when createing consistency group ", e);
            consistencyGroupObj.setInactive(true);
            _dbClient.persistObject(consistencyGroupObj);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateConsistencyGroup", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, Boolean markInactive, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _logger.info("Deleting consistency group, array: {}", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        // check if lungroup has been created in the array
        String lunGroupId = consistencyGroup.getCgNameOnStorageSystem(storage.getId());
        if (lunGroupId == null || lunGroupId.isEmpty()) {
            _logger.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .consistencyGroupNotFound(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
            return;

        }
        VNXeApiClient apiClient = getVnxeClient(storage);
        try {
            apiClient.deleteLunGroup(lunGroupId, false, false);
            URI systemURI = storage.getId();
            consistencyGroup.removeSystemConsistencyGroup(systemURI.toString(),
                    consistencyGroup.getCgNameOnStorageSystem(systemURI));
            if (markInactive) {
                consistencyGroup.setInactive(true);
            }
            _dbClient.persistObject(consistencyGroup);
            _logger.info("Consistency group {} deleted", consistencyGroup.getLabel());
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _logger.info("Failed to delete consistency group: " + e);
            // Set task to error
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                    "doDeleteConsistencyGroup", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

    }

    @Override
    public String doAddStorageSystem(StorageSystem storage)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void doRemoveStorageSystem(StorageSystem storage)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doCopySnapshotsToTarget(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void doActivateFullCopy(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCleanupMetaMembers(StorageSystem storageSystem,
            Volume volume, CleanupMetaVolumeMembersCompleter cleanupCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz,
            StorageSystem storageObj, URI target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        // check if lungroup has been created in the array
        String lunGroupId = consistencyGroup.getCgNameOnStorageSystem(storage.getId());
        if (lunGroupId == null || lunGroupId.isEmpty()) {
            // lun group has not created yet. return error
            _logger.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .consistencyGroupNotFound(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
            return;

        }
        VNXeApiClient apiClient = getVnxeClient(storage);
        try {
            List<String> luns = new ArrayList<String>();
            for (URI volume : blockObjects) {
                luns.add(volume.toString());
            }
            apiClient.addLunsToLunGroup(lunGroupId, luns);
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(consistencyGroupId);
                }
                _dbClient.updateAndReindexObject(blockObject);
            }

            taskCompleter.ready(_dbClient);
            _logger.info("Added volumes to the consistency group successfully");
        } catch (Exception e) {
            _logger.error("Exception caught when adding volumes to the consistency group ", e);
            // Remove any references to the consistency group
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                _dbClient.persistObject(blockObject);
            }
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }

    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        // check if lungroup has been created in the array
        String lunGroupId = consistencyGroup.getCgNameOnStorageSystem(storage.getId());
        if (lunGroupId == null || lunGroupId.isEmpty()) {
            // lun group has not created yet. return error
            _logger.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .consistencyGroupNotFound(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
            return;

        }
        VNXeApiClient apiClient = getVnxeClient(storage);
        try {
            List<String> luns = new ArrayList<String>();
            for (URI volume : blockObjects) {
                luns.add(volume.toString());
            }
            apiClient.removeLunsFromLunGroup(lunGroupId, luns);
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                _dbClient.updateAndReindexObject(blockObject);
            }

            taskCompleter.ready(_dbClient);
            _logger.info("Remove volumes from the consistency group successfully");
        } catch (Exception e) {
            _logger.error("Exception caught when removing volumes from the consistency group ", e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }

    }

    @Override
    public void doAddToReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveFromReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void doCreateMetaVolumes(StorageSystem storage,
            StoragePool storagePool, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeRecommendation recommendation, TaskCompleter completer)
                    throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doExpandAsMetaVolume(StorageSystem storageSystem,
            StoragePool storagePool, Volume volume, long size,
            MetaVolumeRecommendation recommendation,
            VolumeExpandCompleter volumeCompleter)
                    throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void updatePolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVpool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
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

        VNXeApiClient apiClient = getVnxeClient(storage);

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
                    _logger.error("Adding export rules is not supported as there can be only one export rule for VNXe.");
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("updateExportRules",
                            "Adding export rule is not supported for VNXe");
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
                List<String> roHosts = null;
                List<String> rwHosts = null;
                List<String> rootHosts = null;
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
                    if (rwHosts == null) {
                        rwHosts = new ArrayList<String>();
                    }
                    rwHosts.addAll(rule.getReadWriteHosts());
                }
                if (rule.getReadOnlyHosts() != null && !rule.getReadOnlyHosts().isEmpty()) {
                    access = AccessEnum.READ;
                    if (roHosts == null) {
                        roHosts = new ArrayList<String>();
                    }
                    roHosts.addAll(rule.getReadOnlyHosts());
                }
                if (rule.getRootHosts() != null && !rule.getRootHosts().isEmpty()) {
                    access = AccessEnum.ROOT;
                    if (rootHosts == null) {
                        rootHosts = new ArrayList<String>();
                    }
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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("updateExportRules got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("updateExportRules", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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

            VNXeApiClient apiClient = getVnxeClient(storage);

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
                    _logger.info("Delete IsilonExport id {} for path {}",
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
                            _logger.info("Delete IsilonExport id {} for path {}",
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
                    _logger.info("Delete IsilonExport id for path {} f containing subdirectory {}",
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
                _logger.info("Delete IsilonExport id {} for path {}",
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
                _logger.info("Delete IsilonExport id {} for path {}",
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
                completer.error(_dbClient, e);
            }
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception ex) {
            _logger.error("Delete file system got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteFileSystem", ex.getMessage());
            if (completer != null) {
                completer.error(_dbClient, error);
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
    public void doTerminateAnyRestoreSessions(StorageSystem storageDevice, URI source, BlockObject snapshot,
            TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doModifyVolumes(StorageSystem storage, StoragePool storagePool, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public ExportMaskPolicy getExportMaskPolicy(StorageSystem storage, ExportMask mask) {
        // No special policy for this device type yet.
        return new ExportMaskPolicy();
    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qd) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ServiceError serviceError = DeviceControllerErrors.vnxe.operationNotSupported();
        result = BiosCommandResult.createErrorResult(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ServiceError serviceError = DeviceControllerErrors.vnxe.operationNotSupported();
        result = BiosCommandResult.createErrorResult(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qd) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ServiceError serviceError = DeviceControllerErrors.vnxe.operationNotSupported();
        result = BiosCommandResult.createErrorResult(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storageObj,
            FileDeviceInputOutput args) {

        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported());
    }

    @Override
    public void doFractureClone(StorageSystem storageDevice, URI source, URI clone,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResyncClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateGroupClone(StorageSystem storageDevice, List<URI> clones,
            Boolean createInactive, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachGroupClone(StorageSystem storage, List<URI> cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doEstablishVolumeFullCopyGroupRelation(
            StorageSystem storage, URI sourceVolume, URI fullCopy,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromGroupClone(StorageSystem storageSystem,
            List<URI> cloneVolume, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doActivateGroupFullCopy(StorageSystem storageSystem,
            List<URI> fullCopy, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResyncGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.EMPTY_MAP;
    }

    @Override
    public void doFractureGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResyncSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCreateGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doFractureGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean sync, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResumeGroupNativeContinuousCopies(StorageSystem storage,
            List<URI> mirrorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveMirrorFromDeviceMaskingGroup(StorageSystem system,
            List<URI> mirrors, TaskCompleter completer)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateListReplica(StorageSystem storage, List<URI> replicaList, /* String repGroupoName, */ Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnxe.operationNotSupported());
    }
    
    @Override
    public void doUntagVolumes(StorageSystem storageSystem, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // If this operation is unsupported by default it's not necessarily an error
        return;
    }
}
