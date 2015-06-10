/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import java.net.URI;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;

public class XIVCloneOperations implements CloneOperations {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVCloneOperations.class);
    private static final String CREATE_ERROR_MSG_FORMAT = "from %s to %s";

    private DbClient _dbClient;
    private XIVSmisCommandHelper _helper;
    private IBMCIMObjectPathFactory _cimPath;
    private NameGenerator _nameGenerator;
    private XIVSmisStorageDevicePostProcessor _smisStorageDevicePostProcessor;

    public void setCimObjectPathFactory(
            IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(XIVSmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    public void setSmisStorageDevicePostProcessor(
            final XIVSmisStorageDevicePostProcessor smisStorageDevicePostProcessor) {
        _smisStorageDevicePostProcessor = smisStorageDevicePostProcessor;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void createSingleClone(StorageSystem storageSystem,
            URI sourceVolume, URI cloneVolume, Boolean createInactive,
            TaskCompleter taskCompleter) {
        _log.info("START createSingleClone operation");
        try {
            BlockObject sourceObj = BlockObject.fetch(_dbClient, sourceVolume);
            URI tenantUri = null;

            if (sourceObj instanceof BlockSnapshot) {
                // In case of snapshot, get the tenant from its parent volume
                NamedURI parentVolUri = ((BlockSnapshot) sourceObj).getParent();
                Volume parentVolume = _dbClient.queryObject(Volume.class,
                        parentVolUri);
                tenantUri = parentVolume.getTenant().getURI();
            } else {
                tenantUri = ((Volume) sourceObj).getTenant().getURI();
            }

            Volume cloneObj = _dbClient.queryObject(Volume.class, cloneVolume);
            StoragePool targetPool = _dbClient.queryObject(StoragePool.class,
                    cloneObj.getPool());
            TenantOrg tenantOrg = _dbClient.queryObject(TenantOrg.class,
                    tenantUri);
            String cloneLabel = _nameGenerator.generate(tenantOrg.getLabel(),
                    cloneObj.getLabel(), cloneObj.getId().toString(), '-',
                    SmisConstants.MAX_VOLUME_NAME_LENGTH);

            CIMObjectPath sourceVolumePath = _cimPath.getBlockObjectPath(
                    storageSystem, sourceObj);
            CIMArgument[] inArgs = _helper
                    .getCloneInputArguments(cloneLabel, sourceVolumePath,
                            storageSystem, targetPool, createInactive);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callReplicationSvc(storageSystem,
                    SmisConstants.CREATE_ELEMENT_REPLICA, inArgs, outArgs);
            _smisStorageDevicePostProcessor.processCloneCreation(storageSystem,
                    cloneVolume, outArgs,
                    (CloneCreateCompleter) taskCompleter);
        } catch (Exception e) {
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT,
                    sourceVolume, cloneVolume);
            _log.error(errorMsg, e);
            SmisException serviceCode = DeviceControllerExceptions.smis
                    .createFullCopyFailure(errorMsg, e);
            taskCompleter.error(_dbClient, serviceCode);
            throw serviceCode;
        }
    }

    @Override
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter taskCompleter) {
        _log.info("START activateSingleClone operation");
        // no operation, set to ready
        taskCompleter.ready(_dbClient);
    }

    @Override
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume,
            TaskCompleter taskCompleter) {
        _log.info("START detachSingleClone operation");
        // no operation, set to ready
        Volume clone = _dbClient.queryObject(Volume.class, cloneVolume);
        clone.setReplicaState(ReplicationState.DETACHED.name());
        _dbClient.persistObject(clone);
        taskCompleter.ready(_dbClient);
    }
    
    @Override
    public void restoreFromSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        _log.info("START restoreFromSingleClone operation");
        // no operation, set to ready
        completer.ready(_dbClient);
    
    }
    
    @Override
    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume, 
            URI clone, TaskCompleter completer) {
        _log.info("START fractureSingleClone operation");
        // no operation, set to ready
        completer.ready(_dbClient);
    }
    
    @Override
    public void resyncSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        _log.info("START resyncSingleClone operation");
        // no operation, set to ready
        completer.ready(_dbClient);
    }
    
    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
                                  Boolean createInactive, TaskCompleter taskCompleter) {
     // no operation, set to ready
        taskCompleter.ready(_dbClient);
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
    public void detachGroupClones(StorageSystem storageSystem, List<URI> clone,TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        
    }
}
