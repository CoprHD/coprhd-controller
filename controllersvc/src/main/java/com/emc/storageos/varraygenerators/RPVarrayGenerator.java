package com.emc.storageos.varraygenerators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
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
                protectionSystem = (ProtectionSystem) system;
            } else {
                log.info("Not a Storage System: " + system.getNativeGuid());
                return;
            }
            if (!Type.rp.name().equals(protectionSystem.getSystemType())) {
                log.info("Not a RP system: " + protectionSystem.getNativeGuid());
                return;
            }
            if (!isEnabled(EnableBit.RP)) {
                log.info("Auto virtual-array generation for Recover Point not enabled");
                return;
            }
            
            boolean useVplex = isEnabled(EnableBit.VPLEX);

            Map<String, List<VirtualArray>> siteVarray = new HashMap<String, List<VirtualArray>>();

            Set<String> rpVarrayURIs = new HashSet<String>();
            Set<String> rpTargetVarrayURIs = new HashSet<String>();
            
            Set<String> rpVplexVarrayURIs = new HashSet<String>();
            Set<String> rpVplexTargetVarrayURIs = new HashSet<String>();
            
            Map<String, RPTargetVarray> rpTargets = new HashMap<String, RPTargetVarray>();
            Map<String, RPTargetVarray> rpVPlexTargets = new HashMap<String, RPTargetVarray>();
            
            log.info(String.format("Generating varrays for Protection System [%s](%s)",
                    protectionSystem.getLabel(), protectionSystem.getId()));

            for (String assocStorageSystem : protectionSystem.getAssociatedStorageSystems()) {
                String serialNumber = ProtectionSystem.getAssociatedStorageSystemSerialNumber(assocStorageSystem);
                String rpClusterId = ProtectionSystem.getAssociatedStorageSystemSiteName(assocStorageSystem);

                StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, ConnectivityUtil.findStorageSystemBySerialNumber(
                        serialNumber, dbClient, StorageSystemType.BLOCK));

                String virtualArrayName = "";
                boolean vplex = false;
                if (ConnectivityUtil.isAVPlex(storageSystem)) {
                    if (!useVplex) {
                        continue;
                    }
                    
                    vplex = true;
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
                    log.info(String.format(
                            "A generated Virtual Array [%s](%s) for RP Cluster [%s](%s) already exists for Protection System [%s](%s)",
                            existingRPVirtualArray.getLabel(), existingRPVirtualArray.getId(),
                            rpClusterName, rpClusterId,
                            protectionSystem.getLabel(), protectionSystem.getId()));

                    ///////////// TARGETS
                    List<StoragePort> storagePorts = ConnectivityUtil.getVirtualArrayStoragePorts(existingRPVirtualArray.getId(), false, dbClient);
                    List<Network> networks = ConnectivityUtil.getVirtualArrayNetworks(existingRPVirtualArray.getId(), dbClient);
                    if (vplex) {
                        rpVplexVarrayURIs.add(existingRPVirtualArray.getId().toString());
                        
                        // RP+VPLEX Targets
                        if (rpVPlexTargets.get(rpClusterId) == null) {
                            rpVPlexTargets.put(rpClusterId, new RPTargetVarray());
                        }
                        rpVPlexTargets.get(rpClusterId).getStoragePorts().addAll(storagePorts);
                        rpVPlexTargets.get(rpClusterId).getNetworks().addAll(networks);
                        rpVPlexTargets.get(rpClusterId).setVplex(true);
                    } else {
                        rpVarrayURIs.add(existingRPVirtualArray.getId().toString());
                        
                        // RP Targets
                        if (rpTargets.get(rpClusterId) == null) {
                            rpTargets.put(rpClusterId, new RPTargetVarray());
                        }                    
                        rpTargets.get(rpClusterId).getStoragePorts().addAll(storagePorts);
                        rpTargets.get(rpClusterId).getNetworks().addAll(networks);
                    }
                    /////////////

                    if (siteVarray.get(rpClusterId) == null) {
                        siteVarray.put(rpClusterId, new ArrayList<VirtualArray>());
                    }
                    siteVarray.get(rpClusterId).add(existingRPVirtualArray);

                    continue;
                }

                // Remove the RP suffix and hopefully lookup the existing related varray
                virtualArrayName = virtualArrayName.replace(rpSuffix, "");

                VirtualArray existingVirtualArray = getVirtualArray(virtualArrayName);

                if (existingVirtualArray == null) {
                    log.warn(String.format(
                            "No generated Virtual Array exists for Storage System [%s](%s), please re-run Storage System discovery.",
                            storageSystem.getLabel(), storageSystem.getId()));
                    continue;
                }

                // We want to make a copy the existing varray and isolate it to the RP Cluster
                virtualArrayName = virtualArrayName.concat(rpSuffix);
                VirtualArray newRPVirtualArray = copyVirtualArray(existingVirtualArray, virtualArrayName);
                log.info(String.format("Generated new Virtual Array [%s](%s) for RP Cluster [%s](%s) for Protection System [%s](%s).",
                        newRPVirtualArray.getLabel(), newRPVirtualArray.getId(),
                        rpClusterName, rpClusterId,
                        protectionSystem.getLabel(), protectionSystem.getId()));
                protectionSystem.addSiteAssignedVirtualArrayEntry(rpClusterId, newRPVirtualArray.getId().toString());

                ///////////// TARGETS
                // Code duplication - FIX
                List<StoragePort> storagePorts = ConnectivityUtil.getVirtualArrayStoragePorts(newRPVirtualArray.getId(), false, dbClient);
                List<Network> networks = ConnectivityUtil.getVirtualArrayNetworks(newRPVirtualArray.getId(), dbClient);
                if (vplex) {
                    rpVplexVarrayURIs.add(newRPVirtualArray.getId().toString());
                    
                    // RP+VPLEX Targets
                    if (rpVPlexTargets.get(rpClusterId) == null) {
                        rpVPlexTargets.put(rpClusterId, new RPTargetVarray());
                    }
                    rpVPlexTargets.get(rpClusterId).getStoragePorts().addAll(storagePorts);
                    rpVPlexTargets.get(rpClusterId).getNetworks().addAll(networks);
                    rpVPlexTargets.get(rpClusterId).setVplex(true);
                } else {
                    rpVarrayURIs.add(newRPVirtualArray.getId().toString());
                    
                    // RP Targets
                    if (rpTargets.get(rpClusterId) == null) {
                        rpTargets.put(rpClusterId, new RPTargetVarray());
                    }                    
                    rpTargets.get(rpClusterId).getStoragePorts().addAll(storagePorts);
                    rpTargets.get(rpClusterId).getNetworks().addAll(networks);                    
                }
                /////////////

                if (siteVarray.get(rpClusterName) == null) {
                    siteVarray.put(rpClusterName, new ArrayList<VirtualArray>());
                }
                siteVarray.get(rpClusterName).add(newRPVirtualArray);
            }

            // Build RP Target varrays
            for (Map.Entry<String, RPTargetVarray> entry : rpTargets.entrySet()) {
                String rpClusterId = entry.getKey();
                String rpClusterName = (protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(rpClusterId)
                        : rpClusterId;
                RPTargetVarray varray = entry.getValue(); 
                String virtualArrayName = "All_" + rpClusterName;

                VirtualArray existingVirtualArray = getVirtualArray(virtualArrayName);

                if (existingVirtualArray == null) {
                    VirtualArray newRPVirtualArray = buildVarray(virtualArrayName, varray.getStoragePorts(), varray.getNetworks());
                   
                    log.info(String.format("Generated new Virtual Array [%s](%s) for RP Cluster [%s] for Protection System [%s](%s).",
                            newRPVirtualArray.getLabel(), newRPVirtualArray.getId(),
                            rpClusterName,
                            protectionSystem.getLabel(), protectionSystem.getId()));
                    protectionSystem.addSiteAssignedVirtualArrayEntry(rpClusterId, newRPVirtualArray.getId().toString());
                    
                    rpTargetVarrayURIs.add(newRPVirtualArray.getId().toString());
                }

                rpTargetVarrayURIs.add(existingVirtualArray.getId().toString());
            }
            
            // Build RP+VPLEX Target varrays
            for (Map.Entry<String, RPTargetVarray> entry : rpVPlexTargets.entrySet()) {
                String rpClusterId = entry.getKey();
                String rpClusterName = (protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(rpClusterId)
                        : rpClusterId;
                RPTargetVarray varray = entry.getValue(); 
                String virtualArrayName = "All_VPLEX_" + rpClusterName;

                VirtualArray existingVirtualArray = getVirtualArray(virtualArrayName);

                if (existingVirtualArray == null) {
                    VirtualArray newRPVirtualArray = buildVarray(virtualArrayName, varray.getStoragePorts(), varray.getNetworks());
                   
                    log.info(String.format("Generated new Virtual Array [%s](%s) for RP Cluster [%s] for Protection System [%s](%s).",
                            newRPVirtualArray.getLabel(), newRPVirtualArray.getId(),
                            rpClusterName,
                            protectionSystem.getLabel(), protectionSystem.getId()));
                    protectionSystem.addSiteAssignedVirtualArrayEntry(rpClusterId, newRPVirtualArray.getId().toString());
                    
                    rpVplexTargetVarrayURIs.add(newRPVirtualArray.getId().toString());
                }

                rpVplexTargetVarrayURIs.add(existingVirtualArray.getId().toString());
            }

            if (!isEnabled(EnableBit.VPOOL)) {
                return;
            }
            
            // Create RP topology
            Map<String, Set<String>> topology = new HashMap<String, Set<String>>();
            for (String s : protectionSystem.getClusterTopology()) {
                String[] split = s.split(" ");
                String key = split[0];
                String value = split[1];

                String cluster1 = (protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(key)
                        : key;
                String cluster2 = (protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(value)
                        : value;

                if (topology.get(cluster1) == null) {
                    topology.put(cluster1, new HashSet<String>());
                }
                topology.get(cluster1).add(cluster2);
            }

            // Vpools
            VpoolGenerator vpoolGenerator = new VpoolGenerator(dbClient, coordinator);

            // Create array only virtual pools first.
            // Map<String, VirtualPool> arrayTypeToBasicVolumeVpool = new HashMap<String, VirtualPool>();
            VirtualPool rpTargetVpool = null;
            VirtualPool rpVplexTargetVpool = null;
            for (VpoolTemplate template : getVpoolTemplates()) {
                if (!template.hasAttribute("protectionCoS")) {
                    if (!template.hasAttribute("highAvailability")) {
                        String name = template.getAttribute("label");
                        if (!CollectionUtils.isEmpty(rpTargetVarrayURIs)) {
                            rpTargetVpool = makeVpool(vpoolGenerator, template, name, rpTargetVarrayURIs, null, null, null);
                        }
                    } else {
                        if (!CollectionUtils.isEmpty(rpVplexTargetVarrayURIs)) {
                            String name = template.getAttribute("label");
                            rpVplexTargetVpool = makeVpool(vpoolGenerator, template, name, rpVplexTargetVarrayURIs, null, null, null);
                        }
                    }
                } else {
                    if (!template.hasAttribute("highAvailability")) {
                        String name = template.getAttribute("label");

                        for (Map.Entry<String, List<VirtualArray>> entry : siteVarray.entrySet()) {
                            String rpCluster = entry.getKey();
                            List<VirtualArray> varrays = entry.getValue();

                            Set<String> sourceURIs = new HashSet<String>();
                            for (VirtualArray source : varrays) {
                                sourceURIs.add(source.getId().toString());
                            }

                            String vpoolName = name + "-CDP" + "_" + rpCluster.toUpperCase();
                            String virtualArrayName = "All_" + rpCluster;
                            VirtualArray target = getVirtualArray(virtualArrayName);

                            Map<String, String> targetVarrayVpools = new HashMap<String, String>();
                            targetVarrayVpools.put(target.getId().toString(), rpTargetVpool.getId().toString());

                            // CDP
                            if (isEnabled(EnableBit.RP_CDP)) {
                                VirtualPool cdp = makeVpool(vpoolGenerator, template, vpoolName, sourceURIs, null, null, targetVarrayVpools);
                                
                                log.info(String.format("Generated new RP CDP Virtual Pool [%s](%s).",
                                        cdp.getLabel(), cdp.getId()));
                            }

                            Set<String> connectedClusters = topology.get(rpCluster);
                            Iterator<String> it = connectedClusters.iterator();

                            Map<String, String> crrTargetVarrayVpools = new HashMap<String, String>();
                            while (it.hasNext()) {
                                String connectedCluster = it.next();
                                virtualArrayName = "All_" + connectedCluster;
                                target = getVirtualArray(virtualArrayName);

                                crrTargetVarrayVpools.put(target.getId().toString(), rpTargetVpool.getId().toString());
                            }

                            if (!crrTargetVarrayVpools.isEmpty()) {
                                vpoolName = name + "-CRR" + "_" + rpCluster.toUpperCase();
                                // CRR
                                if (isEnabled(EnableBit.RP_CRR)) {
                                    VirtualPool crr = makeVpool(vpoolGenerator, template, vpoolName, sourceURIs, null, null, crrTargetVarrayVpools);
                                    
                                    log.info(String.format("Generated new RP CRR Virtual Pool [%s](%s).",
                                            crr.getLabel(), crr.getId()));
                                }

                                targetVarrayVpools.putAll(crrTargetVarrayVpools);
                                vpoolName = name + "-CLR" + "_" + rpCluster.toUpperCase();
                                // CLR
                                if (isEnabled(EnableBit.RP_CLR)) {
                                    VirtualPool clr = makeVpool(vpoolGenerator, template, vpoolName, sourceURIs, null, null, targetVarrayVpools);
                                    
                                    log.info(String.format("Generated new RP CLR Virtual Pool [%s](%s).",
                                            clr.getLabel(), clr.getId()));
                                }
                            }
                        }
                    } else {
                        // VPLEX / MP
                        
                    }
                }
            }

        } catch (Exception ex) {
            log.info("Unexpected exception in RP Varray Generation: " + ex.getMessage(), ex);
        }
    }

    private void generateVpools() {

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
    
    private class RPTargetVarray {
        private String clusterId;
        List<StoragePort> storagePorts;
        List<Network> networks;
        private boolean vplex = false;
        
        public RPTargetVarray() {            
        }
        
        /**
         * @param clusterName
         * @param storagePorts
         * @param networks
         */
        public RPTargetVarray(String clusterName, List<StoragePort> storagePorts, List<Network> networks) {
            super();
            this.clusterId = clusterName;
            this.storagePorts = storagePorts;
            this.networks = networks;
        }
        
        public String getClusterId() {
            return clusterId;
        }
        public void setClusterId(String clusterId) {
            this.clusterId = clusterId;
        }
        public List<StoragePort> getStoragePorts() {
            if (storagePorts == null) {
                storagePorts = new ArrayList<StoragePort>();
            }
            return storagePorts;
        }
        public void setStoragePorts(List<StoragePort> storagePorts) {
            this.storagePorts = storagePorts;
        }
        public List<Network> getNetworks() {
            if (networks == null) {
                networks = new ArrayList<Network>();
            }
            return networks;
        }
        public void setNetworks(List<Network> networks) {
            this.networks = networks;
        }

        public boolean isVplex() {
            return vplex;
        }

        public void setVplex(boolean vplex) {
            this.vplex = vplex;
        }
    }
}
