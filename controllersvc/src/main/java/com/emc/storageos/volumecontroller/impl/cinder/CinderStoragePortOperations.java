/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.cinder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.model.VolumeAttachResponse;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;

/**
 * This class will be used to perform cinder storage port operations
 * 
 * 1. Modify : To modify the existing storage port with the actual identifier after the
 * volume export/attach goes through fine, the response of the export/attach would
 * give us the actual port information.
 * 
 * 2. Create : To create additional ports for a storage system after the volume export/
 * attach goes through fine, the response for FC would contain the host WWPN to storage
 * port WWPN mapping. For iSCSI, if the successful export returns new IQN which is not
 * present in the ViPR, it will be enumerated as new IP port.
 * 
 * 
 */

public class CinderStoragePortOperations
{
    private static final Logger logger = LoggerFactory.getLogger(CinderStoragePortOperations.class);
    private StorageSystem storageSystem = null;
    private DbClient dbClient = null;
    private List<StoragePort> allStoragePortsList = null;
    private List<StoragePort> newStoragePortsList = new ArrayList<StoragePort>();
    private List<StoragePort> modifiedStoragePortsList = new ArrayList<StoragePort>();;

    // Map that contains instances of CinderStoragePortOperations
    private static Map<URI, CinderStoragePortOperations> instancesMap = new HashMap<URI, CinderStoragePortOperations>();

    private CinderStoragePortOperations(StorageSystem system, DbClient dbc)
    {
        storageSystem = system;
        dbClient = dbc;
    }

    /**
     * Gets the instance from the map if already created, otherwise creates one
     * 
     * @param system
     * @param response
     * @return
     */
    public static CinderStoragePortOperations getInstance(StorageSystem system, DbClient dbc)
    {
        CinderStoragePortOperations instance = instancesMap.get(system.getId());
        if (null == instance)
        {
            synchronized (instancesMap)
            {
                if (null == instance)
                {
                    instance = new CinderStoragePortOperations(system, dbc);
                    instancesMap.put(system.getId(), instance);
                }
            }
        }

        return instance;
    }

    /**
     * Invokes the FC or iSCSI ports operation
     * based on the type of the export/attach operation
     * 
     * @param attachResponse
     * @throws IOException
     */
    public void invoke(VolumeAttachResponse attachResponse)
    {
        logger.info("Cinder Storage Port Invoke Operation Started for" +
                " the storage system : {}", storageSystem.getId());

        synchronized (this)
        {
            try
            {

                // Get the transport type
                String protocolType = attachResponse.connection_info.driver_volume_type;

                Map<String, List<String>> initiatorTargetMap = null;
                if (CinderConstants.ATTACH_RESPONSE_FC_TYPE.equalsIgnoreCase(protocolType))
                {
                    initiatorTargetMap = attachResponse.connection_info.data.initiator_target_map;
                    if (null != initiatorTargetMap && !initiatorTargetMap.isEmpty()) {
                        logger.debug("FC Initiator and Target mappings : {} ", initiatorTargetMap.toString());
                        performFCOperation(initiatorTargetMap);
                    }
                    
                }

                String iqn = null;
                if (CinderConstants.ATTACH_RESPONSE_ISCSI_TYPE.equalsIgnoreCase(protocolType))
                {
                    iqn = attachResponse.connection_info.data.target_iqn;
                    logger.debug("iSCSI target IQN is :{}", iqn);
                    performISCSIOperation(iqn);
                }

                // Update the port to network associations for modified ports and newly created ports.
                if (!modifiedStoragePortsList.isEmpty())
                {
                    StoragePortAssociationHelper.updatePortAssociations(modifiedStoragePortsList, dbClient);
                }

                if (!newStoragePortsList.isEmpty())
                {
                    StoragePortAssociationHelper.updatePortAssociations(newStoragePortsList, dbClient);
                }
            } catch (Exception e) {
                logger.error("There is an error while creating/modifying ports after export/attach," +
                        " Reason:" + e.getMessage(), e);
            } finally {
                // clear modified and new ports list
                modifiedStoragePortsList.clear();
                newStoragePortsList.clear();
            }

        }

        logger.info("Cinder Storage Port Invoke Operation completed for" +
                " the storage system :{} ", storageSystem.getId());

    }

