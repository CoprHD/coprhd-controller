/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.util.DummyDbClient;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;

/**
 * Test program for StoragePortsAllocator
 * 
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
public class StoragePortsAllocatorTest {
    private static boolean vmaxonly = false;
    private static boolean vnxonly = false;
    private static boolean vplexonly = false;
    private static boolean duplicateCpuExpected = false;
    private static boolean pass = true;

    private static final Log _log = LogFactory
            .getLog(StoragePortsAllocatorTest.class);

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.equals("vmaxonly")) {
                vmaxonly = true;
            }
            if (arg.equals("vnxonly")) {
                vnxonly = true;
            }
            if (arg.equals("vplexonly")) {
                vplexonly = true;
            }
        }
        VdcUtil.setDbClient(new DummyDbClient());
        PropertyConfigurator.configure("log4j.properties");
        _log.info("Beginning logging");
        testFC();
        testIP();

        System.out.println("Empty transport zone, should throw PlacementException");
        PortAllocationContext ctx = createEmptyTzone();
        StoragePortsAllocator allocator = new StoragePortsAllocator();
        try {
            alloc(allocator, 1, ctx, true);
        } catch (PlacementException ex) {
            System.out.println("caught PlacementException: " + ex.toString());
        }
        System.out.println("done");
        _log.info(pass ? "PASSED" : "FAILED");
    }

    private static void testIP() throws Exception {
        PortAllocationContext ctx = createVNX2director4portIP();
        StoragePortsAllocator allocator = new StoragePortsAllocator();
        if (!vmaxonly && !vplexonly) {
            alloc(allocator, 1, ctx, false);
            alloc(allocator, 2, ctx, false);
            alloc(allocator, 4, ctx, false);
            alloc(allocator, 8, ctx, false);
            alloc(allocator, 16, ctx, false);
            alloc(allocator, 32, ctx, false);
        }

        ctx = createVMAX3engine4portIP();
        allocator = new StoragePortsAllocator();
        if (!vnxonly && !vplexonly) {
            alloc(allocator, 1, ctx, false);
            alloc(allocator, 2, ctx, false);
            alloc(allocator, 4, ctx, false);
            alloc(allocator, 8, ctx, false);
            duplicateCpuExpected = true;
            alloc(allocator, 16, ctx, false);
            duplicateCpuExpected = true;
            alloc(allocator, 32, ctx, false);
            runIterations(50, allocator, 4, ctx, false);
            System.out
                    .println("Symmetrical two transport zones each with four directors two ports each");
            test2tzones(1, createTzone1IP(), createTzone2IP(), false);
            test2tzones(2, createTzone1IP(), createTzone2IP(), false);
            test2tzones(4, createTzone1IP(), createTzone2IP(), false);
            System.out
                    .println("Asymmetrical transport zones, one with two directors, one with one");
            test2tzones(1, createTzone3IP(), createTzone4IP(), false);
            test2tzones(2, createTzone3IP(), createTzone4IP(), false);
            test2tzones(4, createTzone3IP(), createTzone4IP(), false);
        }
    }

    protected static void testFC() throws Exception {
        PortAllocationContext ctx = createVNX2director4portFC();
        StoragePortsAllocator allocator = new StoragePortsAllocator();
        if (!vmaxonly && !vplexonly) {
            alloc(allocator, 1, ctx, true);
            alloc(allocator, 2, ctx, true);
            alloc(allocator, 4, ctx, true);
            alloc(allocator, 8, ctx, true);
            alloc(allocator, 16, ctx, true);
            alloc(allocator, 32, ctx, true);
        }

        if (!vnxonly && !vplexonly) {
            ctx = createVMAX3engine4portFC();
            allocator = new StoragePortsAllocator();
            alloc(allocator, 1, ctx, true);
            alloc(allocator, 2, ctx, true);
            alloc(allocator, 4, ctx, true);
            alloc(allocator, 8, ctx, true);
            duplicateCpuExpected = true;
            alloc(allocator, 16, ctx, true);
            duplicateCpuExpected = true;
            alloc(allocator, 32, ctx, true);

            ctx = createVMAXWithCpuDuplication();
            allocator = new StoragePortsAllocator();
            alloc(allocator, 1, ctx, true);
            alloc(allocator, 2, ctx, true);
            alloc(allocator, 3, ctx, true);
            _log.debug("Cpu duplication forced");
            duplicateCpuExpected = true;
            alloc(allocator, 4, ctx, true);
        }

        if (!vnxonly && !vmaxonly) {
            ctx = createVplex1engine4portFC();
            allocator = new StoragePortsAllocator();
            _log.info("Start Vplex 1 engine port allocation");
            alloc(allocator, 1, ctx, true);
            alloc(allocator, 2, ctx, true);
            alloc(allocator, 4, ctx, true);
            alloc(allocator, 8, ctx, true);
            alloc(allocator, 16, ctx, true);
            alloc(allocator, 32, ctx, true);
            _log.info("Done with Vplex 1 engine port allocation");

            _log.info("Start Vplex 2 engine port allocation");
            ctx = createVplex2engine4portFC();
            allocator = new StoragePortsAllocator();
            alloc(allocator, 1, ctx, true);
            alloc(allocator, 2, ctx, true);
            alloc(allocator, 4, ctx, true);
            alloc(allocator, 8, ctx, true);
            alloc(allocator, 16, ctx, true);
            alloc(allocator, 32, ctx, true);
            _log.info("Done with Vplex 2 engine port allocation");

            _log.info("Start Vplex 4 engine port allocation");
            ctx = createVplex4engine4portFC();
            allocator = new StoragePortsAllocator();
            alloc(allocator, 1, ctx, true);
            alloc(allocator, 2, ctx, true);
            alloc(allocator, 4, ctx, true);
            alloc(allocator, 8, ctx, true);
            alloc(allocator, 16, ctx, true);
            alloc(allocator, 32, ctx, true);
            _log.info("Done with Vplex 4 engine port allocation");

            runIterations(50, allocator, 4, ctx, true);
            System.out
                    .println("Symmetrical two transport zones each with four directors two ports each");
            test2tzones(1, createTzone1FC(), createTzone2FC(), true);
            test2tzones(2, createTzone1FC(), createTzone2FC(), true);
            test2tzones(4, createTzone1FC(), createTzone2FC(), true);
            System.out
                    .println("Asymmetrical transport zones, one with two directors, one with one");
            test2tzones(1, createTzone3FC(), createTzone4FC(), true);
            test2tzones(2, createTzone3FC(), createTzone4FC(), true);
            test2tzones(4, createTzone3FC(), createTzone4FC(), true);
        }
    }

    private static void runIterations(int niter,
            StoragePortsAllocator allocator, int tznpaths,
            PortAllocationContext ctx, boolean checkConnectivity) throws Exception {
        HashMap<String, Integer> portUsageCounts = new HashMap<String, Integer>();
        for (int i = 0; i < niter; i++) {
            List<URI> portUris = alloc(allocator, tznpaths, ctx, checkConnectivity);
            for (URI uri : portUris) {
                StoragePort port = ctx._idToStoragePort.get(uri);
                String name = port.getPortName();
                if (portUsageCounts.get(name) == null) {
                    portUsageCounts.put(name, new Integer(0));
                }
                Integer newValue = portUsageCounts.get(name) + 1;
                portUsageCounts.put(name, newValue);
            }
        }
        System.out.println("Port usage statistics:");
        for (String key : portUsageCounts.keySet()) {
            System.out.println(String.format("    Port %s used %d times", key,
                    portUsageCounts.get(key)));
        }
    }

    private static List<URI> alloc(StoragePortsAllocator allocator,
            int tznpaths, PortAllocationContext ctx, boolean checkConnectivity) throws Exception {
        System.out.println("TZ Numpaths = " + new Integer(tznpaths));
        ctx.reinitialize();
        try {
            List<URI> portUris = getPortURIs(allocator.allocatePortsForNetwork(tznpaths,
                    ctx, checkConnectivity, null, false));
            checkForDuplicates(portUris, ctx);
            checkForAlternation(portUris, ctx);
            printPorts(portUris, ctx);
            return portUris;
        } catch (PlacementException ex) {
            System.out.println("PlacementException: " + ex.getMessage());
            return new ArrayList<URI>();
        }

    }

    protected static void test2tzones(int numPaths,
            PortAllocationContext tzone1, PortAllocationContext tzone2, boolean checkConnectivity)
            throws Exception {
        StoragePortsAllocator allocator = new StoragePortsAllocator();
        List<URI> tzone1Uris = getPortURIs(
                allocator.allocatePortsForNetwork(numPaths / 2, tzone1, checkConnectivity, null, false));
        System.out.println(String.format("Transport zone: %s paths: %d", tzone1._initiatorNetwork.getLabel(), new Integer(numPaths)));
        printPorts(tzone1Uris, tzone1);
        checkForDuplicates(tzone1Uris, tzone1);
        tzone2._alreadyAllocatedDirectors
                .addAll(tzone1._alreadyAllocatedDirectors);
        tzone2._alreadyAllocatedSwitches
                .addAll(tzone1._alreadyAllocatedSwitches);
        List<URI> tzone2Uris = getPortURIs(
                allocator.allocatePortsForNetwork(numPaths / 2, tzone2, checkConnectivity, null, false));
        System.out.println(String.format("Transport zone: %s paths: %d", tzone2._initiatorNetwork.getLabel(), new Integer(numPaths)));
        printPorts(tzone2Uris, tzone2);
        checkForDuplicates(tzone2Uris, tzone2);
    }

    protected static void checkForDuplicates(List<URI> portUris,
            PortAllocationContext ctx) throws Exception {
        HashSet<String> addressSet = new HashSet<String>();
        for (URI uri : portUris) {
            StoragePort port = ctx._idToStoragePort.get(uri);
            String address = port.getPortNetworkId();
            if (addressSet.contains(address)) {
                pass = false;
                throw new Exception("Duplicate use of port address: " + address);
            }
            addressSet.add(address);
        }
    }

    protected static void checkForAlternation(List<URI> portUris,
            PortAllocationContext ctx) {
        Set<String> engines = new HashSet(ctx._engineToStoragePortSet.keySet());
        Set<String> directors = new HashSet(
                ctx._directorToStoragePortSet.keySet());
        Set<String> cpus = new HashSet(ctx._cpuToStoragePortSet.keySet());
        for (URI uri : portUris) {
            StoragePort port = ctx._idToStoragePort.get(uri);
            String engine = ctx._storagePortToEngine.get(port);
            if (engine != null) {
                if (!engines.contains(engine)) {
                    System.out.println("Duplicate engine: " + port.getPortName());
                    pass = false;
                }
                engines.remove(engine);
            }
            String director = ctx._storagePortToDirector.get(port);
            if (!directors.contains(director)) {
                System.out.println("Duplicate director: " + port.getPortName());
                pass = false;
            }
            directors.remove(director);
            String cpu = ctx._storagePortToCpu.get(port);
            if (cpu != null && !cpus.contains(cpu)) {
                if (duplicateCpuExpected) {
                    System.out.println("Duplicate cpu (expected): " + cpu);
                } else {
                    System.out.println("Duplicate cpu: " + cpu);
                    pass = false;
                }
            }
            cpus.remove(cpu);
            if (engines.isEmpty()) {
                engines = new HashSet(ctx._engineToStoragePortSet.keySet());
            }
            if (directors.isEmpty()) {
                directors = new HashSet(ctx._directorToStoragePortSet.keySet());
            }
        }
        duplicateCpuExpected = false;
    }

    protected static void printPorts(List<URI> portUris,
            PortAllocationContext ctx) {
        for (URI uri : portUris) {
            StoragePort port = ctx._idToStoragePort.get(uri);
            if (port == null) {
                System.out.println("No port found: " + uri);
                continue;
            }
            System.out.println(String.format("Port %s address %s switch %s",
                    port.getPortName(), port.getPortNetworkId(),
                    ctx._storagePortToSwitchName.get(port)));
        }
    }

    protected static PortAllocationContext createVplex1engine4portFC() {
        NetworkLite tz = new NetworkLite("TzoneVplexFC1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        // Cluster 1 Engine 1 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:01:01:00", "director-1-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:01:01:01", "director-1-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:01:01:03", "director-1-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:01:01:04", "director-1-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:01:01:00", "director-1-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:01:01:01", "director-1-1-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:01:01:03", "director-1-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:01:01:04", "director-1-1-B");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVplex2engine4portFC() {
        NetworkLite tz = new NetworkLite("TzoneVplexFC2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        // Cluster 1 Engine 1 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:01:01:00", "director-1-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:01:01:01", "director-1-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:01:01:03", "director-1-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:01:01:04", "director-1-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:01:01:00", "director-1-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:01:01:01", "director-1-1-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:01:01:03", "director-1-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:01:01:04", "director-1-1-B");
        addPort(context, port, "mds-b");
        // Cluster 2 Engine 1 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:02:01:00", "director-2-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:02:01:01", "director-2-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:02:01:03", "director-2-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:02:01:04", "director-2-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:02:01:00", "director-2-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:02:01:01", "director-2-1-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:02:01:03", "director-2-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:02:01:04", "director-2-1-B");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVplex4engine4portFC() {
        NetworkLite tz = new NetworkLite("TzoneVplexFC3");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        // Cluster 1 Engine 1 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:01:01:00", "director-1-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:01:01:01", "director-1-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:01:01:03", "director-1-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:01:01:04", "director-1-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:01:01:00", "director-1-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:01:01:01", "director-1-1-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:01:01:03", "director-1-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:01:01:04", "director-1-1-B");
        addPort(context, port, "mds-b");
        // Cluster 1 Engine 2 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:01:02:00", "director-1-2-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:01:02:01", "director-1-2-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:01:02:03", "director-1-2-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:01:02:04", "director-1-2-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:01:02:00", "director-1-2-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:01:02:01", "director-1-2-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:01:02:03", "director-1-2-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:01:02:04", "director-1-2-B");
        addPort(context, port, "mds-b");
        // Cluster 2 Engine 1 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:02:01:00", "director-2-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:02:01:01", "director-2-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:02:01:03", "director-2-1-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:02:01:04", "director-2-1-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:02:01:00", "director-2-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:02:01:01", "director-2-1-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:02:01:03", "director-2-1-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:02:01:04", "director-2-1-B");
        addPort(context, port, "mds-b");
        // Cluster 2 Engine 2 - Ports
        port = createVplexFCPort("A0-FC00", "50:00:14:42:60:02:02:00", "director-2-2-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC01", "50:00:14:42:60:02:02:01", "director-2-2-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("A0-FC03", "50:00:14:42:60:02:02:03", "director-2-2-A");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("A0-FC04", "50:00:14:42:60:02:02:04", "director-2-2-A");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC00", "50:00:14:42:70:02:02:00", "director-2-2-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC01", "50:00:14:42:70:02:02:01", "director-2-2-B");
        addPort(context, port, "mds-b");
        port = createVplexFCPort("B0-FC03", "50:00:14:42:70:02:02:03", "director-2-2-B");
        addPort(context, port, "mds-a");
        port = createVplexFCPort("B0-FC04", "50:00:14:42:70:02:02:04", "director-2-2-B");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVNX2director4portIP() {
        NetworkLite tz = new NetworkLite("TzoneIP1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createIPPort("SP_A:0", "iqn.1992-04.com.emc:cx.apm00121500018.a0");
        addPort(context, port, null);
        port = createIPPort("SP_A:1", "iqn.1992-04.com.emc:cx.apm00121500018.a1");
        addPort(context, port, null);
        port = createIPPort("SP_A:2", "iqn.1992-04.com.emc:cx.apm00121500018.a2");
        addPort(context, port, null);
        port = createIPPort("SP_A:3", "iqn.1992-04.com.emc:cx.apm00121500018.a3");
        addPort(context, port, null);
        port = createIPPort("SP_B:0", "iqn.1992-04.com.emc:cx.apm00121500018.b0");
        addPort(context, port, null);
        port = createIPPort("SP_B:1", "iqn.1992-04.com.emc:cx.apm00121500018.b1");
        addPort(context, port, null);
        port = createIPPort("SP_B:2", "iqn.1992-04.com.emc:cx.apm00121500018.b2");
        addPort(context, port, null);
        port = createIPPort("SP_B:3", "iqn.1992-04.com.emc:cx.apm00121500018.b3");
        addPort(context, port, null);
        port = createIPPort("SP_C:0", "iqn.1992-04.com.emc:cx.apm00121500018.c0");
        addPort(context, port, null);
        port = createIPPort("SP_C:1", "iqn.1992-04.com.emc:cx.apm00121500018.c1");
        addPort(context, port, null);
        port = createIPPort("SP_C:2", "iqn.1992-04.com.emc:cx.apm00121500018.c2");
        addPort(context, port, null);
        port = createIPPort("SP_C:3", "iqn.1992-04.com.emc:cx.apm00121500018.c3");
        addPort(context, port, null);
        port = createIPPort("SP_D:0", "iqn.1992-04.com.emc:cx.apm00121500018.d0");
        addPort(context, port, null);
        port = createIPPort("SP_D:1", "iqn.1992-04.com.emc:cx.apm00121500018.d1");
        addPort(context, port, null);
        port = createIPPort("SP_D:2", "iqn.1992-04.com.emc:cx.apm00121500018.d2");
        addPort(context, port, null);
        port = createIPPort("SP_D:3", "iqn.1992-04.com.emc:cx.apm00121500018.d3");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createVNX2director4portFC() {
        NetworkLite tz = new NetworkLite("Tzon3d4p1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("SP_A:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, null);
        port = createFCPort("SP_A:1", "50:00:00:00:00:00:00:01");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_A:2", "50:00:00:00:00:01:00:02");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_A:3", "50:00:00:00:00:01:00:03");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_B:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:1", "50:00:00:00:00:00:01:01");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_B:2", "50:00:00:00:00:01:01:02");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_B:3", "50:00:00:00:00:01:01:03");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_C:0", "50:00:00:00:00:00:02:00");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_C:1", "50:00:00:00:00:00:02:01");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_C:2", "50:00:00:00:00:01:02:02");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_C:3", "50:00:00:00:00:01:02:03");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_D:0", "50:00:00:00:00:00:03:00");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_D:1", "50:00:00:00:00:00:03:01");
        addPort(context, port, "mds-a");
        port = createFCPort("SP_D:2", "50:00:00:00:00:01:03:02");
        addPort(context, port, "mds-b");
        port = createFCPort("SP_D:3", "50:00:00:00:00:01:03:03");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createVMAX3engine4portIP() {
        NetworkLite tz = new NetworkLite("TzoneIP2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createIPPort("FA-7E:0", "iqn.1992-04.com.emc:cx.apm00121500018.a0");
        addPort(context, port, null);
        port = createIPPort("FA-7E:1", "iqn.1992-04.com.emc:cx.apm00121500018.a1");
        addPort(context, port, null);
        port = createIPPort("FA-7F:2", "iqn.1992-04.com.emc:cx.apm00121500018.a2");
        addPort(context, port, null);
        port = createIPPort("FA-7F:3", "iqn.1992-04.com.emc:cx.apm00121500018.a3");
        addPort(context, port, null);
        port = createIPPort("FA-8E:0", "iqn.1992-04.com.emc:cx.apm00121500018.b0");
        addPort(context, port, null);
        port = createIPPort("FA-8E:1", "iqn.1992-04.com.emc:cx.apm00121500018.b1");
        addPort(context, port, null);
        port = createIPPort("FA-8F:2", "iqn.1992-04.com.emc:cx.apm00121500018.b2");
        addPort(context, port, null);
        port = createIPPort("FA-8F:3", "iqn.1992-04.com.emc:cx.apm00121500018.b3");
        addPort(context, port, null);
        port = createIPPort("FA-9E:0", "iqn.1992-04.com.emc:cx.apm00121500018.c0");
        addPort(context, port, null);
        port = createIPPort("FA-9E:1", "iqn.1992-04.com.emc:cx.apm00121500018.c1");
        addPort(context, port, null);
        port = createIPPort("FA-9F:2", "iqn.1992-04.com.emc:cx.apm00121500018.c2");
        addPort(context, port, null);
        port = createIPPort("FA-9F:3", "iqn.1992-04.com.emc:cx.apm00121500018.c3");
        addPort(context, port, null);
        port = createIPPort("FA-10E:0", "iqn.1992-04.com.emc:cx.apm00121500018.d0");
        addPort(context, port, null);
        port = createIPPort("FA-10E:1", "iqn.1992-04.com.emc:cx.apm00121500018.d1");
        addPort(context, port, null);
        port = createIPPort("FA-10F:2", "iqn.1992-04.com.emc:cx.apm00121500018.d2");
        addPort(context, port, null);
        port = createIPPort("FA-10F:3", "iqn.1992-04.com.emc:cx.apm00121500018.d3");
        addPort(context, port, null);
        port = createIPPort("FA-11E:0", "iqn.1992-04.com.emc:cx.apm00121500018.e0");
        addPort(context, port, null);
        port = createIPPort("FA-11E:1", "iqn.1992-04.com.emc:cx.apm00121500018.e1");
        addPort(context, port, null);
        port = createIPPort("FA-11F:2", "iqn.1992-04.com.emc:cx.apm00121500018.e2");
        addPort(context, port, null);
        port = createIPPort("FA-11F:3", "iqn.1992-04.com.emc:cx.apm00121500018.e3");
        addPort(context, port, null);
        port = createIPPort("FA-12E:0", "iqn.1992-04.com.emc:cx.apm00121500018.f0");
        addPort(context, port, null);
        port = createIPPort("FA-12E:1", "iqn.1992-04.com.emc:cx.apm00121500018.f1");
        addPort(context, port, null);
        port = createIPPort("FA-12F:2", "iqn.1992-04.com.emc:cx.apm00121500018.f2");
        addPort(context, port, null);
        port = createIPPort("FA-12F:3", "iqn.1992-04.com.emc:cx.apm00121500018.f3");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createVMAX3engine4portFC() {
        NetworkLite tz = new NetworkLite("Tzon3e4p1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-7E:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, null);
        port = createFCPort("FA-7E:1", "50:00:00:00:00:00:00:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-7F:2", "50:00:00:00:00:01:00:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-7F:3", "50:00:00:00:00:01:00:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-8E:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-8E:1", "50:00:00:00:00:00:01:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-8F:2", "50:00:00:00:00:01:01:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-8F:3", "50:00:00:00:00:01:01:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-9E:0", "50:00:00:00:00:00:02:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-9E:1", "50:00:00:00:00:00:02:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-9F:2", "50:00:00:00:00:01:02:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-9F:3", "50:00:00:00:00:01:02:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-10E:0", "50:00:00:00:00:00:03:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-10E:1", "50:00:00:00:00:00:03:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-10F:2", "50:00:00:00:00:01:03:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-10F:3", "50:00:00:00:00:01:03:03");
        addPort(context, port, null);
        port = createFCPort("FA-11E:0", "50:00:00:00:00:00:04:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-11E:1", "50:00:00:00:00:00:04:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-11F:2", "50:00:00:00:00:01:04:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-11F:3", "50:00:00:00:00:01:04:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-12E:0", "50:00:00:00:00:00:05:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-12E:1", "50:00:00:00:00:00:05:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-12F:2", "50:00:00:00:00:01:05:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-12F:3", "50:00:00:00:00:01:05:03");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createVMAXWithCpuDuplication() {
        NetworkLite tz = new NetworkLite("Tzon3e4p1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-7E:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-7F:2", "50:00:00:00:00:01:00:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-8E:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-8E:1", "50:00:00:00:00:00:01:01");
        addPort(context, port, "mds-a");
        return context;
    }

    protected static PortAllocationContext createTzone1IP() {
        NetworkLite tz = new NetworkLite("TzoneIP1");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createIPPort("SP_A:0", "iqn.1992-04.com.emc:cx.apm00121500018.a0");
        addPort(context, port, null);
        port = createIPPort("SP_A:1", "iqn.1992-04.com.emc:cx.apm00121500018.a1");
        addPort(context, port, null);
        port = createIPPort("SP_B:0", "iqn.1992-04.com.emc:cx.apm00121500018.b0");
        addPort(context, port, null);
        port = createIPPort("SP_B:1", "iqn.1992-04.com.emc:cx.apm00121500018.b1");
        addPort(context, port, null);
        port = createIPPort("SP_C:0", "iqn.1992-04.com.emc:cx.apm00121500018.c0");
        addPort(context, port, null);
        port = createIPPort("SP_C:1", "iqn.1992-04.com.emc:cx.apm00121500018.c1");
        addPort(context, port, null);
        port = createIPPort("SP_D:0", "iqn.1992-04.com.emc:cx.apm00121500018.d0");
        addPort(context, port, null);
        port = createIPPort("SP_D:1", "iqn.1992-04.com.emc:cx.apm00121500018.d1");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createTzone2IP() {
        NetworkLite tz = new NetworkLite("TzoneIP2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createIPPort("SP_A:2", "iqn.1992-04.com.emc:cx.apm00121500018.a2");
        addPort(context, port, null);
        port = createIPPort("SP_A:3", "iqn.1992-04.com.emc:cx.apm00121500018.a3");
        addPort(context, port, null);
        port = createIPPort("SP_B:2", "iqn.1992-04.com.emc:cx.apm00121500018.b2");
        addPort(context, port, null);
        port = createIPPort("SP_B:3", "iqn.1992-04.com.emc:cx.apm00121500018.b3");
        addPort(context, port, null);
        port = createIPPort("SP_C:2", "iqn.1992-04.com.emc:cx.apm00121500018.c2");
        addPort(context, port, null);
        port = createIPPort("SP_C:3", "iqn.1992-04.com.emc:cx.apm00121500018.c3");
        addPort(context, port, null);
        port = createIPPort("SP_D:2", "iqn.1992-04.com.emc:cx.apm00121500018.d2");
        addPort(context, port, null);
        port = createIPPort("SP_D:3", "iqn.1992-04.com.emc:cx.apm00121500018.d3");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createTzone3IP() {
        NetworkLite tz = new NetworkLite("TzoneIP3");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createIPPort("SP_A:0", "iqn.1992-04.com.emc:cx.apm00121500018.a0");
        addPort(context, port, null);
        port = createIPPort("SP_A:1", "iqn.1992-04.com.emc:cx.apm00121500018.a1");
        addPort(context, port, null);
        port = createIPPort("SP_B:0", "iqn.1992-04.com.emc:cx.apm00121500018.b0");
        addPort(context, port, null);
        port = createIPPort("SP_B:1", "iqn.1992-04.com.emc:cx.apm00121500018.b1");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createTzone4IP() {
        NetworkLite tz = new NetworkLite("TzoneIP4");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createIPPort("SP_B:2", "iqn.1992-04.com.emc:cx.apm00121500018.b2");
        addPort(context, port, null);
        port = createIPPort("SP_B:3", "iqn.1992-04.com.emc:cx.apm00121500018.b3");
        addPort(context, port, null);
        return context;
    }

    protected static PortAllocationContext createTzone1FC() {
        NetworkLite tz = new NetworkLite("Tzone1");
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
        return context;
    }

    protected static PortAllocationContext createTzone2FC() {
        NetworkLite tz = new NetworkLite("Tzone2");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-7E:2", "50:00:00:00:00:01:00:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-7E:3", "50:00:00:00:00:01:00:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-8E:2", "50:00:00:00:00:01:01:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-8E:3", "50:00:00:00:00:01:01:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-9E:2", "50:00:00:00:00:01:02:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-9E:3", "50:00:00:00:00:01:02:03");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-10E:2", "50:00:00:00:00:01:03:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-10E:3", "50:00:00:00:00:01:03:03");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createTzone3FC() {
        NetworkLite tz = new NetworkLite("Tzone3");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-1E:0", "50:00:00:00:00:00:00:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-1E:1", "50:00:00:00:00:00:00:01");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2E:0", "50:00:00:00:00:00:01:00");
        addPort(context, port, "mds-a");
        port = createFCPort("FA-2E:1", "50:00:00:00:00:00:01:01");
        addPort(context, port, "mds-a");
        return context;
    }

    protected static PortAllocationContext createTzone4FC() {
        NetworkLite tz = new NetworkLite("Tzone4");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        StoragePort port;
        port = createFCPort("FA-3E:2", "50:00:00:00:00:01:01:02");
        addPort(context, port, "mds-b");
        port = createFCPort("FA-3E:3", "50:00:00:00:00:01:01:03");
        addPort(context, port, "mds-b");
        return context;
    }

    protected static PortAllocationContext createEmptyTzone() {
        NetworkLite tz = new NetworkLite("EmptyNetwork");
        PortAllocationContext context = new PortAllocationContext(tz, "test");
        return context;
    }

    protected static StoragePort createIPPort(String name, String iqn) {
        StoragePort port = new StoragePort();
        port.setPortName(name);
        port.setPortGroup(name.replaceAll(":.*", ""));
        port.setPortNetworkId(iqn);
        port.setInactive(false);
        port.setRegistrationStatus("REGISTERED");
        port.setTransportType("IP");
        URI uri = URIUtil.createId(StoragePort.class);
        port.setId(uri);
        port.setNativeGuid(uri.toString());
        return port;
    }

    protected static StoragePort createFCPort(String name, String wwpn) {
        StoragePort port = new StoragePort();
        port.setPortName(name);
        port.setPortGroup(name.replaceAll(":.*", ""));
        port.setPortNetworkId(wwpn);
        port.setInactive(false);
        port.setRegistrationStatus("REGISTERED");
        port.setTransportType("FC");
        URI uri = URIUtil.createId(StoragePort.class);
        port.setId(uri);
        port.setNativeGuid(uri.toString());
        return port;
    }

    protected static StoragePort createVplexFCPort(String name, String wwpn,
            String portGroup) {
        StoragePort port = new StoragePort();
        port.setPortName(name);
        port.setPortGroup(portGroup);
        port.setPortNetworkId(wwpn);
        port.setInactive(false);
        port.setRegistrationStatus("REGISTERED");
        port.setTransportType("FC");
        URI uri = URIUtil.createId(StoragePort.class);
        port.setId(uri);
        port.setNativeGuid(uri.toString());
        return port;
    }

    /**
     * Add ports to a Port Allocation Context.
     * 
     * @param context -- PortAllocationContext used by StoragePortsAllocator
     * @param port -- StoragePort object
     * @param switchName -- Switch name
     */
    public static void addPort(PortAllocationContext context, StoragePort port, String switchName) {
        port.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        port.setNetwork(context._initiatorNetwork.getId());
        String portName = port.getPortName();
        String portGroup = port.getPortGroup();
        StorageHADomain haDomain = new StorageHADomain();
        StorageSystem.Type type = StorageSystem.Type.vnxblock;
        if (port.getPortName().startsWith("FA-")) {
            haDomain.setNativeGuid("SYMMETRIX+" + portName);
        } else if (portGroup != null && portGroup.startsWith("director-")) {
            haDomain.setNativeGuid("VPLEX+" + port.getPortGroup());
        } else if (portGroup.startsWith("X")) {
            haDomain.setNativeGuid("XTREMIO+" + port.getPortGroup());
        } else {
            haDomain.setNativeGuid("VNX+" + portName);
        }

        if (portName.startsWith("SP_A")) {
            haDomain.setSlotNumber("1");
        } else if (portName.startsWith("SP_B")) {
            haDomain.setSlotNumber("2");
        } else if (portName.startsWith("SP_C")) {
            haDomain.setSlotNumber("3");
        } else if (portName.startsWith("SP_D")) {
            haDomain.setSlotNumber("4");
        }
        else if (portName.startsWith("FA-")) {
            type = StorageSystem.Type.vmax;
            int index;
            for (index = 3; index < portName.length(); index++) {
                // Stop on first non-digit after FA-
                if (Character.isDigit(portName.charAt(index)) == false) {
                    break;
                }
            }
            haDomain.setSlotNumber(portName.substring(3, index));
        } else if (portName.startsWith("X")) {
            haDomain.setAdapterName(portGroup);
            type = StorageSystem.Type.xtremio;
        } else {
            haDomain.setSlotNumber("0");
        }

        if (portGroup != null) {
            if (portGroup.equals("director-1-1-A")) {
                haDomain.setSlotNumber("0");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-1-1-B")) {
                haDomain.setSlotNumber("1");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-1-2-A")) {
                haDomain.setSlotNumber("2");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-1-2-B")) {
                haDomain.setSlotNumber("3");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-1-A")) {
                haDomain.setSlotNumber("8");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-1-B")) {
                haDomain.setSlotNumber("9");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-2-A")) {
                haDomain.setSlotNumber("10");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-2-B")) {
                haDomain.setSlotNumber("11");
                type = StorageSystem.Type.vplex;
            }
            haDomain.setName(portGroup);
        }

        String digits = port.getPortName().replaceAll("[^0-9]", "");
        Long usage = new Long(digits);

        context.addPort(port, haDomain, type, switchName, usage);
    }

    /**
     * Returns a list of Storage Port URIs for a list of StoragePorts.
     * 
     * @param ports List<StoragePort>
     * @return List<URI> of StoragePorts
     */
    static protected List<URI> getPortURIs(List<StoragePort> ports) {
        ArrayList<URI> uris = new ArrayList<URI>();
        for (StoragePort port : ports) {
            uris.add(port.getId());
        }
        return uris;
    }
}
