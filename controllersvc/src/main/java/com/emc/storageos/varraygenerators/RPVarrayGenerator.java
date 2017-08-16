package com.emc.storageos.varraygenerators;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;

public class RPVarrayGenerator extends VarrayGenerator implements VarrayGeneratorInterface {
    private static Logger log = LoggerFactory.getLogger(RPVarrayGenerator.class);
    
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
          
          //Map<String, List<>>
          
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
                  virtualArrayName = VplexVarrayGenerator.makeShortVplexName(storageSystem.getNativeGuid().toString()) + vplexSuffix;
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
                  log.warn(String.format("No generated Virtual Array exists for Storage System [%s](%s), please re-run Storage System discovery.", 
                          storageSystem.getLabel(), storageSystem.getId()));
                  continue;
              }
              
              // We want to make a copy the existing varray and isolate it to the RP Cluster
              virtualArrayName = virtualArrayName.concat(rpSuffix);
              VirtualArray newRPVirtualArray = copyVirtualArray(existingVirtualArray, virtualArrayName, storageSystem);
              log.info(String.format("Generated new Virtual Array [%s](%s) for RP Cluster [%s](%s) for Protection System [%s](%s).", 
                      newRPVirtualArray.getLabel(), newRPVirtualArray.getId(), 
                      rpClusterName, rpClusterId,
                      protectionSystem.getLabel(), protectionSystem.getId()));         
              protectionSystem.addSiteAssignedVirtualArrayEntry(rpClusterId, newRPVirtualArray.getId().toString());
          }
        } catch (Exception ex) {
          log.info("Unexpected exception in RP Varray Generation: " + ex.getMessage(), ex);
        }

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
    
    private class RPTopologyLite {
        private String source;
        private List<String> targets;
        private RPTopologyType type;

        public RPTopologyLite(String source, List<String> targets, RPTopologyType type) {
            this.setSource(source);
            this.setTargets(targets);
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public List<String> getTargets() {
            return targets;
        }

        public void setTargets(List<String> targets) {
            this.targets = targets;
        }
    }
    
    private enum RPTopologyType {
        CDP("RP CDP"),
        CRR("RP CRR"),
        CLR("RP CLR"),
        RPVPLEX_LOCAL_CDP("RP+VPLEX Local CDP"),
        RPVPLEX_LOCAL_CRR("RP+VPLEX Local CRR"),
        RPVPLEX_LOCAL_CLR("RP+VPLEX Local CLR"),
        RPVPLEX_METRO_CDP("RP+VPLEX Metro CDP"),
        RPVPLEX_METRO_CRR("RP+VPLEX Metro CRR"),
        RPVPLEX_METRO_CLR("RP+VPLEX Metro CLR"),
        METROPOINT_CDP("MetroPoint CDP"),
        METROPOINT_CRR("MetroPoint CRR"),
        METROPOINT_CLR("MetroPoint CLR");

        private String description;

        private RPTopologyType(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }      
}

