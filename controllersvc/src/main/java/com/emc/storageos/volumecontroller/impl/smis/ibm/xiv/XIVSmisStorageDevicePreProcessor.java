/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;

/*
 * TODO - place holder for now
 */
public class XIVSmisStorageDevicePreProcessor {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSmisStorageDevicePreProcessor.class);
    private XIVSmisCommandHelper _helper;
    private IBMCIMObjectPathFactory _cimPath;
    private CIMConnectionFactory _cimConnection = null;

    public void setCimObjectPathFactory(
            IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(XIVSmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setCimConnectionFactory(CIMConnectionFactory connectionFactory) {
        _cimConnection = connectionFactory;
    }
}
