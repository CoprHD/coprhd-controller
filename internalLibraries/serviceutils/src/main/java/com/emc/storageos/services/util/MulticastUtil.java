/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.emc.storageos.model.property.PropertyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Utility to multicast and list node configuration(s)
 * 1. Use publish to multicast the local node's configuration
 * 2. Use list to get the nodes which are publishing there configurations
 */
public class MulticastUtil {
    private static final Logger _log = LoggerFactory.getLogger(MulticastUtil.class);
    private JmDNS jmdns;
    private static Charset UTF_8 = Charset.forName("UTF-8");

    private static void sleep(long ms) {
        final long expirationTime = System.currentTimeMillis() + ms;
        while (true) {
            final long sleepDelay = expirationTime - System.currentTimeMillis();
            if (sleepDelay <= 0) {
                return;
            }
            try {
                Thread.sleep(sleepDelay);
            } catch (Exception e) {
                ;
            }
        }
    }

    private static InetAddress getLinkLocalAddress(String networkInterfaceName) throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface networkInterface : Collections.list(networkInterfaces)) {
            if (networkInterfaceName == null || networkInterfaceName.equals(networkInterface.getName())) {
                Enumeration<InetAddress> ips = networkInterface.getInetAddresses();
                for (InetAddress ip : Collections.list(ips)) {
                    if (ip.isLinkLocalAddress()) {
                        return ip;
                    }
                }
            }
        }
        return null;
    }

    private MulticastUtil(JmDNS jmdns) {
        this.jmdns = jmdns;
    }

    public static MulticastUtil create(String networkInterfaceName) throws IOException {
        JmDNS jmdns = JmDNS.create(MulticastUtil.getLinkLocalAddress(networkInterfaceName));
        return new MulticastUtil(jmdns);
    }

    public static MulticastUtil create() throws IOException {
        return MulticastUtil.create(null);
    }

    public void close() throws IOException {
        if (this.jmdns != null) {
            this.jmdns.unregisterAllServices();
            this.jmdns.close();
            this.jmdns = null;
        }
    }

    /**
     * Publish local node configuration via multicast
     * 
     * @param serviceName name of service to be published. (e.g. release version for installation)
     *            instanceName name of instance which is publishing. (e.g. the local node id etc.)
     *            nodeConfig info to be published
     *            publishTime how long the info would be published
     */
    public void publish(String serviceName, String instanceName, Map<String, String> values, long publishTime) throws IOException {
        ServiceInfo info = ServiceInfo.create("_" + serviceName + "._tcp.local.", instanceName, 9999, 0, 0, values);
        this.jmdns.registerService(info);

        if (publishTime > 0) {
            sleep(publishTime);
            this.close();
        }
    }

    /**
     * List published node(s) configuration in the network via multicast
     * 
     * @param serviceName name of service published. (e.g. release version for installation)
     * @return node(s) configuration list
     */
    public Map<String, Map<String, String>> list(String serviceName) {
        Map<String, Map<String, String>> results = new HashMap<String, Map<String, String>>();
        ServiceInfo[] infos = jmdns.list("_" + serviceName + "._tcp.local.");
        for (ServiceInfo info : infos) {
            _log.info("ServiceInfo:{}", info);

            // Construct the key
            final String[] hostAddrs = info.getHostAddresses();
            final StringBuffer buf = new StringBuffer();
            for (String hostAddr : hostAddrs) {
                buf.append(hostAddr);
                buf.append(';');
            }
            final String key = buf.toString();
            _log.info("\tkey:{}", key);

            // Construct the values
            final Map<String, String> values = new HashMap<String, String>();
            for (Enumeration<String> e = info.getPropertyNames(); e.hasMoreElements();) {
                final String prop = e.nextElement();
                final String value = new String(info.getPropertyBytes(prop));
                _log.info("\tprop:{}, value:{}", prop, value);
                values.put(prop, value);
            }

            // Put <key,values> into the results
            if (values.isEmpty()) {
                _log.warn("values are empty for key: {}", key);
            }
            results.put(key, values.isEmpty() ? null : values);
        }
        return results;
    }

    /*
     * Broadcast local configuration to others over the network.
     * 
     * @return true if broadcast successful, false if failed.
     */
    public static boolean doBroadcast(String releaseVersion, Configuration config, long publishTime) {
        boolean taskSuccess = true;
        _log.debug("{} - broadcasting cluster configuration {}", config.getNodeId(), config.toString());
        try {
            MulticastUtil.create(config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_NETIF))
                    .publish(releaseVersion, config.getNodeId(), config.getConfigMap(), publishTime);
        } catch (IOException e) {
            taskSuccess = false;
            _log.error("broadcast configuration caught exception with: " + e.getMessage());
        } finally {
            _log.info("broadcast configuration via {} for {} is done",
                    config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_NETIF),
                    config.getScenario());
        }
        return taskSuccess;
    }

    // to be removed
    private static void server(String nodeId, Map<String, String> clusterConfig) {
        final String serviceName = "vipr-2.2.0.0.1";
        try {
            MulticastUtil.create().publish(serviceName, nodeId, clusterConfig, 10000);
        } catch (Exception e) {
        	_log.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    // to be removed
    private static void client() {
        final String serviceName = "vipr-2.2.0.0.1";
        try {
            MulticastUtil.create().list(serviceName);
        } catch (Exception e) {
        	_log.error(e.getMessage(), e);
            System.exit(1);
        }
    }
}
