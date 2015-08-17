/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
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

public class PoolsToTierDomainProcessor extends Processor {

    private Logger _logger = LoggerFactory.getLogger(PoolsToTierDomainProcessor.class);
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
            CIMObjectPath vnxPoolObjectPath = (CIMObjectPath) arguments[0];
            // construct a Map (TierDomainID--->StoragePool), this is needed to construct
            // the relationship between Pools--->Tiers
            while (it.hasNext()) {
                CIMObjectPath tierDomainPath = it.next();
                String tierDomainID = tierDomainPath.getKey(Constants.NAME).getValue().toString();
                addPath(keyMap, Constants.TIERDOMAINS, tierDomainPath);
                keyMap.put(tierDomainID, vnxPoolObjectPath);
            }
        } catch (Exception e) {
            _logger.error("VNX Pools to Tier Domain Processing failed :", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
