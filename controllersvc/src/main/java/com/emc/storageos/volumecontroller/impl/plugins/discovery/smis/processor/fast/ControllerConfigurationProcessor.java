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
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast;

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
 * Processor used in extracting EMC_ControllerConfigurationService for
 * VMAX and VNX.
 * EMC_ControllerConfiguration Service is being stored in Map using key.
 * later it will be used for other SMI-S operations.
 * 
 */
public class ControllerConfigurationProcessor extends Processor {
    private Logger _logger = LoggerFactory
            .getLogger(ControllerConfigurationProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            while (it.hasNext()) {
                CIMObjectPath controllerConfigurationService = it.next();
                String systemName = controllerConfigurationService
                        .getKey(Constants.SYSTEMNAME).getValue().toString();
                String serialID = (String) keyMap.get(Constants._serialID);
                if (systemName.contains(serialID)) {
                    addPath(keyMap, operation.getResult(), controllerConfigurationService);
                    if (systemName.toLowerCase().contains(Constants.SYMMETRIX)) {
                        keyMap.put(Constants.VMAXConfigurationService,
                                controllerConfigurationService);
                    } else if (systemName.toLowerCase().contains(Constants.CLARIION)) {
                        keyMap.put(Constants.VNXConfigurationService,
                                controllerConfigurationService);
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Controller Configuration Service Discovery Failed : ", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
    }
}
