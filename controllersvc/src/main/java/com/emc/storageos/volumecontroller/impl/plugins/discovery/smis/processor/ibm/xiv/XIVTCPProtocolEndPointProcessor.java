/**
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
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageEndPointProcessor;

public class XIVTCPProtocolEndPointProcessor extends StorageEndPointProcessor {
    private Logger _logger = LoggerFactory.getLogger(XIVTCPProtocolEndPointProcessor.class);
    private DbClient _dbClient;
    private static final String PORTNUMBER = "portNumber";
    private List<Object> args;
    
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            while (it.hasNext()) {
                CIMInstance tcpPointInstance = null;
                StoragePort port = null;
                try {
                    tcpPointInstance = it.next();
                    String portInstanceID = getObjectPathfromCIMArgument(args).toString();
                    port = _dbClient.queryObject(StoragePort.class, (URI)keyMap.get(portInstanceID));
                    updateTCPEndPointDetails(port, tcpPointInstance);
                } catch (Exception e) {
                    _logger.warn("Port TCP End Point Discovery failed for {}-->{}", "",
                            getMessage(e));
                }
            }
        } catch (Exception e) {
            _logger.error("Port TCP End Point Discovery failed -->{}", getMessage(e));
        }
    }

    /**
     * update TCP Port Number details
     * 
     * @param port
     * @param tcpPointInstance
     * @throws IOException
     */
    private void updateTCPEndPointDetails(StoragePort port, CIMInstance tcpPointInstance) throws IOException {
        if (null != port) {
            port.setTcpPortNumber(Long.parseLong(getCIMPropertyValue(tcpPointInstance,
                    PORTNUMBER)));
            _dbClient.persistObject(port);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
