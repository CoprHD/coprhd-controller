/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.cinder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.api.CinderApiFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderSingleVolumeCreateJob;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;

public class CinderCloneOperations implements CloneOperations
{
    private static final Logger log = LoggerFactory.getLogger(CinderCloneOperations.class);
    private DbClient dbClient;
    private CinderApiFactory cinderApiFactory;

    public CinderCloneOperations()
    {

    }

    public void setDbClient(DbClient dbClient)
    {
        this.dbClient = dbClient;
    }

    /**
     * @param CinderApiFactory the CinderApiFactory to set
     */
    public void setCinderApiFactory(CinderApiFactory cinderApiFactory)
    {
        this.cinderApiFactory = cinderApiFactory;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.CloneOperations#createSingleClone(
     * com.emc.storageos.db.client.model.StorageSystem, java.net.URI, java.net.URI,
     * java.lang.Boolean,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void createSingleClone(StorageSystem storageSystem,
            URI sourceObject,
            URI cloneVolume,
            Boolean createInactive,
            TaskCompleter taskCompleter)
    {
        log.info("START createSingleClone operation");
        boolean isVolumeClone = true;
        try
        {
            BlockObject sourceObj = BlockObject.fetch(dbClient, sourceObject);
            URI tenantUri = null;
            if (sourceObj instanceof BlockSnapshot)
            { // In case of snapshot, get the tenant from its parent volume
                NamedURI parentVolUri = ((BlockSnapshot) sourceObj).getParent();
                Volume parentVolume = dbClient.queryObject(Volume.class, parentVolUri);
                tenantUri = parentVolume.getTenant().getURI();
                isVolumeClone = false;
            }
            else
            {// This is a default flow
                tenantUri = ((Volume) sourceObj).getTenant().getURI();
                isVolumeClone = true;
            }

            Volume cloneObj = dbClient.queryObject(Volume.class, cloneVolume);
            StoragePool targetPool = dbClient.queryObject(StoragePool.class, cloneObj.getPool());
            TenantOrg tenantOrg = dbClient.queryObject(TenantOrg.class, tenantUri);
            // String cloneLabel = generateLabel(tenantOrg, cloneObj);

            CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(storageSystem.getActiveProviderURI(), dbClient);
            log.info("Getting the cinder APi for the provider with id " + storageSystem.getActiveProviderURI());
            CinderApi cinderApi = cinderApiFactory.getApi(storageSystem.getActiveProviderURI(), ep);

            String volumeId = "";
            if (isVolumeClone)
            {
                volumeId = cinderApi.cloneVolume(cloneObj.getLabel(), (cloneObj.getCapacity() / (1024 * 1024 * 1024)),
                        targetPool.getNativeId(), sourceObj.getNativeId());
            }
            else
            {
                volumeId = cinderApi.createVolumeFromSnapshot(cloneObj.getLabel(), (cloneObj.getCapacity() / (1024 * 1024 * 1024)),
                        targetPool.getNativeId(), sourceObj.getNativeId());
            }

            log.debug("Creating volume with the id " + volumeId + " on Openstack cinder node");
            if (volumeId != null)
            {
                // Cinder volume/snapshot clones are not sync with source, so
                // set the replication state as DETACHED
                cloneObj.setReplicaState(ReplicationState.DETACHED.name());
                dbClient.persistObject(cloneObj);
                
                Map<String, URI> volumeIds = new HashMap<String, URI>();
                volumeIds.put(volumeId, cloneObj.getId());
                ControllerServiceImpl.enqueueJob(new QueueJob(
                        new CinderSingleVolumeCreateJob(volumeId, cloneObj
                                .getLabel(), storageSystem.getId(),
                                CinderConstants.ComponentType.volume.name(),
                                ep, taskCompleter, targetPool.getId(), volumeIds)));
            }
        } catch (InternalException e)
        {
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT, sourceObject, cloneVolume);
            log.error(errorMsg, e);
            taskCompleter.error(dbClient, e);
        } catch (Exception e)
        {
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT, sourceObject, cloneVolume);
            log.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerErrors.cinder.operationFailed("createSingleClone",
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.CloneOperations#detachSingleClone(
     * com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void detachSingleClone(StorageSystem storageSystem,
            URI cloneVolume,
            TaskCompleter taskCompleter)
    {
        // Not Supported
        Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
        ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, dbClient);
        clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
        clone.setReplicaState(ReplicationState.DETACHED.name());
        dbClient.persistObject(clone);
        taskCompleter.ready(dbClient);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.CloneOperations#activateSingleClone(
     * com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void activateSingleClone(StorageSystem storageSystem,
            URI fullCopy,
            TaskCompleter completer)
    {
        // Not supported
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void restoreFromSingleClone(StorageSystem storageSystem,
            URI clone, TaskCompleter completer) {
        // no support
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume,
            URI clone, TaskCompleter completer) {
        // no support
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void resyncSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        // no support
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void restoreGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void resyncGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void detachGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void establishVolumeCloneGroupRelation(StorageSystem storage, URI sourceVolume, URI clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

}
