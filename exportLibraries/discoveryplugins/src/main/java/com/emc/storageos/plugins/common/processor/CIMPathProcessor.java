/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * Responsible for handling Iterators and updating CIMPaths in Map. This
 * processor is responsible for handling Result from providers of type
 * CIMObjectPaths. It just extracts the CIMOBject and push into the Map.
 */
public class CIMPathProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(CIMPathProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        String serialID = null;
        _logger.debug("ID :" + (String) keyMap.get(Constants._serialID));
        try {
            final Iterator<?> it = (Iterator<?>) resultObj;
            while (it.hasNext()) {
                final CIMObjectPath path = (CIMObjectPath) it.next();
                if (operation.getMethod().contains(Constants._enum)) {
                    CIMProperty<?> ccprop = path.getKey(Constants._CreationClassName);
                    String ccName = (String) ccprop.getValue();
                    _logger.debug("CCName :" + ccName);
                    if (ccName.contains(Constants._StorageSystem)) {
                        // Filter out the ObjectPath based on Array Serial ID
                        CIMProperty<?> prop = path.getKey(Constants._Name);
                        serialID = (String) prop.getValue();
                        _logger.info("serial ID Found:" + serialID);
                        if (serialID != null && serialID.toLowerCase().contains(((String) keyMap
                                .get(Constants._serialID)).toLowerCase()))
                        {
                            addPath(keyMap, operation.getResult(), path);
                            break;
                        }
                    }
                } else {
                    addPath(keyMap, operation.getResult(), path);
                }
            }
        } catch (Exception e) {
            _logger.error("Failed while processing Result with serialID : {}", serialID,
                    e.fillInStackTrace());
        }
    }

    @Override
    public void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
    }
}
