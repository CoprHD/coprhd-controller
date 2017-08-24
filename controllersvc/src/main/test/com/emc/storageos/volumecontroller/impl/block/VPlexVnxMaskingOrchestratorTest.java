/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
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
 * Test program for VPlexVnxMaskingOrchestrator
 * 
 * @author watsot3
 *         Execution setup:
 *         Required classpath: default classpath
 *         Required run directory: Directory containing test
 *         Required argument: -Dlog4j.configuration=log4j.properties
 * 
 */
public class VPlexVnxMaskingOrchestratorTest extends StoragePortsAllocatorTest {
    private static final Log _log = LogFactory.getLog(VPlexVnxMaskingOrchestratorTest.class);

    public static void main(String[] args) {
        VdcUtil.setDbClient(new DummyDbClient());
        PropertyConfigurator.configure("log4j.properties");
        _log.info("Beginning logging");
        PortAllocatorTestContext contextPrototype = new PortAllocatorTestContext();
        StoragePortsAllocator.setContextPrototype(contextPrototype);
        VPlexVnxMaskingOrchestrator orca = new VPlexVnxMaskingOrchestrator(null, null);
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

        context = getNet1Ports(networkMap, allocatablePorts);
        context = getNet2Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net1", "net2", null, false, null);
        Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, true, true);
        Set<Map<URI, List<List<StoragePort>>>> portGroups = orca.getPortGroups(null,
                allocatablePorts, networkMap, varray1, initiatorGroups.size(), null, null, null);
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet3Ports(networkMap, allocatablePorts);
        context = getNet4Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net3", "net4", null, false, null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, true, true);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size(), null, null, null);
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        context = getNet1Ports(networkMap, allocatablePorts);
        context = getNet2Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net1", "net2", null, true, null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, true, true);
        portGroups = orca.getPortGroups(
                allocatablePorts, networkMap, varray1, initiatorGroups.size(), null, null, null);
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);

        context.reinitialize();
        networkMap.clear();
        allocatablePorts.clear();
        context = getNet3Ports(networkMap, allocatablePorts);
        context = getNet4Ports(networkMap, allocatablePorts);
        logNetworks(allocatablePorts);
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net3", "net4", null, true, null);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, true, true);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size(), null, null, null);
        makeExportMasks(arrayURI, orca, portGroups, initiatorGroups, networkMap);
        
        
        // switch affinity tests
        networkMap.clear();
        allocatablePorts.clear(); 
        Map<URI, Map<String, List<StoragePort>>> portSwitchMap = new HashMap<URI, Map<String, List<StoragePort>>>();
        Map<URI, PortAllocationContext> contextMap = new HashMap<URI, PortAllocationContext>();
        PortAllocationContext context1 = getNet1PortsSA(networkMap, allocatablePorts, portSwitchMap);
        PortAllocationContext context2 = getNet2PortsSA(networkMap, allocatablePorts, portSwitchMap);
        contextMap.put(context1._initiatorNetwork.getId(), context1);
        contextMap.put(context2._initiatorNetwork.getId(), context2);
        logNetworks(allocatablePorts);
        Map<URI, String> initiatorSwitchMap = new HashMap<URI, String>();
        getInitiatorsVplex154Clus1(directorToInitiators, initiatorIdToNetwork, initiatorMap,
                "net11", "net12", null, true, initiatorSwitchMap);
        initiatorGroups =
                bemgr.getInitiatorGroups("test", directorToInitiators, initiatorIdToNetwork, initiatorMap, false, true);
        portGroups = orca.getPortGroups(allocatablePorts, networkMap, varray1, initiatorGroups.size(),
                getSwitchToPortNumber(networkMap.keySet(), 2), contextMap, null);
        makeExportMasksSA(arrayURI, orca, portGroups, initiatorGroups, networkMap, initiatorSwitchMap, portSwitchMap);
        

    }

    static Integer maskCounter = 1;

    private static void makeExportMasks(URI arrayURI, VPlexVnxMaskingOrchestrator orca,
            Set<Map<URI, List<List<StoragePort>>>> portGroups,
            Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups,
            Map<URI, NetworkLite> networkMap) {
        // Iterate through the PortGroups generating zoning info and an ExportMask
        Iterator<Map<String, Map<URI, Set<Initiator>>>> igIterator = initiatorGroups.iterator();
        Iterator<Map<URI, List<List<StoragePort>>>> pgIterator = portGroups.iterator();

        while (igIterator.hasNext()) {
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup = igIterator.next();
            String maskName = "testMask" + maskCounter.toString();
            if (!pgIterator.hasNext()) {
                break;
            }
            Map<URI, List<List<StoragePort>>> portGroup = pgIterator.next();
            maskCounter++;
            _log.info("Generating ExportMask: " + maskName);
            StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssignerForZones("vnxblock", null);
            StringSetMap zoningMap = orca.configureZoning(portGroup, initiatorGroup, networkMap, assigner, null, null, null);
            VPlexBackendManager mgr = new VPlexBackendManager(null, null, null, null, null, URI.create("project"), URI.create("tenant"),
                    null, null);
            ExportMask exportMask = mgr.generateExportMask(arrayURI, maskName, portGroup, initiatorGroup, zoningMap);
        }
        _log.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    private static PortAllocationContext getNet1Ports(Map<URI, NetworkLite> networkMap, Map<URI, List<StoragePort>> allocatablePorts) {
        String label = "net1";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("SP_A:1", "50:00:00:00:00:00:00:7E:00");
        addPort(context, port, null);
        ports.add(port);
        port = createFCPort("SP_B:1", "50:00:00:00:00:00:00:7F:00");
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
        port = createFCPort("SP_A:2", "50:00:00:00:00:00:00:AE:00");
        addPort(context, port, null);
        ports.add(port);
        port = createFCPort("SP_B:2", "50:00:00:00:00:00:00:AF:00");
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
        port = createFCPort("SP_A:1", "50:00:00:00:00:00:00:7E:00");
        ports.add(port);
        port = createFCPort("SP_B:1", "50:00:00:00:00:00:00:8E:00");
        ports.add(port);
        port = createFCPort("SP_A:3", "50:00:00:00:00:00:00:9E:00");
        ports.add(port);
        port = createFCPort("SP_B:3", "50:00:00:00:00:00:00:AE:00");
        ports.add(port);
        port = createFCPort("SP_A:5", "50:00:00:00:00:00:00:71:00");
        ports.add(port);
        port = createFCPort("SP_B:5", "50:00:00:00:00:00:00:81:00");
        ports.add(port);
        port = createFCPort("SP_A:7", "50:00:00:00:00:00:00:91:00");
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
        port = createFCPort("SP_A:2", "50:00:00:00:00:00:00:7F:00");
        ports.add(port);
        port = createFCPort("SP_B:2", "50:00:00:00:00:00:00:8F:00");
        ports.add(port);
        port = createFCPort("SP_A:4", "50:00:00:00:00:00:00:9F:00");
        ports.add(port);
        port = createFCPort("SP_B:4", "50:00:00:00:00:00:00:AF:00");
        ports.add(port);
        port = createFCPort("SP_A:6", "50:00:00:00:00:00:00:72:00");
        ports.add(port);
        port = createFCPort("SP_B:6", "50:00:00:00:00:00:00:82:00");
        ports.add(port);
        port = createFCPort("SP_B:8", "50:00:00:00:00:00:00:92:00");
        ports.add(port);
        allocatablePorts.put(id, ports);
        return context;
    }

    private static void getInitiatorsVplex154Clus1(
            Map<String, Set<String>> directorToInitiators,
            Map<String, URI> initiatorIdToNetwork,
            Map<String, Initiator> initiatorMap, String net1, String net2, String net3, boolean half,
            Map<URI, String> initiatorSwitchMap) {
        directorToInitiators.clear();
        initiatorIdToNetwork.clear();
        initiatorMap.clear();
        if (net3 != null) {
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:10", net1, initiatorSwitchMap, "mds-a");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:11", net2, initiatorSwitchMap, "mds-a");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:12", net1, initiatorSwitchMap, "mds-a");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:13", net3, initiatorSwitchMap, "mds-a");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:10", net1, initiatorSwitchMap, "mds-b");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:11", net2, initiatorSwitchMap, "mds-b");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:12", net1, initiatorSwitchMap, "mds-b");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:13", net3, initiatorSwitchMap, "mds-b");
        } else {
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:10", net1, initiatorSwitchMap, "mds-a");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:11", net2, initiatorSwitchMap, "mds-a");
            if (half == false) {
                addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:12", net1, initiatorSwitchMap, "mds-a");
                addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-A", "50:00:14:42:60:7D:C4:13", net2, initiatorSwitchMap, "mds-a");
            }
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:10", net1, initiatorSwitchMap, "mds-b");
            addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:11", net2, initiatorSwitchMap, "mds-b");
            if (half == false) {
                addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:12", net1, initiatorSwitchMap, "mds-b");
                addInitiator(initiatorMap, directorToInitiators, initiatorIdToNetwork, "director-1-1-B", "50:00:14:42:70:7D:C4:13", net2, initiatorSwitchMap, "mds-b");
            }
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
            String director, String wwn, String net,
            Map<URI, String> initiatorSwitchMap,
            String switchName) {
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
        if (initiatorSwitchMap != null) {
            initiatorSwitchMap.put(initiator.getId(), switchName);
        }
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
    
    private static PortAllocationContext getNet1PortsSA(Map<URI, NetworkLite> networkMap, 
            Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, Map<String, List<StoragePort>>> portSwitchMap) {
        String label = "net11";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        Map<String, List<StoragePort>>switchMap = new HashMap<String, List<StoragePort>>();
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        List<StoragePort> switchAPorts = new ArrayList<StoragePort>();
        List<StoragePort> switchBPorts = new ArrayList<StoragePort>();
        port = createFCPort("SP_A:1", "50:00:00:00:00:00:00:7E:00");
        addPort(context, port, "mds-a");
        ports.add(port);
        switchAPorts.add(port);
        port = createFCPort("SP_B:1", "50:00:00:00:00:00:00:7F:00");
        addPort(context, port, "mds-a");
        ports.add(port);
        switchAPorts.add(port);
        port = createFCPort("SP_A:3", "50:00:00:00:00:00:00:9E:00");
        addPort(context, port, "mds-b");
        ports.add(port);
        switchBPorts.add(port);
        port = createFCPort("SP_B:3", "50:00:00:00:00:00:00:AE:00");
        addPort(context, port, "mds-b");
        ports.add(port);
        switchBPorts.add(port);
        allocatablePorts.put(id, ports);
        switchMap.put("mds-a", switchAPorts);
        switchMap.put("mds-b", switchBPorts);
        portSwitchMap.put(id, switchMap);
        return context;
    }

    private static PortAllocationContext getNet2PortsSA(Map<URI, NetworkLite> networkMap, 
            Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, Map<String, List<StoragePort>>> portSwitchMap) {
        String label = "net12";
        URI id = URI.create(label);
        NetworkLite net = new NetworkLite(id, label);
        networkMap.put(id, net);
        Map<String, List<StoragePort>>switchMap = new HashMap<String, List<StoragePort>>();
        PortAllocationContext context = new PortAllocationContext(net, label);
        StoragePort port = null;
        List<StoragePort> ports = new ArrayList<StoragePort>();
        port = createFCPort("SP_A:2", "50:00:00:00:00:00:00:AE:00");
        addPort(context, port, "mds-b");
        ports.add(port);
        port = createFCPort("SP_B:2", "50:00:00:00:00:00:00:AF:00");
        addPort(context, port, "mds-b");
        ports.add(port);
        allocatablePorts.put(id, ports);
        switchMap.put("mds-b", ports);
        portSwitchMap.put(id, switchMap);
        return context;
    }
 
    private static Map<URI, Map<String, Integer>> getSwitchToPortNumber(Collection<URI> networkList, Integer path) {
        Map<URI, Map<String, Integer>> result = new HashMap<URI, Map<String, Integer>>();
        for (URI net : networkList) {
            Map<String, Integer> switchMap = new HashMap<String, Integer>();
            switchMap.put("mds-a", path);
            switchMap.put("mds-b", path);
            result.put(net, switchMap);
        }
        return result;
    }
    
    private static void makeExportMasksSA(URI arrayURI, VPlexVnxMaskingOrchestrator orca,
            Set<Map<URI, List<List<StoragePort>>>> portGroups,
            Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups,
            Map<URI, NetworkLite> networkMap,
            Map<URI, String> initiatorSwitchMap,
            Map<URI, Map<String, List<StoragePort>>>portSwitchMap) {
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
            StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssignerForZones("vnxblock", null);
            StringSetMap zoningMap = orca.configureZoning(portGroup, initiatorGroup, networkMap, assigner, initiatorSwitchMap,
                    portSwitchMap,  VPlexVmaxMaskingOrchestratorTest.getPortSwitchNameMap(portSwitchMap));
            VPlexBackendManager mgr = new VPlexBackendManager(null, null, null, null, null, URI.create("project"), URI.create("tenant"),
                    null, null);
            ExportMask exportMask = mgr.generateExportMask(arrayURI, maskName, portGroup, initiatorGroup, zoningMap);
        }
        _log.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }
}
