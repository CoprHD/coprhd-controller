/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * 
 * Check if ManifestCollection already exists in the provider or not.
 * If doesn't exist then we will create a new manifestCollection.
 * 
 */
public class CheckManifestExistsProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(CheckManifestExistsProcessor.class);

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            while (it.hasNext()) {
                CIMInstance manifestInstance = null;
                try {
                    manifestInstance = it.next();
                    String manifestName = getCIMPropertyValue(manifestInstance, Constants.ELEMENTNAME);
                    if (null != manifestName && Constants.MANIFEST_COLLECTION_NAME.equalsIgnoreCase(manifestName)) {
                        _logger.info("Found manifest in provider {}", manifestName);
                        addPath(keyMap, operation.getResult(), manifestInstance.getObjectPath());
                        List<String> manifestCollectionList = (List<String>) keyMap.get(Constants.MANIFEST_EXISTS);
                        manifestCollectionList.remove(Constants.MANIFEST_COLLECTION_NAME);
                        break;
                    }

                } catch (Exception e) {
                    _logger.warn("ManifestCollection call failed for {}",
                            getCIMPropertyValue(manifestInstance, Constants.INSTANCEID), e);
                }
            }
        } catch (Exception e) {
            _logger.error("Processing ManifestCollection Existence failed", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
