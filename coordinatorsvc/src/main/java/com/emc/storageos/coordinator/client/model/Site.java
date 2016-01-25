/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

/**
 * Representation for a ViPR site, both primary and standby
 */
public class Site {
    private static final Logger log = LoggerFactory.getLogger(Site.class);

    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_VIP = "vip";
    private static final String KEY_SITE_SHORTID = "siteShortId";
    private static final String KEY_CREATIONTIME = "creationTime";
    private static final String KEY_LASTSTATEUPDATETIME = "lastStateUpdateTime";
    private static final String KEY_LASTOPERATION = "lastOperation";
    private static final String KEY_SITE_STATE = "state";
    private static final String KEY_NODESADDR = "nodesAddr";
    private static final String KEY_NODESADDR6 = "nodesAddr6";
    private static final String KEY_NODECOUNT = "nodeCount";
    private static TreeMap<String, String> treeMapSorter = new TreeMap<String, String>();
    
    public static final String CONFIG_KIND = "disasterRecoverySites";

    private String uuid;
    private String vdcShortId;
    private String name;
    private String vip;
    private String description;
    private Map<String, String> hostIPv4AddressMap = new HashMap<>();
    private Map<String, String> hostIPv6AddressMap = new HashMap<>();
    private String siteShortId;
    private long creationTime;
    private long lastStateUpdateTime;
    private SiteState lastOperation;
    private SiteState state = SiteState.ACTIVE;
    private int nodeCount;

    public Site() {
    }
    
    public Site(Configuration config) {
        if (config != null) {
            fromConfiguration(config);
        }
    }

    public SiteState getLastOperation() {
        return lastOperation;
    }

    public void setLastOperation(SiteState lastOperation) {
        this.lastOperation = lastOperation;
    }
    
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getVdcShortId() {
        return vdcShortId;
    }

    public void setVdcShortId(String vdcShortId) {
        this.vdcShortId = vdcShortId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
    }

    public Map<String, String> getHostIPv4AddressMap() {
        return hostIPv4AddressMap;
    }

    public void setHostIPv4AddressMap(Map<String, String> hostIPv4AddressMap) {
        this.hostIPv4AddressMap = hostIPv4AddressMap;
    }

    public Map<String, String> getHostIPv6AddressMap() {
        return hostIPv6AddressMap;
    }

    public void setHostIPv6AddressMap(Map<String, String> hostIPv6AddressMap) {
        this.hostIPv6AddressMap = hostIPv6AddressMap;
    }
    
    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSiteShortId() {
        return siteShortId;
    }

    public void setSiteShortId(String shortId) {
        this.siteShortId = shortId;
    }
    
    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastStateUpdateTime() {
        return lastStateUpdateTime;
    }

    public void setLastStateUpdateTime(long lastStateUpdateTime) {
        this.lastStateUpdateTime = lastStateUpdateTime;
    }

    public SiteState getState() {
        return state;
    }

