/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.RemoteSystem;
import com.emc.storageos.vnxe.models.ReplicationSession;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityMirrorOperations extends VNXUnityOperations implements FileMirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(VNXUnityMirrorOperations.class);

    /**
     * Create Mirror between source and target fileshare
     */
    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {

        FileShare sourceFileShare = dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        VNXeApiClient apiClient = getVnxUnityClient(sourceStorageSystem);

        int maxTimeOutOfSync = -1;
        String name = targetFileShare.getLabel();

        try {
            VNXeCommandJob job = apiClient.createReplicationSession(getResIdByFileShareURI(source), getResIdByFileShareURI(target),
                    maxTimeOutOfSync, getRemoteSystem(apiClient, targetStorageSystem), name);
            VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "createMirrorFileShareLink");
            ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            _log.info("Creating File Share Replication Session");
        } catch (VNXeException ex) {
            completer.error(dbClient, ex);
        } catch (Exception ex) {
            _log.error("createMirrorFileShareLink got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("createMirrorFileShareLink", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
        }
    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            FileShare sourceFileShare = dbClient.queryObject(FileShare.class, target.getParentFileShare().getURI());

            VNXeApiClient apiClient = getVnxUnityClient(system);
            VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());
            int maxTimeOutOfSync = -1;
            if (virtualPool != null) {
                maxTimeOutOfSync = convertToMinutes(virtualPool.getFrRpoValue(), virtualPool.getFrRpoType());
                if (virtualPool.getFrRpoValue() == 0) {
                    // Zero RPO value means policy has to be started manually-NO Schedule
                    maxTimeOutOfSync = -1;
                }
            }
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                apiClient.modifyReplicationSessionSync(session.getId(), maxTimeOutOfSync);
                VNXeCommandJob job = apiClient.syncReplicationSession(session.getId()); // sync for the first time
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "startMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("startMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("startMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.pauseReplicationSession(session.getId());
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "pauseMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("pauseMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("pauseMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.resumeReplicationSession(session.getId(), false);
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "resumeMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("resumeMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("resumeMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }

        }
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        VNXeApiClient apiClient = getVnxUnityClient(system);
        try {
            ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(source),
                    getResIdByFileShareURI(target));
            VNXeCommandJob job = apiClient.deleteReplicationSession(session.getId());
            VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "deleteMirrorFileShareLink");
            ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
        } catch (VNXeException ex) {
            completer.error(dbClient, ex);
        } catch (Exception ex) {
            _log.error("deleteMirrorFileShareLink got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("deleteMirrorFileShareLink", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
        }
    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem systemTarget, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {

        if (target.getParentFileShare() != null) {
            FileShare sourceFileShare = dbClient.queryObject(FileShare.class, target.getParentFileShare());
            StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
            VNXeApiClient apiClient = getVnxUnityClient(sourceStorageSystem);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.failoverReplicationSession(session.getId(), true);
                VNXeJob replicationJob = new VNXeJob(job.getId(), sourceStorageSystem.getId(), completer, "failoverMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("failoverMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("failoverMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void failbackMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            FileShare sourceFileShare = dbClient.queryObject(FileShare.class, target.getParentFileShare());
            StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
            VNXeApiClient apiClient = getVnxUnityClient(sourceStorageSystem);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.failbackReplicationSession(session.getId(), false);
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "failbackMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("failbackMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("failbackMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
            TaskCompleter completer, String policyName) {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(primarySystem);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.syncReplicationSession(session.getId());
                VNXeJob replicationJob = new VNXeJob(job.getId(), primarySystem.getId(), completer, "resyncMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("resyncMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("resyncMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.modifyReplicationSession(session.getId(), convertToMinutes(rpoValue, rpoType));
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "doModifyReplicationRPO");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("doModifyReplicationRPO got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("doModifyReplicationRPO", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String devPolicyName)
            throws DeviceControllerException {
        VNXeApiClient apiClient = getVnxUnityClient(system);
        try {
            ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                    getResIdByFileShareURI(target.getId()));
            VNXeCommandJob job = apiClient.deleteReplicationSession(session.getId());
            VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "cancelMirrorFileShareLink");
            ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
        } catch (VNXeException ex) {
            completer.error(dbClient, ex);
        } catch (Exception ex) {
            _log.error("cancelMirrorFileShareLink got an exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("cancelMirrorFileShareLink", ex.getMessage());
            if (completer != null) {
                completer.error(dbClient, error);
            }
        }
    }

    @Override
    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.syncReplicationSession(session.getId());
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "refreshMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("refreshMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("refreshMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            try {
                ReplicationSession session = apiClient.getReplicationSession(getResIdByFileShareURI(target.getParentFileShare().getURI()),
                        getResIdByFileShareURI(target.getId()));
                VNXeCommandJob job = apiClient.pauseReplicationSession(session.getId());
                VNXeJob replicationJob = new VNXeJob(job.getId(), system.getId(), completer, "pauseMirrorFileShareLink");
                ControllerServiceImpl.enqueueJob(new QueueJob(replicationJob));
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            } catch (Exception ex) {
                _log.error("pauseMirrorFileShareLink got an exception", ex);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("pauseMirrorFileShareLink", ex.getMessage());
                if (completer != null) {
                    completer.error(dbClient, error);
                }
            }
        }
    }

    // Util Methods

    private String getResIdByFileShareURI(URI fsId) {
        FileShare fs = dbClient.queryObject(FileShare.class, fsId);
        VNXeApiClient apiClient = getVnxUnityClient(dbClient.queryObject(StorageSystem.class, fs.getStorageDevice()));
        return apiClient.getFileSystemByFSId(fs.getNativeId()).getStorageResource().getId();
    }

    private RemoteSystem getRemoteSystem(VNXeApiClient client, StorageSystem system) {
        RemoteSystem remoteSystem = client.getRemoteSystemBySerial(system.getSerialNumber());
        if (remoteSystem != null) {
            if (remoteSystem.getId().equalsIgnoreCase("RS_0")) {
                return null;
            }
            return remoteSystem;
        }
        return null;
    }

    private int convertToMinutes(Long fsRpoValue, String fsRpoType) {
        int multiplier = 1;
        switch (fsRpoType.toUpperCase()) {
            case "MINUTES":
                multiplier = 1;
                break;
            case "HOURS":
                multiplier = 60;
                break;
            case "DAYS":
                multiplier = 60 * 24;
                break;
        }
        return fsRpoValue.intValue() * multiplier;
    }
}
