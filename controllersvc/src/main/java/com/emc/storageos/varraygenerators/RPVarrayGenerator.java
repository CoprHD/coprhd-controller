package com.emc.storageos.varraygenerators;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.util.TagUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;

public class RPVarrayGenerator extends VarrayGenerator implements VarrayGeneratorInterface {
    private static Logger log = LoggerFactory.getLogger(RPVarrayGenerator.class);
    //private static boolean explicitPortAssignment = true;
    
    public RPVarrayGenerator() {
        super(DiscoveredDataObject.Type.rp.name());
    }

    @Override
    public void generateVarraysForDiscoveredSystem(DiscoveredSystemObject system) {
        try {
          ProtectionSystem protectionSystem = null;            
          if (system instanceof ProtectionSystem) {
              protectionSystem = (ProtectionSystem)system;
          } else {
              log.info("Not a Storage System: " + system.getNativeGuid());
              return;
          }
          if (!Type.rp.name().equals(protectionSystem.getSystemType())) {
              log.info("Not a RP system: " + protectionSystem.getNativeGuid());
              return;
          }
          
          log.info(String.format("Generating varrays for Protection System [%s](%s)", 
                  protectionSystem.getLabel(), protectionSystem.getId()));

          for (String assocStorageSystem : protectionSystem.getAssociatedStorageSystems()) {
              String serialNumber = ProtectionSystem.getAssociatedStorageSystemSerialNumber(assocStorageSystem);
              String rpClusterId = ProtectionSystem.getAssociatedStorageSystemSiteName(assocStorageSystem);
              
              StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, ConnectivityUtil.findStorageSystemBySerialNumber(
                      serialNumber, dbClient, StorageSystemType.BLOCK));
             
              String virtualArrayName = "";              
              if (ConnectivityUtil.isAVPlex(storageSystem)) {
                  String clusterId = storageSystem.getVplexAssemblyIdtoClusterId().get(serialNumber);
                  String vplexSuffix = "";
                  if (clusterId.equals("1")) {
                      vplexSuffix = VplexVarrayGenerator.VARRAY_CLUSTER1_SUFFIX;
                  } else {
                      vplexSuffix = VplexVarrayGenerator.VARRAY_CLUSTER2_SUFFIX;
                  }
                  virtualArrayName = makeShortVplexName(storageSystem.getNativeGuid().toString()) + vplexSuffix;
              } else {
                  virtualArrayName = makeShortGuid(storageSystem.getNativeGuid().toString());
              }
              
              String rpClusterName = (protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(rpClusterId)
                      : rpClusterId;
              
              String rpSuffix = "_" + rpClusterName;              
              virtualArrayName = virtualArrayName.concat(rpSuffix);
              
              VirtualArray existingRPVirtualArray = getVirtualArray(virtualArrayName);
              
              if (existingRPVirtualArray != null) {
                  // VA already exists, keep going.
                  log.info(String.format("A generated Virtual Array [%s](%s) for RP Cluster [%s](%s) already exists for Protection System [%s](%s)", 
                          existingRPVirtualArray.getLabel(), existingRPVirtualArray.getId(), 
                          rpClusterName, rpClusterId,
                          protectionSystem.getLabel(), protectionSystem.getId()));
                  continue;
              }
              
              // Remove the RP suffix and hopefully lookup the existing related varray
              virtualArrayName = virtualArrayName.replace(rpSuffix, "");
              
              VirtualArray existingVirtualArray = getVirtualArray(virtualArrayName);
              
              if (existingVirtualArray == null) {
                  // VA already exists, keep going.
                  log.warn(String.format("No generated Virtual Array exists for Storage System [%s](%s), please re-run Storage System discovery.", 
                          storageSystem.getLabel(), storageSystem.getId()));
                  continue;
              }
              
              // We want to make a copy the existing varray and isolate it to the RP Cluster
              virtualArrayName = virtualArrayName.concat(rpSuffix);
              VirtualArray newRPVirtualArray = copyVirtualArray(existingVirtualArray, virtualArrayName, storageSystem);
              log.info(String.format("Generated new Virtual Array [%s](%s) for RP Cluster [%s](%s) for Protection System [%s](%s)", 
                      newRPVirtualArray.getLabel(), newRPVirtualArray.getId(), 
                      rpClusterName, rpClusterId,
                      protectionSystem.getLabel(), protectionSystem.getId()));         
              protectionSystem.addSiteAssignedVirtualArrayEntry(rpClusterId, newRPVirtualArray.getId().toString());
          }
          
          /*
           rpSiteNames = StringMap {
           0x19d6bedb=NewYork, 
           0x346dca6453a00ebd=Boston, 
           0x42e4eb08cb847925=Syracuse}
           */
          
          /*
          associatedStorageSystems = 
              StringSet [
              0x346dca6453a00ebd 000197800376, 
              0x19d6bedb 000197800376, 
              0x346dca6453a00ebd 000196801612, 
              0x346dca6453a00ebd 000196801518, 
              0x42e4eb08cb847925 000196801612, 
              0x42e4eb08cb847925 000196801518]
          */
          
          // Relying on the other varray generators to do their job and generate the appropriate
          // varrays for the registered Storage Systems.
          //List<URI> allVirtualArrays = dbClient.queryByType(VirtualArray.class, true);
          
          
        } catch (Exception ex) {
          log.info("Unexpected exception in Varray Generation: " + ex.getMessage(), ex);
        }
        
        /*
         * StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, ConnectivityUtil.findStorageSystemBySerialNumber(
                serialNumber, dbClient, StorageSystemType.BLOCK));
        if (storageSystem == null) {
            return false;
        }

        // Special check for VPLEX
        if (ConnectivityUtil.isAVPlex(storageSystem)) {
            String clusterId = storageSystem.getVplexAssemblyIdtoClusterId().get(serialNumber);

            // Check to see if this varray and VPLEX cluster match up, if so we're Ok to use it.
            return VPlexUtil.checkIfVarrayContainsSpecifiedVplexSystem(varray.getId().toString(), clusterId, storageSystem.getId(),
                    dbClient);
        }

        // For a single serial number, we wouldn't be in this code if it weren't in the varray, so we keep things
        // simple and return true.
        if (storageSystem.getSerialNumber().equals(serialNumber)) {
            return true;
        }
         */
        
/*
 * private List<String> reorderAssociatedStorageSystems(ProtectionSystem protectionSystem,
            Collection<String> validAssociatedStorageSystems,
            VirtualArray varray) {

        Map<String, Boolean> serialNumberInVarray = new HashMap<>();

        // Create a sorted list of storage systems, with splitter-visible arrays at the top of the list
        List<String> sortedVisibleStorageSystems = new ArrayList<String>();
        for (String assocStorageSystem : validAssociatedStorageSystems) {
            String assocSerialNumber = ProtectionSystem.getAssociatedStorageSystemSerialNumber(assocStorageSystem);
            String rpCluster = ProtectionSystem.getAssociatedStorageSystemSiteName(assocStorageSystem);

            // Calling isStorageArrayInVarray is very expensive (and something we don't want to do going forward)
            // So to minimize the calls, only call for each serial number once and store the result.
            if (!serialNumberInVarray.containsKey(assocSerialNumber)) {
                if (isStorageArrayInVarray(varray, assocSerialNumber)) {
                    serialNumberInVarray.put(assocSerialNumber, Boolean.TRUE);
                } else {
                    serialNumberInVarray.put(assocSerialNumber, Boolean.FALSE);
                }
            }

            // If this serial number/storage array isn't in our varray, don't continue.
            if (!serialNumberInVarray.get(assocSerialNumber)) {
                continue;
            }

            // Is this array seen by any RP cluster already according to the RP? If so, put it to the front of the list
            if (protectionSystem.getSiteVisibleStorageArrays() != null) {
                for (Map.Entry<String, AbstractChangeTrackingSet<String>> clusterStorageSystemsEntry : protectionSystem
                        .getSiteVisibleStorageArrays().entrySet()) {
                    if (rpCluster.equals(clusterStorageSystemsEntry.getKey())) {
                        for (String serialNumber : clusterStorageSystemsEntry.getValue()) {
                            if (assocSerialNumber.equals(serialNumber)) {
                                sortedVisibleStorageSystems.add(rpCluster + " " + serialNumber);
                            }
                        }
                    }
                }
            }
        }

        // If there is no RP-array list at all or it's not currently a splitter, the array is added to the list anyway.
        // It's just added further down the line.
        for (String assocStorageSystem : validAssociatedStorageSystems) {
            String assocSerialNumber = ProtectionSystem.getAssociatedStorageSystemSerialNumber(assocStorageSystem);
            String rpCluster = ProtectionSystem.getAssociatedStorageSystemSiteName(assocStorageSystem);

            if (!sortedVisibleStorageSystems.contains(rpCluster + " " + assocSerialNumber) &&
                    serialNumberInVarray.get(assocSerialNumber)) {
                sortedVisibleStorageSystems.add(rpCluster + " " + assocSerialNumber);
            }
        }

        return sortedVisibleStorageSystems;
    }        
 */
        
        
/*
 * 
    public boolean isRpSiteConnectedToVarray(URI storageSystemURI, URI protectionSystemURI, String siteId, VirtualArray virtualArray) {
        ProtectionSystem protectionSystem =
                dbClient.queryObject(ProtectionSystem.class, protectionSystemURI);
        StringSet siteInitiators =
                protectionSystem.getSiteInitiators().get(siteId);

        boolean connected = false;

        for (String wwn : siteInitiators) {
            NetworkLite network = NetworkUtil.getEndpointNetworkLite(wwn, dbClient);
            // The network is connected if it is assigned or implicitly connected to the varray
            if (RPHelper.isNetworkConnectedToVarray(network, virtualArray)) {
                connected = true;
                break;
            }
        }

        // Check to make sure the RP site is connected to the varray
        return (connected && RPHelper.rpInitiatorsInStorageConnectedNetwork(
                storageSystemURI, protectionSystemURI, siteId, virtualArray.getId(), dbClient));
    }        
 */
        
//        try {
//            StorageSystem storageSystem = null;            
//            if (system instanceof StorageSystem) {
//                storageSystem = (StorageSystem)system;
//            } else {
//                log.info("Not a Storage System: " + system.getNativeGuid());
//                return;
//            }
//            if (!Type.vplex.name().equals(storageSystem.getSystemType())) {
//                log.info("Not a VPLEX system: " + storageSystem.getNativeGuid());
//                return;
//            }
//            log.info("Generating varrays for VPLEX system: " + storageSystem.getNativeGuid());
//            // Determine the storage ports and partition them by VPLEX cluster..Collect the networks for each cluster.
//            List<StoragePort> vplexPorts = ConnectivityUtil.getStoragePortsForSystem(dbClient, storageSystem.getId());
//            // VPLEX ports in cluster1 and cluster2
//            List<StoragePort> cluster1Ports = new ArrayList<StoragePort>();
//            List<StoragePort> cluster2Ports = new ArrayList<StoragePort>();
//            // Networks containing any VPLEX port in cluster1 or cluster2
//            Set<URI> cluster1Nets = new HashSet<URI>();
//            Set<URI> cluster2Nets = new HashSet<URI>();
//            // Networks containing backend VPLEX port in cluster1 or cluster2
//            Set<URI> cluster1BackendNets = new HashSet<URI>();
//            Set<URI> cluster2BackendNets = new HashSet<URI>();
//            for (StoragePort port : vplexPorts) {
//                String cluster = ConnectivityUtil.getVplexClusterOfPort(port);
//                if (ConnectivityUtil.CLUSTER1.equals(cluster)) {
//                    cluster1Ports.add(port);
//                    if (!NullColumnValueGetter.isNullURI(port.getNetwork())) {
//                        cluster1Nets.add(port.getNetwork());
//                        if (port.getPortType().equals(StoragePort.PortType.backend.name())) {
//                            cluster1BackendNets.add(port.getNetwork());
//                        }
//                    }
//                } else if (ConnectivityUtil.CLUSTER2.equals(cluster)) {
//                    cluster2Ports.add(port);
//                    if (!NullColumnValueGetter.isNullURI(port.getNetwork())) {
//                        cluster2Nets.add(port.getNetwork());
//                        if (port.getPortType().equals(StoragePort.PortType.backend.name())) {
//                            cluster2BackendNets.add(port.getNetwork());
//                        }
//                    }
//                } else {
//                    log.info(String.format("VPLEX port %s %s not in either cluster", port.getPortName(), port.getId()));
//                }
//            }
//            printNetworks("Networks connected to VPlexCluster1:  ", cluster1Nets);
//            printNetworks("Networks connected to VPlexCluster2:  ", cluster2Nets);
//
//            // Look for existing virtual arrays. create new ones if necessary
//            String varray1Name = makeShortVplexName(storageSystem.getNativeGuid()) + "-Cluster1";
//            String varray2Name = makeShortVplexName(storageSystem.getNativeGuid()) + "-Cluster2";
//            VirtualArray existingVA1 = getVirtualArray(varray1Name);
//            VirtualArray existingVA2 = getVirtualArray(varray2Name);
//            VirtualArray varray1 = (existingVA1 != null ? existingVA1 : newVirtualArray(varray1Name));
//            VirtualArray varray2 = (existingVA2 != null ? existingVA2 : newVirtualArray(varray2Name));
//            Map<URI, StoragePort> portsToUpdate = new HashMap<URI, StoragePort>();
//            Map<URI, Network> networksToUpdate = new HashMap<URI, Network>();
//
//            // Loop through the networks of each Varray, explicitly assigning them to the varray.
//            // For each network, get all the storage ports, and mark them connected to the varray.
//            for (URI networkURI : cluster1Nets) {
//                addVarrayToNetwork(varray1.getId(), networkURI, networksToUpdate);
//                List<StoragePort> portsInNetwork = NetworkAssociationHelper.getNetworkStoragePorts(networkURI.toString(), null, dbClient);
//                for (StoragePort port : portsInNetwork) {
//                    connectVarrayToPort(varray1.getId(), port, portsToUpdate); 
//                }
//            }
//            for (URI networkURI : cluster2Nets) {
//                addVarrayToNetwork(varray2.getId(), networkURI, networksToUpdate);
//                List<StoragePort> portsInNetwork = NetworkAssociationHelper.getNetworkStoragePorts(networkURI.toString(), null, dbClient);
//                for (StoragePort port : portsInNetwork) {
//                    connectVarrayToPort(varray2.getId(), port, portsToUpdate); 
//                }
//            }
//
//            // Loop through the VPLEX ports, assigning each to the appropriate virtual array
//            for (StoragePort port : cluster1Ports) {
//                assignVarrayToPort(varray1.getId(), port, portsToUpdate);
//                unassignVarrayFromPort(varray2.getId(), port, portsToUpdate);
//            }
//            for (StoragePort port : cluster2Ports) {
//                assignVarrayToPort(varray2.getId(), port, portsToUpdate);
//                unassignVarrayFromPort(varray1.getId(), port, portsToUpdate);
//            }
//
//            // Persist things.
//            boolean hasCluster1 = !cluster1Ports.isEmpty();
//            boolean hasCluster2 = !cluster2Ports.isEmpty();
//            boolean hasBothClusters = (hasCluster1 && hasCluster2);
//            if (hasCluster1) {
//                if (existingVA1 == varray1) {
//                    dbClient.updateObject(varray1);
//                    log.info("Updated virtual array: " + varray1.getLabel());
//                } else {
//                    dbClient.createObject(varray1);
//                    log.info("Created virtual array: " + varray1.getLabel());
//                }
//            }
//            if (hasCluster2) {
//                if (existingVA2 == varray2) {
//                    dbClient.updateObject(varray2);
//                    log.info("Updated virtual array: " + varray2.getLabel());
//                } else {
//                    dbClient.createObject(varray2);
//                    log.info("Created virtual array: " + varray2.getLabel());
//                }
//            }
//            updatePorts(portsToUpdate);
//            updateNetworks(networksToUpdate);
//
//            // If desired, associated the (non-VPLEX) storage system ports explicitly to the networks
//            // of the VPLEX of they meet criteria. This is done independently for each VPLEX cluster. .
//            if (explicitPortAssignment) {
//                setExplicitArrayPorts(cluster1BackendNets, varray1, null);
//                setExplicitArrayPorts(cluster2BackendNets, varray2, null);
//            }
//
//            if (!portsToUpdate.keySet().isEmpty()) {
//                // Update the storage pool associations.
//                updateStoragePoolsFromPorts(varray1.getId(), cluster1Ports, null);
//                updateStoragePoolsFromPorts(varray2.getId(), cluster2Ports, null);
//            }
//            
//            VpoolGenerator vpoolGenerator = new VpoolGenerator(dbClient, coordinator);
//            Set<String> varrayURIs = new HashSet<String>();
//            if (varray1 != null && hasCluster1) {
//                varrayURIs.add(varray1.getId().toString());
//            }
//            if (varray2 != null && hasCluster2) {
//                varrayURIs.add(varray2.getId().toString());
//            }
//            
//            // If the VPLEX is associated with a Site, then add one of the clusters to a Site varray.
//            VirtualArray siteVarray = null, altSiteVarray = null;
//            String vplexClusterForSite  = ConnectivityUtil.CLUSTER_UNKNOWN;
//            String siteName = TagUtils.getSiteName(storageSystem);
//            if (siteName != null) {
//                String siteVarrayName = String.format("%s %s", SITE, siteName);
//                siteVarray = getVirtualArray(siteVarrayName);
//                if (siteVarray != null) {
//                    // Make sure not to mix new ports of different cluster with old ports
//                    vplexClusterForSite = ConnectivityUtil.getVplexClusterForVarray(siteVarray.getId(), storageSystem.getId(), dbClient);
//                } 
//                if (vplexClusterForSite.equals(ConnectivityUtil.CLUSTER_UNKNOWN)) {
//                    // Otherwise choose the Cluster with the most Ports.
//                    vplexClusterForSite = ((cluster1Ports.size() >= cluster2Ports.size()) ? ConnectivityUtil.CLUSTER1 : ConnectivityUtil.CLUSTER2);
//                }
//                    
//                if (vplexClusterForSite.equals(ConnectivityUtil.CLUSTER2) && hasCluster2) {
//                    siteVarray = buildVarray(storageSystem, siteVarrayName, cluster2Ports, cluster2Nets);
//                    varrayURIs.add(siteVarray.getId().toString());
//                    setExplicitArrayPorts(cluster2BackendNets, siteVarray, siteName);
//                } else if (hasCluster1){
//                    siteVarray = buildVarray(storageSystem, siteVarrayName, cluster1Ports, cluster1Nets);
//                    varrayURIs.add(siteVarray.getId().toString());
//                    setExplicitArrayPorts(cluster1BackendNets, siteVarray, siteName);
//                }
//                // Now create the alternate EGO for VPLEX HA
//                siteVarrayName = String.format("%s VPLEX-HA", siteVarrayName);
//                altSiteVarray = getVirtualArray(siteVarrayName);
//                if (!vplexClusterForSite.equals(ConnectivityUtil.CLUSTER2) && hasCluster2) {
//                    altSiteVarray = buildVarray(storageSystem, siteVarrayName, cluster2Ports, cluster2Nets);
//                    varrayURIs.add(altSiteVarray.getId().toString());
//                    setExplicitArrayPorts(cluster2BackendNets, altSiteVarray, siteName);
//                } else if (hasCluster1) {
//                    altSiteVarray = buildVarray(storageSystem, siteVarrayName, cluster1Ports, cluster1Nets);
//                    varrayURIs.add(altSiteVarray.getId().toString());
//                    setExplicitArrayPorts(cluster1BackendNets, altSiteVarray, siteName);
//                }
//            }
//
//            // Create array only virtual pools first.
//            Map<String, VirtualPool> arrayTypeToBasicVolumeVpool = new HashMap<String, VirtualPool>();
//            for (VpoolTemplate template : getVpoolTemplates()) {
//                if (!template.hasAttribute("highAvailability")) {
//                    String name = template.getAttribute("label");
//                    VirtualPool vpool = makeVpool(vpoolGenerator, template, name, varrayURIs, null, null);
//                    if (template.getSystemType() != null) {
//                        arrayTypeToBasicVolumeVpool.put(template.getSystemType(), vpool);
//                    } else {
//                        arrayTypeToBasicVolumeVpool.put("none", vpool);
//                    }
//                }
//            }
//
//            // Create Vplex local and distributed virtual pools.
//            for (VpoolTemplate template : getVpoolTemplates()) {
//                if (template.getAttribute("highAvailability").equals("vplex_local")) {
//                    String name = template.getAttribute("label");
//                    makeVpool(vpoolGenerator, template, name, varrayURIs, null, null);
//                } else if (hasBothClusters && template.getAttribute("highAvailability").equals("vplex_distributed")) {
//                    String type = template.getSystemType();
//                    type = "none";  // BUG: can't seem to handle HA vpools selecting specific array type
//                    String haVpool = null;
//                    if (type != null && arrayTypeToBasicVolumeVpool.containsKey(type)) {
//                        VirtualPool highAvailabilityVirtualPool = arrayTypeToBasicVolumeVpool.get(type); 
//                        haVpool = highAvailabilityVirtualPool.getId().toString();
//                    }
//                    // varray1 -> varray2
//                    Set<String> varray1URIs = new HashSet<String>();
//                    varray1URIs.add(varray1.getId().toString());
//                    String name = varray1Name + " " + template.getAttribute("label");
//                    makeVpool(vpoolGenerator, template, name, varray1URIs, varray2.getId().toString(), haVpool);
//                    // varray2-> varray1
//                    Set<String> varray2URIs = new HashSet<String>();
//                    varray2URIs.add(varray2.getId().toString());
//                    name = varray2Name + " " + template.getAttribute("label");
//                    makeVpool(vpoolGenerator, template, name, varray2URIs, varray1.getId().toString(), haVpool);
//                    // siteVarray -> altSiteVarray
//                    if (siteVarray != null && altSiteVarray != null) {
//                        varray1URIs = new HashSet<String>();
//                        varray1URIs.add(siteVarray.getId().toString());
//                        name = siteVarray.getLabel() + " " + template.getAttribute("label");
//                        Set<String> siteVarrays = new HashSet<String>();
//                        siteVarrays.add(siteVarray.getId().toString());
//                        makeVpool(vpoolGenerator, template, name, siteVarrays, altSiteVarray.getId().toString(), haVpool);
//                    }
//                }
//            }
//        } catch (Exception ex) {
//            log.info("Unexpected exception in Varray Generation: " + ex.getMessage(), ex);
//        }
    }
    
