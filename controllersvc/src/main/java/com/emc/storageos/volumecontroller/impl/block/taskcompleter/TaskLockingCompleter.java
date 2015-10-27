/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Locking-based task completer
 */
@XmlRootElement
public abstract class TaskLockingCompleter extends TaskCompleter {
    private static final String LOCK_SEPARATOR = ":";
    private static final Logger _logger = LoggerFactory.getLogger(TaskLockingCompleter.class);
    private static final long serialVersionUID = -1520175533121538993L;

    @XmlTransient
    private String lockedName = null;

    /**
     * JAXB requirement
     */
    public TaskLockingCompleter() {
    }

    public TaskLockingCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public TaskLockingCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    public TaskLockingCompleter(AsyncTask task) {
        super(task);
    }

    /**
     * Unlock the CG associated with the volumes in the operation.
     *
     * @param dbClient db client
     * @param locker locker service
     */
    protected void unlockCG(DbClient dbClient, ControllerLockingService locker) throws DeviceControllerException {
        // Warn if a state is met where there is a lock defined, but the locker wasn't sent down. (programming error)
        if (locker == null && lockedName != null) {
            _logger.error(String.format(
                    "Completer is not freeing up lock: %s!  This error will lead to a stray lock in the system and must be addressed",
                    lockedName));
            throw DeviceControllerExceptions.recoverpoint.invalidUnlock(lockedName);
        }

        List<URI> volumeIds = new ArrayList<URI>();

        for (URI id : getIds()) {
            // If this is a snapshot object completer, get the volume ids from the snapshot.
            if (URIUtil.isType(id, BlockSnapshot.class)) {
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, id);
                volumeIds.add(snapshot.getParent().getURI());
            } else if (URIUtil.isType(id, BlockConsistencyGroup.class)) {
                List<Volume> cgVolumes = CustomQueryUtility
                        .queryActiveResourcesByConstraint(dbClient, Volume.class,
                                getVolumesByConsistencyGroup(getId()));
                if (cgVolumes != null && !cgVolumes.isEmpty()) {
                    // Get the first volume in the CG
                    volumeIds.add(cgVolumes.get(0).getId());
                }
            } else {
                volumeIds.add(id);
            }
        }

        // Figure out the lock ID (rpSystemInstallationID:CGName)
        if (locker != null && lockedName != null) {
            for (URI id : volumeIds) {
                Volume volume = dbClient.queryObject(Volume.class, id);
                if (volume != null) {
                    // Volume's protection set will be set to a null URI value, not "null" itself after a delete.
                    if (volume.getProtectionController() != null && volume.getProtectionSet() != null) {
                        ProtectionSystem rpSystem = dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());
                        if (rpSystem != null) {
                            // Unlock the CG based on this volume
                            if (locker.releaseLock(lockedName)) {
                                _logger.info("Released lock: " + lockedName);
                                lockedName = null;
                                break;
                            } else {
                                _logger.info("Failed to release lock: " + lockedName);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Lock the entire CG based on this volume.
     *
     * @param dbClient db client
     * @param locker locker service
     * @return true if lock was acquired
     */
    public boolean lockCG(DbClient dbClient, ControllerLockingService locker) {
        // Figure out the lock ID (rpSystemInstallationID:CGName)
        URI volumeId = getId();

        // If this is a snapshot object completer, get the volume id from the snapshot.
        if (URIUtil.isType(getId(), BlockSnapshot.class)) {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, getId());
            volumeId = snapshot.getParent().getURI();
        } else if (URIUtil.isType(getId(), BlockConsistencyGroup.class)) {
            List<Volume> cgVolumes = CustomQueryUtility
                    .queryActiveResourcesByConstraint(dbClient, Volume.class,
                            getVolumesByConsistencyGroup(getId()));
            if (cgVolumes != null && !cgVolumes.isEmpty()) {
                // Get the first volume in the CG
                volumeId = cgVolumes.get(0).getId();
            }
        }

        // Figure out the lock ID (rpSystemInstallationID:CGName)
        Volume volume = dbClient.queryObject(Volume.class, volumeId);

        if (volume != null && locker != null) {
            if (volume.getProtectionController() != null && volume.getProtectionSet() != null) {
                ProtectionSystem rpSystem = dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());
                ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                if (rpSystem != null && protectionSet != null && rpSystem.getInstallationId() != null && protectionSet.getLabel() != null) {
                    // Unlock the CG based on this volume
                    String lockName = rpSystem.getInstallationId() + LOCK_SEPARATOR + protectionSet.getLabel();
                    if (locker.acquireLock(lockName, 5)) {
                        _logger.info("Acquired lock: " + lockName);
                        lockedName = lockName;
                        return true;
                    } else {
                        _logger.info("Failed to acquire lock: " + lockName);
                    }
                }
            } else if (volume.getProtectionSet() == null) {
                _logger.info("Lock not required, no CG in use");
                lockedName = null;
                return true;
            }
        }
        return false;
    }

    /**
     * This method will be called upon job execution finish with a locking controller.
     * It is not expected that non-locking controllers will call this version, however we need a base
     * method so we don't need to ship around TaskLockingCompleters all over the code.
     *
     * @param dbClient
     * @param locker
     * @param status
     * @param coded
     * @throws DeviceControllerException
     */
    @Override
    protected void complete(DbClient dbClient, ControllerLockingService locker, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        unlockCG(dbClient, locker);
        complete(dbClient, status, coded);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        updateConsistencyGroupTasks(dbClient, status, coded);
        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }
    }

    private void updateConsistencyGroupTasks(DbClient dbClient, Operation.Status status, ServiceCoded coded) {
        for (URI consistencyGroupId : getConsistencyGroupIds()) {
            _logger.info("Updating consistency group task: {}", consistencyGroupId);
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockConsistencyGroup.class, consistencyGroupId, coded);
                    break;
                case ready:
                    setReadyOnDataObject(dbClient, BlockConsistencyGroup.class, consistencyGroupId);
                    break;
            }
        }
    }
}
