/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;

/*
 * Test for Storage Port Assigner and Allocator.
 * @author watsot3
 * 
 *         Required classpath: placement (directory containing the test source)
 *         eclipse.out (directory containing complied classes)
 *         slf4j-api-1.6.4.jar slf4j-ext-1.6.4.jar slf4j-log4j12-1.6.4.jar
 *         log4j-1.2.16.jar
 *         jersey-core-1.12.jar
 *         commons-lang-2.4.jar (Apache commons) 
 *         Required run directory: Directory containing test
 *         source Required argument: -Dlog4j.configuration=log4j.properties
 *         
 */
public class StoragePortsAssignerTest extends StoragePortsAllocatorTest {
    private static final Log _log = LogFactory
            .getLog(StoragePortsAssignerTest.class);

    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure("log4j.properties");
        StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner("VMAX");
        _log.info("Beginning logging");

        List<Initiator> initA = getHostInitiators(2);
//        System.out.println("Beginning NBS test");
        Map<URI, List<Initiator>> net2InitiatorsMap = makeNet2InitiatorsMap(initA, 3, 4);
//        for (int i = 0; i < 100; i++) {
//            testVMAX2NetNBSAllocAssign(net2InitiatorsMap, 2, 2, 1);
//        }
//        System.out.println("Ended NBS test");

//        for (int j=1; j <= 2; j++) { // pathsPerInitiator
//            for (int i=1; i <= 10; i++) { // maxPaths
//                System.out.println("2 initiators across 2 networks: " + "max_paths = " + i + " paths_per_initiator = " + j);
//                try {
//                    ExportPathParams pathParam = new ExportPathParams(i, 1, j);
//                    Map<URI, Integer> portsPerNetwork = assigner.getPortsNeededPerNetwork(
//                            net2InitiatorsMap,pathParam, null, null);
//                    for (Integer ival : portsPerNetwork.values()) {
//                        System.out.println("   " + ival);
//                    }
//                } catch (PlacementException ex) {
//                    System.out.println("PlacementException: " + ex.getMessage());
//                }
//            }
//        }

//        System.out.println("Testing calculation of number of ports per network required");
//        initA = getHostInitiators(8);
//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 2);
//        for (int j=1; j <= 2; j++) { // pathsPerInitiator
//            for (int i=1; i <= 10; i++) { // maxPaths
//                System.out.println("8 initiators across 2 networks: " + "max_paths = " + i + " paths_per_initiator = " + j);
//                try {
//                    ExportPathParams pathParam = new ExportPathParams(i, 1, j);
//                    Map<URI, Integer> portsPerNetwork = assigner.getPortsNeededPerNetwork(
//                            net2InitiatorsMap, pathParam, null, null);
//                    for (Integer ival : portsPerNetwork.values()) {
//                        System.out.println("   " + ival);
//                    }
//                } catch (PlacementException ex) {
//                    System.out.println("PlacementException: " + ex.getMessage());
//                }
//            }
//        }
//        
        

//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 3);
//        for (int j=1; j <= 2; j++) { // pathsPerInitiator
//            for (int i=1; i <= 10; i++) { // maxPaths
//                System.out.println("8 initiators across 3 networks: " + "max_paths = " + i + " paths_per_initiator = " + j);
//                try {
//                    ExportPathParams pathParam = new ExportPathParams(i, 1, j);
//                    Map<URI, Integer> portsPerNetwork =
//                            assigner.getPortsNeededPerNetwork(net2InitiatorsMap,pathParam, null, null);
//                    for (Integer ival : portsPerNetwork.values()) {
//                        System.out.println("   " + ival);
//                    }
//                } catch (PlacementException ex) {
//                    System.out.println("throws PlacementException: " + ex.getMessage());
//                }
//            }
//        }
        

//        initA = getHostInitiators(2);
//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 2);
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 6; i++) {
//                System.out.println("*** 2 initiators across 2 networks: max_paths = " + i
//                        + " min_paths " + i
//                        + " paths_per_initiator = " + j);
//                if (2 * j > i) {
//                    int initiatorCap = j / i;
//                    if (initiatorCap * j < i) {
//                        System.out.println("Expecting PlacementException due to insufficient max_paths for paths_per_initiator");
//                    }
//                }
//                if (2 * j < i) {
//                    System.out.println("Expecting PlacementException due to insufficient initiators");
//                }
//                testVMAX2NetAllocAssign(net2InitiatorsMap, i, i, j);
//            }
//        }

        initA = getHostInitiators(4);
        Map<URI, List<Initiator>> net2InitiatorsMapA = makeNet2InitiatorsMap(initA, 1,2);
        List<Initiator> initB = getHostInitiators(4);
        Map<URI, List<Initiator>> net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 1,2);
        for (int k=1; k <= 2; k++) {  //initiators per port
        for (int j = 1; j <= 2; j++) {  // paths per initiator
            for (int i = 1; i <= 8; i++) {  // max paths
                System.out.println("*** 4 initiators across 2 networks: " 
                    + "max_paths = " + i + " paths_per_initiator = " + j + " initiators per port " + k);
                testVMAX2NetAllocAssign(net2InitiatorsMapA, net2InitiatorsMapB, null, null, i, 0, j, k);
            }
        }
        }
        
        System.out.println("Hosts on non-overlapping networks!");
        initA = getHostInitiators(4);
        net2InitiatorsMapA = makeNet2InitiatorsMap(initA, 1,2);
        initB = getHostInitiators(4);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 3,4);
        for (int k=1; k <= 2; k++) {  //initiators per port
        for (int j = 1; j <= 2; j++) {  // paths per initiator
            for (int i = 1; i <= 8; i++) {  // max paths
                System.out.println("*** 4 initiators across 2 networks: " 
                    + "max_paths = " + i + " paths_per_initiator = " + j + " initiators per port " + k);
                testVMAX4NetAllocAssign(net2InitiatorsMapA, net2InitiatorsMapB, null, null, i, 0, j, k);
            }
        }
        }

