/**
 *  Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

/**
 * Process AllocatedFromStoragePool instances.
 */
public class AllocatedFromStoragePoolProcessor extends StorageProcessor {
    private static final Logger _logger = LoggerFactory
            .getLogger(AllocatedFromStoragePoolProcessor.class);
    private Map<String, String> _volumeToSpaceConsumedMap;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.debug("Calling AllocatedFromStoragePoolProcessor");
        _volumeToSpaceConsumedMap = (Map<String, String>) keyMap.get(Constants.VOLUME_SPACE_CONSUMED_MAP);
        processResultbyChunk(resultObj, keyMap);
    }

    @Override
    protected int processInstances(Iterator<CIMInstance> instances) {
        int count = 0;
        while (instances.hasNext()) {
            try {
                count++;
                CIMInstance instance = instances.next();
                CIMObjectPath volPath = (CIMObjectPath) instance.getObjectPath().getKeyValue(DEPENDENT);
                if (_symmvolume.equals(volPath.getObjectName())) {
                    String spaceConsumed = getCIMPropertyValue(instance, SPACE_CONSUMED);
                    _volumeToSpaceConsumedMap.put(volPath.toString(), spaceConsumed);
                }
            } catch (Exception e) {
                _logger.error("Exception on processing instances", e);
            }
        }

        return count;
    }
}
