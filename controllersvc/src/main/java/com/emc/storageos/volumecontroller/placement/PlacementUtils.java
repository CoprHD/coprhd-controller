/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

public class PlacementUtils {
    protected static final Logger log = LoggerFactory.getLogger(PlacementUtils.class);

    /**
     * Get switch names for initiators and storage ports. Those information is needed when switch affinity is enabled for
     * port allocation. so it would only get the information if it is turned on.
     * 
     * @param initiatorsByNetMap the map of network to initiators
     * @param storagePortsMap the map of network to storage ports
     * @param dbClient
     * @param system - storage system
     * @param switchInitiatorsByNet - OUTPUT the map of switch to initiators by network
     * @param switchStoragePortsByNet -- OUTPUT the map of switch to storage ports by network
     */
    public static void getSwitchfoForInititaorsStoragePorts(Map<URI, List<Initiator>> initiatorsByNetMap, 
            Map<URI, List<StoragePort>> storagePortsMap, DbClient dbClient, StorageSystem system, 
            Map<URI, Map<String, List<Initiator>>> switchInitiatorsByNet,
            Map<URI, Map<String, List<StoragePort>>> switchStoragePortsByNet) {
        boolean isSwitchAffinityEnabled = BlockStorageScheduler.isSwitchAffinityAllocationEnabled(system.getSystemType());
        if (!isSwitchAffinityEnabled) {
        	log.info("Switch affinity disabled- returning without initiator and port switch information");
            return;
        }
        for (Map.Entry<URI, List<Initiator>> entry : initiatorsByNetMap.entrySet()) {
            URI net = entry.getKey();
            List <Initiator> initiators = entry.getValue();
            Map<String, List<Initiator>> switchInitiatorMap = switchInitiatorsByNet.get(net);
            if (switchInitiatorMap == null) {
                switchInitiatorMap = new HashMap<String, List<Initiator>>();
                switchInitiatorsByNet.put(net, switchInitiatorMap);
            }
            for (Initiator initiator : initiators) {
                String switchName = PlacementUtils.getSwitchName(initiator.getInitiatorPort(), dbClient);
                if (switchName == null || switchName.isEmpty()) {
                    log.info(String.format("The initiator %s does not have switch info", initiator.getInitiatorPort()));
                    switchName = NullColumnValueGetter.getNullStr();
                }
                List<Initiator> inits = switchInitiatorMap.get(switchName);
                if (inits == null) {
                    inits = new ArrayList<>();
                    switchInitiatorMap.put(switchName, inits);
                }
                inits.add(initiator);
            }
        }
        
        for (Map.Entry<URI, List<StoragePort>> entry : storagePortsMap.entrySet()) {
            URI net = entry.getKey();
            List <StoragePort> ports = entry.getValue();
            Map<String, List<StoragePort>> switchPortMap = switchStoragePortsByNet.get(net);
            if (switchPortMap == null) {
                switchPortMap = new HashMap<String, List<StoragePort>>();
                switchStoragePortsByNet.put(net, switchPortMap);
            }
            for (StoragePort port : ports) {
                String switchName = PlacementUtils.getSwitchName(port.getPortNetworkId(), dbClient);
                if (switchName == null || switchName.isEmpty()) {
                    switchName = NullColumnValueGetter.getNullStr();
                }
                List<StoragePort> portsInMap = switchPortMap.get(switchName);
                if (portsInMap == null) {
                    portsInMap = new ArrayList<>();
                    switchPortMap.put(switchName, portsInMap);
                }
                portsInMap.add(port);
            }
        }
        
    }
    
    /**
     * Get the name of a SAN switch that is connected to this StoragePort.
     * Return null if no SAN switches are connected to this StoragePort.
     * 
     * @param port
     * @param dbClient
     * @return
     */
    public static String getSwitchName(StoragePort port, DbClient dbClient) {
        return getSwitchName(port.getPortNetworkId(), dbClient);
    }

    /**
     * Get the name of a SAN switch that is connected to the storage port or initiator with the portWWN.
     * Return null if no SAN switches are connected.
     * 
     * @param portWWN portWWN of a storagePort or an initiator
     * @param dbClient
     * @return the connected switch name
     */
    public static String getSwitchName(String portWWN, DbClient dbClient) {
        if (portWWN != null && !portWWN.isEmpty()) {
            URIQueryResultList uriList = new URIQueryResultList();
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory
                            .getFCEndpointRemotePortNameConstraint(portWWN), uriList);
            for (URI uri : uriList) {
                FCEndpoint endpoint = dbClient.queryObject(FCEndpoint.class, uri);
                if (endpoint != null) {           
                    if (endpoint.getSwitchName() != null) {
                        // Return the switch name if it is known.
                        if (endpoint.getAwolCount() == 0) {
                            return endpoint.getSwitchName();
                        }
                    }
                }
            }
        } else {
            log.warn("The portWWN is not set");
        }
        return null;
    }
    
    /**
     * Get switch name for initiators and storage ports.
     * 
     * @param initiators initiators
     * @param storagePorts storage ports
     * @param dbClient
     * @param system storage system
     * @param initiatorSwitchMap OUTPUT map of initiator to switch name
     * @param switchStoragePortsMap OUPTUT map of switch name to list of storage ports
     * @param portSwitchMap OUTPUT map of port to switch name
     */
    public static void getSwitchNameForInititaorsStoragePorts(List<Initiator> initiators, 
            Map<URI, List<StoragePort>> storagePorts, DbClient dbClient, StorageSystem system, 
            Map<URI, String> initiatorSwitchMap,
            Map<URI, Map<String, List<StoragePort>>> switchStoragePortsByNetMap,
            Map<URI, String> portSwitchMap) {
        boolean isSwitchAffinityEnabled = BlockStorageScheduler.isSwitchAffinityAllocationEnabled(system.getSystemType());
        if (!isSwitchAffinityEnabled) {
            return;
        }
        for (Initiator initiator : initiators) {
            String switchName = PlacementUtils.getSwitchName(initiator.getInitiatorPort(), dbClient);
            if (switchName == null || switchName.isEmpty()) {
                switchName = NullColumnValueGetter.getNullStr();
            }
            initiatorSwitchMap.put(initiator.getId(), switchName);
        }

        for (Map.Entry<URI, List<StoragePort>> entry : storagePorts.entrySet()) {
            URI net = entry.getKey();
            Collection <StoragePort> ports = entry.getValue();
            Map<String, List<StoragePort>> switchPortMap = switchStoragePortsByNetMap.get(net);
            if (switchPortMap == null) {
                switchPortMap = new HashMap<String, List<StoragePort>>();
                switchStoragePortsByNetMap.put(net, switchPortMap);
            }
            for (StoragePort port : ports) {
                String switchName = PlacementUtils.getSwitchName(port.getPortNetworkId(), dbClient);
                if (switchName == null || switchName.isEmpty()) {
                    switchName = NullColumnValueGetter.getNullStr();
                }
                portSwitchMap.put(port.getId(), switchName);
                List<StoragePort> portsInMap = switchPortMap.get(switchName);
                if (portsInMap == null) {
                    portsInMap = new ArrayList<>();
                    switchPortMap.put(switchName, portsInMap);
                }
                portsInMap.add(port);
            }
        }
        
    }
    
}
