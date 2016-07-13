/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.VPlexHighAvailabilityMatcher;

public class PlacementTestUtils {
    public static final long SIZE_GB = (1024 * 1024); // 1GB in KB. Use KB since all pool capacities are represented in KB.
    private static final Logger _log = LoggerFactory.getLogger(PlacementTestUtils.class);

    public static VirtualPoolCapabilityValuesWrapper createCapabilities(String size, int count, BlockConsistencyGroup cg) {
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        Long volumeSize = SizeUtil.translateSize(size);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, volumeSize);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, count);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        Set<String> protocols = new HashSet<>();
        protocols.add("FC");
        capabilities.put(VirtualPoolCapabilityValuesWrapper.PROTOCOLS, protocols);
        if (cg != null) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, cg.getId());
        }
        return capabilities;
    }

    public static Network createNetwork(DbClient dbClient, String[] endpoints,
            String label, String nativeGUID, StringSet connectedVArrays) {
        Network network = new Network();
        network.setId(URI.create(label));
        network.setDiscovered(true);
        network.setLabel(label);
        network.setNativeGuid(nativeGUID);
        network.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        network.setTransportType("FC");
        StringMap feMap = new StringMap();
        for (int i = 0; i < endpoints.length; i++) {
            feMap.put(endpoints[i], "true");
        }
        network.setEndpointsMap(feMap);
        network.setConnectedVirtualArrays(connectedVArrays);
        dbClient.createObject(network);
        return network;
    }

    public static void addEndpoints(DbClientImpl dbClient, Network network, String[] endpoints) {
        List<String> endpointList = Arrays.asList(endpoints);
        network.addEndpoints(endpointList, true);
        dbClient.updateAndReindexObject(network);
    }

    public static StoragePool createStoragePool(DbClient dbClient,
            VirtualArray varray, StorageSystem storageSystem, String id,
            String label, Long freeCapacity,
            Long totalCapacity,
            int maxPoolUtilizationPercentage, int maxThinPoolSubscriptionPercentage, String supportedResourceType) {
        StoragePool pool1 = new StoragePool();
        pool1.setId(URI.create(id));
        pool1.setLabel(storageSystem.getLabel()+ "+" +label);
        pool1.setNativeGuid(storageSystem.getLabel()+ "+" +label);
        pool1.setStorageDevice(storageSystem.getId());
        pool1.setFreeCapacity(freeCapacity);
        pool1.setTotalCapacity(totalCapacity);
        pool1.setMaxPoolUtilizationPercentage(maxPoolUtilizationPercentage);
        pool1.setMaxThinPoolSubscriptionPercentage(maxThinPoolSubscriptionPercentage);
        pool1.setSupportedResourceTypes(supportedResourceType);
        pool1.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        pool1.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.toString());
        StringSet varrays = new StringSet();
        varrays.add(varray.getId().toString());
        pool1.setAssignedVirtualArrays(varrays);
        pool1.setTaggedVirtualArrays(varrays);
        dbClient.createObject(pool1);
        return pool1;
    }

    public static StoragePort createStoragePort(DbClient dbClient, DiscoveredSystemObject storageSystem, Network network,
            String portNetworkId, VirtualArray varray, String portType, String portGroup, String portName) {
        StoragePort storagePort = new StoragePort();
        portName = storageSystem.getLabel() + ":" + portName;
        storagePort.setId(URI.create(portName));
        storagePort.setLabel(portName);
        storagePort.setStorageDevice(storageSystem.getId());
        storagePort.addAssignedVirtualArray(varray.getId().toString());
        storagePort.setOperationalStatus(StoragePort.OperationalStatus.OK.toString());
        storagePort.setNetwork(network.getId());
        storagePort.setPortGroup(portGroup);
        storagePort.setPortName(portName);
        storagePort.setPortNetworkId(portNetworkId);
        storagePort.setPortType(portType);
        storagePort.setNativeGuid(portName);
        storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        dbClient.createObject(storagePort);
        return storagePort;
    }

    public static StorageSystem createStorageSystem(DbClient dbClient, String type, String label) {
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setId(URI.create(label));
        storageSystem.setLabel(label);
        storageSystem.setNativeGuid(label);
        storageSystem.setInactive(false);
        storageSystem.setSystemType(type);
        storageSystem.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        storageSystem.setReachableStatus(true);
        if (type.equals("vplex")) {
            storageSystem.setSerialNumber(label + "cluster1:" + label + "cluster2");
            StringMap vplexAssemblyIdtoClusterId = new StringMap();
            vplexAssemblyIdtoClusterId.put(label + "cluster1", "1");
            vplexAssemblyIdtoClusterId.put(label + "cluster2", "2");
            storageSystem.setVplexAssemblyIdtoClusterId(vplexAssemblyIdtoClusterId);
        }
        else {
            storageSystem.setSerialNumber(label);
        }
        dbClient.createObject(storageSystem);
        return storageSystem;
    }

    public static ProtectionSystem createProtectionSystem(DbClient dbClient, String type, String label, String cluster1, String cluster2, String cluster3, String protocol,
                                                          StringSetMap siteInitiators, StringSet associatedStorageSystems, StringSetMap rpVisibleArrays, Long cgCapacity,
                                                          Long cgCount, StringMap siteVolCap, StringMap siteVolCnt) {
        ProtectionSystem ps = new ProtectionSystem();
        String topology = ps.assembleClusterTopology(cluster1, cluster2, protocol);
        String topology2 = ps.assembleClusterTopology(cluster2, cluster1, protocol);
        StringSet clusterTopology = new StringSet();
        clusterTopology.add(topology);
        clusterTopology.add(topology2);
        if (cluster3 != null) {
        	String topology3 = ps.assembleClusterTopology(cluster1,  cluster3, protocol);
        	String topology4 = ps.assembleClusterTopology(cluster3,  cluster1, protocol);
        	String topology5 = ps.assembleClusterTopology(cluster2,  cluster3, protocol);
        	String topology6 = ps.assembleClusterTopology(cluster3,  cluster2, protocol);
        	clusterTopology.add(topology3);
        	clusterTopology.add(topology4);
        	clusterTopology.add(topology5);
        	clusterTopology.add(topology6);
        }
        
        ps.setClusterTopology(clusterTopology);
        ps.setSiteInitiators(siteInitiators);
        ps.setId(URI.create(label));
        ps.setLabel(label);
        ps.setInactive(false);
        ps.setSystemType(type);
        ps.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        ps.setReachableStatus(true);
        ps.setAssociatedStorageSystems(associatedStorageSystems);
        ps.setCgCapacity(cgCapacity);
        ps.setCgCount(cgCount);
        ps.setSiteVolumeCapacity(siteVolCap);
        ps.setSiteVolumeCount(siteVolCnt);
        ps.setSiteVisibleStorageArrays(rpVisibleArrays);    
        StringMap siteNames = new StringMap();
        siteNames.put(cluster1, cluster1);
        siteNames.put(cluster2, cluster2);
        if (null != cluster3) {
        	siteNames.put(cluster3, cluster3);
        }
        ps.setRpSiteNames(siteNames);
        dbClient.createObject(ps);
       
        return ps;
    }

    public static VirtualArray createVirtualArray(DbClient dbClient, String label) {
        // Create a virtual array
        VirtualArray varray = new VirtualArray();
        varray.setId(URI.create(label));
        varray.setLabel(label);
        dbClient.createObject(varray);
        return varray;
    }
    
    private static PlacementManager setupSchedulers(DbClient dbClient, CoordinatorClient coordinator) {
        PortMetricsProcessor portMetricsProcessor = new PortMetricsProcessor();
        portMetricsProcessor.setDbClient(dbClient);
        portMetricsProcessor.setCoordinator(coordinator);

        PlacementManager placementManager = new PlacementManager();
        placementManager.setDbClient(dbClient);
        Map<String, Scheduler> schedulerMap = new HashMap<String, Scheduler>();

        StorageScheduler storageScheduler = new StorageScheduler();
        storageScheduler.setDbClient(dbClient);
        storageScheduler.setCoordinator(coordinator);
        AttributeMatcherFramework matcherFramework = new AttributeMatcherFramework();
        storageScheduler.setMatcherFramework(matcherFramework);
        storageScheduler.setPortMetricsProcessor(portMetricsProcessor);

        SRDFScheduler srdfScheduler = new SRDFScheduler();
        srdfScheduler.setDbClient(dbClient);
        srdfScheduler.setCoordinator(coordinator);
        srdfScheduler.setBlockScheduler(storageScheduler);
        srdfScheduler._permissionsHelper = new PermissionsHelper(dbClient);

        VPlexScheduler vplexScheduler = new VPlexScheduler();
        vplexScheduler.setDbClient(dbClient);
        vplexScheduler.setBlockScheduler(storageScheduler);
        vplexScheduler.setPlacementManager(placementManager);
        PermissionsHelper permHelperVplex = new PermissionsHelper(dbClient);
        vplexScheduler._permissionsHelper = permHelperVplex;

        RecoverPointScheduler rpScheduler = new RecoverPointScheduler();
        rpScheduler.setDbClient(dbClient);
        rpScheduler.setVplexScheduler(vplexScheduler);
        rpScheduler.setBlockScheduler(storageScheduler);
        PermissionsHelper permHelper = new PermissionsHelper(dbClient);
        rpScheduler._permissionsHelper = permHelper;
        RPHelper rpHelper = new RPHelper();
        rpHelper.setDbClient(dbClient);
        rpScheduler.setRpHelper(rpHelper);
    
        schedulerMap.put("srdf", srdfScheduler);
        schedulerMap.put("vplex", vplexScheduler);
        schedulerMap.put("block", storageScheduler);
        schedulerMap.put("rp", rpScheduler);        
        placementManager.setStorageSchedulers(schedulerMap);
        
        // Set up the new schedulerStack.
        List<String> schedulerStack = new ArrayList<String>();
        schedulerStack.add("rp");
        schedulerStack.add("vplex");
        schedulerStack.add("srdf");
        schedulerStack.add("block");
        placementManager.setSchedulerStack(schedulerStack);
        return placementManager;
    }

    public static List invokePlacement(DbClient dbClient, CoordinatorClient coordinator, VirtualArray varray, Project project,
            VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        PlacementManager placementManager = setupSchedulers(dbClient, coordinator);
		
		return placementManager.getRecommendationsForVolumeCreateRequest(varray, project, vpool, capabilities);
	}
    
    public static Map<VpoolUse, List<Recommendation>> 
    invokePlacementForVpool(DbClient dbClient, CoordinatorClient coordinator, VirtualArray varray, Project project,
            VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        PlacementManager placementManager = setupSchedulers(dbClient, coordinator);
        
        return placementManager.getRecommendationsForVirtualPool(varray, project, vpool, capabilities);
    }
    
    public static StorageSystem createStorageSystem(DbClient dbClient, 
            String label, Network network, String[] portWWNs, VirtualArray varray) {
        StorageSystem storageSystem = PlacementTestUtils.createStorageSystem(dbClient, "vmax", label);
        
        // Create two front-end storage ports system1.
        List<StoragePort> system1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < portWWNs.length; i++) {
            system1Ports.add(PlacementTestUtils.createStoragePort(dbClient, storageSystem, network, portWWNs[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupSite3vmax" + i, label + "_C0+FC0" + i));
        }
        return storageSystem;
    }
    
    /**
     * Create SRDF paired vmax arrays.
     * @param dbClient
     * @param label1
     * @param network1
     * @param portWWNs1
     * @param varray1
     * @param label2
     * @param network2
     * @param portWWNs2
     * @param varray2
     * @return StorageSystem[1] and StorageSystem[2]; StorageSystem[0] not used.
     */
    public static StorageSystem[] createSRDFStorageSystems(DbClient dbClient, 
            String label1, Network network1, String[] portWWNs1, VirtualArray varray1, 
            String label2, Network network2, String[] portWWNs2, VirtualArray varray2) {
        StorageSystem[] storageSystems = new StorageSystem[3];
        // Create 2 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(dbClient, "vmax", label1);
        storageSystems[1] = storageSystem1;
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(dbClient, "vmax", label2);
        storageSystems[2] = storageSystem2;
        // Mark them SRDF capable
        StringSet supportedAsynchronousActions = new StringSet();
        supportedAsynchronousActions.add(StorageSystem.AsyncActions.CreateElementReplica.name());
        supportedAsynchronousActions.add(StorageSystem.AsyncActions.CreateGroupReplica.name());
        storageSystem1.setSupportedAsynchronousActions(supportedAsynchronousActions);
        storageSystem2.setSupportedAsynchronousActions(supportedAsynchronousActions);
        StringSet supportedReplicationTypes = new StringSet();
        supportedReplicationTypes.add(StorageSystem.SupportedReplicationTypes.SRDF.name());
        storageSystem1.setSupportedReplicationTypes(supportedReplicationTypes);
        storageSystem2.setSupportedReplicationTypes(supportedReplicationTypes);
        // Set connected to.
        StringSet connectedTo = new StringSet();
        connectedTo.add(storageSystem2.getId().toString());
        storageSystem1.setRemotelyConnectedTo(connectedTo);
        connectedTo = new StringSet();
        connectedTo.add(storageSystem1.getId().toString());
        storageSystem2.setRemotelyConnectedTo(connectedTo);
        dbClient.updateObject(storageSystem1, storageSystem2);
        
        // Create RemoteDirectorGroups
        RemoteDirectorGroup rdg1 = new RemoteDirectorGroup();
        rdg1.setActive(true);
        rdg1.setConnectivityStatus(RemoteDirectorGroup.ConnectivityStatus.UP.name());
        rdg1.setLabel("RDG1");
        rdg1.setId(URI.create("RDG1"+label1));
        rdg1.setNativeGuid("$label1+$label2+6");
        rdg1.setRemoteGroupId("6");
        rdg1.setRemoteStorageSystemUri(storageSystem2.getId());
        rdg1.setSourceGroupId("6");
        rdg1.setSourceStorageSystemUri(storageSystem1.getId());
        rdg1.setSupportedCopyMode(RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.name());
        dbClient.createObject(rdg1);
        RemoteDirectorGroup rdg2 = new RemoteDirectorGroup();
        rdg2.setActive(true);
        rdg2.setConnectivityStatus(RemoteDirectorGroup.ConnectivityStatus.UP.name());
        rdg2.setLabel("RDG2");
        rdg2.setId(URI.create("RDG2"+label1));
        rdg2.setNativeGuid("$label2+$label1+6");
        rdg2.setRemoteGroupId("6");
        rdg2.setRemoteStorageSystemUri(storageSystem1.getId());
        rdg2.setSourceGroupId("6");
        rdg2.setSourceStorageSystemUri(storageSystem2.getId());
        rdg2.setSupportedCopyMode(RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.name());
        dbClient.createObject(rdg2);
        
        // Create two front-end storage ports system1.
        List<StoragePort> system1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < portWWNs1.length; i++) {
            system1Ports.add(PlacementTestUtils.createStoragePort(dbClient, storageSystem1, network1, portWWNs1[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupSite1vmax" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports system2
        List<StoragePort> system2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < portWWNs2.length; i++) {
            system2Ports.add(PlacementTestUtils.createStoragePort(dbClient, storageSystem2, network2, portWWNs2[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupSite2vmax" + i, "D0+FC0" + i));
        }
        return storageSystems;
    }
    
    public static StoragePool[] createStoragePools(DbClient dbClient, StorageSystem storageSystem, VirtualArray varray) {
        StoragePool[] storagePools = new StoragePool[7];
        // Create a storage pool for storageSystem
        storagePools[1] = PlacementTestUtils.createStoragePool(dbClient, varray, storageSystem, 
                storageSystem.getLabel() + "pool1", storageSystem.getLabel() + "Pool1",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
        // Create a storage pool for storageSystem
        storagePools[2] = PlacementTestUtils.createStoragePool(dbClient, varray, storageSystem, 
                storageSystem.getLabel() + "pool2", storageSystem.getLabel() + "Pool2",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
        // Create a bad storage pool for vmstorageSystemax1
        storagePools[3] = PlacementTestUtils.createStoragePool(dbClient, varray, storageSystem, 
                storageSystem.getLabel() + "pool3", storageSystem.getLabel() + "Pool3",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
        return storagePools;
    }
    
    public static StoragePool[] createStoragePoolsForTwo(DbClient dbClient, StorageSystem storageSystem1, VirtualArray varray1, 
            StorageSystem storageSystem2, VirtualArray varray2) {
        StoragePool[] storagePools = new StoragePool[7];
        // Create a storage pool for vmax1
        String system1 = storageSystem1.getLabel();
        String system2 = storageSystem2.getLabel();
        storagePools[1] = PlacementTestUtils.createStoragePool(dbClient, varray1, storageSystem1, "pool1"+system1, "Pool1"+system1,
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        storagePools[2] = PlacementTestUtils.createStoragePool(dbClient, varray1, storageSystem1, "pool2"+system1, "Pool2"+system1,
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        storagePools[3] = PlacementTestUtils.createStoragePool(dbClient, varray1, storageSystem1, "pool3"+system1, "Pool3"+system1,
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        storagePools[4] = PlacementTestUtils.createStoragePool(dbClient, varray2, storageSystem2, "pool4"+system2, "Pool4"+system2,
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        storagePools[5]= PlacementTestUtils.createStoragePool(dbClient, varray2, storageSystem2, "pool5"+system2, "Pool5"+system2,
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        storagePools[6]= PlacementTestUtils.createStoragePool(dbClient, varray2, storageSystem2, "pool6"+system2, "Pool6"+system2,
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
        return storagePools;
    }
    
    public static StorageSystem createVPlexOneCluster(DbClient dbClient, String label, VirtualArray varray, 
            Network networkFE, Network networkBE, String[] vplexFE, String[] vplexBE) {
        // Create a VPLEX storage system
        StorageSystem vplexStorageSystem = PlacementTestUtils.createStorageSystem(dbClient, "vplex", label);

        // Create two front-end storage ports VPLEX
        List<StoragePort> fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(dbClient, vplexStorageSystem, networkFE, vplexFE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, label +"_A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        List<StoragePort> bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(dbClient, vplexStorageSystem, networkBE, vplexBE[i], varray,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, label +"_B0+FC0" + i));
        }
        return vplexStorageSystem;
    }
    
    /**
     * Creates a two cluster distrubted system.
     * @param dbClient - db client handle
     * @param label - name of the Storage System
     * @param varray1 - varray ckyster 1
     * @param networkFE1  network for cluster 1 Front End ports WWN
     * @param networkBE1 - network for cluster 1 Back End ports WWn
     * @param vplexFE1 - Front End Ports cluster 1
     * @param vplexBE1 - Back End  ports cluster 1
     * @param varray2 -- corresponding stuff for cluster 2
     * @param networkFE2
     * @param networkBE2
     * @param vplexFE2
     * @param vplexBE2
     * @return Storage System created represending Vplex Metro System
     */
    public static StorageSystem createVPlexTwoCluster(DbClient dbClient, String label, 
            VirtualArray varray1, Network networkFE1, Network networkBE1, String[] vplexFE1, String[] vplexBE1,
            VirtualArray varray2, Network networkFE2, Network networkBE2, String[] vplexFE2, String[] vplexBE2) {
        // Create a VPLEX storage system
        StorageSystem vplexStorageSystem = PlacementTestUtils.createStorageSystem(dbClient, "vplex", label);

        // Cluster one.
        // Create two front-end storage ports VPLEX
        List<StoragePort> fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE1.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(dbClient, vplexStorageSystem, networkFE1, vplexFE1[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, label +"_A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        List<StoragePort> bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE1.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(dbClient, vplexStorageSystem, networkBE1, vplexBE1[i], varray1,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, label +"_B0+FC0" + i));
        }
        
        // Cluster two.
        // Create two front-end storage ports VPLEX
        fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE2.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(dbClient, vplexStorageSystem, networkFE2, vplexFE2[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, label +"_C0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE2.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(dbClient, vplexStorageSystem, networkBE2, vplexBE2[i], varray2,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, label +"_D0+FC0" + i));
        }
        return vplexStorageSystem;
    }

    /**
     * Runs the VPLEX high availability matcher
     * @param vpool
     * @param pools
     * @return
     */
    public static StringSet runVPlexHighAvailabilityMatcher(DbClientImpl dbClient, VirtualPool vpool, List<StoragePool> pools) {
        Set<String> poolNames = new HashSet<String>();
        for (StoragePool pool: pools) {
            poolNames.add(pool.getLabel());
        }
        _log.info("Calling VPlexHighAvailabilityMatcher on pools: " + poolNames.toString());
        VPlexHighAvailabilityMatcher matcher = new VPlexHighAvailabilityMatcher();
        ObjectLocalCache cache = new ObjectLocalCache(dbClient);
        matcher.setObjectCache(cache);
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        attributeMap.put(Attributes.high_availability_type.name(), vpool.getHighAvailability());
        attributeMap.put(Attributes.varrays.name(), vpool.getVirtualArrays());
        attributeMap.put(Attributes.high_availability_varray.name(), null);
        attributeMap.put(Attributes.high_availability_vpool.name(), null);
        if (vpool.getHaVarrayVpoolMap() != null) {
            for (Map.Entry<String, String> entry : vpool.getHaVarrayVpoolMap().entrySet()) {
                attributeMap.put(Attributes.high_availability_varray.name(), entry.getKey());
                attributeMap.put(Attributes.high_availability_vpool.name(), entry.getValue());
            }
        }
        List<StoragePool> matchedPools =  matcher.matchStoragePoolsWithAttributeOn(pools, attributeMap);
        StringSet result = new StringSet();
        for (StoragePool matchedPool : matchedPools) {
            result.add(matchedPool.getId().toString());
        }
        _log.info("Matched results: " + result.toString());
        return result;
    }
}
