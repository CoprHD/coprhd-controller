/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014-2015 EMC Corporation
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

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

/**
 * This processor gets invoked only for VMAX unManaged volume discoveries.
 * It populates mapping between Storage Groups-->Fast Policy
 * later this mapping information is used to find out the fast policies associated with Volumes belonging to SGs.
 */
public class SGsWithFastProcessor extends StorageProcessor {
    private List<Object> _args;
    private Logger _logger = LoggerFactory.getLogger(SGsWithFastProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> policyToStorageGroupMapping = (Map<String, String>) keyMap
                    .get(Constants.POLICY_STORAGE_GROUP_MAPPING);
            CIMObjectPath tierPolicyPath = getObjectPathfromCIMArgument(_args);
            String policyName = tierPolicyPath.getKey(Constants.POLICYRULENAME).getValue().toString();
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            while (it.hasNext()) {
                final CIMObjectPath storageGroupPath = it.next();
                String groupId = getCIMPropertyValue(storageGroupPath, Constants.INSTANCEID);
                _logger.info("Adding Group {} To Policy {} mapping", groupId, policyName);
                policyToStorageGroupMapping.put(groupId, policyName);
                addPath(keyMap, operation.get_result(), storageGroupPath);
            }
        } catch (Exception e) {
            _logger.error("Discovering SGs with FAST failed", e);
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;

    }

}
