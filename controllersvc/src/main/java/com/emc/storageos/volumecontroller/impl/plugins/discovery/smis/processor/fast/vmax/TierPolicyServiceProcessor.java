/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vmax;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;

import com.emc.storageos.db.client.constraint.URIQueryResultList;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class TierPolicyServiceProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(TierPolicyServiceProcessor.class);
    private static final String SYSTEMNAME = "SystemName";
    private DbClient _dbClient;
    private AccessProfile profile;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        boolean tierServiceFound = false;
        try {
           
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            String serialID = (String) keyMap.get(Constants._serialID);
            URI storageSystemURI = profile.getSystemId();
            
            while (it.hasNext()) {
                CIMObjectPath tierPolicyService = it.next();
                String systemName = tierPolicyService.getKey(SYSTEMNAME).getValue()
                        .toString();
                
                if (systemName.contains(serialID)) {
                    tierServiceFound = true;
                    if (systemName.toLowerCase().contains("symmetrix")) {
                        keyMap.put(Constants.VMAXTierPolicyService, tierPolicyService);
                        
                    } else if (systemName.toLowerCase().contains("clariion")) {
                        keyMap.put(Constants.VNXTierPolicyService, tierPolicyService);
                        
                    }
                   
                }
            }
            
            setFASTStatusOnStorageSystem(storageSystemURI, tierServiceFound);
        } catch (Exception e) {
            _logger.error("Tier Policy Service Discovery Failed : ", e);
        } finally {
                 
        }
    }
    
    private void setFASTStatusOnStorageSystem(URI storageSystemuri, boolean tierServiceFound) throws IOException {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, storageSystemuri);
        if (null == system)
            return;
        system.setAutoTieringEnabled(tierServiceFound);
        _dbClient.persistObject(system);
        
    }
   

   
    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
