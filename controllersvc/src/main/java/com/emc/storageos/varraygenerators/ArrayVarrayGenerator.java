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
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.util.TagUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.varraygenerators.VarrayGenerator.EnableBit;

public class ArrayVarrayGenerator extends VarrayGenerator implements VarrayGeneratorInterface {
    private static Logger log = LoggerFactory.getLogger(ArrayVarrayGenerator.class);
    public ArrayVarrayGenerator() {
        super(StorageSystem.Type.vmax.name());
        getRegistrationMap().put(StorageSystem.Type.xtremio.name(), this);
        getRegistrationMap().put(StorageSystem.Type.vnxblock.name(), this);
    }

    @Override
    public void generateVarraysForDiscoveredSystem(DiscoveredSystemObject system) {        
        try {
            StorageSystem storageSystem = null;            
            if (system instanceof StorageSystem) {
                storageSystem = (StorageSystem)system;
            } else {
                log.info("Not a Storage System: " + system.getNativeGuid());
                return;
            }
            if (!Type.vmax.name().equals(storageSystem.getSystemType()) && !Type.xtremio.name().equals(storageSystem.getSystemType())) {
                log.info("Not an appropriate array: " + storageSystem.getNativeGuid());
            }
            if (!isEnabled(EnableBit.ARRAY)) {
                log.info("Auto virtual-array generation for ARRAYs not enabled");
                return;
            }
            log.info("Generating varrays for storage system: " + storageSystem.getNativeGuid());
            // Get storage ports for the array
            List<StoragePort> ports = ConnectivityUtil.getStoragePortsForSystem(dbClient, storageSystem.getId());
            
            // Make a varray specifically for this Storage System.
            // Add the networks that the ports reside on.
            Set<URI> networks = new HashSet<URI>();
            
            for (StoragePort port : ports) {
                if (!NullColumnValueGetter.isNullURI(port.getNetwork())) {
                    networks.add(port.getNetwork());
                }
            }
            printNetworks("Networks connected to this storage system: ", networks);
            
            // Build the virtual array with standard configuration
            String varrayName = makeShortGuid(storageSystem.getNativeGuid());
            VirtualArray varray = buildVarray(varrayName, ports, networks);
            
            // If the array is part of a Site, add it to the Site array.
            boolean siteEnabled = isEnabled(EnableBit.SITE);
            VirtualArray siteVarray = null;
            String siteName = TagUtils.getSiteName(storageSystem);
            if (siteEnabled && siteName != null) {
                siteName = String.format("%s %s", SITE, siteName);
                siteVarray = buildVarray(storageSystem, siteName, ports, networks);
            } else {
                log.info("SITE not enabled or no site name specified.");
                return;
            }
            
            if (!isEnabled(EnableBit.VPOOL)) {
                log.info("Auto generation of Virtual Pools not enabled");
                return;            
            }
            
            // Create array virtual pools.
            VpoolGenerator vpoolGenerator = new VpoolGenerator(dbClient, coordinator);
            Set<String> varraySet = new HashSet<String>();
            varraySet.add(varray.getId().toString());
            if (siteVarray != null) {
                varraySet.add(siteVarray.getId().toString());
            }
            for (VpoolTemplate template : getVpoolTemplates()) {
                // If not vplex, and if matches our system type
                if (!template.hasAttribute("highAvailability")
                        && (template.getSystemType() == null || template.getSystemType().equals(storageSystem.getSystemType()))) {
                    String name = template.getAttribute("label");
                    VirtualPool vpool = makeVpool(vpoolGenerator, template, name, varraySet, null, null, null, null);
                }
            }
            
        } catch (Exception ex) {
            
        }
    }
}