//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 12; i++) {
//                System.out.println("*** 4 initiators across 2 networks: max_paths = " + i + " paths_per_initiator = " + j);
//                testVNX2NetAllocAssign(net2InitiatorsMap, i, j);
//            }
//        }

//        // Four hosts, each 2 initiators, across 2 networks
//        initA = getHostInitiators(2);
//        List<Initiator> initB = getHostInitiators(2);
//        initA.addAll(initB);
//        initB = getHostInitiators(2);
//        initA.addAll(initB);
//        initB = getHostInitiators(2);
//        initA.addAll(initB);
//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 2);
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 6; i++) {
//                System.out.println("*** Four hosts 2 initiators across 2 networks: max_paths = " + i + " paths_per_initiator = " + j);
//                testVMAX2NetAllocAssign(net2InitiatorsMap, i, 0, j);
//            }
//        }

//        for (int i = 1; i <= 6; i++) {
//            int j = 1;
//            System.out.println("*** Four hosts 2 initiators across 2 networks: max_paths = " + i + " paths_per_initiator = " + j);
//            testVNXSmallAllocAssign(net2InitiatorsMap, i, j);
//        }

//        // Test incremental initiator / port addition
//        initA = getHostInitiators(2);
//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 2);
//        initB = getHostInitiators(2);
//        Map<URI, List<Initiator>> net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 2);
//
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 4; i++) {
//                System.out.println("*** 2 initiators across 2 networks, incremental 2 more initiators in new host: max_paths = " + i
//                        + " paths_per_initiator = " + j);
//                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
//            }
//        }

//        URI hostAid = initA.get(0).getHost();
//        String hostAname = initA.get(0).getHostName();
//        List<Initiator> initC = addHostInitiators(2, hostAid, hostAname);
//        net2InitiatorsMapB = makeNet2InitiatorsMap(initC, 2);
//
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 6; i++) {
//                System.out.println("*** 2 initiators across 2 networks, incremental 2 more initiators in same host: max_paths = " + i
//                        + " paths_per_initiator = " + j);
//                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
//            }
//        }
//
//        initA = getHostInitiators(4);
//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 2);
//        initB = getHostInitiators(4);
//        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 2);
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 8; i++) {
//                System.out.println("*** 4 initiators across 2 networks, incremental 4 more initiators in new host: max_paths = " + i
//                        + " paths_per_initiator = " + j);
//                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
//            }
//        }
//
//        initA = getHostInitiators(4);
//        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 2);
//        hostAid = initA.get(0).getHost();
//        hostAname = initA.get(0).getHostName();
//        initC = addHostInitiators(4, hostAid, hostAname);
//        net2InitiatorsMapB = makeNet2InitiatorsMap(initC, 2);
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 1; i <= 12; i++) {
//                System.out.println("*** 4 initiators across 2 networks, incremental 4 more initiators in same host: max_paths = " + i
//                        + " paths_per_initiator = " + j);
//                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
//            }
//        }
//
//        for (int j = 1; j <= 2; j++) {
//            for (int i = 2; i <= 12; i++) {
//                System.out.println("*** 4 initiators across 2 networks, incremental 4 more initiators in same host: max_paths = " + i
//                        + " paths_per_initiator = " + j);
//                testVNX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
//            }
//        }

        System.out.println("done!");
    }

