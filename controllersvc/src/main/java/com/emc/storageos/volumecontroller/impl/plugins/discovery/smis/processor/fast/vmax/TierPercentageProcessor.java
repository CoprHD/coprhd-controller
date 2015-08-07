/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vmax;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;

/**
 * Processor to discover Vmax Tier Percentages associated with Fast Policy
 */
public class TierPercentageProcessor extends AbstractFASTPolicyProcessor {
    private Logger _logger = LoggerFactory.getLogger(TierPercentageProcessor.class);
    private static final String DEPENDENT = "Dependent";
    private static final String MAX_PERCENT_ALLOCATED = "MaxPercentAllocated";
    private DbClient _dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        while (it.hasNext()) {
            try {
                CIMInstance symmAssociatedPolicyInstance = it.next();
                CIMObjectPath symmAssociatedPolicyPath = symmAssociatedPolicyInstance
                        .getObjectPath();
                CIMObjectPath tierPath = (CIMObjectPath) symmAssociatedPolicyPath.getKey(
                        DEPENDENT).getValue();
                String tierPercent = symmAssociatedPolicyInstance.getPropertyValue(
                        MAX_PERCENT_ALLOCATED).toString();
                String tierID = tierPath.getKey(Constants.INSTANCEID).getValue()
                        .toString();
                String tierNativeGuid = getTierNativeGuidForVMax(tierID);
                StorageTier tierObject = checkStorageTierExistsInDB(tierNativeGuid,
                        _dbClient);
                if (null != tierObject) {
                    tierObject.setPercentage(tierPercent);
                    _dbClient.persistObject(tierObject);
                }
            } catch (Exception e) {
                _logger.error("Vmax Tier Percentage Discovery failed", e);
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
