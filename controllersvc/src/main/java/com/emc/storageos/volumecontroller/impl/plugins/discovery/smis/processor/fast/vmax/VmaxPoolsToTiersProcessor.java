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

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;

import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;

public class VmaxPoolsToTiersProcessor extends AbstractFASTPolicyProcessor{

private Logger _logger = LoggerFactory.getLogger(VmaxPoolsToTiersProcessor.class);
    
    private DbClient _dbClient;
    List<Object> _args;
    
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {

        @SuppressWarnings("unchecked")
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
       
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            Object[] arguments = (Object[]) _args.get(0);
            CIMObjectPath storagePoolPath = (CIMObjectPath) arguments[0];
            addTiersToPool(storagePoolPath, it, _dbClient, keyMap);
            
        }catch(Exception e) {
            _logger.error("VMAX Pools To Tiers Processing failed :", e );
        }
        
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
     _args = inputArgs;
        
    }
}
