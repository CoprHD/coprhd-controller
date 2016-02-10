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
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class PlacementTestUtils {
    public static final long SIZE_GB = (1024 * 1024); // 1GB in KB. Use KB since all pool capacities are represented in KB.

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

    public static Network createNetwork(DbClient _dbClient, String[] endpoints,
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
        _dbClient.createObject(network);
        return network;
    }

    public static void addEndpoints(DbClientImpl _dbClient, Network network, String[] endpoints) {
        List<String> endpointList = Arrays.asList(endpoints);
        network.addEndpoints(endpointList, true);
        _dbClient.updateAndReindexObject(network);
    }

    public static StoragePool createStoragePool(DbClient _dbClient,
            VirtualArray varray, StorageSystem storageSystem, String id,
            String label, Long freeCapacity,
            Long totalCapacity,
            int maxPoolUtilizationPercentage, int maxThinPoolSubscriptionPercentage, String supportedResourceType) {
        StoragePool pool1 = new StoragePool();
        pool1.setId(URI.create(id));
        pool1.setLabel(label);
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
        _dbClient.createObject(pool1);
        return pool1;
    }

    public static StoragePort createStoragePort(DbClient _dbClient, DiscoveredSystemObject storageSystem, Network network,
            String portNetworkId, VirtualArray varray, String portType, String portGroup, String portName) {
        StoragePort storagePort = new StoragePort();
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
        _dbClient.createObject(storagePort);
        return storagePort;
    }

    public static StorageSystem createStorageSystem(DbClient _dbClient, String type, String label) {
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
        _dbClient.createObject(storageSystem);
        return storageSystem;
    }

    public static ProtectionSystem createProtectionSystem(DbClient _dbClient, String type, String label, String cluster1, String cluster2, String cluster3, String protocol,
                                                          StringSetMap siteInitiators, StringSet associatedStorageSystems, StringSetMap rpVisibleArrays, Long cgCapacity,
                                                          Long cgCount, StringMap siteVolCap, StringMap siteVolCnt) {
        ProtectionSystem proSystem = new ProtectionSystem();
        String topology = proSystem.assembleClusterTopology(cluster1, cluster2, protocol);
        String topology2 = proSystem.assembleClusterTopology(cluster2, cluster1, protocol);
        StringSet cluTopo = new StringSet();
        cluTopo.add(topology);
        cluTopo.add(topology2);
        if (cluster3 != null) {
        	String topology3 = proSystem.assembleClusterTopology(cluster1,  cluster3, protocol);
        	String topology4 = proSystem.assembleClusterTopology(cluster3,  cluster1, protocol);
        	String topology5 = proSystem.assembleClusterTopology(cluster2,  cluster3, protocol);
        	String topology6 = proSystem.assembleClusterTopology(cluster3,  cluster2, protocol);
        	cluTopo.add(topology3);
        	cluTopo.add(topology4);
        	cluTopo.add(topology5);
        	cluTopo.add(topology6);
        }
        
        proSystem.setClusterTopology(cluTopo);
        proSystem.setSiteInitiators(siteInitiators);
        proSystem.setId(URI.create(label));
        proSystem.setLabel(label);
        proSystem.setInactive(false);
        proSystem.setSystemType(type);
        proSystem.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        proSystem.setReachableStatus(true);
        proSystem.setAssociatedStorageSystems(associatedStorageSystems);
        proSystem.setCgCapacity(cgCapacity);
        proSystem.setCgCount(cgCount);
        proSystem.setSiteVolumeCapacity(siteVolCap);
        proSystem.setSiteVolumeCount(siteVolCnt);
        proSystem.setSiteVisibleStorageArrays(rpVisibleArrays);    
        StringMap siteNames = new StringMap();
        siteNames.put(cluster1, cluster1);
        siteNames.put(cluster2, cluster2);
        if (null != cluster3) {
        	siteNames.put(cluster3, cluster3);
        }
        proSystem.setRpSiteNames(siteNames);
        _dbClient.createObject(proSystem);
       
        return proSystem;
    }

    public static VirtualArray createVirtualArray(DbClient _dbClient, String label) {
        // Create a virtual array
        VirtualArray varray = new VirtualArray();
        varray.setId(URI.create(label));
        varray.setLabel(label);
        _dbClient.createObject(varray);
        return varray;
    }
    
    private static PlacementManager setupSchedulers(DbClient dbClient, CoordinatorClient _coordinator) {
        PortMetricsProcessor portMetricsProcessor = new PortMetricsProcessor();
        portMetricsProcessor.setDbClient(dbClient);
        portMetricsProcessor.setCoordinator(_coordinator);

        PlacementManager placementManager = new PlacementManager();
        placementManager.setDbClient(dbClient);
        Map<String, Scheduler> schedulerMap = new HashMap<String, Scheduler>();

        StorageScheduler storageScheduler = new StorageScheduler();
        storageScheduler.setDbClient(dbClient);
        storageScheduler.setCoordinator(_coordinator);
        AttributeMatcherFramework matcherFramework = new AttributeMatcherFramework();
        storageScheduler.setMatcherFramework(matcherFramework);
        storageScheduler.setPortMetricsProcessor(portMetricsProcessor);

        SRDFScheduler srdfScheduler = new SRDFScheduler();
        srdfScheduler.setDbClient(dbClient);
        srdfScheduler.setCoordinator(_coordinator);
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
        return placementManager;
    }

    public static List invokePlacement(DbClient dbClient, CoordinatorClient _coordinator, VirtualArray varray, Project project,
            VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        PlacementManager placementManager = setupSchedulers(dbClient, _coordinator);
		
		return placementManager.getRecommendationsForVolumeCreateRequest(varray, project, vpool, capabilities);
	}
    
    public static Map<VpoolUse, List<Recommendation>> 
    invokePlacementForVpool(DbClient dbClient, CoordinatorClient _coordinator, VirtualArray varray, Project project,
            VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        PlacementManager placementManager = setupSchedulers(dbClient, _coordinator);
        
        return placementManager.getRecommendationsForVirtualPool(varray, project, vpool, capabilities);
    }
    
    /**
     * Creates a pair of SRDF connected storage systems.
     * @param _dbClient
     * @param label1 -- Label for the first system
     * @param label2 == Label for the second system
     * @return StorageSystem[1] and StorageSystem[2] objects in array (StorageSystem[0] not used
     */
    public static StorageSystem[] createSRDFStorageSystems(DbClient _dbClient, String label1, String label2) {
        StorageSystem[] storageSystems = new StorageSystem[3];
        // Create 2 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", label1);
        storageSystems[1] = storageSystem1;
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", label2);
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
        _dbClient.updateObject(storageSystem1, storageSystem2);
        
        // Create RemoteDirectorGroups
        RemoteDirectorGroup rdg1 = new RemoteDirectorGroup();
        rdg1.setActive(true);
        rdg1.setConnectivityStatus(RemoteDirectorGroup.ConnectivityStatus.UP.name());
        rdg1.setLabel("RDG1");
        rdg1.setId(URI.create("RDG1"));
        rdg1.setNativeGuid("vmax1+vmax2+6");
        rdg1.setRemoteGroupId("6");
        rdg1.setRemoteStorageSystemUri(storageSystem2.getId());
        rdg1.setSourceGroupId("6");
        rdg1.setSourceStorageSystemUri(storageSystem1.getId());
        rdg1.setSupportedCopyMode(RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.name());
        _dbClient.createObject(rdg1);
        RemoteDirectorGroup rdg2 = new RemoteDirectorGroup();
        rdg2.setActive(true);
        rdg2.setConnectivityStatus(RemoteDirectorGroup.ConnectivityStatus.UP.name());
        rdg2.setLabel("RDG2");
        rdg2.setId(URI.create("RDG2"));
        rdg2.setNativeGuid("vmax2+vmax1+6");
        rdg2.setRemoteGroupId("6");
        rdg2.setRemoteStorageSystemUri(storageSystem1.getId());
        rdg2.setSourceGroupId("6");
        rdg2.setSourceStorageSystemUri(storageSystem2.getId());
        rdg2.setSupportedCopyMode(RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.name());
        _dbClient.createObject(rdg2);
        return storageSystems;
    }
    
    public static StoragePool[] createStoragePoolsForTwo(DbClient _dbClient, StorageSystem storageSystem1, VirtualArray varray1, 
            StorageSystem storageSystem2, VirtualArray varray2) {
        StoragePool[] storagePools = new StoragePool[7];
        // Create a storage pool for vmax1
        storagePools[1] = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        storagePools[2] = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool2", "Pool2",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        storagePools[3] = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool3", "Pool3",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        storagePools[4] = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        storagePools[5]= PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        storagePools[6]= PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool6", "Pool6",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
        return storagePools;
    }
    
    public static StorageSystem createVPlexOneCluster(DbClient _dbClient, String label, VirtualArray varray, 
            Network networkFE, Network networkBE, String[] vplexFE, String[] vplexBE) {
        // Create a VPLEX storage system
        StorageSystem vplexStorageSystem = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", label);

        // Create two front-end storage ports VPLEX
        List<StoragePort> fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkFE, vplexFE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, label +"_A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        List<StoragePort> bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkBE, vplexBE[i], varray,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, label +"_B0+FC0" + i));
        }
        return vplexStorageSystem;
    }
    
    public static StorageSystem createVPlexTwoCluster(DbClient _dbClient, String label, VirtualArray varray1, 
            Network networkFE1, Network networkBE1, String[] vplexFE1, String[] vplexBE1,
            VirtualArray varray, Network networkFE2, Network networkBE2, String[] vplexFE2, String[] vplexBE2) {
        // Create a VPLEX storage system
        StorageSystem vplexStorageSystem = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", label);

        // Cluster one.
        // Create two front-end storage ports VPLEX
        List<StoragePort> fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE1.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkFE1, vplexFE1[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, label +"_A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        List<StoragePort> bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE1.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkBE1, vplexBE1[i], varray,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, label +"_B0+FC0" + i));
        }
        
        // Cluster two.
        // Create two front-end storage ports VPLEX
        fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE2.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkFE2, vplexFE2[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, label +"_C0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE2.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkBE2, vplexBE2[i], varray,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, label +"_D0+FC0" + i));
        }
        return vplexStorageSystem;
    }

}
