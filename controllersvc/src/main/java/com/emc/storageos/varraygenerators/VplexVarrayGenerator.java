package com.emc.storageos.varraygenerators;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.service.Coordinator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;

public class VplexVarrayGenerator extends VarrayGenerator implements VarrayGeneratorInterface {
    private static Logger log = LoggerFactory.getLogger(VplexVarrayGenerator.class);
    public VplexVarrayGenerator() {
        super(StorageSystem.Type.vplex.name());
    }

    public void generateVarraysForStorageSystem(StorageSystem system) {
        if (!Type.vplex.name().equals(system.getSystemType())) {
            log.info("Not a VPLEX system: " + system.getNativeGuid());
                    return;
        }
        log.info("Generating varrays for VPLEX system: " + system.getNativeGuid());
        // Determine the storage ports and partition them by VPLEX cluster..Collect the networks for each cluster.
        List<StoragePort> ports = ConnectivityUtil.getStoragePortsForSystem(dbClient, system.getId());
        List<StoragePort> cluster1Ports = new ArrayList<StoragePort>();
        List<StoragePort> cluster2Ports = new ArrayList<StoragePort>();
        Set<URI> cluster1Nets = new HashSet<URI>();
        Set<URI> cluster2Nets = new HashSet<URI>();
        for (StoragePort port : ports) {
           String cluster = ConnectivityUtil.getVplexClusterOfPort(port);
           if (ConnectivityUtil.CLUSTER1.equals(cluster)) {
               cluster1Ports.add(port);
               if (port.getNetwork() != null) {
                   cluster1Nets.add(port.getNetwork());
               }
           } else if (ConnectivityUtil.CLUSTER2.equals(cluster)) {
               cluster2Ports.add(port);
               if (port.getNetwork() != null) {
                   cluster2Nets.add(port.getNetwork());
               }
           } else {
               log.info(String.format("VPLEX port %s %s not in either cluster", port.getPortName(), port.getId()));
           }
        }
        StringBuilder buf = new StringBuilder();
        buf.append("Networks connected to VPlexCluster1: ");
        for (URI cluster1Net : cluster1Nets) {
            NetworkLite net1 = NetworkUtil.getNetworkLite(cluster1Net, dbClient);
            buf.append(net1.getLabel() + " ");
        }
        log.info(buf.toString());
        buf.setLength(0);
        buf.append("Networks connected to VPlexCluster2: ");
        for (URI cluster1Net : cluster1Nets) {
            NetworkLite net2 = NetworkUtil.getNetworkLite(cluster1Net, dbClient);
            buf.append(net2.getLabel() + " ");
        }
        log.info(buf.toString());
        buf.setLength(0);

        // Look for existing virtual arrays. create new ones if necessary
        String varray1Name = system.getNativeGuid() + "-Cluster1";
        String varray2Name = system.getNativeGuid() + "-Cluster2";
        VirtualArray existingVA1 = getVirtualArray(varray1Name);
        VirtualArray existingVA2 = getVirtualArray(varray2Name);
        VirtualArray varray1 = (existingVA1 != null ? existingVA1 : newVirtualArray(varray1Name));
        VirtualArray varray2 = (existingVA2 != null ? existingVA2 : newVirtualArray(varray2Name));
        boolean varray1Changed = false, varray2Changed = false;
        Map<URI, StoragePort> portsToUpdate = new HashMap<URI, StoragePort>();
        Map<URI, Network> networksToUpdate = new HashMap<URI, Network>();

        // Loop through the networks of each Varray, explicitly assigning them to the varray.
        // For each network, get all the storage ports, and mark them connected to the varray.
        for (URI networkURI : cluster1Nets) {
            addVarrayToNetwork(varray1.getId(), networkURI, networksToUpdate);
            List<StoragePort> portsInNetwork = NetworkAssociationHelper.getNetworkStoragePorts(networkURI.toString(), null, dbClient);
            for (StoragePort port : portsInNetwork) {
               connectVarrayToPort(varray1.getId(), port.getId(), portsToUpdate); 
            }
        }
        for (URI networkURI : cluster2Nets) {
            addVarrayToNetwork(varray2.getId(), networkURI, networksToUpdate);
            List<StoragePort> portsInNetwork = NetworkAssociationHelper.getNetworkStoragePorts(networkURI.toString(), null, dbClient);
            for (StoragePort port : portsInNetwork) {
               connectVarrayToPort(varray2.getId(), port.getId(), portsToUpdate); 
            }
        }
        
        // Loop through the VPLEX ports, assigning each to the appropriate virtual array
        for (StoragePort port : cluster1Ports) {
            assignVarrayToPort(varray1.getId(), port.getId(), portsToUpdate);
        }
        for (StoragePort port : cluster2Ports) {
            assignVarrayToPort(varray2.getId(), port.getId(), portsToUpdate);
        }
        
        // Persist things.
        if (existingVA1 == varray1) {
            dbClient.updateObject(varray1);
            log.info("Updated virtual array: " + varray1.getLabel());
        } else {
            dbClient.createObject(varray1);
            log.info("Created virtual array: " + varray1.getLabel());
        }
        if (existingVA2 == varray2) {
            dbClient.updateObject(varray2);
            log.info("Updated virtual array: " + varray2.getLabel());
        } else {
            dbClient.createObject(varray2);
            log.info("Created virtual array: " + varray2.getLabel());
        }
        updateNetworks(networksToUpdate);
        updatePorts(portsToUpdate);
        for (StoragePort port : portsToUpdate.values()) {
            buf.append(port.getNativeGuid() + " ");
        }
        log.info("Ports updated: " + buf.toString());

        // Update the storage pool associations.
        List<StoragePort> allPorts = new ArrayList<StoragePort>();
        allPorts.addAll(cluster1Ports);
        allPorts.addAll(cluster2Ports);
        updateStoragePoolsFromPorts(varray1.getId(), cluster1Ports, new ArrayList<StoragePort>());
        updateStoragePoolsFromPorts(varray2.getId(), cluster2Ports, new ArrayList<StoragePort>());
    }

}
