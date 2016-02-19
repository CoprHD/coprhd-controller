/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

/* 
 * Processor used in updating the Access State of unmanaged volumes.
 * VolumeView classes doesn't have Access property attached to them , hence right now, 
 * we don't have any other option again to get Volumes from Pools.
 */
public class VolumeAccessStateProcessor extends StorageProcessor {

    private Logger _logger = LoggerFactory.getLogger(VolumeAccessStateProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;
    private PartitionManager _partitionManager;

    List<UnManagedVolume> _unManagedVolumesUpdate = null;
    Set<URI> unManagedVolumesReturnedFromProvider = new HashSet<URI>();

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        CloseableIterator<CIMInstance> volumeInstances = null;
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            _unManagedVolumesUpdate = new ArrayList<UnManagedVolume>();
            CIMObjectPath storagePoolPath = getObjectPathfromCIMArgument(_args);
            String poolNativeGuid = NativeGUIDGenerator
                    .generateNativeGuidForPool(storagePoolPath);
            StoragePool pool = checkStoragePoolExistsInDB(poolNativeGuid, _dbClient);
            if (pool == null) {
                _logger.error(
                        "Skipping unmanaged volume discovery of Access Sattes as the storage pool with path {} doesn't exist in ViPR",
                        storagePoolPath.toString());
                return;
            }
            EnumerateResponse<CIMInstance> volumeInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
            volumeInstances = volumeInstanceChunks.getResponses();

            processVolumes(volumeInstances, keyMap, operation);
            while (!volumeInstanceChunks.isEnd()) {
                _logger.debug("Processing Next Volume Chunk of size {}", BATCH_SIZE);
                volumeInstanceChunks = client.getInstancesWithPath(storagePoolPath,
                        volumeInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processVolumes(volumeInstanceChunks.getResponses(), keyMap, operation);
            }
            if (null != _unManagedVolumesUpdate && !_unManagedVolumesUpdate.isEmpty()) {
                _partitionManager.updateInBatches(_unManagedVolumesUpdate,
                        getPartitionSize(keyMap), _dbClient, "UnManagedVolume");
            }

        } catch (Exception e) {
            _logger.error("Discovering Access States of unManaged Volumes failed", e);
        } finally {
            volumeInstances.close();
        }

    }

    private void processVolumes(Iterator<CIMInstance> it,
            Map<String, Object> keyMap, Operation operation) {

        while (it.hasNext()) {

            try {
                CIMInstance volumeInstance = it.next();
                CIMObjectPath volumePath = volumeInstance.getObjectPath();
                // TODO add logic to get Access
                String access = null;
                Object value = volumeInstance.getPropertyValue(SupportedVolumeInformation.ACCESS.toString());
                if (value != null) {
                    access = value.toString();
                }
                StringSet statusDesc = new StringSet();
                String[] descriptions = null;
                value = volumeInstance.getPropertyValue(SupportedVolumeInformation.STATUS_DESCRIPTIONS.toString());
                if (value != null) {
                    descriptions = (String[]) value;
                    for (String desc : descriptions) {
                        statusDesc.add(desc);
                    }
                }
                String volumeNativeGuid = getVolumeNativeGuid(volumePath);
                Volume volume = checkStorageVolumeExistsInDB(volumeNativeGuid, _dbClient);
                if (null != volume) {
                    _logger.debug("Skipping discovery, as this Volume is already being managed by ViPR :"
                            + volumeNativeGuid);
                    continue;
                }

                String unManagedVolumeNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
                UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(
                        unManagedVolumeNativeGuid, _dbClient);
                if (null != unManagedVolume) {
                    _logger.debug("Adding Access {}", unManagedVolumeNativeGuid);
                    StringSet accessSet = new StringSet();
                    if (access != null) {
                        accessSet.add(access);
                    }
                    if (null == unManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.ACCESS.toString())) {
                        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.ACCESS.toString(), accessSet);
                    } else {
                        unManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.ACCESS.toString()).replace(accessSet);
                    }

                    if (null == unManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.STATUS_DESCRIPTIONS.toString())) {
                        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.STATUS_DESCRIPTIONS.toString(), statusDesc);
                    } else {
                        unManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.STATUS_DESCRIPTIONS.toString())
                                .replace(statusDesc);
                    }

                    _unManagedVolumesUpdate.add(unManagedVolume);
                }

                if (_unManagedVolumesUpdate.size() > BATCH_SIZE) {
                    _partitionManager.updateInBatches(_unManagedVolumesUpdate,
                            getPartitionSize(keyMap), _dbClient, "UnManagedVolume");
                    _unManagedVolumesUpdate.clear();
                }

            } catch (Exception ex) {
                _logger.error("Processing UnManaged Storage Volume", ex);
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }

}
