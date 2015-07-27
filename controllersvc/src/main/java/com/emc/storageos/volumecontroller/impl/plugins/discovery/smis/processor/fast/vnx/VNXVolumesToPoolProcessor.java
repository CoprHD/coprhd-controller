/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;


import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;

import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;

/** refer VNXPolicyToVolumesProcessor before looking into below
  * get Pools associated with Volumes for VNX
 * Mapping had been already constructed between Volumes --> Policy in previous SMI-S Call
 * while getting Policy--->Volumes for VNX
 * With existing mapping information, we can build the relationship between Policy--->Pools
 * for VNX
 */
public class VNXVolumesToPoolProcessor extends AbstractFASTPolicyProcessor{
    private Logger _logger = LoggerFactory.getLogger(VNXVolumesToPoolProcessor.class);
    
    private DbClient _dbClient;
    List<Object> _args;
    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            //values previously set
            Object[] arguments = (Object[]) _args.get(0);
            CIMObjectPath volumePath = (CIMObjectPath) arguments[0];
            //Mapping had been already constructed between Volumes --> Policy in previous SMI-S Call
            // while getting Policy--->Volumes for VNX
            CIMObjectPath policyPath =  (CIMObjectPath) keyMap.get(volumePath.getKey(Constants.DEVICEID).getValue());
            //add Pools to POlicy
          //  addStoragePoolstoPolicy(policyPath, it, _dbClient,keyMap);
                     
        }catch(Exception e) {
            _logger.error("Extracting Pools from Volumnes failed on FAST Polciy Discovery",e);
        }
    }

   
    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
       _args= inputArgs;
        
    }
}
