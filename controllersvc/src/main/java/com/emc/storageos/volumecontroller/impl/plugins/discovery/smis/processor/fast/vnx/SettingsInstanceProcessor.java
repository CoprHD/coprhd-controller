/**
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

import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
/**
 * Refer CreatePoolSettingProcessor before looking into this
 * After a successful creation of VNX Storage Pool Setting, the call returns the 
 * object Path alone. But to modify the instance we need the teh corresponding CIMInstance
 * This processor gets invoked , on running getInstance on CreatedPoolSetting Object Path. 
 * The instance reference is being stored, so that modify Instance will be called above that.
 * 
 */
public class SettingsInstanceProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(SettingsInstanceProcessor.class);
    
    
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final CIMInstance poolSettingInstance = (CIMInstance) resultObj;
            addInstance(keyMap,operation.get_result(),poolSettingInstance);
            
        }catch(Exception e) {
            _logger.error("Processing Pool Setting Instances failed :",e);
        }
        
    }

   

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
        
    }
}
