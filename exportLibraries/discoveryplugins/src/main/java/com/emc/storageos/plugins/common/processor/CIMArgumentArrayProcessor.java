/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common.processor;

import java.util.List;
import java.util.Map;
import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;


/**
 * Responsible for handling CIMArgument[] outputs, get CIMPath and update it in
 * Map.
 */
public class CIMArgumentArrayProcessor extends Processor {
    protected Logger _logger = LoggerFactory.getLogger(CIMArgumentArrayProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            if (resultObj instanceof CIMArgument<?>[]) {
                CIMArgument<?>[] _outputArguments = (CIMArgument<?>[]) resultObj;
                CIMObjectPath _path = (CIMObjectPath) _outputArguments[0].getValue();
                keyMap.put(operation.getResult(), _path);
            }
        } catch (Exception e) {
            _logger.error("Failed while processing Result", e);
        }
    }

    @Override
    public void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