//    /**
//     * Test allocation in a VMAX two network environment.
//     * 
//     * @param net2InitiatorsMap -- Network to Initiators Map
//     * @param maxPaths -- total maxPaths
//     * @param minPaths -- total minPaths
//     * @param pathsPerInitiator -- desired paths per initiator
//     */
//    public static void testVMAX2NetAllocAssign(
//            Map<URI, List<Initiator>> net2InitiatorsMap, int maxPaths, int minPaths, int pathsPerInitiator)
//            throws Exception {
//        Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
//        URI host1 = URI.create("hostA");
//        hostToNetToInitiatorsMap.put(host1, net2InitiatorsMap);
//        PortAllocationContext net1ctx = createVmaxNet1();
//        PortAllocationContext net2ctx = createVmaxNet2();
//        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
//        testAllocationAssignment(contexts, hostToNetToInitiatorsMap, maxPaths, minPaths, pathsPerInitiator, 1, "vmax", null);
//    }
    
    public static void testVMAX2NetAllocAssign(
            Map<URI, List<Initiator>> net2InitiatorsMapA, 
            Map<URI, List<Initiator>> net2InitiatorsMapB,
            Map<URI, List<Initiator>> net2InitiatorsMapC,
            Map<URI, List<Initiator>> net2InitiatorsMapD,
            int maxPaths, int minPaths, int pathsPerInitiator, int initiatorsPerPort)
            throws Exception {
        Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
        URI hostA = getHostURI(net2InitiatorsMapA);
        hostToNetToInitiatorsMap.put(hostA, net2InitiatorsMapA);
        if (net2InitiatorsMapB != null) {
            URI hostB = getHostURI(net2InitiatorsMapB);
            hostToNetToInitiatorsMap.put(hostB, net2InitiatorsMapB);
        }
        if (net2InitiatorsMapC != null) {
            URI hostC = getHostURI(net2InitiatorsMapC);
            hostToNetToInitiatorsMap.put(hostC, net2InitiatorsMapC);
        }
        if (net2InitiatorsMapD != null) {
            URI hostD = getHostURI(net2InitiatorsMapD);
            hostToNetToInitiatorsMap.put(hostD, net2InitiatorsMapD);
        }
        PortAllocationContext net1ctx = createVmaxNet1();
        PortAllocationContext net2ctx = createVmaxNet2();
        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
        testAllocationAssignment(contexts, hostToNetToInitiatorsMap, maxPaths, minPaths, 
                pathsPerInitiator, initiatorsPerPort, "vmax", null);
    }
    
    public static void testVMAX4NetAllocAssign(
            Map<URI, List<Initiator>> net2InitiatorsMapA, 
            Map<URI, List<Initiator>> net2InitiatorsMapB,
            Map<URI, List<Initiator>> net2InitiatorsMapC,
            Map<URI, List<Initiator>> net2InitiatorsMapD,
            int maxPaths, int minPaths, int pathsPerInitiator, int initiatorsPerPort)
            throws Exception {
        Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
        URI hostA = getHostURI(net2InitiatorsMapA);
        hostToNetToInitiatorsMap.put(hostA, net2InitiatorsMapA);
        if (net2InitiatorsMapB != null) {
            URI hostB = getHostURI(net2InitiatorsMapB);
            hostToNetToInitiatorsMap.put(hostB, net2InitiatorsMapB);
        }
        if (net2InitiatorsMapC != null) {
            URI hostC = getHostURI(net2InitiatorsMapC);
            hostToNetToInitiatorsMap.put(hostC, net2InitiatorsMapC);
        }
        if (net2InitiatorsMapD != null) {
            URI hostD = getHostURI(net2InitiatorsMapD);
            hostToNetToInitiatorsMap.put(hostD, net2InitiatorsMapD);
        }
        PortAllocationContext net1ctx = createVmaxNet1();
        PortAllocationContext net2ctx = createVmaxNet2();
        PortAllocationContext net3ctx = createVmaxNet3();
        PortAllocationContext net4ctx = createVmaxNet4();
        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx, net3ctx, net4ctx };
        testAllocationAssignment(contexts, hostToNetToInitiatorsMap, maxPaths, minPaths, 
                pathsPerInitiator, initiatorsPerPort, "vmax", null);
    }

//    public static void testVMAX2NetNBSAllocAssign(
//            Map<URI, List<Initiator>> net2InitiatorsMap, int maxPaths, int minPaths, int pathsPerInitiator)
//            throws Exception {
//        PortAllocationContext net1ctx = createVmaxNet3();
//        PortAllocationContext net2ctx = createVmaxNet4();
//        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
//        testAllocationAssignment(contexts, net2InitiatorsMap, maxPaths, minPaths, pathsPerInitiator, "vmax", null);
//    }