    /**
     * Creates or modifies IP/iSCSI ports
     * 
     * Steps-
     * 1. Get all iSCSI ports of the storage system.
     * 2. If single port is present -
     * a) Check if it contains valid IQN as port network Id.
     * b) If it does not contain the valid IQN, then just update its port network id.
     * c) If it contains the valid IQN as port network id and that port network id
     * does not equal the one received in the response, then create new port.
     * 3. If there are more than one ports
     * a) Form a list of port network ids by iterating all storage port instances.
     * b) Check if the IQN is present in the list constructed in 3.a.
     * c) If it is not present in the list 3.a, then create a new port.
     * 
     * @param iqn
     */
    private void performISCSIOperation(String iqn)
    {
        logger.info("Start iSCSI Ports create/modify operations," +
                " for storage system : {} ", storageSystem.getId());

        List<StoragePort> iscsiPorts = getISCSIPorts();
        if (iscsiPorts.size() == 1)
        {
            // If there is only a port, check if the networkId is the IQN
            // If it is not IQN, then update it with the IQN received in the response.
            StoragePort singlePort = iscsiPorts.get(0);
            String portNetworkId = singlePort.getPortNetworkId();
            if (!StorageProtocol.checkInitiator(Block.iSCSI.name(), null, portNetworkId))
            {
                modify(singlePort, iqn);
            }
            else
            {
                // It is IQN, check if the port's IQN matches with the IQN received
                // If it does not match, just create the new IP port.
                if (!portNetworkId.equalsIgnoreCase(iqn))
                {
                    create(iqn, StorageProtocol.Transport.IP.name());
                }
            }

        }
        else
        {
            List<String> portNetworkIdsList = new ArrayList<String>();
            // If there are more than 1 ports, construct a list of portNetworkIds
            for (StoragePort port : iscsiPorts)
            {
                portNetworkIdsList.add(port.getPortNetworkId());
            }

            // Now, check if the IQN in the response is already present in the
            // existing portNetworkIds list. If it is not present, then create new IP port.
            if (!portNetworkIdsList.contains(iqn))
            {
                create(iqn, StorageProtocol.Transport.IP.name());
            }
        }

        logger.info("End iSCSI Ports create/modify operations," +
                " for storage system :{}", storageSystem.getId());
    }

    /**
     * Creates or modifies FC ports
     * 
     * Steps-
     * 1. Merge all target WWNs into a single list.
     * 2. Get all FC ports of storage system.
     * 3. From the list constructed in step 1, remove all existing port network Ids
     * 4. Create storage port instances for the remaining WWNs in the merged list.
     * 
     * @param initiatorTargetMap
     */
    private void performFCOperation(Map<String, List<String>> initiatorTargetMap)
    {
        logger.info("Start FC Ports create/modify operations," +
                " for storage system:{} ", storageSystem.getId());

        // Merge all target wwns into a single list
        Set<String> mergedWwnSet = new HashSet<String>();
        Set<String> keys = initiatorTargetMap.keySet();
        for (String key : keys)
        {
            List<String> wwnList = initiatorTargetMap.get(key);
            for (String wwn : wwnList)
            {
                String formattedWwn = addColon(wwn);
                mergedWwnSet.add(formattedWwn);
            }
        }

        // Get all existing FC storage ports
        List<StoragePort> fcPorts = getFCPorts();

        // Remove the existing storage port wwns from the merged list
        for (StoragePort fcPort : fcPorts)
        {
            String portNetworkId = fcPort.getPortNetworkId();
            if (mergedWwnSet.contains(portNetworkId))
            {
                mergedWwnSet.remove(portNetworkId);
            }
        }

        // Now, create new ports for each item present in the final mergedWwnList
        for (String targetStoragePortWWN : mergedWwnSet)
        {
            create(targetStoragePortWWN, StorageProtocol.Transport.FC.name());
        }

        logger.info("End FC Ports create/modify operations," +
                " for storage system : {}", storageSystem.getId());
    }

    /**
     * Modify the port with new port network Id.
     * 
     * @param port
     * @param portNetworkId
     */
    private void modify(StoragePort port, String portNetworkId)
    {
        port.setPortNetworkId(portNetworkId);
        dbClient.persistObject(port);

        modifiedStoragePortsList.add(port);
    }

