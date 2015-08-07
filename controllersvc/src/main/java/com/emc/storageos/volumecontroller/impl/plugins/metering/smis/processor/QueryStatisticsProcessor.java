/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;

/**
 * Responsible for retrieving Provider's Last Collection TimeStamp.
 */
public class QueryStatisticsProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(QueryStatisticsProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<?> it = (Iterator<?>) resultObj;
            // Only 1 entry per each Array always
            while (it.hasNext()) {
                final CIMInstance queryInstance = (CIMInstance) it.next();
                keyMap.put(Constants._TimeCollected, System.currentTimeMillis());
                addPath(keyMap, operation.getResult(), queryInstance.getObjectPath());
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