//    public static void testVMAX2NetAllocIncrementalAssign(
//            Map<URI, List<Initiator>> net2InitiatorsMapA,
//            Map<URI, List<Initiator>> net2InitiatorsMapB, int maxPaths, int pathsPerInitiator)
//            throws Exception {
//        PortAllocationContext net1ctx = createVmaxNet1();
//        PortAllocationContext net2ctx = createVmaxNet2();
//        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
//        // Allocate initial assignments
//        System.out.println("Initial assignments:");
//        Map<Initiator, List<StoragePort>> assignments =
//                testAllocationAssignment(contexts, net2InitiatorsMapA, maxPaths, 0, pathsPerInitiator, "vmax", null);
//        // Allocate incremental assignments
//        List<StoragePort> existingTargets = new ArrayList<StoragePort>();
//        if (assignments == null) {
//            return;
//        }
//        for (List<StoragePort> list : assignments.values()) {
//            existingTargets.addAll(list);
//        }
//        List<Initiator> existingInitiators = new ArrayList<Initiator>();
//        for (List<Initiator> oldInitiators : net2InitiatorsMapA.values()) {
//            existingInitiators.addAll(oldInitiators);
//        }
//        System.out.println("Incremental assignments:");
//        assignments = testAllocationAssignment(contexts, net2InitiatorsMapB, maxPaths, 0, pathsPerInitiator,
//                "vmax", assignments);
//    }

//    public static void testVNX2NetAllocIncrementalAssign(
//            Map<URI, List<Initiator>> net2InitiatorsMapA,
//            Map<URI, List<Initiator>> net2InitiatorsMapB, int maxPaths, int pathsPerInitiator)
//            throws Exception {
//        PortAllocationContext net1ctx = createVNXNet1();
//        PortAllocationContext net2ctx = createVNXNet2();
//        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
//        // Allocate initial assignments
//        System.out.println("Initial assignments:");
//        Map<Initiator, List<StoragePort>> assignments =
//                testAllocationAssignment(contexts, net2InitiatorsMapA,
//                        maxPaths, 0, pathsPerInitiator, "vnxblock", null);
//        // Allocate incremental assignments
//        List<StoragePort> existingTargets = new ArrayList<StoragePort>();
//        if (assignments == null) {
//            return;
//        }
//        for (List<StoragePort> list : assignments.values()) {
//            existingTargets.addAll(list);
//        }
//        List<Initiator> existingInitiators = new ArrayList<Initiator>();
//        for (List<Initiator> oldInitiators : net2InitiatorsMapA.values()) {
//            existingInitiators.addAll(oldInitiators);
//        }
//        System.out.println("Incremental assignments:");
//        assignments = testAllocationAssignment(contexts, net2InitiatorsMapB,
//                maxPaths, 0, pathsPerInitiator, "vnxblock", assignments);
//    }

    /**
     * Test allocation in a VNX two network environment.
     * 
     * @param net2InitiatorsMap
     * @param maxPaths
     */
//    public static void testVNX2NetAllocAssign(
//            Map<URI, List<Initiator>> net2InitiatorsMap, int maxPaths, int pathsPerInitiator) throws Exception {
//        PortAllocationContext net1ctx = createVNXNet1();
//        PortAllocationContext net2ctx = createVNXNet2();
//        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
//        testAllocationAssignment(contexts, net2InitiatorsMap, maxPaths, 0, pathsPerInitiator, "vnxblock", null);
//    }

