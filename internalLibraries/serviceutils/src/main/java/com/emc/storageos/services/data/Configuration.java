/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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
package com.emc.storageos.services.data;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

import com.emc.storageos.services.util.InstallerConstants;

/**
 * Class holds the configuration data user entered.
 *
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
		networkIpv4Config.putAll(convertIpv4DisplayNameToPropertyKey(map));
	}

	/**
	 * Check if installer is running at install mode.
	 * @return true if it is at install mode, otherwise false.
	 */
	public boolean isInstallMode() {
		return scenario.equals(InstallerConstants.INSTALL_MODE) ? true : false;
	}
	
	/**
	 * Check if installer is running at config mode.
	 * @return true if it is at config mode, otherwise false.
	 */
	public boolean isConfigMode() {
		return scenario.equals(InstallerConstants.CONFIG_MODE) ? true : false;
	}
	
	/**
	 * Check if installer is running at redeploy mode.
	 * @return true if it is at redeploy mode, otherwise false.
	 */
	public boolean isRedeployMode() {
		return scenario.equals(InstallerConstants.REDEPLOY_MODE) ? true : false;
	}
	
	/*
	 * Convert the IPv4 network display name/label to property key
	 * @param inMap the input map
	 * @return the map with property key
	 */
	private LinkedHashMap<String, String> convertIpv4DisplayNameToPropertyKey(LinkedHashMap<String, String> inMap) {
		LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
		for (Entry<String, String> entry : inMap.entrySet()) {
			String key = entry.getKey();
			if (ipv4NameConversionTable.containsKey(key)){
				outMap.put(ipv4NameConversionTable.get(key), entry.getValue());
			} else {
				outMap.put(key, entry.getValue());
			}
		}
		return outMap;
	}

	/**
	 * Set IPv6 network address map. If the input is display name/label, convert it 
	 * to property key.
	 * @param map the input map
	 */
	public void setIpv6NetworkConfig(LinkedHashMap<String, String> map) {
		networkIpv6Config.clear();
		networkIpv6Config.putAll(convertIpv6DisplayNameToPropertyKey(map));
	}

	/*
	 * Convert the IPv6 network display name/label to property key
	 * @param inMap the input map
	 * @return the map with property key
	 */
	private LinkedHashMap<String, String> convertIpv6DisplayNameToPropertyKey(LinkedHashMap<String, String> inMap) {
		LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
		for (Entry<String, String> entry : inMap.entrySet()) {
			String key = entry.getKey();
			if (ipv6NameConversionTable.containsKey(key)){
				outMap.put(ipv6NameConversionTable.get(key), entry.getValue());
			} else {
				outMap.put(key, entry.getValue());
			}
		}
		return outMap;
	}
    
    /*
     * IPv4 Network addresses conversion table between display label and property key
     */
    private static final Map<String, String> ipv4NameConversionTable;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
        for (int i=1; i<=5; i++) {
        	aMap.put(String.format(InstallerConstants.DISPLAY_LABEL_IPV4_NODE_ADDR, i), 
        			String.format(InstallerConstants.PROPERTY_KEY_IPV4_ADDR, i));
        }
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV4_VIP, InstallerConstants.PROPERTY_KEY_IPV4_VIP);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV4_NETMASK, InstallerConstants.PROPERTY_KEY_IPV4_NETMASK);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV4_GATEWAY, InstallerConstants.PROPERTY_KEY_IPV4_GATEWAY);
        ipv4NameConversionTable = Collections.unmodifiableMap(aMap);
    }
    
    /*
     * IPv6 Network addresses conversion table between display label and property key
     */
    private static final Map<String, String> ipv6NameConversionTable;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
        for (int i=1; i<=5; i++) {
        	aMap.put(String.format(InstallerConstants.DISPLAY_LABEL_IPV6_NODE_ADDR, i), 
        			String.format(InstallerConstants.PROPERTY_KEY_IPV6_ADDR, i));
        }
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV6_VIP, InstallerConstants.PROPERTY_KEY_IPV6_VIP);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX, InstallerConstants.PROPERTY_KEY_IPV6_PREFIX);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV6_GATEWAY, InstallerConstants.PROPERTY_KEY_IPV6_GATEWAY);
        ipv6NameConversionTable = Collections.unmodifiableMap(aMap);
    }
    
    // help method to reverse map key value pairs
    private static Map<String, String> ovfKeyToDisplayTag(Map<String, String> map) {
        Map<String, String> rev = new HashMap<String, String>();
        for(Entry<String, String> entry : map.entrySet())
            rev.put(entry.getValue(), entry.getKey());
        return rev;
    }
    
    /**
     * Get IPv4 network addresses with display labels
     * @return the map with display labels
     */
    public LinkedHashMap<String, String> getIpv4DisplayMap() {
    	Map<String, String> conversionTable = ovfKeyToDisplayTag(ipv4NameConversionTable);
    	LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
    	for (Entry<String, String> entry : networkIpv4Config.entrySet()) {
			String key = entry.getKey();
			if (conversionTable.containsKey(key)){
				outMap.put(conversionTable.get(key), entry.getValue());
			} else {
				outMap.put(key, entry.getValue());
			}
		}
		return outMap;	
    }
    
    /**
     * Get IPv6 network addresses with display labels
     * @return the map with display labels
     */
    public LinkedHashMap<String, String> getIpv6DisplayMap() {
    	Map<String, String> conversionTable = ovfKeyToDisplayTag(ipv6NameConversionTable);
    	LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
    	for (Entry<String, String> entry : networkIpv6Config.entrySet()) {
			String key = entry.getKey();
			if (conversionTable.containsKey(key)){
				outMap.put(conversionTable.get(key), entry.getValue());
			} else {
				outMap.put(key, entry.getValue());
			}
		}
		return outMap;	
    }
    
    /**
     * Get ovf property map with property keys.
     * @return ovf property map
     */
    public Map<String, String> getOVFProps() {
		Map<String, String> propMap = new HashMap<String, String>();
		propMap.putAll(networkIpv4Config);
		propMap.putAll(networkIpv6Config);
		propMap.put(InstallerConstants.PROPERTY_KEY_NODE_COUNT, String.valueOf(nodeCount));
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
        final String v4vip = getNetworkIpv4Config().get(InstallerConstants.PROPERTY_KEY_IPV4_VIP);
        final String v6vip = getNetworkIpv6Config().get(InstallerConstants.PROPERTY_KEY_IPV6_VIP);

        if (v4vip.equals(InstallerConstants.IPV4_ADDR_DEFAULT)) {
            return v6vip;
        } else if (v6vip.equals(InstallerConstants.IPV6_ADDR_DEFAULT)) {
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
        setNodeId(propMap.get(InstallerConstants.PROPERTY_KEY_NODE_ID));
        setNodeCount(Integer.parseInt(propMap.get(InstallerConstants.PROPERTY_KEY_NODE_COUNT)));

        getNetworkIpv4Config().put(InstallerConstants.PROPERTY_KEY_IPV4_VIP, propMap.get(InstallerConstants.PROPERTY_KEY_IPV4_VIP));
        getNetworkIpv4Config().put(InstallerConstants.PROPERTY_KEY_IPV4_NETMASK, propMap.get(InstallerConstants.PROPERTY_KEY_IPV4_NETMASK));
        getNetworkIpv4Config().put(InstallerConstants.PROPERTY_KEY_IPV4_GATEWAY, propMap.get(InstallerConstants.PROPERTY_KEY_IPV4_GATEWAY));
        for (int i=1; i<= getNodeCount(); i++) {
            String network_ipaddr_key = String.format(InstallerConstants.PROPERTY_KEY_IPV4_ADDR, i);
            getNetworkIpv4Config().put(network_ipaddr_key, propMap.get(network_ipaddr_key));
        }

        getNetworkIpv6Config().put(InstallerConstants.PROPERTY_KEY_IPV6_VIP, propMap.get(InstallerConstants.PROPERTY_KEY_IPV6_VIP));
        getNetworkIpv6Config().put(InstallerConstants.PROPERTY_KEY_IPV6_PREFIX, propMap.get(InstallerConstants.PROPERTY_KEY_IPV6_PREFIX));
        getNetworkIpv6Config().put(InstallerConstants.PROPERTY_KEY_IPV6_GATEWAY, propMap.get(InstallerConstants.PROPERTY_KEY_IPV6_GATEWAY));
        for (int i=1; i<= getNodeCount(); i++) {
            String network_ipaddr6_key = String.format(InstallerConstants.PROPERTY_KEY_IPV6_ADDR, i);
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
        configMap.put(InstallerConstants.CONFIG_KEY_SCENARIO, scenario);
        configMap.put(InstallerConstants.PROPERTY_KEY_NODE_COUNT, String.valueOf(nodeCount));
        configMap.put(InstallerConstants.PROPERTY_KEY_NODE_ID, nodeId);
        configMap.putAll(hwConfig);
        configMap.putAll(networkIpv4Config);
        configMap.putAll(networkIpv6Config);
        for (String node: aliveNodes) {
            configMap.put(String.format(InstallerConstants.PROPERTY_KEY_ALIVE_NODE, node), node);
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
        this.scenario = configMap.get(InstallerConstants.CONFIG_KEY_SCENARIO);

        this.nodeCount = Integer.valueOf(configMap.get(InstallerConstants.PROPERTY_KEY_NODE_COUNT));
        this.nodeId = configMap.get(InstallerConstants.PROPERTY_KEY_NODE_ID);
        for (String key : configMap.keySet()) {
            if (key.contains("alive_node")) {
                this.aliveNodes.add(configMap.get(key));
            }
        }

        this.hwConfig.put(InstallerConstants.PROPERTY_KEY_NETIF, configMap.get(InstallerConstants.PROPERTY_KEY_NETIF));
        this.hwConfig.put(InstallerConstants.PROPERTY_KEY_DISK, configMap.get(InstallerConstants.PROPERTY_KEY_DISK));
        this.hwConfig.put(InstallerConstants.PROPERTY_KEY_DISK_CAPACITY, configMap.get(InstallerConstants.PROPERTY_KEY_DISK_CAPACITY));
        this.hwConfig.put(InstallerConstants.PROPERTY_KEY_CPU_CORE, configMap.get(InstallerConstants.PROPERTY_KEY_CPU_CORE));
        this.hwConfig.put(InstallerConstants.PROPERTY_KEY_MEMORY_SIZE, configMap.get(InstallerConstants.PROPERTY_KEY_MEMORY_SIZE));

        for (int i = 1; i <= nodeCount; i++) {
            String ipv4Key = String.format(InstallerConstants.PROPERTY_KEY_IPV4_ADDR, i);
            this.networkIpv4Config.put(ipv4Key, configMap.get(ipv4Key));
            String ipv6Key = String.format(InstallerConstants.PROPERTY_KEY_IPV6_ADDR, i);
            this.networkIpv6Config.put(ipv6Key, configMap.get(ipv6Key));
        }
        this.networkIpv4Config.put(InstallerConstants.PROPERTY_KEY_IPV4_VIP, configMap.get(InstallerConstants.PROPERTY_KEY_IPV4_VIP));
        this.networkIpv4Config.put(InstallerConstants.PROPERTY_KEY_IPV4_NETMASK, configMap.get(InstallerConstants.PROPERTY_KEY_IPV4_NETMASK));
        this.networkIpv4Config.put(InstallerConstants.PROPERTY_KEY_IPV4_GATEWAY, configMap.get(InstallerConstants.PROPERTY_KEY_IPV4_GATEWAY));
        this.networkIpv6Config.put(InstallerConstants.PROPERTY_KEY_IPV6_VIP, configMap.get(InstallerConstants.PROPERTY_KEY_IPV6_VIP));
        this.networkIpv6Config.put(InstallerConstants.PROPERTY_KEY_IPV6_PREFIX, configMap.get(InstallerConstants.PROPERTY_KEY_IPV6_PREFIX));
        this.networkIpv6Config.put(InstallerConstants.PROPERTY_KEY_IPV6_GATEWAY, configMap.get(InstallerConstants.PROPERTY_KEY_IPV6_GATEWAY));
    }

}
