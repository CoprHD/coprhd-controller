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
