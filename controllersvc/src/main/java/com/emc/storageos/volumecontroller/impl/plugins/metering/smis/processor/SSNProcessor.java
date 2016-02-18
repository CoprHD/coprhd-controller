/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
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
import com.emc.storageos.plugins.metering.smis.SMIPluginException;

/**
 * Processor used to calculate the capacity of Snapshots for a Volume, and the
 * SnapShots Count for a Volume. This class might not be useful , for the new
 * VNX Snapshots. StorageSynchronizedProcessor
 */
public class SSNProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(SSNProcessor.class);

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws SMIPluginException {
        CloseableIterator<CIMInstance> synchronizedInstances = null;
        EnumerateResponse<CIMInstance> synchronizedInstanceChunks = null;
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);

        try {
            synchronizedInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
            synchronizedInstances = synchronizedInstanceChunks.getResponses();

            processSynchronizedInstance(synchronizedInstances, keyMap);

            while (!synchronizedInstanceChunks.isEnd()) {
                synchronizedInstanceChunks = client.getInstancesWithPath(Constants.SYNC_PATH, synchronizedInstanceChunks
                        .getContext(), Constants.SYNC_BATCH_SIZE);
                processSynchronizedInstance(synchronizedInstanceChunks.getResponses(), keyMap);
            }
        } //
        catch (Exception e) {
            if (!(e instanceof BaseCollectionException)) {
                _logger.error("Processing Snapshots failed : ", e);
            }
        } finally {
            if (null != synchronizedInstances) {
                synchronizedInstances.close();
            }
            if (null != synchronizedInstanceChunks) {
                try {
                    client.closeEnumeration(Constants.SYNC_PATH, synchronizedInstanceChunks.getContext());
                } catch (WBEMException e) {
                    _logger.warn("Exception occurred while closing enumeration", e);
                }
            }
        }

        resultObj = null;
    }

    private void processSynchronizedInstance(CloseableIterator<CIMInstance> synchronizedInstances, Map<String, Object> keyMap)
            throws Exception {
        while (synchronizedInstances.hasNext()) {

            CIMInstance instance = synchronizedInstances.next();
            CIMObjectPath volumePath = instance.getObjectPath();
            CIMObjectPath sourcePath = (CIMObjectPath) volumePath.getKey(
                    Constants._SystemElement).getValue();
            CIMObjectPath destPath = (CIMObjectPath) volumePath.getKey(
                    Constants._SyncedElement).getValue();
            String syncType = instance.getProperty(Constants._SyncType).getValue()
                    .toString();
            CIMProperty<?> prop = sourcePath.getKey(Constants._SystemName);
            String[] serialNumber_split = prop.getValue().toString().split(Constants.PATH_DELIMITER_REGEX);
            if (serialNumber_split[1].equalsIgnoreCase((String) keyMap
                    .get(Constants._serialID))) {
                _logger.debug(
                        "Finding Snapshots for Volumes in this Array with SyncType : {} : {}",
                        prop.getValue().toString(), syncType);
                if (syncType.equalsIgnoreCase(Constants._Seven)) {
                    String key = createKeyfromPath(sourcePath);
                    if (null == getMetrics(keyMap, key)
                            || !(getMetrics(keyMap, key) instanceof Stat)) {
                        return;
                    }
                    Stat metrics = (Stat) getMetrics(keyMap, key);

                    String destkey = createKeyfromPath(destPath);
                    Object value = getMetrics(keyMap, destkey);
                    if (null == value) {
                        return;
                    }

                    Long allocatedCapacityForSnapShots = 0L;

                    if (value instanceof Stat) {
                        Stat syncedMetrics = (Stat) value;
                        allocatedCapacityForSnapShots = syncedMetrics
                                .getAllocatedCapacity();
                    } else {
                        allocatedCapacityForSnapShots = (Long) value;
                    }
                    // removed parallel processing from framework, hence synchronization is not needed.
                    metrics.setSnapshotCount(metrics.getSnapshotCount() + 1);
                    metrics.setSnapshotCapacity(metrics.getSnapshotCapacity()
                            + allocatedCapacityForSnapShots);
                }
            }
        }

    }

    @Override
    public void setPrerequisiteObjects(List<Object> inputArgs) throws SMIPluginException {
    }
}