    /**
     * Returns a VPLEX name with the last 4 digits of the serial number from each cluster
     * @param nativeGuid - VPLEX native GUID
     * @return - shortened VPLEX name
     */
    private String makeShortVplexName(String nativeGuid) {
        String[] parts = nativeGuid.split("[+:]");
        StringBuilder buf = new StringBuilder();
        buf.append(parts[0]);
        if (parts.length > 1) {
            buf.append("+");
            int begin = parts[1].length() - 4;
            begin = (begin < 0) ? 0 : begin;
            int end = parts[1].length();
            buf.append(parts[1].substring(begin, end));
        }
        if (parts.length > 2) {
            buf.append(":");
            int begin = parts[2].length() - 4;
            begin = (begin < 0) ? 0 : begin;
            int end = parts[2].length();
            buf.append(parts[2].substring(begin, end));
        }
        return buf.toString();
    }
    
    /**
     * Sets ports to be explicitly associated with the "parking" virtual array (used to prevent implicit associations) and
     * the virtual array of the argument.
     * @param cluster
     * @param networks
     * @param site -- optional Site argument
     * @param varray
     */
    protected void setExplicitArrayPorts(Set<URI> networks, VirtualArray varray, String site) {
        VirtualArray parkingVarray = getParkingVarray();
        // Iterate through the Networks, putting all StoragePorts in the appropriate parking and Vplex varray.
        for (URI network : networks) {
            Map<URI, StoragePort> portsToUpdate = new HashMap<URI, StoragePort>();
            NetworkLite net = NetworkUtil.getNetworkLite(network, dbClient);
            log.info(String.format("Looking at network %s for explicit array port assignments for varray %s", net.getLabel(), varray.getLabel()));
            List<StoragePort> storagePorts = NetworkAssociationHelper.getNetworkConnectedStoragePorts(network.toString(), dbClient);
            // Make Map of StoragePorts to Arrays
            Map<URI, Set<StoragePort>> storageSystemToPortMap = new HashMap<URI, Set<StoragePort>>();
            for (StoragePort port : storagePorts) {
                StorageSystem system = dbClient.queryObject(StorageSystem.class, port.getStorageDevice());
                // Don't match other VPLEX systems
                if (Type.isVPlexStorageSystem(Type.valueOf(system.getSystemType()))) {
                    continue;
                }
                if (site != null && !site.equals(TagUtils.getSiteName(system))) {
                    // skip arrays not in this site
                    continue;
                }
                if (!storageSystemToPortMap.containsKey(port.getStorageDevice())) {
                    storageSystemToPortMap.put(port.getStorageDevice(), new HashSet<StoragePort>());
                }
                storageSystemToPortMap.get(port.getStorageDevice()).add(port);
            }
            for (Map.Entry<URI, Set<StoragePort>> entry : storageSystemToPortMap.entrySet()) {
                // Assign all ports to the parking varray.
                for (StoragePort port : entry.getValue()) {
                    assignVarrayToPort(parkingVarray.getId(), port, portsToUpdate);
                }
                // Require at least two ports to assign array to network in VPLEX varray
                if (validateArrayPortsForNetwork(entry.getKey(), entry.getValue())) {
                    for (StoragePort port : entry.getValue()) {
                        assignVarrayToPort(varray.getId(), port, portsToUpdate);
                    }
                }
            }
            updatePorts(portsToUpdate);
            updateStoragePoolsFromPorts(varray.getId(), new ArrayList<StoragePort>(portsToUpdate.values()), null);
        }
    }
    
