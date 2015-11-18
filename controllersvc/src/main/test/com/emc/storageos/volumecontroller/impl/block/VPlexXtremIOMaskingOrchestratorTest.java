/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.util.DummyDbClient;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.placement.PortAllocatorTestContext;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocatorTest;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssignerFactory;
import com.emc.storageos.vplexcontroller.VPlexBackendManager;

/**
 * Test program for VPlexXtremIOMaskingOrchestrator
 * 
 * Execution setup:
 * Required classpath: default classpath
 * Required run directory: Directory containing test
 * Required argument: -Dlog4j.configuration=log4j.properties
 * 
 */
public class VPlexXtremIOMaskingOrchestratorTest extends StoragePortsAllocatorTest {
    private static final Log _log = LogFactory.getLog(VPlexXtremIOMaskingOrchestratorTest.class);

    public static void main(String[] args) throws InterruptedException {
        VdcUtil.setDbClient(new DummyDbClient());
        PropertyConfigurator.configure("log4j.properties");
        _log.info("Beginning logging");
        PortAllocatorTestContext contextPrototype = new PortAllocatorTestContext();
        StoragePortsAllocator.setContextPrototype(contextPrototype);
        VplexXtremIOMaskingOrchestrator orca = new VplexXtremIOMaskingOrchestrator(null, null);
        VPlexBackendManager bemgr = new VPlexBackendManager();
        orca.setSimulation(true);
        URI arrayURI = URI.create("vmaxArray");

        Map<String, Set<String>> directorToInitiators = new HashMap<String, Set<String>>();
        Map<String, URI> initiatorIdToNetwork = new HashMap<String, URI>();
        Map<String, Initiator> initiatorMap = new HashMap<String, Initiator>();

        PortAllocationContext context = null;
        Map<URI, NetworkLite> networkMap = new HashMap<URI, NetworkLite>();
        Map<URI, List<StoragePort>> allocatablePorts = new HashMap<URI, List<StoragePort>>();
        URI varray1 = URI.create("varray1");

        // single VPLEX Engine
        /**
         * Single VPLEX Engine (2 Directors)
         * Single X-brick (2 SCs, 4 storage ports), 2 networks
         * SC ports spread across networks
         */
        context = getNet1Ports(networkMap, allocatablePorts);
        context = getNet2Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net1", "net2", null);
        Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        // orca.getInitiatorGroups(directorToInitiators, initiatorIdToNetwork, initiatorMap);
        int directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        Set<Map<URI, List<List<StoragePort>>>> portGroups = orca.getPortGroups(
                allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Single VPLEX Engine (2 Directors)
         * Dual X-bricks (4 SCs, 8 storage ports), 2 networks
         * ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet3Ports(networkMap, allocatablePorts);
        context = getNet4Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net3", "net4", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Single VPLEX Engine (2 Directors)
         * Dual X-bricks (4 SCs, 8 storage ports), 2 networks
         * network-1 has X-brick 1's ports; network-2 has X-brick 2's ports;
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet5Ports(networkMap, allocatablePorts);
        context = getNet6Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net5", "net6", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Single VPLEX Engine (2 Directors)
         * Quad X-bricks (8 SCs, 16 storage ports), 2 networks
         * ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet7Ports(networkMap, allocatablePorts);
        context = getNet8Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net7", "net8", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Single VPLEX Engine (2 Directors)
         * Dual X-bricks (4 SCs, 3 ports from X-brick 1, 3 ports from X-brick 2), 2 networks
         * ports spread across networks with second network having only 2 ports
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNetAPorts(networkMap, allocatablePorts);
        context = getNetBPorts(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "netA", "netB", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);


        // Dual VPLEX Engine
        /**
         * Dual VPLEX Engine (4 Directors)
         * Single X-brick (2 SCs, 4 storage ports), 2 networks
         * SC ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet1Ports(networkMap, allocatablePorts);
        context = getNet2Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1DualEngines(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net1", "net2", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Dual VPLEX Engine (4 Directors)
         * Dual X-bricks (4 SCs, 8 storage ports), 2 networks
         * ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet3Ports(networkMap, allocatablePorts);
        context = getNet4Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1DualEngines(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net3", "net4", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Dual VPLEX Engine (4 Directors)
         * Quad X-bricks (8 SCs, 16 storage ports), 2 networks
         * ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet7Ports(networkMap, allocatablePorts);
        context = getNet8Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1DualEngines(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net7", "net8", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);


        // Quad VPLEX Engine
        /**
         * Quad VPLEX Engine (8 Directors)
         * Single X-brick (2 SCs, 4 storage ports), 2 networks
         * SC ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet1Ports(networkMap, allocatablePorts);
        context = getNet2Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1QuadEngines(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net1", "net2", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Quad VPLEX Engine (8 Directors)
         * Dual X-bricks (4 SCs, 8 storage ports), 2 networks
         * ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet3Ports(networkMap, allocatablePorts);
        context = getNet4Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1QuadEngines(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net3", "net4", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        /**
         * Quad VPLEX Engine (8 Directors)
         * Quad X-bricks (8 SCs, 16 storage ports), 2 networks
         * ports spread across networks
         */
        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet7Ports(networkMap, allocatablePorts);
        context = getNet8Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1QuadEngines(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net7", "net8", null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        directorCount = bemgr.getVplexDirectorCount(initiatorGroups);
        orca.setVplexDirectorCount(directorCount);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size());
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);
    }

