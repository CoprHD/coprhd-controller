/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service;

import static org.junit.Assert.fail;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

public class CoordinatorClientInetAddressMapTest extends CoordinatorTestBase {
    private static final Logger _logger = LoggerFactory
            .getLogger(CoordinatorClientInetAddressMapTest.class);
    private static final String[] nodes = new String[] { "vipr1", "vipr2", "vipr3" };
    private static final String[] ip4 = new String[] { "10.10.10.1", "10.10.10.2", "10.10.10.3" };
    private static final String[] ip6 = new String[] { "FE80:0000:0000:0000:0202:B3FF:FE1E:8329",
            "fe80::250:56ff:fe8e:4976", "ABCD:EF01:2345:6789:ABCD:EF01:2345:6789" };
    private static final String[] external_nodes = new String[] { "data1", "data2", "data3" };
    private static final String external_ipv4 = "10.10.10.110";
    private static final String external_ipv6 = "fe80::226:2dff:fefa:5ff";
    private DualInetAddress external_dualinetaddress;
    private CoordinatorClientImpl client;

    private CoordinatorClientInetAddressMap nodeMap = new CoordinatorClientInetAddressMap();

    @Before
    public void setUp() throws Exception {
        // init the map with defaults
        Map<String, DualInetAddress> controllerNodeMap = new ConcurrentHashMap<String, DualInetAddress>();
        Map<String, DualInetAddress> externalNodeMap = new ConcurrentHashMap<String, DualInetAddress>();

        for (int i = 0; i < nodes.length; i++) {
            controllerNodeMap.put(nodes[i], DualInetAddress.fromAddresses(ip4[i], ip6[i]));
        }
        // one external node - 10.10.10.110/fe80::226:2dff:fefa:5ff
        external_dualinetaddress = DualInetAddress.fromAddresses(external_ipv4, external_ipv6);
        externalNodeMap.put(external_nodes[0], external_dualinetaddress);

        // set to node map
        // *** assume this node[0]
        nodeMap.setNodeId(nodes[0]);
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(ip4[0], ip6[0]));