    public void setState(SiteState state) {
        this.state = state;
        setLastStateUpdateTime(System.currentTimeMillis());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Site))
            return false;
        Site other = (Site) obj;
        if (uuid == null) {
            if (other.uuid != null)
                return false;
        } else if (!uuid.equals(other.uuid))
            return false;
        return true;
    }

    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(String.format("%s/%s", CONFIG_KIND, vdcShortId));
        config.setId(uuid);
        if (name != null) {
            config.setConfig(KEY_NAME, name);
        }
        if (description != null) {
            config.setConfig(KEY_DESCRIPTION, description);
        }
        if (vip != null) {
            config.setConfig(KEY_VIP, vip);
        }
        if (siteShortId != null) {
            config.setConfig(KEY_SITE_SHORTID, this.siteShortId);
        }
        config.setConfig(KEY_CREATIONTIME, String.valueOf(creationTime));
        if (lastStateUpdateTime != 0L) {
            config.setConfig(KEY_LASTSTATEUPDATETIME, String.valueOf(lastStateUpdateTime));
        }

        if (lastOperation != null) {
            config.setConfig(KEY_LASTOPERATION, String.valueOf(lastOperation));
        }

        if (state != null) {
            config.setConfig(KEY_SITE_STATE, String.valueOf(state));
        }

        config.setConfig(KEY_NODECOUNT, String.valueOf(nodeCount));
        
        treeMapSorter.clear();
        treeMapSorter.putAll(this.hostIPv4AddressMap);
        config.setConfig(KEY_NODESADDR, StringUtil.join(treeMapSorter.values(), ","));
        
        treeMapSorter.clear();
        treeMapSorter.putAll(this.hostIPv6AddressMap);
        config.setConfig(KEY_NODESADDR6, StringUtil.join(treeMapSorter.values(), ","));
        
        return config;
    }

    private void fromConfiguration(Configuration config) {
        String kindStr = config.getKind();
        if (!kindStr.split("/")[0].equals(CONFIG_KIND)) {
            throw new IllegalArgumentException("Unexpected configuration kind for Site");
        }
        try {
            this.vdcShortId = kindStr.split("/")[1];
            this.uuid = config.getId();
            this.name = config.getConfig(KEY_NAME);
            this.description = config.getConfig(KEY_DESCRIPTION);
            this.vip = config.getConfig(KEY_VIP);
            this.siteShortId = config.getConfig(KEY_SITE_SHORTID);
            String s = config.getConfig(KEY_CREATIONTIME);
            if (s != null) {
                this.creationTime = Long.valueOf(s);
            }
            s = config.getConfig(KEY_LASTSTATEUPDATETIME);
            if (s != null) {
                this.lastStateUpdateTime = Long.valueOf(s);
            }

            s = config.getConfig(KEY_LASTOPERATION);
            if (s != null) {
                lastOperation = SiteState.valueOf(config.getConfig(KEY_LASTOPERATION));
            }

            s = config.getConfig(KEY_SITE_STATE);
            if (s != null) {
                state = SiteState.valueOf(config.getConfig(KEY_SITE_STATE));
            }
            s = config.getConfig(KEY_NODECOUNT);
            if (s != null) {
                nodeCount = Integer.valueOf(s);
            }
            
            String addrs = config.getConfig(KEY_NODESADDR);
            if (!StringUtil.isBlank(addrs)) {
                int i = 1;
                for (String addr : addrs.split(",")) {
                    hostIPv4AddressMap.put(String.format("node%d", i++), addr);
                }
            }
            
            String addr6s = config.getConfig(KEY_NODESADDR6);
            if (!StringUtil.isBlank(addr6s)) {
                int i = 1;
                for (String addr : addr6s.split(",")) {
                    hostIPv6AddressMap.put(String.format("node%d", i++), addr);
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unrecognized configuration data for Site", ex);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Site [uuid=");
        builder.append(uuid);
        builder.append(", vdc=");
        builder.append(vdcShortId);
        builder.append(", name=");
        builder.append(name);
        builder.append(", vip=");
        builder.append(vip);
        builder.append(", state=");
        builder.append(state);
        builder.append(", description=");
        builder.append(description);
        builder.append(", hostIPv4AddressMap=");
        builder.append(hostIPv4AddressMap);
        builder.append(", hostIPv6AddressMap=");
        builder.append(hostIPv6AddressMap);
        builder.append(", standbyShortId=");
        builder.append(siteShortId);
        builder.append(", creationTime=");
        builder.append(creationTime);
        builder.append("]");
        return builder.toString();
    }

    /**
     * @return human-readable abstract of this site, only contains site name/vip/uuid
     * The String returned by this method should only be used in display situation (e.g. log or audit log)
     */
    public String toBriefString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Site [name=").append(name);
        builder.append(", vip=").append(vip);
        builder.append(", uuid=").append(uuid).append("]");
        return builder.toString();
    }
}