/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.PoolProcessor;

/**
 * Processor responsible to process the result of the following CIM Call.
 * 
 * ai(ref-pool, null, EMC_StoragePoolCapabilities...) => Instances of EMC_StorageCapabilities.
 * 
 * and add the response to the KeyMap for further processing.
 * Currently this call is supported only for vmax3 systems.
 * 
 */
public class PoolCapabilitiesProcessor extends PoolProcessor {

    private Logger _logger = LoggerFactory.getLogger(PoolCapabilitiesProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap
                    .get(Constants.ACCESSPROFILE);
            StorageSystem device = getStorageSystem(_dbClient,
                    profile.getSystemId());
            // Process the response only for vmax3 systems.
            if (device.checkIfVmax3()) {
                while (it.hasNext()) {
                    CIMInstance capabilitiesInstance = null;
                    try {
                        capabilitiesInstance = it.next();
                        String instanceID = capabilitiesInstance.getPropertyValue(
                                Constants.INSTANCEID).toString();
                        addPath(keyMap, operation.get_result(), capabilitiesInstance.getObjectPath());
                    } catch (Exception e) {
                        _logger.warn(
                                "Pool Capabilities detailed discovery failed for {}-->{}",
                                capabilitiesInstance.getObjectPath(), getMessage(e));
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Pool Capabilities detailed discovery failed", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