        nodeMap.setControllerNodeIPLookupMap(controllerNodeMap);
        nodeMap.setExternalInetAddressLookupMap(externalNodeMap);
        client = (CoordinatorClientImpl) connectClient();
        client.setInetAddessLookupMap(nodeMap);
        nodeMap.setCoordinatorClient(client);
        client.start();
    }

    /**
     * Connects to test coordinator
     * 
     * @return connected client
     * @throws Exception
     */
    protected static CoordinatorClient connectClient() throws Exception {
        CoordinatorClientImpl client = new CoordinatorClientImpl();
        client.setZkConnection(createConnection(10 * 1000));
        return client;
    }

    /**
     * Set node info to zk.
     */
    public void setNodeDualInetAddressInfo(String nodeId, String address) {
        try {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setId(nodeId);
            cfg.setKind(Constants.NODE_DUALINETADDR_CONFIG);
            cfg.setConfig(Constants.CONFIG_DUAL_INETADDRESSES, address);
            client.persistServiceConfiguration(cfg);
        } catch (Exception e) {
            _logger.error("Failed to set node DualInetAddress info", e);
            fail(e.getMessage());
        }
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test getConnectableAddress(node_id), given a nodeId. Node will be looked up in the controller
     * map, external node map, then the zk configurations. Not connectable exception will be thrown
     * if no connectable ip is found.
     * 
     * @see DualInetAddress getConnectableAddress(String host)
     */
    @Test
    public void testGetConnectableAddressWithId() throws Exception {
        // *** assume this node[0]
        nodeMap.setNodeId(nodes[0]);
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(ip4[0], ip6[0]));

        // 1. test controller nodes, this should pass lookup
        String nodeId = nodes[1];
        String ip = nodeMap.getConnectableInternalAddress(nodeId);
        Assert.assertNotNull(ip);
        Assert.assertTrue(ip.compareTo(ip4[1]) == 0);
        // 2. test external node in the map, this should pass
        nodeId = external_nodes[0];
        ip = nodeMap.getConnectableInternalAddress(nodeId);
        Assert.assertNotNull(ip);
        Assert.assertTrue(ip.compareTo(external_dualinetaddress.getInet4()) == 0);

        // 3. test nodes not known, in zk -
        // create the external node not in the map and send to zk
        ip = "10.10.10.111";// ipv4
        nodeId = external_nodes[2];// date3
        setNodeDualInetAddressInfo(nodeId, ip);

        // now look it up in the map, should pass
        String foundIp = nodeMap.getConnectableInternalAddress(nodeId);
        Assert.assertNotNull(ip);
        Assert.assertTrue(foundIp.compareTo(ip) == 0);

        // 4. If local node is ipv6 - this should fail, notconnectable exception
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(null, ip6[0]));
        boolean found = true;
        try {
            foundIp = nodeMap.getConnectableInternalAddress(nodeId);
        } catch (Exception ex) {
            found = false;
        }
        Assert.assertFalse(found);

        // 5. Given remote server dual stack, local node ipv6
        // should return v6 only
        ip = "10.10.10.112,fe80:0:ff:ffff:ffff:ffff:ffff:ffff";// ipv4/ipv6
        nodeId = external_nodes[1];// date3
        setNodeDualInetAddressInfo(nodeId, ip);
        // now look it up - should return ipv6
        foundIp = nodeMap.getConnectableInternalAddress(nodeId);
        Assert.assertNotNull(ip);
        Assert.assertTrue(foundIp.compareToIgnoreCase("fe80:0:ff:ffff:ffff:ffff:ffff:ffff") == 0);
    }

    /**
     * Test expandURI, @see CoordinatorClientInetAddressMap.expandURI(URI Uri). Should ba able to
     * find the host from the uri and resolve with ip addresses. If not in the controller map nor
     * external map, should look up in zk; Fails if still not able to find, notConnectable exception
     * will occur.
     */
    @Test
    public void testExpandURI() throws Exception {
        // *** assume this node[0], dual stack
        nodeMap.setNodeId(nodes[0]);
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(ip4[0], ip6[0]));

        // 1. controller nodes should resolve/expand
        String uriString = "http://" + nodes[0] + ":9998/upgrade/internal";
        URI uri = URI.create(uriString);
        URI newUri = nodeMap.expandURI(uri);
        Assert.assertNotNull(newUri);
        // local dualstack, remote dualstack, should return ipv4
        Assert.assertTrue(newUri.getHost().compareTo(ip4[0]) == 0);

        // 2. external known node, should resolve/expand
        uriString = "http://" + external_nodes[0] + ":9091/upgrade/internal";
        uri = URI.create(uriString);
        newUri = nodeMap.expandURI(uri);
        Assert.assertNotNull(newUri);
        // should return ipv4
        Assert.assertTrue(newUri.getHost().compareTo(external_ipv4) == 0);

        // 3. local ipv6 only, should return ipv6
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(null, ip6[0]));
        uriString = "http://" + external_nodes[0] + ":9091/upgrade/internal";
        uri = URI.create(uriString);
        newUri = nodeMap.expandURI(uri);
        Assert.assertNotNull(newUri);
        // should return ipv6
        Assert.assertTrue(newUri.getHost().compareTo("[" + external_ipv6 + "]") == 0);

        // 4.local dual stack, external unknown, throw exception, return same uri
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(ip4[0], ip6[0]));
        uriString = "http://" + external_nodes[1] + ":9091/upgrade/internal";
        uri = URI.create(uriString);
        newUri = nodeMap.expandURI(uri);
        String host = newUri.getHost();
        Assert.assertTrue(host.compareToIgnoreCase(external_nodes[1]) == 0);

        // now if I persist data2 into zk, expandURi should pass
        String ip = "10.10.10.115";// ipv4
        String nodeId = external_nodes[1];// date2
        setNodeDualInetAddressInfo(nodeId, ip);

        uriString = "http://" + external_nodes[1] + ":9091/upgrade/internal";
        uri = URI.create(uriString);
        newUri = nodeMap.expandURI(uri);
        host = newUri.getHost();
        Assert.assertFalse(host.compareToIgnoreCase(external_nodes[1]) == 0);
        Assert.assertTrue(host.compareTo(ip) == 0);

        // 5. data service
        ip = "10.10.10.115";// ipv4
        nodeId = "dataservice-10-10-10-115";// date2
        setNodeDualInetAddressInfo(nodeId, ip);
        uriString = "http://dataservice-10-10-10-115:9091/";
        uri = URI.create(uriString);
        newUri = nodeMap.expandURI(uri);
        host = newUri.getHost();
        Assert.assertNotNull(host);
        Assert.assertTrue(host.compareTo(ip) == 0);
    }

    /**
     * Test get remote server IP address based on local ip.
     * 
     * @throws UnknownHostException
     */
    @Test
    public void testGetExternalConnectableAddress() throws UnknownHostException {
        nodeMap.setNodeId(nodes[0]);
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(ip4[0], ip6[0]));

        String external_ip = "10.10.10.200";// remote node
        String remote = nodeMap.getExternalConnectableAddress(external_ip);
        Assert.assertNotNull(remote);
        Assert.assertTrue(remote.compareTo(external_ip) == 0);

        external_ip = "emc.com";// remote node
        remote = nodeMap.getExternalConnectableAddress(external_ip);
        // Above should return ipv4
        Assert.assertNotNull(remote);
        Assert.assertNotNull(DualInetAddress.fromAddresses(remote, null));

        // test empty or "0.0.0.0"
        external_ip = "0.0.0.0";// remote node
        remote = nodeMap.getExternalConnectableAddress(external_ip);
        // Above should return ipv4
        Assert.assertNotNull(remote);
        Assert.assertNotNull(DualInetAddress.fromAddresses(remote, null));

        external_ip = " ";// remote node
        remote = nodeMap.getExternalConnectableAddress(external_ip);
        // Above should return null
        Assert.assertNull(remote);

        // local v6 only, remote v4, fail
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(null, ip6[0]));
        external_ip = "10.10.10.200";// remote node
        boolean found = true;
        try {
            remote = nodeMap.getExternalConnectableAddress(external_ip);
        } catch (Exception ex) {
            found = false;
        }
        Assert.assertFalse(found);

        // both ipv6 return v6
        // local v6 only, remote v4, fail
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(null, ip6[0]));
        external_ip = ip6[1];// remote node
        remote = nodeMap.getExternalConnectableAddress(external_ip);
        Assert.assertNotNull(remote);
        Assert.assertTrue(remote.compareTo(external_ip) == 0);
    }

    /**
     * Test get local server IP address based on remote ip address.
     * 
     * @throws UnknownHostException
     */
    @Test
    public void testGetLocalConnectableAddress() throws UnknownHostException {
        nodeMap.setNodeId(nodes[0]);
        nodeMap.setDualInetAddress(DualInetAddress.fromAddresses(ip4[0], ip6[0]));

        String external_ip = "10.10.10.200";// remote node
        String local = nodeMap.getLocalConnectableAddress(external_ip);
        Assert.assertNotNull(local);
        Assert.assertTrue(local.compareTo(ip4[0]) == 0);

        external_ip = "emc.com";// remote node
        local = nodeMap.getLocalConnectableAddress(external_ip);
        // Above should return ipv4
        Assert.assertNotNull(local);
        Assert.assertNotNull(DualInetAddress.fromAddresses(local, null));

        // test empty or "0.0.0.0"
        external_ip = "0.0.0.0";// remote node
        local = nodeMap.getLocalConnectableAddress(external_ip);
        // Above should return ipv4
        Assert.assertNotNull(local);
        Assert.assertNotNull(DualInetAddress.fromAddresses(local, null));

        external_ip = "";// remote node
        local = nodeMap.getExternalConnectableAddress(external_ip);
        // Above should return null
        Assert.assertNull(local);

        // remote v6, local should return v6
        external_ip = ip6[1];// remote node
        local = nodeMap.getLocalConnectableAddress(external_ip);
        Assert.assertNotNull(local);
        Assert.assertNotNull(DualInetAddress.fromAddresses(null, local));
        Assert.assertTrue(local.compareTo(ip6[0]) == 0);
    }

}
