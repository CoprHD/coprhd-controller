/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * Used to collect the CIM CLOCK_TICK_INTERVAL returned by CIM_BlockStatisticsCapabilities.
 * @author watson
 */
public class BlockStatisticsCapabilitiesProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(BlockStatisticsCapabilitiesProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            final Iterator<?> it = (Iterator<?>) resultObj;
            // Only 1 entry per each Array always
            while (it.hasNext()) {
                final CIMInstance queryInstance = (CIMInstance) it.next();
                CIMProperty prop = queryInstance.getProperty(Constants.CLOCK_TICK_INTERVAL);
                keyMap.put(Constants.CLOCK_TICK_INTERVAL, prop.getValue().toString());
            }
        } catch (Exception e) {
            _logger.error("Failed while processing QueryStatistics :", e);
        }
        resultObj = null;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

}
