/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.SRDF;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;

import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;

import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;

import com.emc.storageos.plugins.common.domainmodel.Operation;

import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

public class ProtocolEndPointToPortProcessor extends StorageProcessor {

    private Logger _log = LoggerFactory.getLogger(ProtocolEndPointToPortProcessor.class);
    private List<Object> args;

    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {

        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            CIMObjectPath protocolEndPointPath = getObjectPathfromCIMArgument(args);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            String protocolEndPointId = protocolEndPointPath.getKey(Constants.NAME).getValue().toString();
            _log.info("Protocol End Point ID :" + protocolEndPointId);
            @SuppressWarnings("unchecked")
            Map<String, URI> volumeToRAGroupMap = (Map<String, URI>) keyMap.get(Constants.RAGROUP);
            URI remoteRAGroupUri = volumeToRAGroupMap.get(protocolEndPointId);
            _log.info("Remote RA Group URI :" + remoteRAGroupUri);
            String sourceSystemSerialId = keyMap.get(Constants._serialID).toString();
            _log.info("Source Serial ID :" + sourceSystemSerialId);
            RemoteDirectorGroup remoteGroup = _dbClient.queryObject(RemoteDirectorGroup.class, remoteRAGroupUri);
            if (remoteGroup == null) {
                _log.info("RA Group Not Found {}", remoteRAGroupUri);
            }
            while (it.hasNext()) {
                CIMInstance portInstance = it.next();
                StoragePort port = checkStoragePortExistsInDB(portInstance, device, _dbClient);
                if (null == port) {
                    _log.info("RA Group Port Not Found {}", portInstance.getObjectPath());
                    continue;
                }
                if (portInstance.getObjectPath().toString().contains(sourceSystemSerialId)) {
                    remoteGroup.setSourcePort(port.getId());
                    _log.info("Source Port added :" + portInstance.getObjectPath());
                } else {
                    remoteGroup.setRemotePort(port.getId());
                    _log.info("Remote Port added :" + portInstance.getObjectPath());
                }

                _dbClient.persistObject(remoteGroup);
            }
        } catch (Exception e) {
            _log.error("Discovering Ports for RA Groups failed", e);
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;

    }

}
