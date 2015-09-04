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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageEndPointProcessor;
import com.google.common.base.Joiner;

public class XIVStorageProtocolEndPointProcessor extends StorageEndPointProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(XIVStorageProtocolEndPointProcessor.class);
    private DbClient _dbClient;
    private static final String NAME = "Name";
    private List<Object> args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            @SuppressWarnings("unchecked")
            Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap.get(Constants.MODIFIED_STORAGEPOOLS);
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            List<StoragePort> newPorts = new ArrayList<StoragePort>();
            List<StoragePort> existingPorts = new ArrayList<StoragePort>();
            while (it.hasNext()) {
                CIMInstance endPointInstance = null;
                StoragePort port = null;
                try {
                    endPointInstance = it.next();
                    String name = getCIMPropertyValue(endPointInstance, NAME).toLowerCase();

                    String portInstanceID = getObjectPathfromCIMArgument(args).toString();
                    port = checkEthernetStoragePortExistsInDB(name, _dbClient, device);
                    URI portID = createEthernetStoragePort(keyMap, port, name,
                            portInstanceID, newPorts, existingPorts);
                    keyMap.put(endPointInstance.getObjectPath().toString(), portID);
                    addPath(keyMap, operation.get_result(),
                            endPointInstance.getObjectPath());
                } catch (Exception e) {
                    _logger.warn("SCSI End Point Discovery failed for {}-->{}", "",
                            getMessage(e));
                }
            }

            @SuppressWarnings("unchecked")
            List<List<StoragePort>> portsUsedToRunNetworkConnectivity = (List<List<StoragePort>>) keyMap.get(Constants.STORAGE_PORTS);
            portsUsedToRunNetworkConnectivity.add(newPorts);

            // discovered ports used later to check for not visible ports
            List<StoragePort> discoveredPorts = (List<StoragePort>) keyMap.get(Constants.DISCOVERED_PORTS);
            discoveredPorts.addAll(newPorts);
            discoveredPorts.addAll(existingPorts);

            List<StoragePool> modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(_dbClient, newPorts, null);
            for (StoragePool pool : modifiedPools) {
                // pool matcher will be invoked on this pool
                if (!poolsToMatchWithVpool.containsKey(pool.getId())) {
                    poolsToMatchWithVpool.put(pool.getId(), pool);
                }
            }

            _logger.debug("# Pools used in invoking PoolMatcher during StorageProtoclEndPoint {}",
                    Joiner.on("\t").join(poolsToMatchWithVpool.keySet()));
        } catch (Exception e) {
            _logger.error("SCSI End Point Discovery failed -->{}", getMessage(e));
        } finally {
        }
    }

    /**
     * create Ethernet Storage Port.
     * StoragePorts would have been created in SToragePorts Processor, but for ethernet those
     * will not get updated to DB, as to get SCSIAddress ,we need a different SMI Class ProtocolEndPoint
     * Algo :
     * 1. Check if StorageEthernet Port available in DB.
     * 2. If not, then get already created StoragePort, update SCSI Address and persist.
     * 3. If yes, then just update the properties alone.
     * 
     * @param keyMap
     * @param port
     * @param endPointInstance
     * @param portInstanceID
     * @throws IOException
     */
    private URI createEthernetStoragePort(
            Map<String, Object> keyMap, StoragePort port, String name,
            String portInstanceID, List<StoragePort> newPorts, List<StoragePort> existingPorts) throws IOException {
        StoragePort portinMemory = (StoragePort) keyMap.get(portInstanceID);
        if (null == port) {
            // Name Property's value --> iqn.23.....,t,0x0001
            portinMemory.setPortNetworkId(name);
            portinMemory.setPortEndPointID(name);
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(_dbClient, portinMemory);
            portinMemory.setNativeGuid(portNativeGuid);
            portinMemory.setLabel(portNativeGuid);
            _dbClient.createObject(portinMemory);
            newPorts.add(portinMemory);

            return portinMemory.getId();
        } else {
            port.setPortName(portinMemory.getPortName());
            port.setPortSpeed(portinMemory.getPortSpeed());
            port.setPortEndPointID(name);
            port.setCompatibilityStatus(portinMemory.getCompatibilityStatus());
            port.setDiscoveryStatus(portinMemory.getDiscoveryStatus());
            port.setOperationalStatus(portinMemory.getOperationalStatus());
            port.setPortType(portinMemory.getPortType());
            _dbClient.persistObject(port);
            existingPorts.add(port);
            return port.getId();
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
