/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service.impl;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.NotConnectableException;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

/**
 * Coordinator-client private mapping from ViPR nodeId to DualInetAddress Note that the Controller
 * nodes are inserted statically through a XML bean config file created at the boot time. The extra
 * nodes (e.g. datanodes) are inserted (registered) dynamically through an internal REST API call -
 * There is no removal (unregistration) of extra nodes. Thus, the map may contain expired entries.
 */
public class CoordinatorClientInetAddressMap {
    private static final Logger _logger = LoggerFactory
            .getLogger(CoordinatorClientInetAddressMap.class);
    // initial delay and wait interval in minutes for map cleaning thread
    private static final int INITIAL_DELAY_MINUTES = 5;// 5 min
    private static final int WAIT_INTERVAL_MINUTES = 5;// 5 min
    // Wait before forcing terminate of the cleaning task
    private static final int TERMINATE_DELAY = 1; // second

    private static final String POOL_NAME = "CoordinatorClientInetAddressMap";

    // This is the node name where the coordinator client is running
    // Coordinator client has reference to this map
    private String nodeName;

    /**
     * This holds the ipv4 and ipv6 addresses of this node. Connectable version is chosen while
     * connecting to other nodes.
     */
    private DualInetAddress dualInetAddress;

    /**
     * stores<node_id/DualInetAddress{ipv4,ipv6}> info of the controller nodes. This is initialized
     * from the bean xml file.
     */
    private Map<String, DualInetAddress> controllerNodeIPLookupMap;

    /**
     * This map maintains the extra nodes' ip address info.
     */
    private Map<String, DualInetAddress> extraNodeInetAddressMap = new ConcurrentHashMap<String, DualInetAddress>();

    private CoordinatorClient coordinatorClient;

    private ScheduledExecutorService executor = new NamedScheduledThreadPoolExecutor(POOL_NAME, 1);

    public Map<String, DualInetAddress> getControllerNodeIPLookupMap() {
        return controllerNodeIPLookupMap;
    }

    public void setControllerNodeIPLookupMap(Map<String, DualInetAddress> controllerNodeIPLookupMap) {
        this.controllerNodeIPLookupMap = controllerNodeIPLookupMap;
    }

    public Map<String, DualInetAddress> getExternalInetAddressLookupMap() {
        return extraNodeInetAddressMap;
    }

    public void setExternalInetAddressLookupMap(
            Map<String, DualInetAddress> externalInetAddressLookupMap) {
        this.extraNodeInetAddressMap = externalInetAddressLookupMap;
    }

    /**
     * Get the node id where the coordinator client is on.
     * 
     * @return
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Setter - set the node name of the client.
     * 
     * @param node
     *            the name to be set to
     */
    public void setNodeName(String node) {
        _logger.debug("Setting local node name: " + node);
        this.nodeName = node;
    }

    public DualInetAddress getDualInetAddress() {
        return dualInetAddress;
    }

    public void setDualInetAddress(DualInetAddress dualInetAddress) {
        _logger.debug("Setting local node DualInetAddress: " + dualInetAddress.toString());
        this.dualInetAddress = dualInetAddress;
    }

    /**
     * Prints out records in the map
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getControllerNodeIPLookupMap() != null && (getControllerNodeIPLookupMap().size() > 0)) {
            Iterator<String> itr = getControllerNodeIPLookupMap().keySet().iterator();
            while (itr.hasNext()) {
                String key = itr.next().toString();
                DualInetAddress record = getControllerNodeIPLookupMap().get(key);
                if (StringUtils.isNotBlank(key)) {
                    sb.append(key).append("-").append(record.toString()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Wrapper- add entry to the map. This is needed for data node ip changes.
     * 
     * @param nodeId
     *            - data node id - data1. data2, etc
     * @param value
     *            - the DualInetAddress that has v4 and/or v6 ip
     */
    public void put(String nodeId, DualInetAddress value) {
        _logger.info("Adding external node: "+ nodeId +" and DualInetAddress: " + dualInetAddress.toString() + " to CoordinatorClientInetAddressMap.");
        getExternalInetAddressLookupMap().put(nodeId, value);
    }

