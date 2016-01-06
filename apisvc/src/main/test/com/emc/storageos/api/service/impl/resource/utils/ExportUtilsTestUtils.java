/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class ExportUtilsTestUtils {

    public static VirtualPoolCapabilityValuesWrapper createCapabilities(String size, int count,
            BlockConsistencyGroup cg) {
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

    public static StoragePort createStoragePort(DbClient _dbClient, DiscoveredSystemObject storageSystem,
            Network network,
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
        } else {
            storageSystem.setSerialNumber(label);
        }
        _dbClient.createObject(storageSystem);
        return storageSystem;
    }

    public static ProtectionSystem createProtectionSystem(DbClient _dbClient, String type, String label,
            String cluster1, String cluster2, String cluster3, String protocol,
            StringSetMap siteInitiators, StringSet associatedStorageSystems, StringSetMap rpVisibleArrays,
            Long cgCapacity,
            Long cgCount, StringMap siteVolCap, StringMap siteVolCnt) {
        ProtectionSystem proSystem = new ProtectionSystem();
        String topology = proSystem.assembleClusterTopology(cluster1, cluster2, protocol);
        String topology2 = proSystem.assembleClusterTopology(cluster2, cluster1, protocol);
        StringSet cluTopo = new StringSet();
        cluTopo.add(topology);
        cluTopo.add(topology2);
        if (cluster3 != null) {
            String topology3 = proSystem.assembleClusterTopology(cluster1, cluster3, protocol);
            String topology4 = proSystem.assembleClusterTopology(cluster3, cluster1, protocol);
            String topology5 = proSystem.assembleClusterTopology(cluster2, cluster3, protocol);
            String topology6 = proSystem.assembleClusterTopology(cluster3, cluster2, protocol);
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

    public static Initiator createInitiator(DbClientImpl _dbClient, Network network, int i) {
        Initiator initiator = new Initiator();
        String wwnbase = ":00:00:00:00:00:00:" + String.format("%02x", i);

        initiator.setId(URI.create("init" + i));
        initiator.setClusterName("cluster1");
        initiator.setHost(null);
        initiator.setInitiatorPort("50" + wwnbase);
        initiator.setInitiatorNode("51" + wwnbase);
        initiator.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        initiator.setProtocol(Protocol.FC.toString());
        _dbClient.createObject(initiator);

        network.addEndpoints(Arrays.asList(initiator.getInitiatorPort()), true);
        _dbClient.createObject(network);

        return initiator;
    }

    public static Volume createVolume(DbClientImpl _dbClient, VirtualArray varray, int i) {
        Volume volume = new Volume();
        String label = "volume" + i;

        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(label);
        volume.setVirtualArray(varray.getId());
        _dbClient.createObject(volume);

        return volume;
    }

    public static ExportGroup createExportGroup(DbClientImpl _dbClient, List<Initiator> initiators,
            List<Volume> volumes, VirtualArray varray, int i) {
        ExportGroup eg = new ExportGroup();
        String label = "eg" + i;

        eg.setId(URI.create(label));
        eg.setLabel(label);
        eg.setVirtualArray(varray.getId());
        for (Initiator initiator : initiators) {
            eg.addInitiator(initiator);
        }
        for (Volume volume : volumes) {
            eg.addVolume(volume.getId(), i);
        }
        _dbClient.createObject(eg);

        return eg;
    }

    public static ExportMask createExportMask(DbClientImpl _dbClient, List<ExportGroup> egs, List<Initiator> initiators,
            List<Volume> volumes, List<StoragePort> storagePorts, int i) {
        ExportMask em = new ExportMask();
        String label = "mask" + i;

        em.setId(URI.create(label));
        em.setLabel(label);
        em.setMaskName(label);

        StringSet storageIds = new StringSet();
        for (StoragePort sp : storagePorts) {
            storageIds.add(sp.getId().toString());
        }
        em.setStoragePorts(storageIds);

        for (Initiator initiator : initiators) {
            em.addInitiator(initiator);
            // Add some ITL mappings
            em.addZoningMapEntry(initiator.getId().toString(), storageIds);
            // Add FC Zone Reference

            for (StoragePort sp : storagePorts) {
                for (Volume v : volumes) {
                    FCZoneReference zr = new FCZoneReference();
                    String key = FCZoneReference.makeEndpointsKey(
                            Arrays.asList(new String[] { initiator.getInitiatorPort(), sp.getPortNetworkId() }));
                    zr.setId(URIUtil.createId(FCZoneReference.class));
                    zr.setLabel(FCZoneReference.makeLabel(zr.getPwwnKey(), v.getId().toString()));
                    zr.setPwwnKey(key);
                    zr.setVolumeUri(v.getId());
                    _dbClient.createObject(zr);
                }
            }

        }
        for (Volume volume : volumes) {
            em.addVolume(volume.getId(), i);
        }

        _dbClient.createObject(em);

        for (ExportGroup eg : egs) {
            eg.addExportMask(em.getId());
            _dbClient.updateObject(eg);
        }

        return em;

    }

}
