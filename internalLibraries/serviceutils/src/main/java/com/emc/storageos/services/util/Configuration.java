/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.services.util;

import com.emc.storageos.model.property.PropertyConstants;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

/**
 * Configuration class holding the configuration info
 * while user install/config/redeploy via the seperate installer.
 */
public class Configuration implements Serializable {
	private static final long serialVersionUID = 1L;

    private String scenario;  // Scenario for multicast the cluster configuration
	private Map<String, String> hwConfig;
	private LinkedHashMap<String, String> networkIpv4Config;
	private LinkedHashMap<String, String> networkIpv6Config;
	private String nodeId;
	private int nodeCount;
    private List<String> aliveNodes;
    private Map<String, String> configMap;

    public Configuration() {
		this.hwConfig = new HashMap<String, String>();
		this.networkIpv4Config = new LinkedHashMap<String, String>();
		this.networkIpv6Config = new LinkedHashMap<String, String>();
		this.aliveNodes = new ArrayList<String>();
        this.configMap = new HashMap<String, String>();
	}

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }
    
    public String getScenario() {
        return this.scenario;
    }
    
    public void setAliveNodes(List<String> nodes) {
    	aliveNodes.clear();
    	aliveNodes.addAll(nodes);
    }
    
    public List<String> getAliveNodes() {
    	return this.aliveNodes;
    }

	/**
	 * Set IPv4 network address map. If the input is display name/label, convert it
	 * to property key.
	 * @param map the input map
	 */
	public void setIpv4NetworkConfig(LinkedHashMap<String, String> map) {
		networkIpv4Config.clear();
		networkIpv4Config.putAll(map);
	}

    /**
     * Set IPv6 network address map. If the input is display name/label, convert it
     * to property key.
     * @param map the input map
     */
    public void setIpv6NetworkConfig(LinkedHashMap<String, String> map) {
        networkIpv6Config.clear();
        networkIpv6Config.putAll(map);
    }

	/**
	 * Check if installer is running at install mode.
	 * @return true if it is at install mode, otherwise false.
	 */
	public boolean isInstallMode() {
		return scenario.equals(PropertyConstants.INSTALL_MODE) ? true : false;
	}
	
	/**
	 * Check if installer is running at config mode.
	 * @return true if it is at config mode, otherwise false.
	 */
	public boolean isConfigMode() {
		return scenario.equals(PropertyConstants.CONFIG_MODE) ? true : false;
	}
	
	/**
	 * Check if installer is running at redeploy mode.
	 * @return true if it is at redeploy mode, otherwise false.
	 */
	public boolean isRedeployMode() {
		return scenario.equals(PropertyConstants.REDEPLOY_MODE) ? true : false;
	}

    /**
     * Get ovf property map with property keys.
     * @return ovf property map
     */
    public Map<String, String> getOVFProps() {
		Map<String, String> propMap = new HashMap<String, String>();
		propMap.putAll(networkIpv4Config);
		propMap.putAll(networkIpv6Config);
		propMap.put(PropertyConstants.NODE_COUNT_KEY, String.valueOf(nodeCount));
		return propMap;
	}

    public Map<String, String> getHwConfig() {
		return hwConfig;
	}

	public LinkedHashMap<String, String> getNetworkIpv4Config() {
		return networkIpv4Config;
	}
	
	public LinkedHashMap<String, String> getNetworkIpv6Config() {
		return networkIpv6Config;
	}

    /**
     * Get network vip string for GUI usage
     * @return 
     *         "IPv4 VIP" if IPv6 is not configured
     *         "IPv6 VIP" if IPv4 is not configured
     *         "IPv4 VIP/IPv6 VIP" if both IPv4 and IPv6 are configured
     */
    public String getNetworkVip() {
        final String v4vip = getNetworkIpv4Config().get(PropertyConstants.IPV4_VIP_KEY);
        final String v6vip = getNetworkIpv6Config().get(PropertyConstants.IPV6_VIP_KEY);

        if (v4vip.equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
            return v6vip;
        } else if (v6vip.equals(PropertyConstants.IPV6_ADDR_DEFAULT)) {
            return v4vip;
        } else {
            return v4vip + "/" + v6vip;
        }
    }

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public int getNodeCount() {
		return nodeCount;
	}

	public void setNodeCount(int nodeCount) {
		this.nodeCount = nodeCount;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		  builder.append(" [");
		  builder.append(" scenario: ").append(scenario);
		  builder.append(", aliveNodes: ").append(aliveNodes);
		  builder.append(", nodecount: ").append(nodeCount);
		  builder.append(", nodeId: ").append(nodeId);
		  builder.append(", hwConfig: ").append(hwConfig);
		  builder.append(", ipv4: ").append(networkIpv4Config);
		  builder.append(", ipv6: ").append(networkIpv6Config);
		  builder.append(" ]");
		  return builder.toString();
	}

    /* Load key/value property map
     */
    public void loadFromPropertyMap(Map<String, String> propMap)
    {
        setNodeId(propMap.get(PropertyConstants.NODE_ID_KEY));
        setNodeCount(Integer.parseInt(propMap.get(PropertyConstants.NODE_COUNT_KEY)));

        getNetworkIpv4Config().put(PropertyConstants.IPV4_VIP_KEY, propMap.get(PropertyConstants.IPV4_VIP_KEY));
        getNetworkIpv4Config().put(PropertyConstants.IPV4_NETMASK_KEY, propMap.get(PropertyConstants.IPV4_NETMASK_KEY));
        getNetworkIpv4Config().put(PropertyConstants.IPV4_GATEWAY_KEY, propMap.get(PropertyConstants.IPV4_GATEWAY_KEY));
        for (int i=1; i<= getNodeCount(); i++) {
            String network_ipaddr_key = String.format(PropertyConstants.IPV4_ADDR_KEY, i);
            getNetworkIpv4Config().put(network_ipaddr_key, propMap.get(network_ipaddr_key));
        }

        getNetworkIpv6Config().put(PropertyConstants.IPV6_VIP_KEY, propMap.get(PropertyConstants.IPV6_VIP_KEY));
        getNetworkIpv6Config().put(PropertyConstants.IPV6_PREFIX_KEY, propMap.get(PropertyConstants.IPV6_PREFIX_KEY));
        getNetworkIpv6Config().put(PropertyConstants.IPV6_GATEWAY_KEY, propMap.get(PropertyConstants.IPV6_GATEWAY_KEY));
        for (int i=1; i<= getNodeCount(); i++) {
            String network_ipaddr6_key = String.format(PropertyConstants.IPV6_ADDR_KEY, i);
            getNetworkIpv6Config().put(network_ipaddr6_key, propMap.get(network_ipaddr6_key));
        }
    }

    /**
     * Get configuration properties in a map of key/value pairs
     * @return configuration properties in a map
     */
    public Map<String, String> getConfigMap() {
        setMapProperties();
        return configMap;
    }

    private void setMapProperties() {
        configMap.put(PropertyConstants.CONFIG_KEY_SCENARIO, scenario);
        configMap.put(PropertyConstants.NODE_COUNT_KEY, String.valueOf(nodeCount));
        configMap.put(PropertyConstants.NODE_ID_KEY, nodeId);
        configMap.putAll(hwConfig);
        configMap.putAll(networkIpv4Config);
        configMap.putAll(networkIpv6Config);
        for (String node: aliveNodes) {
            configMap.put(String.format(PropertyConstants.PROPERTY_KEY_ALIVE_NODE, node), node);
        }
    }

    /**
     * Set the configuration class variables from the property map
     * @param configMap the property map
     */
    public void setConfigMap(Map<String, String> configMap) {
        this.configMap = configMap;
        setClassProperties();
    }

    // set class properties from map key/value pairs
    private void setClassProperties() {
        this.scenario = configMap.get(PropertyConstants.CONFIG_KEY_SCENARIO);

        this.nodeCount = Integer.valueOf(configMap.get(PropertyConstants.NODE_COUNT_KEY));
        this.nodeId = configMap.get(PropertyConstants.NODE_ID_KEY);
        for (String key : configMap.keySet()) {
            if (key.contains("alive_node")) {
                this.aliveNodes.add(configMap.get(key));
            }
        }

        this.hwConfig.put(PropertyConstants.PROPERTY_KEY_NETIF, configMap.get(PropertyConstants.PROPERTY_KEY_NETIF));
        this.hwConfig.put(PropertyConstants.PROPERTY_KEY_DISK, configMap.get(PropertyConstants.PROPERTY_KEY_DISK));
        this.hwConfig.put(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY, configMap.get(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY));
        this.hwConfig.put(PropertyConstants.PROPERTY_KEY_CPU_CORE, configMap.get(PropertyConstants.PROPERTY_KEY_CPU_CORE));
        this.hwConfig.put(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE, configMap.get(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE));

        for (int i = 1; i <= nodeCount; i++) {
            String ipv4Key = String.format(PropertyConstants.IPV4_ADDR_KEY, i);
            this.networkIpv4Config.put(ipv4Key, configMap.get(ipv4Key));
            String ipv6Key = String.format(PropertyConstants.IPV6_ADDR_KEY, i);
            this.networkIpv6Config.put(ipv6Key, configMap.get(ipv6Key));
        }
        this.networkIpv4Config.put(PropertyConstants.IPV4_VIP_KEY, configMap.get(PropertyConstants.IPV4_VIP_KEY));
        this.networkIpv4Config.put(PropertyConstants.IPV4_NETMASK_KEY, configMap.get(PropertyConstants.IPV4_NETMASK_KEY));
        this.networkIpv4Config.put(PropertyConstants.IPV4_GATEWAY_KEY, configMap.get(PropertyConstants.IPV4_GATEWAY_KEY));
        this.networkIpv6Config.put(PropertyConstants.IPV6_VIP_KEY, configMap.get(PropertyConstants.IPV6_VIP_KEY));
        this.networkIpv6Config.put(PropertyConstants.IPV6_PREFIX_KEY, configMap.get(PropertyConstants.IPV6_PREFIX_KEY));
        this.networkIpv6Config.put(PropertyConstants.IPV6_GATEWAY_KEY, configMap.get(PropertyConstants.IPV6_GATEWAY_KEY));
    }
}