    /**
     * Creates an instance of new storage port
     * and associates it with the storage system.
     * 
     * @param portNetworkId
     * @param transportType
     */
    private void create(String portNetworkId, String transportType)
    {
        logger.info("Start create storage port for portNetworkId={}" +
                " and transportType={}", portNetworkId, transportType);

        StorageHADomain adapter = CinderUtils.getStorageAdapter(storageSystem, dbClient);

        StoragePort port = new StoragePort();
        port.setId(URIUtil.createId(StoragePort.class));
        port.setStorageDevice(storageSystem.getId());
        String portName = generatePortName();
        logger.debug("New storage port name is = {}", portName);
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystem,
                portName, NativeGUIDGenerator.PORT);
        port.setNativeGuid(nativeGuid);
        port.setPortNetworkId(portNetworkId);
        port.setStorageHADomain(adapter.getId());

        port.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                .toString());
        // always treat it as a frontend port
        port.setPortType(PortType.frontend.name());
        port.setOperationalStatus(OperationalStatus.OK.toString());
        port.setTransportType(transportType);
        port.setLabel(portName);
        port.setPortName(portName);
        port.setPortGroup("Cinder-PortGroup");
        port.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
        port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
        dbClient.createObject(port);

        // Add it to the new ports list
        newStoragePortsList.add(port);

        // Add it to the local list
        allStoragePortsList.add(port);

        logger.info("End create storage port for portNetworkId={}" +
                " and transportType={}", portNetworkId, transportType);
    }

    /**
     * Gets list of active ports belonging to a storage system.
     * 
     * @return
     */
    private List<StoragePort> getStoragePortList(boolean isRefresh)
    {
        logger.debug("Start getStoragePortList");
        if (null == allStoragePortsList || isRefresh)
        {
            allStoragePortsList = new ArrayList<StoragePort>();
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            URI sysid = storageSystem.getId();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.
                    getStorageDeviceStoragePortConstraint(sysid),
                    storagePortURIs);

            Iterator<URI> storagePortsIter = storagePortURIs.iterator();
            while (storagePortsIter.hasNext())
            {
                URI storagePortURI = storagePortsIter.next();
                StoragePort storagePort = dbClient.queryObject(StoragePort.class,
                        storagePortURI);
                if (storagePort != null && !storagePort.getInactive())
                {
                    allStoragePortsList.add(storagePort);
                }
            }
        }

        logger.debug("End getStoragePortList");

        return allStoragePortsList;
    }

    private List<StoragePort> filterPortsByType(String transportType)
    {
        List<StoragePort> filteredList = new ArrayList<StoragePort>();

        List<StoragePort> allPorts = getStoragePortList(false);
        for (StoragePort port : allPorts)
        {
            if (transportType.equals(port.getTransportType()))
            {
                filteredList.add(port);
            }

        }

        return filteredList;
    }

    /*
     * Filters FC ports from all port list
     */
    private List<StoragePort> getFCPorts()
    {
        return filterPortsByType(StorageProtocol.Transport.FC.name());
    }

    /*
     * Filters iSCSI ports from all port list
     */
    private List<StoragePort> getISCSIPorts()
    {
        return filterPortsByType(StorageProtocol.Transport.IP.name());
    }

    /**
     * Generates the name for storage port.
     * 
     * All Cinder storage system ports will be named as
     * 
     * First port would have been named as "DEFAULT" during the
     * first discovery, then onwards ports will be named as.
     * 
     * 'Cinder Storage Port:0'
     * 'Cinder Storage Port:1'
     * 'Cinder Storage Port:2'
     * and so on ....
     * 
     * @return
     */
    private String generatePortName()
    {
        StringBuffer portName = new StringBuffer("Cinder Storage Port:");
        int portCount = allStoragePortsList.size();

        portName = portName.append(String.valueOf(portCount - 1));

        return portName.toString();
    }

    /**
     * Formats the WWN by adding colon to it.
     * 
     * @param wwn
     * @return
     */
    private String addColon(String wwn)
    {
        StringBuffer buf = new StringBuffer();
        char[] charArray = wwn.toCharArray();

        int count = 0;
        for (char c : charArray)
        {
            if (count != 0 && count % 2 == 0)
            {
                buf.append(CinderConstants.COLON);
            }

            buf.append(c);
            count++;
        }

        return buf.toString().toUpperCase();
    }

}