    /**
     * Get IP address record based on node_id to lookup
     * 
     * @param nodeId
     *            - data node id
     * @return data node address information, see @DualInetAddress.
     */
    public DualInetAddress get(String nodeId) {
        DualInetAddress address = getControllerNodeIPLookupMap().get(nodeId);
        if (address == null) {
            address = getExternalInetAddressLookupMap().get(nodeId);
        }
        return address;
    }

    /**
     * Given endpoint uri, Replace the URI's host/ip with matching version of IP address
     * 
     * @param uri
     *            - the endpoint to look up.
     * @return new URI with resolved IP address of correct version.
     * 
     */
    public URI expandURI(URI uri) {
        String node = uri.getHost();// get node_id
        _logger.debug("Expand uri: " + uri);
        URI newUri = null;

        // NOTE: this is needed for backward compatiblity
        // TODO:verify - we probably need to add exclusion for 'localhost' as well.
        if (uri.getHost().compareToIgnoreCase("localhost") == 0) {
            return uri;
        }
        if (node.indexOf('.') > 0 || node.indexOf(':') > 0) {
            return uri;
        }

        try {
            // this is a node_id format, find its ip
            String ip = getConnectableInternalAddress(node);

            newUri = new URI(uri.getScheme(), uri.getUserInfo(), ip, uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment());
            _logger.debug("New expanded uri: " + newUri);

        } catch (Exception e) {
            _logger.error("Failed expanding URI. ", e);
            return uri;
        }
        return newUri;
    }

    /**
     * Given server nodeid, find compatible ip based assuming caller is the client.
     * 
     * @param nodeId node id of the server
     * @return a connectable ip address as string.
     * @throws NotConnectableException
     */
    public String getConnectableInternalAddress(String nodeId) {
        DualInetAddress client = getDualInetAddress();
        _logger.debug("local address: " + client);
        DualInetAddress address = null;
        if (nodeId.compareToIgnoreCase(getNodeName()) == 0) {
            address = getDualInetAddress();
        } else {
            address = getControllerNodeIPLookupMap().get(nodeId);
            if (address == null) {
                // lookup in extra nodes map
                address = getExternalInetAddressLookupMap().get(nodeId);
                if (address == null) {
                    address = ((CoordinatorClientImpl) coordinatorClient)
                            .loadInetAddressFromCoordinator(nodeId);
                    if (address == null)
                        throw CoordinatorException.fatals
                                .notConnectableError("Node lookup failed: " + nodeId);
                }
            }
        }
        return client.getConnectableAddress(address);
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
        startCleaningTask();
    }

    /**
     * Check if specific node is a controller node.
     * @return true if controller node map has it
     */
    public boolean isControllerNode() {
        return (controllerNodeIPLookupMap.get(getNodeName()) != null);
    }

    /**
     * Start cache cleaning thread.
     */
    public void startCleaningTask() {
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                _logger.debug("Entering {}",
                        Thread.currentThread().getStackTrace()[1].getMethodName());
                getExternalInetAddressLookupMap().clear();
                _logger.debug("Exiting {}",
                        Thread.currentThread().getStackTrace()[1].getMethodName());
            }
        }, INITIAL_DELAY_MINUTES, WAIT_INTERVAL_MINUTES, TimeUnit.SECONDS);
    }

    /**
     * Stop cache cleaning thread.
     */
    public void stop() {
        executor.shutdown();
        try {
            executor.awaitTermination(TERMINATE_DELAY, TimeUnit.SECONDS);
        } catch (Exception e) {
            _logger.error("TimeOut occured while waiting for Cleaning thread to end.");
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Get the connectable ip version of the external server.
     * Local node is used as a client to determine the coonnectable address pair.
     * 
     * @param server external server that we are trying to connect
     * @return connectable ip address string
     * @throws UnknownHostException
     */
    public String getExternalConnectableAddress(String server) throws UnknownHostException {
        return getDualInetAddress().getConnectableAddress(server);
    }

    /**
     * Get the connectable ip address string for the external client.
     * Local node is used as a server to determine the coonnectable
     * address pair.
     * 
     * @param client  the external client that is requesting a connection
     * @return the connectable ip address for external client.
     * @throws UnknownHostException
     */
    public String getLocalConnectableAddress(String client) throws UnknownHostException {
        return getDualInetAddress().getConnectableLocalAddress(client);
    }

}
