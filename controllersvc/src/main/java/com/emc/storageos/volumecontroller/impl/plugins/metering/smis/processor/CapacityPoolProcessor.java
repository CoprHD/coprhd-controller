/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * 
 * Responsible for calculating Provisioned Capacity via Storage Pool Relations.
 * StoragaePool--->CIM_AllocatedFromStoragePool If, huge number of Volumes are
 * managed by Bourne, its better to get Volume Instances in bulk, i.e. via
 * Storage Pools rather than getting AllocatedFromStoragePool for each Volume.
 * ProvisionedCapacityProcessor- Responsible for calculating Provisioned
 * Capacity for Volume via Volume Relations Formula for calculating Provisioned
 * Capacity : NumberofBlocks * BlockSize. Synchronized Block is not needed for
 * either retrieving Metrics Object from Map or while adding provisioned
 * capacity to Metrics. as Each thread is responsible for handling a unique
 * Volume Instance which doesn't share any information across other threads.
 * To-Do: Currently, the underlying SBLIM library takes up the responsibility of
 * parsing CIMXML and provide us Instances back. These Instances are kept in
 * memory for further processing.Need to analyze the memory impact. If needed,
 * look for alternatives like using Native Heaps via EhCache, or Mem-cache
 */
public class CapacityPoolProcessor extends Processor {

    private static final int BATCH_SIZE = 200;
    private List<Object> _args;

    private Logger _logger = LoggerFactory.getLogger(CapacityPoolProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        EnumerateResponse<CIMInstance> volumeInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);

        CloseableIterator<CIMInstance> volumeInstances = volumeInstanceChunks.getResponses();
        try {
            processVolumeCapacity(volumeInstances, keyMap);
            while (!volumeInstanceChunks.isEnd()) {
                CIMObjectPath storagePoolPath = getObjectPathfromCIMArgument(_args);
                _logger.info("Processing Next Volume Chunk of size {}",
                        BATCH_SIZE);
                volumeInstanceChunks = client.getInstancesWithPath(
                        storagePoolPath, volumeInstanceChunks.getContext(),
                        new UnsignedInteger32(BATCH_SIZE));
                processVolumeCapacity(volumeInstanceChunks.getResponses(),
                        keyMap);
            }
        } catch (Exception e) {
            _logger.error("Provisioned Capacity failure :", e);
        } finally {
            resultObj = null;
            if (null != volumeInstances) {
                volumeInstances.close();
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> arg0)
            throws BaseCollectionException {
        _args = arg0;
    }

    /**
     * Process volume capacity, iterates over the given chunk and process
     * each volume capacity.
     * 
     * @param volumeInstances {@link CloseableIterator} instance
     * @param keyMap {@link Map} instance
     */
    private void processVolumeCapacity(
            CloseableIterator<CIMInstance> volumeInstances,
            Map<String, Object> keyMap) {
        while (volumeInstances.hasNext()) {
            try {
                final CIMInstance volumeInstance = (CIMInstance) volumeInstances
                        .next();
                String key = null;
                String spaceConsumed = null;
                if (keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                        && Boolean.valueOf(keyMap.get(
                                Constants.IS_NEW_SMIS_PROVIDER).toString())) {
                    key = createKeyfor8x(volumeInstance);
                    spaceConsumed = volumeInstance
                            .getProperty(_emcspaceConsumed).getValue()
                            .toString();
                } else {
                    key = createKeyfromProps(volumeInstance);
                    spaceConsumed = volumeInstance.getProperty(_spaceConsumed)
                            .getValue().toString();
                }
                Object value = getMetrics(keyMap, key);

                if (null == value) {
                    keyMap.put(key, Long.parseLong(spaceConsumed));
                } else if (value instanceof Stat) {
                    Stat metrics = (Stat) value;
                    metrics.setProvisionedCapacity(returnProvisionedCapacity(
                            volumeInstance, keyMap));
                    metrics.setAllocatedCapacity(Long.parseLong(spaceConsumed));
                }
            } catch (Exception ex) {
                // This check will make sure to skip unnecessary logs
                if (!(ex instanceof BaseCollectionException)) {
                    _logger.error("Provisioned Capacity failure : ", ex);
                }
            }
        }

    }
}
