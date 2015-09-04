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

public class XIVStorageIPProtocolEndPointProcessor extends StorageEndPointProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(XIVStorageIPProtocolEndPointProcessor.class);
    private DbClient _dbClient;
    private static final String IPv4Address = "IPv4Address";
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
                CIMInstance ipPointInstance = null;
                StoragePort port = null;
                try {
                    ipPointInstance = it.next();
                    String portInstanceID = getObjectPathfromCIMArgument(args).toString();
                    port = _dbClient.queryObject(StoragePort.class, (URI) keyMap.get(portInstanceID));
                    keyMap.put(ipPointInstance.getObjectPath().toString(), port.getId());
                    updateIPEndPointDetails(port, ipPointInstance);
                } catch (Exception e) {
                    _logger.warn("Port IP End Point Discovery failed for {}-->{}", "",
                            getMessage(e));
                }
            }
        } catch (Exception e) {
            _logger.error("Port IP End Point Discovery failed -->{}", getMessage(e));
        } finally {
        }
    }

    /**
     * update End Point Details
     * 
     * @param port
     * @param ipPointInstance
     * @throws IOException
     */
    private void updateIPEndPointDetails(StoragePort port, CIMInstance ipPointInstance) throws IOException {
        if (null != port) {
            updateIPAddress(getCIMPropertyValue(ipPointInstance, IPv4Address), port);
            _dbClient.persistObject(port);
        }
    }

    /**
     * 
     * If ipAddress not available, then don't set it.
     * 
     * @param ipAddress
     * @param port
     */
    private void updateIPAddress(String ipAddress, StoragePort port) {
        // IF IP address not set, the value is being set to IQN Name in SMIS Provider.
        // hence set IP address field only, if the value is not equal to IQN Name
        if (null != ipAddress && !ipAddress.equalsIgnoreCase(port.getPortNetworkId())) {
            port.setIpAddress(ipAddress);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