    /**
     * Validates that the array ports for a given network have sufficient number, redundancy, status
     * and so forth to be used for the Vplex.
     * @param arrayURI -- URI of StorageSystem
     * @param arrayPorts - Set of StoragePorts 
     * @return true if should be sufficient for VPLEX backend use
     */
    private boolean validateArrayPortsForNetwork(URI arrayURI, Set<StoragePort> arrayPorts) {
        StorageSystem system = dbClient.queryObject(StorageSystem.class, arrayURI);
        Set<URI> haDomains = new HashSet<URI>();
        Set<StoragePort> portsToBeRemoved = new HashSet<StoragePort>();
        Set<String> portsRemovedForBadStatus = new HashSet<String>();
        for (StoragePort port : arrayPorts) {
            if (!ExportUtils.portHasAssignableStatus(port)) {
                portsRemovedForBadStatus.add(port.getPortName());
                portsToBeRemoved.add(port);
            }
        }
        if (!portsToBeRemoved.isEmpty()) {
            log.info(String.format("Ports being removed because of bad status: %s %s", system.getNativeGuid(), portsRemovedForBadStatus));
        }
        arrayPorts.removeAll(portsToBeRemoved);
        // Check redundancy and number
        for (StoragePort port : arrayPorts) {
            haDomains.add(port.getStorageHADomain());
        }
        // At least two ports in different high availability domains
        if (arrayPorts.size() >= 2 && haDomains.size() >=2 ) {
            return true;
        } else {
            log.info("Insufficient number of ports or HA redundancy groups array: " + system.getNativeGuid());
            return false;
        }
    }
}
