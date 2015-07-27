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
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.common.Processor;

/**
 * Processor responsible for collecting Allocated Capacity 
 * StoragePool Processor, is common for both Volume & Storage pool Approach
 */
public class SPProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(SPProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        final Iterator<?> it = (Iterator<?>) resultObj;
        while (it.hasNext()) {
            try {
                final CIMInstance allocatedfrompool = (CIMInstance) it.next();
                CIMObjectPath path = (CIMObjectPath) allocatedfrompool.getProperty(
                        "Dependent").getValue();
                if (path.getObjectName().contains(_volume)) {
                    String key = createKeyfromPath(path);
                    // this check means, validating whether this Volume is
                    // managed by Bourne
                    Stat metrics = (Stat) getMetrics(keyMap, key);
                    _logger.debug("Processing Volume to extract Allocated Capacity: {}",
                            key);
                    // Allocated Capacity =
                    // CIM_AllocatedFromStoragePool.SpaceConsumed (in bytes)
                    metrics.setAllocatedCapacity(Long.parseLong(allocatedfrompool
                            .getProperty(_spaceConsumed).getValue().toString()));
                }
            } catch (Exception e) {
                if (!(e instanceof BaseCollectionException))
                    _logger.error(" Allocated Capacity : ", e);
            }
        }
        resultObj = null;
    }

    @Override
    public void setPrerequisiteObjects(List<Object> inputArgs) throws SMIPluginException {
        // TODO Auto-generated method stub
    }
}
