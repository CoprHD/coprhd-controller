package com.emc.storageos.varraygenerators;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;

public class ArrayVarrayGenerator extends VarrayGenerator implements VarrayGeneratorInterface {
    private static Logger log = LoggerFactory.getLogger(ArrayVarrayGenerator.class);
    public ArrayVarrayGenerator() {
        super(StorageSystem.Type.vmax.name());
        getRegistrationMap().put(StorageSystem.Type.xtremio.name(), this);
        getRegistrationMap().put(StorageSystem.Type.vnxblock.name(), this);
    }

    @Override
    public void generateVarraysForStorageSystem(StorageSystem system) {
        try {
            if (!Type.vmax.name().equals(system.getSystemType()) && !Type.xtremio.name().equals(system.getSystemType())) {
                log.info("Not an appropriate array: " + system.getNativeGuid());
            }
            log.info("Generating varrays for storage system: " + system.getNativeGuid());
            // Get storage ports for the arrayo
            List<StoragePort> ports = ConnectivityUtil.getStoragePortsForSystem(dbClient, system.getId());
            
            // Make a varray specifically for this Storage System.
            // Add the networks that the ports reside on.
            Set<URI> networks = new HashSet<URI>();
            
            for (StoragePort port : ports) {
                if (!NullColumnValueGetter.isNullURI(port.getNetwork())) {
                    networks.add(port.getNetwork());
                }
            }
            printNetworks("Networks connected to this storage system: ", networks);
            
            // Get the existing, or a new varray.
            String varrayName = makeShortGuid(system.getNativeGuid());
            VirtualArray existingVA = getVirtualArray(varrayName);
            VirtualArray varray = (existingVA != null ? existingVA : newVirtualArray(varrayName));
            
            // Explicitly assign the networks
            Map<URI, StoragePort> portsToUpdate = new HashMap<URI, StoragePort>();
            Map<URI, Network> networksToUpdate = new HashMap<URI, Network>();
            for (URI netURI : networks) {
                addVarrayToNetwork(varray.getId(), netURI, networksToUpdate);
            }
            // Explicitly assign the storage ports.
            for (StoragePort port : ports) {
                connectVarrayToPort(varray.getId(), port, portsToUpdate);
            }
            for (StoragePort port : ports) {
                assignVarrayToPort(varray.getId(), port, portsToUpdate);
            }
            
            // Persist things.
            if (existingVA == varray) {
                dbClient.updateObject(varray);
                log.info("Updated virtual array: " + varray.getLabel());
            } else {
                dbClient.createObject(varray);
                log.info("Created virtual array: " + varray.getLabel());;
            }
            updatePorts(portsToUpdate);
            updateNetworks(networksToUpdate);
            
            // Create array virtual pools.
            VpoolGenerator vpoolGenerator = new VpoolGenerator(dbClient, coordinator);
            Set<String> varraySet = new HashSet<String>();
            varraySet.add(varray.getId().toString());
            for (VpoolTemplate template : getVpoolTemplates()) {
                // If not vplex, and if matches our system type
                if (!template.hasAttribute("highAvailability")
                        && (template.getSystemType() == null || template.getSystemType().equals(system.getSystemType()))) {
                    String name = template.getAttribute("label");
                    VirtualPool vpool = makeVpool(vpoolGenerator, template, name, varraySet, null, null);
                }
            }
            
        } catch (Exception ex) {
            
        }
    }

}
