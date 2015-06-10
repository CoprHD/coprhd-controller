/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Executor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * This class is responsible for generating the command objects from the
 * operations and also execute them sequentially.
 */
public class VNXFileDiscExecutor extends Executor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory.getLogger(VNXFileDiscExecutor.class);

    @Override
    protected void customizeException(Exception e, Operation operation)
            throws BaseCollectionException {
        Throwable vnxFileEx = e.getCause();

        if (e instanceof IllegalArgumentException) {
            throw new VNXFilePluginException(
                    VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION, e,
                    vnxFileEx != null ? vnxFileEx.toString() : "Wrong number of Arguments.");
        }
        if (e instanceof IllegalAccessException) {
            throw new VNXFilePluginException(
                    VNXFilePluginException.ERRORCODE_ILLEGALACCESSEXCEPTION, e,
                    vnxFileEx != null ? vnxFileEx.toString()
                            : "Access Denied for Class or Method");
        }
        if (e instanceof InvocationTargetException) {
            throw new VNXFilePluginException(
                    VNXFilePluginException.ERRORCODE_INVOCATIONTARGETEXCEPTION, e,
                    vnxFileEx != null ? vnxFileEx.toString() : "Internal Error");
        }
    }

}
