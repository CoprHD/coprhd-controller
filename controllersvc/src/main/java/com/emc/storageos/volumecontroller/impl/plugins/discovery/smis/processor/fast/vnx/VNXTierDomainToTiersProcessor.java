/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;

public class VNXTierDomainToTiersProcessor extends AbstractFASTPolicyProcessor {
    private Logger _logger = LoggerFactory.getLogger(VNXTierDomainToTiersProcessor.class);
    private DbClient _dbClient;
    List<Object> _args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            // indicates ExtremePerformance or Performance or Capacity Tier
            Object[] arguments = (Object[]) _args.get(0);
            CIMObjectPath tierDomainPath = (CIMObjectPath) arguments[0];
            CIMObjectPath storagePoolpath = (CIMObjectPath) keyMap.get(tierDomainPath
                    .getKey(Constants.NAME).getValue());
            addTiersToPool(storagePoolpath, it, _dbClient, keyMap);
        } catch (Exception e) {
            _logger.error("VNX TierDomain to Tier Processing failed :", e);
        }
    }

   

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
