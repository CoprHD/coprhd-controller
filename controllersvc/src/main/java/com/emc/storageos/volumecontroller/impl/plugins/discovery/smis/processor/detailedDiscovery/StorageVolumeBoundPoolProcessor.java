/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.HashSet;
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
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;

/**
 * Processor used in getting bound storage volumes for VMAX2 Thin pool.
 * 
 * A VMAX2 FAST thin volume may be associated with more than one storage pool.
 * one is bound pool where it is originally created from, and the others are because of data movement due to auto-tiering policy.
 * With the bound volumes list for each thin pool, skip the non-bound volumes in StorageVolumeInfoProcessor while creating UnManaged
 * Volumes.
 * 
 */
public class StorageVolumeBoundPoolProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory.getLogger(StorageVolumeBoundPoolProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        CloseableIterator<CIMInstance> allocatedFromStoragePoolInstances = null;
        EnumerateResponse<CIMInstance> allocatedFromStoragePoolInstanceChunks = null;
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> vmax2ThinPoolToBoundVolumesMap = (Map<String, Set<String>>) keyMap
                .get(Constants.VMAX2_THIN_POOL_TO_BOUND_VOLUMES);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
        CIMObjectPath storagePoolPath = null;
        try {
            storagePoolPath = getObjectPathfromCIMArgument(_args);
            _logger.debug("VMAX2 Thin Pool: {}", storagePoolPath.toString());
            String poolNativeGuid = NativeGUIDGenerator.generateNativeGuidForPool(storagePoolPath);
            StoragePool pool = checkStoragePoolExistsInDB(poolNativeGuid, _dbClient);
            if (pool == null) {
                _logger.error(
                        "Skipping unmanaged volume discovery as the storage pool with path {} doesn't exist in ViPR",
                        storagePoolPath.toString());
                return;
            }

            Set<String> boundVolumes = new HashSet<String>();
            allocatedFromStoragePoolInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
            allocatedFromStoragePoolInstances = allocatedFromStoragePoolInstanceChunks.getResponses();

            processVolumes(allocatedFromStoragePoolInstances, boundVolumes);

            while (!allocatedFromStoragePoolInstanceChunks.isEnd()) {
                _logger.info("Processing Next Volume Chunk of size {}", BATCH_SIZE);
                allocatedFromStoragePoolInstanceChunks = client.getInstancesWithPath(storagePoolPath,
                        allocatedFromStoragePoolInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processVolumes(allocatedFromStoragePoolInstanceChunks.getResponses(), boundVolumes);
            }
            vmax2ThinPoolToBoundVolumesMap.put(storagePoolPath.toString(), boundVolumes);
            _logger.debug("Bound volumes list {}", Joiner.on("\t").join(boundVolumes));
        } catch (Exception e) {
            _logger.error("Processing Bound Storage Volume Information failed :", e);
        } finally {
            if (null != allocatedFromStoragePoolInstances) {
                allocatedFromStoragePoolInstances.close();
            }
            if (null != allocatedFromStoragePoolInstanceChunks) {
                try {
                    client.closeEnumeration(storagePoolPath, allocatedFromStoragePoolInstanceChunks.getContext());
                } catch (Exception e) {
                    _logger.warn("Exception occurred while closing enumeration", e);
                }
            }

        }
    }

    private void processVolumes(CloseableIterator<CIMInstance> allocatedFromStoragePoolInstances,
            Set<String> volumesList) {
        while (allocatedFromStoragePoolInstances.hasNext()) {
            CIMInstance allocatedFromStoragePoolInstance = allocatedFromStoragePoolInstances.next();
            String boundToThinStoragePool = allocatedFromStoragePoolInstance.getPropertyValue(SmisConstants.EMC_BOUND_TO_THIN_STORAGE_POOL)
                    .toString();
            if (Boolean.valueOf(boundToThinStoragePool)) {
                String volume = allocatedFromStoragePoolInstance.getPropertyValue(SmisConstants.CP_DEPENDENT).toString();
                CIMObjectPath volumePath = new CIMObjectPath(volume);
                String deviceId = volumePath.getKey(DEVICE_ID).getValue().toString();
                volumesList.add(deviceId);
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;
    }
}