    static Integer maskCounter = 1;

    private static void makeExportMasks(URI arrayURI, VplexXtremIOMaskingOrchestrator orca,
            Set<Map<URI, List<List<StoragePort>>>> portGroups,
            Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups,
            Map<URI, NetworkLite> networkMap) {
        // Iterate through the PortGroups generating zoning info and an ExportMask
        Iterator<Map<String, Map<URI, Set<Initiator>>>> igIterator = initiatorGroups.iterator();
        for (Map<URI, List<List<StoragePort>>> portGroup : portGroups) {
            String maskName = "testMask" + maskCounter.toString();
            maskCounter++;
            _log.info("Generating ExportMask: " + maskName);
            if (!igIterator.hasNext()) {
                igIterator = initiatorGroups.iterator();
            }
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup = igIterator.next();
            StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssignerForZones("vmax", null);
            StringSetMap zoningMap = orca.configureZoning(portGroup, initiatorGroup, networkMap, assigner);
            VPlexBackendManager mgr = new VPlexBackendManager(null, null, null, null, null, URI.create("project"), URI.create("tenant"),
                    null);
            ExportMask exportMask = mgr.generateExportMask(arrayURI, maskName, portGroup, initiatorGroup, zoningMap);
        }
        _log.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
    }

    private static PortAllocationContext getNet1Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net1";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc1", "50:00:00:00:00:00:00:7E:00");
        addPort(context, port, null);
        ports.add(port);
        port = createFCPort("X1-SC2:fc1", "50:00:00:00:00:00:00:7F:00");
        addPort(context, port, null);
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet2Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net2";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc2", "50:00:00:00:00:00:00:AE:00");
        addPort(context, port, null);
        ports.add(port);
        port = createFCPort("X1-SC2:fc2", "50:00:00:00:00:00:00:AF:00");
        addPort(context, port, null);
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet3Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net3";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc1", "50:00:00:00:00:00:00:7E:00");
        ports.add(port);
        port = createFCPort("X1-SC2:fc1", "50:00:00:00:00:00:00:8E:00");
        ports.add(port);
        port = createFCPort("X2-SC1:fc1", "50:00:00:00:00:00:00:9E:00");
        ports.add(port);
        port = createFCPort("X2-SC2:fc1", "50:00:00:00:00:00:00:AE:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet4Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net4";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc2", "50:00:00:00:00:00:00:7F:00");
        ports.add(port);
        port = createFCPort("X1-SC2:fc2", "50:00:00:00:00:00:00:8F:00");
        ports.add(port);
        port = createFCPort("X2-SC1:fc2", "50:00:00:00:00:00:00:9F:00");
        ports.add(port);
        port = createFCPort("X2-SC2:fc2", "50:00:00:00:00:00:00:AF:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet5Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net5";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc1", "50:00:00:00:00:00:00:7E:00");
        ports.add(port);
        port = createFCPort("X1-SC1:fc2", "50:00:00:00:00:00:00:8E:00");
        ports.add(port);
        port = createFCPort("X1-SC2:fc1", "50:00:00:00:00:00:00:9E:00");
        ports.add(port);
        port = createFCPort("X1-SC2:fc2", "50:00:00:00:00:00:00:AE:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet6Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net6";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X2-SC1:fc1", "50:00:00:00:00:00:00:7F:00");
        ports.add(port);
        port = createFCPort("X2-SC1:fc2", "50:00:00:00:00:00:00:8F:00");
        ports.add(port);
        port = createFCPort("X2-SC2:fc1", "50:00:00:00:00:00:00:9F:00");
        ports.add(port);
        port = createFCPort("X2-SC2:fc2", "50:00:00:00:00:00:00:AF:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet7Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net7";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc1", "50:00:00:00:00:00:00:1E:00");
        ports.add(port);
        port = createFCPort("X1-SC2:fc1", "50:00:00:00:00:00:00:2E:00");
        ports.add(port);
        port = createFCPort("X2-SC1:fc1", "50:00:00:00:00:00:00:3E:00");
        ports.add(port);
        port = createFCPort("X2-SC2:fc1", "50:00:00:00:00:00:00:4E:00");
        ports.add(port);
        port = createFCPort("X3-SC1:fc1", "50:00:00:00:00:00:00:5E:00");
        ports.add(port);
        port = createFCPort("X3-SC2:fc1", "50:00:00:00:00:00:00:6E:00");
        ports.add(port);
        port = createFCPort("X4-SC1:fc1", "50:00:00:00:00:00:00:7E:00");
        ports.add(port);
        port = createFCPort("X4-SC2:fc1", "50:00:00:00:00:00:00:8E:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNet8Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net8";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc2", "50:00:00:00:00:00:00:1F:00");
        ports.add(port);
        port = createFCPort("X1-SC2:fc2", "50:00:00:00:00:00:00:2F:00");
        ports.add(port);
        port = createFCPort("X2-SC1:fc2", "50:00:00:00:00:00:00:3F:00");
        ports.add(port);
        port = createFCPort("X2-SC2:fc2", "50:00:00:00:00:00:00:4F:00");
        ports.add(port);
        port = createFCPort("X3-SC1:fc2", "50:00:00:00:00:00:00:5F:00");
        ports.add(port);
        port = createFCPort("X3-SC2:fc2", "50:00:00:00:00:00:00:6F:00");
        ports.add(port);
        port = createFCPort("X4-SC1:fc2", "50:00:00:00:00:00:00:7F:00");
        ports.add(port);
        port = createFCPort("X4-SC2:fc2", "50:00:00:00:00:00:00:8F:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNetAPorts(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "netA";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc1", "50:00:09:73:00:18:95:19");
        ports.add(port);
        port = createFCPort("X1-SC2:fc1", "50:00:09:73:00:18:95:1C");
        ports.add(port);
        port = createFCPort("X2-SC1:fc1", "50:00:09:73:00:18:95:1D");
        ports.add(port);
        port = createFCPort("X2-SC2:fc1", "50:00:09:73:00:18:95:5C");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static PortAllocationContext getNetBPorts(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "netB";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("X1-SC1:fc2", "50:00:09:73:00:18:95:18");
        ports.add(port);
        port = createFCPort("X2-SC1:fc2", "50:00:09:73:00:18:95:21");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    /**
     * VPLEX Single Engine
     */
    private static void getInitiatorsVplex154Clus1(
            Map<String, Set<String>> directorToInitiators,
            Map<String, URI> initiatorIdToNetwork,
            Map<String, Initiator> initiatorMap, String net1, String net2, String net3) {
        directorToInitiators.clear();
        initiatorIdToNetwork.clear();
        initiatorMap.clear();
        if (net3 != null) {
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:13", net3);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:13", net3);
        } else {
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:13", net2);
        }
    }

    /**
     * VPLEX Dual Engine
     */
    private static void getInitiatorsVplex154Clus1DualEngines(
            Map<String, Set<String>> directorToInitiators,
            Map<String, URI> initiatorIdToNetwork,
            Map<String, Initiator> initiatorMap, String net1, String net2, String net3) {
        directorToInitiators.clear();
        initiatorIdToNetwork.clear();
        initiatorMap.clear();
        if (net3 != null) {
        } else {
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:13", net2);

            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:13", net2);
        }
    }

    /**
     * VPLEX Quad Engine
     */
    private static void getInitiatorsVplex154Clus1QuadEngines(
            Map<String, Set<String>> directorToInitiators,
            Map<String, URI> initiatorIdToNetwork,
            Map<String, Initiator> initiatorMap, String net1, String net2, String net3) {
        directorToInitiators.clear();
        initiatorIdToNetwork.clear();
        initiatorMap.clear();
        if (net3 != null) {
        } else {
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:13", net2);

            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-A", "50:00:14:42:80:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-2-1-B", "50:00:14:42:90:7D:C4:13", net2);

            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-A", "50:00:14:43:60:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-A", "50:00:14:43:60:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-A", "50:00:14:43:60:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-A", "50:00:14:43:60:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-B", "50:00:14:43:70:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-B", "50:00:14:43:70:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-B", "50:00:14:43:70:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-3-1-B", "50:00:14:43:70:7D:C4:13", net2);

            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-A", "50:00:14:43:80:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-A", "50:00:14:43:80:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-A", "50:00:14:43:80:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-A", "50:00:14:43:80:7D:C4:13", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-B", "50:00:14:43:90:7D:C4:10", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-B", "50:00:14:43:90:7D:C4:11", net2);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-B", "50:00:14:43:90:7D:C4:12", net1);
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-4-1-B", "50:00:14:43:90:7D:C4:13", net2);
        }
    }

    /**
     * Add a VPlex initiator to the simulation.
     * 
     * @param initiatorMap -- Map of id to initiators being generated.
     * @param directorToInitiators -- Director to initiators map.
     * @param netToInitiators -- Network to initiators map.
     * @param director -- director id to be used.
     * @param wwn
     * @param net
     */
    private static void addInitiator(
            Map<String, Initiator> initiatorMap,
            Map<String, Set<String>> directorToInitiators,
            Map<String, URI> initiatorIdToNetwork,
            String director, String wwn, String net) {
        Initiator initiator = new Initiator();
        initiator.setId(URI.create(director + "-" + wwn));
        initiator.setLabel(director + "-" + wwn);
        initiator.setInitiatorPort(wwn);
        initiatorMap.put(initiator.getId().toString(), initiator);
        if (directorToInitiators.get(director) == null) {
            directorToInitiators.put(director, new HashSet<String>());
        }
        URI netURI = URI.create(net);
        directorToInitiators.get(director).add(initiator.getId().toString());
        initiatorIdToNetwork.put(initiator.getId().toString(), netURI);
    }

    private static void logNetworks(Map<URI, List<StoragePort>> allocatablePorts) {
        for (URI netURI : allocatablePorts.keySet()) {
            StringBuilder buf = new StringBuilder();
            buf.append(netURI.toString() + " ports: ");
            for (StoragePort port : allocatablePorts.get(netURI)) {
                buf.append(port.getPortName() + " ");
            }
            _log.info(buf.toString());
            ;
        }
    }
}
