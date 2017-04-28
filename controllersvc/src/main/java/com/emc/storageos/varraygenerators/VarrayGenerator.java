package com.emc.storageos.varraygenerators;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.service.Coordinator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;

public class VarrayGenerator implements VarrayGeneratorInterface {

    protected CoordinatorClient coordinator;
    protected DbClient dbClient;
    private static Map<String, VarrayGeneratorInterface> registrationMap = new HashMap<String, VarrayGeneratorInterface>();
    
    protected VarrayGenerator(String type) {
        registrationMap.put(type, this);
    }
    
    /**
     * Generic interface for generating varrays for any type StorageSystem.
     * @param storageSystemURI
     */
    public static void generateVarrays(StorageSystem system) {
        String type = system.getSystemType();
        
        if (registrationMap.get(type) != null) {
            registrationMap.get(type).generateVarraysForStorageSystem(system);
        }
    }

    /**
     * Tnis method is not used but is there for the interface.
     */
    public void generateVarraysForStorageSystem(StorageSystem system) {
    }

    protected VirtualArray getVirtualArray(String label) {

        List<VirtualArray> existingVAs = CustomQueryUtility.queryActiveResourcesByConstraint(
                dbClient, VirtualArray.class, PrefixConstraint.Factory.getFullMatchConstraint(VirtualArray.class, "label", label));
        if (existingVAs != null && !existingVAs.isEmpty()) {
            return existingVAs.get(0);
        }
        return null;
    }
    
    protected VirtualArray newVirtualArray(String label) {
        VirtualArray newVA = new VirtualArray();
        newVA.setId(URIUtil.createId(VirtualArray.class));
        newVA.setLabel(label);
        newVA.setAutoSanZoning(true);
        return newVA;
    }
    
    /**
     * Adds the specified varrayURI to the assigned and connected virtual arrays of the network.
     * Implements caching. Call updateNetworks to write cache back.
     * @param varrayURI -- URI of virtual array
     * @param networkURI -- URI of network to update
     * @param networksToUpdate - cache of networks to be updated, map of URI to Network
     */
    protected void addVarrayToNetwork(URI varrayURI, URI networkURI, Map<URI, Network> networksToUpdate) {
        Network network = networksToUpdate.get(networkURI);
        if (network == null) {
            network = dbClient.queryObject(Network.class, networkURI);
        }
        StringSet assignedVirtualArrays = network.getAssignedVirtualArrays();
        if (assignedVirtualArrays == null || !assignedVirtualArrays.contains(varrayURI.toString())) {
            network.addAssignedVirtualArrays(URIUtil.asStrings(Arrays.asList(varrayURI)));
            networksToUpdate.put(networkURI, network);
        }
        StringSet connectedVirtualArrays = network.getConnectedVirtualArrays();
        if (connectedVirtualArrays == null || !connectedVirtualArrays.contains(varrayURI.toString())) {
            network.addConnectedVirtualArrays(URIUtil.asStrings(Arrays.asList(varrayURI)));
            networksToUpdate.put(networkURI,  network);
        }
    }
    
    /**
     * Writes the networksToUpdate to database.
     * @param networksToUpdate -- map of URI to Network
     */
    protected void updateNetworks(Map<URI, Network> networksToUpdate) {
        dbClient.updateObject(networksToUpdate.values());
        for (Network network : networksToUpdate.values()) {
            List<URI> addedVarrays = StringSetUtil.stringSetToUriList(network.getAssignedVirtualArrays());
            NetworkAssociationHelper.handleNetworkUpdated(network, addedVarrays, new ArrayList<URI>(),
                    new ArrayList<String>(), new ArrayList<String>(), dbClient, coordinator);
        }
    }
    
    /**
     * Add the virtual array to the port's connected virtual arrays.
     * @param varrayURI
     * @param portURI
     * @param portsToUpdate
     */
    protected void connectVarrayToPort(URI varrayURI, URI portURI, Map<URI, StoragePort> portsToUpdate) {
        StoragePort port = portsToUpdate.get(portURI);
        if (port == null) {
            port = dbClient.queryObject(StoragePort.class, portURI);
        }
        StringSet connectedVirtualArrays = port.getConnectedVirtualArrays();
        if (connectedVirtualArrays == null || !connectedVirtualArrays.contains(varrayURI.toString())) {
            port.addConnectedVirtualArray(varrayURI.toString());
            portsToUpdate.put(portURI, port);
        }
    }
    
    /**
     * Adds the virtual array to the assigned virtual arrays in the port.
     * @param varrayURI - virtual array URI
     * @param portURI port URI
     * @param portsToUpdate - cache of ports to be updated, map of URI to StoragePort
     */
    protected void assignVarrayToPort(URI varrayURI, URI portURI, Map<URI, StoragePort> portsToUpdate) {
        StoragePort port = portsToUpdate.get(portURI);
        if (port == null) {
            port = dbClient.queryObject(StoragePort.class, portURI);
        }
        port.addAssignedVirtualArray(varrayURI.toString());
        portsToUpdate.put(portURI, port);
    }
    
    /**
     * Writes the portsToUpdate to database.
     * @param portsToUpdate - map of URI to StoragePort
     */
    protected void updatePorts(Map<URI, StoragePort> portsToUpdate) {
        for (StoragePort portToUpdate : portsToUpdate.values()) {
            portToUpdate.updateVirtualArrayTags();
            
        }
        dbClient.updateObject(portsToUpdate.values());
    }
    
    /**
     * Update the storage pool associations with the virtual array.
     * @param varrayURI
     * @param addedPorts
     * @param removedPorts
     */
    protected void updateStoragePoolsFromPorts(URI varrayURI, List<StoragePort> addedPorts, List<StoragePort> removedPorts) {
        List<StoragePool> pools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(dbClient, addedPorts, removedPorts, true);
        StoragePortAssociationHelper.runUpdatePortAssociationsProcess(addedPorts, removedPorts, dbClient, coordinator, pools);
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

}
