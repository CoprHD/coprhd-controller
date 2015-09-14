/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

/**
 * Processor Responsible for finding out volumes which are part of existing consistency groups.
 */

public class ReplicationGroupToVolumeProcessor extends StorageProcessor {

    private Logger _logger = LoggerFactory.getLogger(ReplicationGroupToVolumeProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
        @SuppressWarnings("unchecked")
        final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
        Set<String> volumePaths = new HashSet<String>();
        try {
            while (it.hasNext()) {
                CIMObjectPath path = it.next();
                String volumeNativeGuid = getVolumeNativeGuid(path);
                volumePaths.add(volumeNativeGuid);
            }
            // add these volumes to container, which will be used later to
            // skip creation of unmanaged volumes.
            if (!keyMap.containsKey(Constants.VOLUMES_PART_OF_CG)) {
                keyMap.put(Constants.VOLUMES_PART_OF_CG, volumePaths);
            } else {
                @SuppressWarnings("unchecked")
                Set<String> existingVolumesInCG = (Set<String>) keyMap
                        .get(Constants.VOLUMES_PART_OF_CG);
                existingVolumesInCG.addAll(volumePaths);
            }
        } catch (Exception e) {
            _logger.error("Discovering Volumes part of Consistency Group failed.", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

}
