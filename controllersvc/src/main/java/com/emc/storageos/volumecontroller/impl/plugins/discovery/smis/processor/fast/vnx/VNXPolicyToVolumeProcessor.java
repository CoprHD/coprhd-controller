/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * refer FASTPolicyProcessor before looking into below
 * For each discovered VNX policy, run associatorNames to get the list of
 * Volumes, as volumes are associated directly with Policies.
 * VNXPolicy-->Volumes-->Pool
 * Add the Volumes to volumeList, which will be used later to get Pools.
 * 
 */
public class VNXPolicyToVolumeProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(VNXPolicyToVolumeProcessor.class);

    private List<Object> _args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            // value will be set already always
            Object[] arguments = (Object[]) _args.get(0);
            CIMObjectPath vnxFastPolicyRule = (CIMObjectPath) arguments[0];
            // construct VolumeID-->PolicyRule mapping
            while (it.hasNext()) {
                CIMObjectPath volumePath = it.next();
                String volumeID = volumePath.getKey(Constants.DEVICEID).getValue().toString();
                keyMap.put(volumeID, vnxFastPolicyRule);
                addPath(keyMap, Constants.STORAGEVOLUMES, volumePath);
            }
        } catch (Exception e) {
            _logger.error(
                    "FAST Policy Discovery : DiscoveryExtracting Volumes from Policy failed",
                    e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
