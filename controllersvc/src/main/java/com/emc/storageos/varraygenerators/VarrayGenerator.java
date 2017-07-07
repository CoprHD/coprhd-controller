package com.emc.storageos.varraygenerators;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static Logger log = LoggerFactory.getLogger(VarrayGenerator.class);
    protected CoordinatorClient coordinator;
    protected DbClient dbClient;
    private static Map<String, VarrayGeneratorInterface> registrationMap = new HashMap<String, VarrayGeneratorInterface>();
    private Set<VpoolTemplate> vpoolTemplates = new HashSet<VpoolTemplate>();;
    
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
        if (networksToUpdate.isEmpty()) {
            log.info("No networks were updated");
            return;
        }
        StringBuilder buf = new StringBuilder();
        dbClient.updateObject(networksToUpdate.values());
        for (Network network : networksToUpdate.values()) {
            List<URI> addedVarrays = StringSetUtil.stringSetToUriList(network.getAssignedVirtualArrays());
            NetworkAssociationHelper.handleNetworkUpdated(network, addedVarrays, new ArrayList<URI>(),
                    new ArrayList<String>(), new ArrayList<String>(), dbClient, coordinator);
            buf.append("network.getNetworkName()" + " ");
        }
        log.info("Networks that were updated: " + buf.toString());
    }
    
    /**
     * Add the virtual array to the port's connected virtual arrays.
     * @param varrayURI -- varray URI
     * @param port -- StoragePort
     * @param portsToUpdate - cache of ports to be updated, map of URI to StoragePort
     */
    protected void connectVarrayToPort(URI varrayURI, StoragePort port, Map<URI, StoragePort> portsToUpdate) {
        StringSet connectedVirtualArrays = port.getConnectedVirtualArrays();
        if (connectedVirtualArrays == null || !connectedVirtualArrays.contains(varrayURI.toString())) {
            port.addConnectedVirtualArray(varrayURI.toString());
            portsToUpdate.put(port.getId(), port);
        }
    }
    
    /**
     * Adds the virtual array to the assigned virtual arrays in the port.
     * @param varrayURI - virtual array URI
     * @param port -- StoragePort
     * @param portsToUpdate - cache of ports to be updated, map of URI to StoragePort
     */
    protected void assignVarrayToPort(URI varrayURI, StoragePort port, Map<URI, StoragePort> portsToUpdate) {
        StringSet assignedVirtualArrays = port.getAssignedVirtualArrays();
        if (assignedVirtualArrays == null || !assignedVirtualArrays.contains(varrayURI.toString())) {
            HashSet<String> addedVirtualArrays = new HashSet<String>();
            addedVirtualArrays.add(varrayURI.toString());
            port.addAssignedVirtualArrays(addedVirtualArrays);
            portsToUpdate.put(port.getId(), port);
            log.info(String.format("updated %s: tagged varrays %s\n", port.getNativeGuid(), port.getTaggedVirtualArrays().toString()));
//            dbClient.updateObject(port);
        }
    }

    
    /**
     * Removes the virtual array from the assigned virtual arrays in the port.
     * @param varrayURI - virtual array URI
     * @param port -- StoragePort
     * @param portsToUpdate - cache of ports to be updated, map of URI to StoragePort
     */
    protected void unassignVarrayFromPort(URI varrayURI, StoragePort port, Map<URI, StoragePort> portsToUpdate) {
        StringSet assignedVirtualArrays = port.getAssignedVirtualArrays();
        if (assignedVirtualArrays != null && assignedVirtualArrays.contains(varrayURI.toString())) {
            HashSet<String> removedVirtualArrays = new HashSet<String>();
            removedVirtualArrays.add(varrayURI.toString());
            port.removeAssignedVirtualArrays(removedVirtualArrays);
            portsToUpdate.put(port.getId(), port);
            log.info(String.format("updated %s: tagged varrays %s\n", port.getNativeGuid(), port.getTaggedVirtualArrays().toString()));
//            dbClient.updateObject(port);
        }
    }
    
    /**
     * Writes the portsToUpdate to database.
     * @param portsToUpdate - map of URI to StoragePort
     */
    protected void updatePorts(Map<URI, StoragePort> portsToUpdate) {
        if (portsToUpdate.isEmpty()) {
            log.info("No ports were updated");
            return;
        }
        // Update the ports and log them..
        StringBuilder buf = new StringBuilder();
        for (StoragePort portToUpdate : portsToUpdate.values()) {
            buf.append(String.format("%s: tagged varrays %s\n", portToUpdate.getNativeGuid(), portToUpdate.getTaggedVirtualArrays().toString()));
        }
//        for (StoragePort portToUpdate : portsToUpdate.values()) {
//            dbClient.updateAndReindexObject(portToUpdate);
//            log.info(String.format("Updated object %s (%s)", portToUpdate.getNativeGuid(), portToUpdate.getId()));;
//        }
        dbClient.updateObject(portsToUpdate.values());
        log.info("Ports that were updated:\n" + buf.toString());
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
    
    /**
     * Get or create the "parking varray" which is associated with all ports to avoid
     * implicit port assignments
     * @return VirtualArray"
     */
    protected VirtualArray getParkingVarray() {
        String name = "All Storage Arrays";
        VirtualArray varray = getVirtualArray(name);
        if (varray != null && !varray.getInactive()) {
            return varray;
        }
        VirtualArray newVarray = newVirtualArray(name);
        dbClient.createObject(newVarray);
        return newVarray;
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

    public Set<VpoolTemplate> getVpoolTemplates() {
        return vpoolTemplates;
    }

    public void setVpoolTemplates(Set<VpoolTemplate> vpoolTemplates) {
        this.vpoolTemplates = vpoolTemplates;
    }

    public static Map<String, VarrayGeneratorInterface> getRegistrationMap() {
        return registrationMap;
    }

}
