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
import com.emc.storageos.db.client.util.NullColumnValueGetter;
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

        List<Initiator> initA = getHostInitiators(4);
        Map<URI, List<Initiator>> net2InitiatorsMapA = makeNet2InitiatorsMap(initA, 1,2);
        List<Initiator> initB = getHostInitiators(4);
        Map<URI, List<Initiator>> net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 1,2);
        for (int k=1; k <= 2; k++) {  //initiators per port
        for (int j = 1; j <= 2; j++) {  // paths per initiator
            for (int i = 1; i <= 8; i++) {  // max paths
                System.out.println("*** Two hosts each 4 initiators across 2 networks: " 
                    + "max_paths = " + i + " paths_per_initiator = " + j + " initiators per port " + k);
                testVMAX2NetAllocAssign(net2InitiatorsMapA, net2InitiatorsMapB, null, null, i, 0, j, k);
            }
        }
        }
        
        System.out.println("Testing Hosts on non-overlapping networks!");
        initA = getHostInitiators(4);
        net2InitiatorsMapA = makeNet2InitiatorsMap(initA, 1,2);
        initB = getHostInitiators(4);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 3,4);
        List<Initiator> initC = getHostInitiators(4);
        Map<URI, List<Initiator>> net2InitiatorsMapC = makeNet2InitiatorsMap(initC, 4, 4);
        for (int k=1; k <= 2; k++) {  //initiators per port
        for (int j = 1; j <= 2; j++) {  // paths per initiator
            for (int i = 1; i <= 8; i++) {  // max paths
                System.out.println("*** Three hosts (net1,net2), (net3,net4), (net4), each 4 initiators: " 
                    + "max_paths = " + i + " paths_per_initiator = " + j + " initiators per port " + k);
                testVMAX4NetAllocAssign(net2InitiatorsMapA, net2InitiatorsMapB, net2InitiatorsMapC, null, i, 0, j, k);
            }
        }
        }
        
        initA = getHostInitiators(4);
        net2InitiatorsMapA = makeNet2InitiatorsMap(initA, 1,2);
        initB = getHostInitiators(4);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 3,4);
        initC = getHostInitiators(4);
        net2InitiatorsMapC = makeNet2InitiatorsMap(initC, 4, 4);
        List<Initiator> initD = getHostInitiators(4);
        Map<URI, List<Initiator>> net2InitiatorsMapD = makeNet2InitiatorsMap(initD, 1, 4);
        for (int k=1; k <= 2; k++) {  //initiators per port
        for (int j = 1; j <= 2; j++) {  // paths per initiator
            for (int i = 1; i <= 8; i++) {  // max paths
                System.out.println("*** Four hosts (net1,net2), (net3,net4), (net4), (net1,net2, net3, net4) each 4 initiators: " 
                    + "max_paths = " + i + " paths_per_initiator = " + j + " initiators per port " + k);
                testVMAX4NetAllocAssign(net2InitiatorsMapA, net2InitiatorsMapB, net2InitiatorsMapC, net2InitiatorsMapD, i, 0, j, k);
            }
        }
        }
                
        System.out.println("End Testing Hosts on non-overlapping networks!\n\n");
        
        System.out.println("Testing VNX two hosts");
        initA = getHostInitiators(4);
        net2InitiatorsMapA = makeNet2InitiatorsMap(initA, 1,2);
        initB = getHostInitiators(4);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 1,2);
        for (int j = 1; j <= 2; j++) {
            for (int i = 2; i <= 6; i++) {
                System.out.println("*** Two VNX hosts, each 4 initiators across 2 networks: max_paths = " + i + " paths_per_initiator = " + j);
                testVNX2NetAllocAssign(net2InitiatorsMapA, net2InitiatorsMapB, null, null, i, 1, j, 1);
            }
        }
        System.out.println("End of Testing VNX two hosts");


        // Test incremental initiator / port addition
        initA = getHostInitiators(2);
        Map<URI, List<Initiator>> net2InitiatorsMap = makeNet2InitiatorsMap(initA, 1, 2);
        initB = getHostInitiators(2);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB,1,2);

        for (int j = 1; j <= 2; j++) {
            for (int i = 4; i <= 4; i++) {
               System.out.println("*** 2 initiators across 2 networks, incremental 2 more initiators in new host: max_paths = " + i
                        + " paths_per_initiator = " + j);
                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
            }
        }

        URI hostAid = initA.get(0).getHost();
        String hostAname = initA.get(0).getHostName();
        initC = addHostInitiators(2, hostAid, hostAname);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initC, 1,2);

        for (int j = 1; j <= 2; j++) {
            for (int i = 2; i <= 8; i++) {
                System.out.println("*** 2 initiators across 2 networks, incremental 2 more initiators in same host: max_paths = " + i
                        + " paths_per_initiator = " + j);
                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
            }
        }

        initA = getHostInitiators(4);
        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 1, 2);
        initB = getHostInitiators(4);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 1, 2);
        for (int j = 1; j <= 2; j++) {
            for (int i = 1; i <= 8; i++) {
                System.out.println("*** 4 initiators across 2 networks, incremental 4 more initiators in new host: max_paths = " + i
                        + " paths_per_initiator = " + j);
                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
            }
        }

        initA = getHostInitiators(4);
        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 1, 2);
        hostAid = initA.get(0).getHost();
        hostAname = initA.get(0).getHostName();
        initC = addHostInitiators(4, hostAid, hostAname);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initC, 1, 2);
        for (int j = 1; j <= 2; j++) {
            for (int i = 1; i <= 12; i++) {
                System.out.println("*** 4 initiators across 2 networks, incremental 4 more initiators in same host: max_paths = " + i
                        + " paths_per_initiator = " + j);
                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
            }
        }
        
        initA = getHostInitiators(4);
        net2InitiatorsMap = makeNet2InitiatorsMap(initA, 1, 2);
        initB = getHostInitiators(4);
        net2InitiatorsMapB = makeNet2InitiatorsMap(initB, 1, 2);
        // The following loop assumes symmetric networks across the two hosts
        for (Map.Entry<URI, List<Initiator>> entry : net2InitiatorsMap.entrySet()) {
            net2InitiatorsMap.get(entry.getKey()).addAll(net2InitiatorsMapB.get(entry.getKey()));
        }
        for (int j = 1; j <= 2; j++) {
            for (int i = 1; i <= 8; i++) {
                System.out.println("*** 4 initiators across 2 networks, incremental same hosts with more paths; initial max_paths = " + i
                        + " paths_per_initiator = " + j);
                testVMAX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMap, i, j, i+4, j+1);
            }
        }

        for (int j = 1; j <= 2; j++) {
            for (int i = 2; i <= 12; i++) {
                System.out.println("*** 4 initiators across 2 networks, incremental 4 more initiators in same host: max_paths = " + i
                        + " paths_per_initiator = " + j);
                testVNX2NetAllocIncrementalAssign(net2InitiatorsMap, net2InitiatorsMapB, i, j);
            }
        }

        System.out.println("done!");
    }

    
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

    public static void testVMAX2NetAllocIncrementalAssign(
            Map<URI, List<Initiator>> net2InitiatorsMapA,
            Map<URI, List<Initiator>> net2InitiatorsMapB, int maxPaths, int pathsPerInitiator)
            throws Exception {
         testVMAX2NetAllocIncrementalAssign(net2InitiatorsMapA, net2InitiatorsMapB, 
                maxPaths, pathsPerInitiator, maxPaths, pathsPerInitiator);
    }

    public static void testVMAX2NetAllocIncrementalAssign(
            Map<URI, List<Initiator>> net2InitiatorsMapA,
            Map<URI, List<Initiator>> net2InitiatorsMapB, int initialMaxPaths, int initialPathsPerInitiator,
            int finalMaxPaths, int finalPathsPerInitiator)
            throws Exception {
        Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
        URI hostA = getHostURI(net2InitiatorsMapA);
        hostToNetToInitiatorsMap = getHostInitiatorsMap(net2InitiatorsMapA);
        
        PortAllocationContext net1ctx = createVmaxNet1();
        PortAllocationContext net2ctx = createVmaxNet2();
        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
        // Allocate initial assignments
        System.out.println("***** Initial assignments: *****");
        Map<Initiator, List<StoragePort>> assignments =
                testAllocationAssignment(contexts, hostToNetToInitiatorsMap, initialMaxPaths, 0, initialPathsPerInitiator, 1, 
                        "vmax", null);

        URI hostB = getHostURI(net2InitiatorsMapB);
        if (hostA.equals(hostB)) {
            Map<URI, List<Initiator>> mergedNet2Initiators = new HashMap<URI, List<Initiator>>();
            for (URI netA : net2InitiatorsMapA.keySet()) {
                mergedNet2Initiators.put(netA, new ArrayList<Initiator>(net2InitiatorsMapA.get(netA)));
            }
            for (URI netb : net2InitiatorsMapB.keySet()) {
                if (mergedNet2Initiators.containsKey(netb)) {
                    mergedNet2Initiators.get(netb).addAll(net2InitiatorsMapB.get(netb));
                } else {
                    mergedNet2Initiators.put(netb, new ArrayList<Initiator>(net2InitiatorsMapB.get(netb)));
                }
            }
            hostToNetToInitiatorsMap = getHostInitiatorsMap(mergedNet2Initiators);
        } else {
            hostToNetToInitiatorsMap.put(hostB,  net2InitiatorsMapB);
        }
        System.out.println("***** Incremental assignments: *****" + " maxPaths " + finalMaxPaths + " ppi " + finalPathsPerInitiator);
        assignments = testAllocationAssignment(contexts, hostToNetToInitiatorsMap, finalMaxPaths, 0, finalPathsPerInitiator, 1,
                "vmax", assignments);
    }

    public static void testVNX2NetAllocIncrementalAssign(
            Map<URI, List<Initiator>> net2InitiatorsMapA,
            Map<URI, List<Initiator>> net2InitiatorsMapB, int maxPaths, int pathsPerInitiator)
            throws Exception {
        Map<URI, Map<URI, List<Initiator>>> hostToNetToInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
        URI hostA = getHostURI(net2InitiatorsMapA);
        hostToNetToInitiatorsMap.put(hostA, net2InitiatorsMapA);
        PortAllocationContext net1ctx = createVNXNet1();
        PortAllocationContext net2ctx = createVNXNet2();
        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
        // Allocate initial assignments
        System.out.println("Initial assignments:");
        Map<Initiator, List<StoragePort>> assignments =
                testAllocationAssignment(contexts, hostToNetToInitiatorsMap,
                        maxPaths, 0, pathsPerInitiator, 1, "vnxblock", null);
        URI hostB = getHostURI(net2InitiatorsMapB);
        if (hostA.equals(hostB)) {
            Map<URI, List<Initiator>> mergedNet2Initiators = new HashMap<URI, List<Initiator>>();
            for (URI netA : net2InitiatorsMapA.keySet()) {
                mergedNet2Initiators.put(netA, new ArrayList<Initiator>(net2InitiatorsMapA.get(netA)));
            }
            for (URI netb : net2InitiatorsMapB.keySet()) {
                if (mergedNet2Initiators.containsKey(netb)) {
                    mergedNet2Initiators.get(netb).addAll(net2InitiatorsMapB.get(netb));
                } else {
                    mergedNet2Initiators.put(netb, new ArrayList<Initiator>(net2InitiatorsMapB.get(netb)));
                }
            }
            hostToNetToInitiatorsMap.clear();
            hostToNetToInitiatorsMap.put(hostA,  mergedNet2Initiators);
        } else {
            hostToNetToInitiatorsMap.put(hostB,  net2InitiatorsMapB);
        }
        // Allocate incremental assignments
        
        System.out.println("Incremental assignments:");
        assignments = testAllocationAssignment(contexts, hostToNetToInitiatorsMap,
                maxPaths, 0, pathsPerInitiator, 1, "vnxblock", assignments);
    }

    /**
     * Test allocation in a VNX two network environment.
     * 
     * @param net2InitiatorsMapA -- initiators for host A
     * @param net2InitiatorsMapB -- initiators for host B (optional)
     * @param net2InitiatorsMapC -- initiators for host C (optional)
     * @param net2InitiatorsMapD -- initiators for host D (optional)
     * @param maxPaths
     * @param minPaths
     * @param pathsPerInitiator
     * @param initiatorsPerPort
     * 
     */
    public static void testVNX2NetAllocAssign(
            Map<URI, List<Initiator>> net2InitiatorsMapA, 
            Map<URI, List<Initiator>> net2InitiatorsMapB,
            Map<URI, List<Initiator>> net2InitiatorsMapC,
            Map<URI, List<Initiator>> net2InitiatorsMapD,
                    int maxPaths, int minPaths, int pathsPerInitiator, int initiatorsPerPort) throws Exception {
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
        PortAllocationContext net1ctx = createVNXNet1();
        PortAllocationContext net2ctx = createVNXNet2();
        PortAllocationContext[] contexts = new PortAllocationContext[] { net1ctx, net2ctx };
        testAllocationAssignment(contexts, hostToNetToInitiatorsMap, 
                maxPaths, minPaths, pathsPerInitiator, initiatorsPerPort, "vnxblock", null);
    }

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
            List<URI> networkOrder = new ArrayList<URI>();
            Map<URI, Integer> net2PortsNeeded = assigner.getPortsNeededPerNetwork(
                    net2InitiatorsMap, pathParams, existingPortsMap, existingInitiatorsMap, networkOrder);

            // For each Network, allocate the ports required, and then assign the ports.
            StoragePortsAllocator allocator = new StoragePortsAllocator();

            Map<URI, List<StoragePort>> netToPortsAllocated = new HashMap<URI, List<StoragePort>>();
            PortAllocationContext previousContext = null;
            for (URI netURI : networkOrder) {
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
                        allocator.allocatePortsForNetwork(portsNeeded, context, false, existingPortsMap.get(netURI), true);
                netToPortsAllocated.put(netURI,portsAllocated);
                
            }
            
            // Now for each host, do the port assignment.
            for (Map.Entry<URI, Map<URI, List<Initiator>>> entry: hostToNetToInitiators.entrySet()) {
                System.out.println("Assign ports for host " + entry.getKey());
                assigner.assignPortsToHost(assignments, entry.getValue(), netToPortsAllocated, 
                        pathParams, existingAssignments, entry.getKey(), null);   

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
     * Partials out the initiators against networks specified.
     * 
     * @param initiators List<Initiator>
     * @param lowNet integer (lowest network to be used like net1)
     * @param highNet integer (highest network to be used like net2)
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
    
    /**
     * Given a list of networks-to-initiators, further break down the map by host so
     * that the end result is a map of hosts-to-networks-to-initiators.
     * 
     * @param net2InitiatorsMap networks-to-initiators map
     * @return a map of hosts-to-network-to-initiators
     */
    private static Map<URI, Map<URI, List<Initiator>>> getHostInitiatorsMap(Map<URI, List<Initiator>> net2InitiatorsMap) {
        Map<URI, Map<URI, List<Initiator>>> hostInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
        for (Map.Entry<URI, List<Initiator>> entry : net2InitiatorsMap.entrySet()) {
            List<Initiator> initiators = entry.getValue();
            for (Initiator initiator : initiators) {
                URI host = initiator.getHost();
                if (NullColumnValueGetter.isNullURI(host)) {
                    host = StoragePortsAssigner.unknown_host_uri;
                }
                Map<URI, List<Initiator>> hostMap = hostInitiatorsMap.get(host);
                if (hostMap == null) {
                    hostMap = new HashMap<URI, List<Initiator>>();
                    hostInitiatorsMap.put(host, hostMap);
                }
                if (hostMap.get(entry.getKey()) == null) {
                    hostMap.put(entry.getKey(), new ArrayList<Initiator>());
                }
                hostMap.get(entry.getKey()).add(initiator);
            }
        }
        return hostInitiatorsMap;
    }
}
