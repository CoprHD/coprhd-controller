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