//    public static void testVNXSmallAllocAssign(
//            Map<URI, List<Initiator>> net2InitiatorsMap, int maxPaths, int pathsPerInitiator) throws Exception {
//        PortAllocationContext net1ctx = createVNXNet3();
//        PortAllocationContext net2ctx = createVNXNet4();
//        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
//        testAllocationAssignment(contexts, net2InitiatorsMap, maxPaths, 0, pathsPerInitiator, "vnxblock", null);
//    }

    /**
     * Test port allocation and assignment.
     * 
     * @param contexts -- Array of PortAllocationContext structures, one for each Network.
     * @param hostToNetToInitiators -- Map of host to map of network URI to Initiators
     * @param maxPaths -- max_paths variable
     * @param minPaths -- mininmum number of paths to provision
     * @param pathsPerInitiator -- desired number of paths per initiator
     * @param initiatorsPerPort -- maximum number of initiators in a host using same port
     * @param arrayType -- String array type
     * @param existingAssignments - previously assigned Initiator to StoragePort list mappings
     */
    public static Map<Initiator, List<StoragePort>> testAllocationAssignment(PortAllocationContext[] contexts,
            Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiators,
            int maxPaths, int minPaths, int pathsPerInitiator, int initiatorsPerPort, 
            String arrayType,
            Map<Initiator, List<StoragePort>> existingAssignments) throws Exception {
        Map<URI, List<Initiator>> net2InitiatorsMap = makeNet2InitiatorsMap(hostToNetToInitiators);
        Map<Initiator, List<StoragePort>> assignments = new HashMap<Initiator, List<StoragePort>>();
        if (pathsPerInitiator > maxPaths) {
            return assignments;
        }
        ExportPathParams pathParams = new ExportPathParams(maxPaths, minPaths, pathsPerInitiator);
        pathParams.setMaxInitiatorsPerPort(initiatorsPerPort);
        try {
            for (int i = 0; i < contexts.length; i++) {
                contexts[i].reinitialize();
            }
            Map<URI, PortAllocationContext> net2ContextMap = new HashMap<URI, PortAllocationContext>();
            for (int i = 0; i < contexts.length; i++) {
                PortAllocationContext context = contexts[i];
                net2ContextMap.put(context._initiatorNetwork.getId(), context);
            }

            // Map the existing (already allocated) StoragePorts to their Networks.
            Map<URI, Set<StoragePort>> existingPortsMap =
                    generateNetworkToStoragePortsMap(existingAssignments);

            // Make a Map of Network to existing Initiators
            Map<URI, Set<Initiator>> existingInitiatorsMap =
                    generateNetworkToInitiatorsMap(existingAssignments);

            // Compute the number of Ports needed for each Network
            StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner(arrayType);
            Map<URI, Integer> net2PortsNeeded = assigner.getPortsNeededPerNetwork(
                    net2InitiatorsMap, pathParams, existingPortsMap, existingInitiatorsMap);

            // For each Network, allocate the ports required, and then assign the ports.
            StoragePortsAllocator allocator = new StoragePortsAllocator();

            Map<URI, List<StoragePort>> netToPortsAllocated = new HashMap<URI, List<StoragePort>>();
            PortAllocationContext previousContext = null;
            for (URI netURI : net2PortsNeeded.keySet()) {
                Integer portsNeeded = net2PortsNeeded.get(netURI);
                if (portsNeeded == 0) {
                    System.out.println("No ports to be assigned for net: " + netURI);
                    continue;
                }
                // Get the context for this network.
                PortAllocationContext context = net2ContextMap.get(netURI);
                // Copy context from the previous allocation.
                if (previousContext != null) {
                    context.copyPreviousNetworkContext(previousContext);
                }
                previousContext = context;
                List<StoragePort> portsAllocated =
                        allocator.allocatePortsForNetwork(portsNeeded, context, false, existingPortsMap.get(netURI), false);
                netToPortsAllocated.put(netURI,portsAllocated);
                
            }
            
            // Noew for each host, do the port assignment.
            for (Map.Entry<URI, Map<URI, List<Initiator>>> entry: hostToNetToInitiators.entrySet()) {
                System.out.println("Assign ports for host " + entry.getKey());
                assigner.assignPortsToHost(assignments, entry.getValue(), netToPortsAllocated, 
                        pathParams, existingAssignments, entry.getKey());   

            }

            for (Initiator initiator : assignments.keySet()) {
                System.out.print(initiator.getHostName() + "-" + initiator.getInitiatorPort() + " -> ");
                List<StoragePort> ports = assignments.get(initiator);
                if (ports == null) {
                    System.out.print("<ignored>");
                } else {
                    for (StoragePort port : assignments.get(initiator)) {
                        System.out.print(port.getPortName() + " ");
                    }
                }
                System.out.println(" ");
            }

            verifyAssignments(assignments, arrayType, maxPaths, pathsPerInitiator, initiatorsPerPort,
                    net2InitiatorsMap, existingAssignments);
        } catch (PlacementException ex) {
            System.out.println("PlacementException: " + ex.getMessage());
        } catch (Exception ex) {
            System.out.println("ERROR: " + ex.getMessage());
            throw ex;
        }
        return assignments;
    }

    /**
     * Verify the assignments are consistent with tne number of initiators,
     * the number of ports allocated, and the max_paths parameter.
     * 
     * @param assignments
     * @param arrayType
     * @param maxPaths
     * @param pathsPerInitiator
     * @param maxInitiatorsPerPort
     * @param net2InitiatorsMap
     * @throws Exception
     */
    static public void verifyAssignments(Map<Initiator, List<StoragePort>> assignments,
            String arrayType, int maxPaths, int pathsPerInitiator, int maxInitiatorsPerPort,
            Map<URI, List<Initiator>> net2InitiatorsMap,
            Map<Initiator, List<StoragePort>> existingAssignments)
            throws Exception {
        // sum up the StoragePorts
        Set<URI> allPorts = new HashSet<URI>();
        for (List<StoragePort> ports : assignments.values()) {
            for (StoragePort port : ports) {
                allPorts.add(port.getId());
            }
        }
        if (existingAssignments != null) {
            for (List<StoragePort> ports : existingAssignments.values()) {
                for (StoragePort port : ports) {
                    allPorts.add(port.getId());
                }
            }
        }

        // Make a map of Host to Initiators
        Set<Initiator> allInitiators = new HashSet<Initiator>();
        allInitiators.addAll(assignments.keySet());
        if (existingAssignments != null) {
            allInitiators.addAll(existingAssignments.keySet());
        }
        Map<URI, Set<Initiator>> hostInitiatorsMap = generateHostInitiatorsMap(allInitiators);

        // Check each host:
        for (URI host : hostInitiatorsMap.keySet()) {
            Set<Initiator> initiators = hostInitiatorsMap.get(host);
            Integer ninitiators = initiators.size();
            // Compute how many assignments should have been made
            Map<StoragePort, Integer> portUseCounts = new HashMap<StoragePort, Integer>();
            
            for (Initiator initiator : initiators) {
                // Find the assignments for this initiator
                List<StoragePort> portAssignments = assignments.get(initiator);
                if (portAssignments == null && existingAssignments != null) {
                    existingAssignments.get(initiator);
                }
                if (portAssignments == null)
                {
                    continue;  // if unassigned
                }
                for (StoragePort port : portAssignments) {
                    DefaultStoragePortsAssigner.addPortUse(portUseCounts, port);
                }
                Integer nports = portAssignments.size();
                if (nports > pathsPerInitiator) {
                    throw new Exception(String.format(
                            "Initiator %s has too many ports: %s", initiator.getInitiatorPort(), nports));
                }
            }
            // Verify the total number of ports for the host is not > maxPaths
            if (portUseCounts.size() > maxPaths) {
                throw new Exception(String.format(
                        "Host %s uses more storage ports (%s) than maxPaths %d", host, portUseCounts.size(), maxPaths));
            }
            for (Map.Entry<StoragePort, Integer> portEntry : portUseCounts.entrySet()) {
                if (portEntry.getValue() > maxInitiatorsPerPort) {
                    throw new Exception((String.format("Port %s was used by %d initiators but maxInitiatorsPerPort was %d", 
                            portEntry.getKey().getLabel(), portEntry.getValue(), maxInitiatorsPerPort)))
;                }
            }
            if (portUseCounts.size() < maxPaths) {
                // Then we should be limited by the number of Initiators * pathsPerInitiator.
                if (portUseCounts.size() > (ninitiators * pathsPerInitiator)) {
                    throw new Exception(String.format(
                            "Host %s used more ports than expected (ninitiators %s * pathsPerInitiator %s)",
                            host, ninitiators, pathsPerInitiator));
                }
            }
        }
    }

    /**
     * Returns number of hosts in the net2InitiatorsMap
     * 
     * @param net2InitiatorsMap
     * @return
     */
    static int numberOfHosts(Map<URI, List<Initiator>> net2InitiatorsMap) {
        Set<URI> hostSet = new HashSet<URI>();
        for (List<Initiator> initiators : net2InitiatorsMap.values()) {
            for (Initiator init : initiators) {
                hostSet.add(init.getHost());
            }
        }
        return hostSet.size();
    }

    static private int hostIndex = 1;
    static private int initIndex = 1;

    /**
     * Returns initiators in a single host.
     * 
     * @param numberInitiators -- number of initiators
     * @return List<Initiator>
     */
    static private List<Initiator> getHostInitiators(int numberInitiators) {
        Host host = new Host();
        host.setHostName("host" + hostIndex++);
        host.setId(URI.create(host.getHostName()));
        List<Initiator> initiators = new ArrayList<Initiator>();
        for (int i = 0; i < numberInitiators; i++) {
            Initiator initiator = new Initiator();
            initiator.setHost(host.getId());
            initiator.setHostName(host.getHostName());
            String byte1 = String.format("%02x", initIndex / 256);
            String byte0 = String.format("%02x", initIndex % 256);
            initiator.setInitiatorPort("10:00:00:00:00:00:" + byte1 + ":" + byte0);
            initiator.setId(URI.create("init" + initIndex++));
            initiators.add(initiator);
        }
        return initiators;
    }

    /**
     * Generates additional host initiators for an existing host.
     * 
     * @param numberInitiators -- number of new initiators to generate
     * @param host -- existing Host
     * @return -- List of all initiators
     */
    static private List<Initiator> addHostInitiators(int numberInitiators, URI hostId, String hostName) {
        List<Initiator> initiators = new ArrayList<Initiator>();
        for (int i = 0; i < numberInitiators; i++) {
            Initiator initiator = new Initiator();
            initiator.setHost(hostId);
            initiator.setHostName(hostName);
            String byte1 = String.format("%02x", initIndex / 256);
            String byte0 = String.format("%02x", initIndex % 256);
            initiator.setInitiatorPort("10:00:00:00:00:00:" + byte1 + ":" + byte0);
            initiator.setId(URI.create("init" + initIndex++));
            initiators.add(initiator);
        }
        return initiators;
    }

    /**
     * Partials out the initiators against the number of networks specified.
     * 
     * @param initiators List<Initiator>
     * @param split the initiators across lowNet to highNet inclusive
     * @return Map<URI, List<Initiator>> map from network URI to list of Initiators
     */
    static Map<URI, List<Initiator>> makeNet2InitiatorsMap(List<Initiator> initiators, int lowNet, int highNet) {
        HashMap<URI, List<Initiator>> map = new HashMap<URI, List<Initiator>>();
        // Make an entry for each network.
        for (int i = lowNet; i <= highNet; i++) {
            URI net = (URI.create("net" + i));
            map.put(net, new ArrayList<Initiator>());
        }
        // Divide the initiators among the networks.
        int numNetworks = highNet - lowNet + 1;
        for (int i = 0; i < initiators.size(); i++) {
            Initiator initiator = initiators.get(i);
            int index = i % numNetworks;
            URI net = URI.create("net" + (index + lowNet));
            map.get(net).add(initiator);
        }
        return map;
    }

    /**
     * Creates a map of Network URI to a Set<StoragePort> of ports in that Network.
     * 
     * @param existingAssignments -- Map of Initiator to a list of Storage Port assignments
     * @return Map of Network URI to set of Storage Ports in that Network
     */
    private static Map<URI, Set<StoragePort>> generateNetworkToStoragePortsMap(
            Map<Initiator, List<StoragePort>> existingAssignments) {
        Map<URI, Set<StoragePort>> network2StoragePortsMap = new HashMap<URI, Set<StoragePort>>();
        if (existingAssignments == null) {
            return network2StoragePortsMap;
        }
        for (List<StoragePort> ports : existingAssignments.values()) {
            for (StoragePort port : ports) {
                if (port.getRegistrationStatus().toString()
                        .equals(DiscoveredDataObject.RegistrationStatus.REGISTERED.name())
                        && DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                                .equals(port.getCompatibilityStatus())
                        && DiscoveryStatus.VISIBLE.name().equals(port.getDiscoveryStatus())) {
                    if (network2StoragePortsMap.get(port.getNetwork()) == null) {
                        network2StoragePortsMap.put(port.getNetwork(), new HashSet<StoragePort>());
                    }
                    network2StoragePortsMap.get(port.getNetwork()).add(port);
                }
            }
        }
        return network2StoragePortsMap;
    }

    /**
     * Creates a map of Network URI to a Set<Initiator> of initiators in that Network.
     * 
     * @param existingAssignments -- Map of Initiator to a list of Storage Port assignments
     * @return Map of Network URI to a set of Initiators in that Network
     */
    private static Map<URI, Set<Initiator>> generateNetworkToInitiatorsMap(
            Map<Initiator, List<StoragePort>> existingAssignments) {
        Map<URI, Set<Initiator>> network2InitiatorsMap = new HashMap<URI, Set<Initiator>>();
        if (existingAssignments == null) {
            return network2InitiatorsMap;
        }
        for (Initiator initiator : existingAssignments.keySet()) {
            List<StoragePort> ports = existingAssignments.get(initiator);
            if (ports == null || ports.isEmpty()) {
                continue;
            }
            URI network = ports.get(0).getNetwork();
            if (network2InitiatorsMap.get(network) == null) {
                network2InitiatorsMap.put(network, new HashSet<Initiator>());
            }
            network2InitiatorsMap.get(network).add(initiator);
        }
        return network2InitiatorsMap;
    }

    protected static PortAllocationContext createVmaxNet1() {
        NetworkLite tz = new NetworkLite(URI.create("net1"), "net1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-7E:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-7E:1", "50:00:00:00:00:00:00:01");
        addPort(context, port, "mds-a");

        port = createFCPort("FA-8E:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-8E:1", "50:00:00:00:00:00:01:01");
        addPort(context, port, "mds-a");

        port = createFCPort("FA-9E:0", "50:00:00:00:00:00:02:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-9E:1", "50:00:00:00:00:00:02:01");
        addPort(context, port, "mds-a");

        port = createFCPort("FA-10E:0", "50:00:00:00:00:00:03:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-10E:1", "50:00:00:00:00:00:03:01");
        addPort(context, port, "mds-a");

        port = createFCPort("FA-11E:0", "50:00:00:00:00:00:04:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-11E:1", "50:00:00:00:00:00:04:01");
        addPort(context, port, "mds-a");

        port = createFCPort("FA-12E:0", "50:00:00:00:00:00:05:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-12E:1", "50:00:00:00:00:00:05:01");
        addPort(context, port, "mds-a");

        return context;
    }

    // NBS VSAN 10
    protected static PortAllocationContext createVmaxNet3() {
        NetworkLite tz = new NetworkLite(URI.create("net3"), "net3");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-1E:0", "50:00:09:75:10:04:95:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-1F:0", "50:00:09:75:10:04:95:40");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-1G:0", "50:00:09:75:10:04:95:80");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2E:0", "50:00:09:75:10:04:95:04");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2F:0", "50:00:09:75:10:04:95:44");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2G:0", "50:00:09:75:10:04:95:84");
        addPort(context, port, "mds-a");
        return context;
    }

    // NBS VSAN 11
    protected static PortAllocationContext createVmaxNet4() {
        NetworkLite tz = new NetworkLite(URI.create("net4"), "net4");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-1E:1", "50:00:09:75:10:04:95:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-1F:1", "50:00:09:75:10:04:95:41");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-1G:1", "50:00:09:75:10:04:95:81");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2E:1", "50:00:09:75:10:04:95:05");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2F:1", "50:00:09:75:10:04:95:45");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2G:1", "50:00:09:75:10:04:95:85");
        addPort(context, port, "mds-a");
        return context;
    }

    protected static PortAllocationContext createVmaxNet2() {
        NetworkLite tz = new NetworkLite(URI.create("net2"), "net2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;

        port = createFCPort("FA-7F:2", "50:00:00:00:00:01:00:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-7F:3", "50:00:00:00:00:01:00:03");
        addPort(context, port, "mds-b");

        port = createFCPort("FA-8F:2", "50:00:00:00:00:01:01:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-8F:3", "50:00:00:00:00:01:01:03");
        addPort(context, port, "mds-b");

        port = createFCPort("FA-9F:2", "50:00:00:00:00:01:02:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-9F:3", "50:00:00:00:00:01:02:03");
        addPort(context, port, "mds-b");

        port = createFCPort("FA-10F:2", "50:00:00:00:00:01:03:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-10F:3", "50:00:00:00:00:01:03:03");
        addPort(context, port, "mds-b");

        port = createFCPort("FA-11F:2", "50:00:00:00:00:01:04:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-11F:3", "50:00:00:00:00:01:04:03");
        addPort(context, port, "mds-b");

        port = createFCPort("FA-12F:2", "50:00:00:00:00:01:05:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-12F:3", "50:00:00:00:00:01:05:03");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVNXNet1() {
        NetworkLite tz = new NetworkLite(URI.create("net1"), "net1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("SP_A:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_A:1", "50:00:00:00:00:00:00:01");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_A:2", "50:00:00:00:00:00:00:02");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_A:3", "50:00:00:00:00:00:00:03");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_B:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:1", "50:00:00:00:00:00:01:01");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_B:2", "50:00:00:00:00:00:01:02");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:3", "50:00:00:00:00:00:01:03");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVNXNet2() {
        NetworkLite tz = new NetworkLite(URI.create("net2"), "net2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("SP_A:4", "50:00:00:00:00:00:00:04");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_A:5", "50:00:00:00:00:00:00:05");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_A:6", "50:00:00:00:00:00:00:06");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_A:7", "50:00:00:00:00:00:00:07");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_B:4", "50:00:00:00:00:00:01:04");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:5", "50:00:00:00:00:00:01:05");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_B:6", "50:00:00:00:00:00:01:06");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:7", "50:00:00:00:00:00:01:07");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVNXNet3() {
        NetworkLite tz = new NetworkLite(URI.create("net1"), "net1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("SP_A:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        return context;
    }

    protected static PortAllocationContext createVNXNet4() {
        NetworkLite tz = new NetworkLite(URI.create("net2"), "net2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("SP_A:4", "50:00:00:00:00:00:00:04");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:4", "50:00:00:00:00:00:01:04");
        addPort(context, port, "mds-a");
        return context;
    }

    /**
     * Generates a map from Host URI to a set of Initiator URIs.
     * 
     * @initiators -- a Collection of Initiator objects
     * @return
     */
    private static Map<URI, Set<Initiator>> generateHostInitiatorsMap(Collection<Initiator> initiators) {
        Map<URI, Set<Initiator>> hostInitiatorsMap = new HashMap<URI, Set<Initiator>>();
        for (Initiator initiator : initiators) {
            URI host = initiator.getHost();
            if (hostInitiatorsMap.get(host) == null) {
                hostInitiatorsMap.put(host, new HashSet<Initiator>());
            }
            hostInitiatorsMap.get(host).add(initiator);
        }
        return hostInitiatorsMap;
    }
    
    private static Map<URI, List<Initiator>> makeNet2InitiatorsMap(
            Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiators) {
        Map<URI, List<Initiator>> netToInitiatorsMap = new HashMap<URI, List<Initiator>>();
        for (Map<URI, List<Initiator>> hostEntries : hostToNetToInitiators.values()) {
            for (URI net : hostEntries.keySet()) {
                if (!netToInitiatorsMap.containsKey(net)) {
                    netToInitiatorsMap.put(net, new ArrayList<Initiator>());
                }
                netToInitiatorsMap.get(net).addAll(hostEntries.get(net));
            }
        }
        return netToInitiatorsMap;
    }
    
    private static URI getHostURI(Map<URI, List<Initiator>> net2InitiatorsMap) {
        for (List<Initiator> initiators : net2InitiatorsMap.values()) {
            for (Initiator initiator : initiators) {
                if (initiator.getHost() != null) {
                    return initiator.getHost();
                }
            }
        }
        return URI.create("unknown-host");
    }
}
