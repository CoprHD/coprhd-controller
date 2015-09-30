/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * Representation for a ViPR site, both primary and standby
 */
public class Site {
    private static final Logger log = LoggerFactory.getLogger(Site.class);
    
    private static final String KEY_VDC = "vdc";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_VIP = "vip";
    private static final String KEY_SECRETKEY = "secretKey";
    private static final String KEY_STANDBY_SHORTID = "standbyShortId";
    private static final String KEY_CREATIONTIME = "creationTime";
    private static final String KEY_SITE_STATE = "state";
    private static final String KEY_NODESADDR = "nodesAddr";
    private static final String KEY_NODESADDR6 = "nodesAddr6";
    
    public static final String CONFIG_KIND = "disasterRecoverySites";

    private String uuid;
    private URI vdc;
    private String name = "";
    private String vip = "";
    private String secretKey = "";
    private String description = "";
    private Map<String, String> hostIPv4AddressMap = new HashMap<String, String>();
    private Map<String, String> hostIPv6AddressMap = new HashMap<String, String>();
    private String standbyShortId = "";
    private long creationTime = 0;
    private SiteState state = SiteState.ACTIVE;
    
    public Site() {
    }
    
    public Site(Configuration config) {
        if (config != null) {
            fromConfiguration(config);
        }
    }
    
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public URI getVdc() {
        return vdc;
    }

    public void setVdc(URI vdc) {
        this.vdc = vdc;
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

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStandbyShortId() {
        return standbyShortId;
    }

    public void setStandbyShortId(String standbyShortId) {
        this.standbyShortId = standbyShortId;
    }
    
    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public SiteState getState() {
        return state;
    }

    public void setState(SiteState state) {
        this.state = state;
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
        if (obj instanceof Site)
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
        config.setKind(CONFIG_KIND);
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
        if (vdc != null) {
            config.setConfig(KEY_VDC, vdc.toString());
        }
        if (secretKey != null) {
            config.setConfig(KEY_SECRETKEY, this.secretKey);
        }
        if (standbyShortId != null) {
            config.setConfig(KEY_STANDBY_SHORTID, this.standbyShortId);
        }
        config.setConfig(KEY_CREATIONTIME, String.valueOf(creationTime));
        if (state != null) {
            config.setConfig(KEY_SITE_STATE, String.valueOf(state));
        }
        config.setConfig(KEY_NODESADDR, StringUtil.join(this.hostIPv4AddressMap.values(), ","));
        config.setConfig(KEY_NODESADDR6, StringUtil.join(this.hostIPv6AddressMap.values(), ","));
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (!config.getKind().equals(CONFIG_KIND)) {
            throw new IllegalArgumentException("Unexpected configuration kind for Site");
        }
        try {
            this.uuid = config.getId();
            this.name = config.getConfig(KEY_NAME);
            this.description = config.getConfig(KEY_DESCRIPTION);
            String s = config.getConfig(KEY_VDC);
            if (s != null) {
                this.vdc = new URI(s);
            }
            this.vip = config.getConfig(KEY_VIP);
            this.secretKey = config.getConfig(KEY_SECRETKEY);
            this.standbyShortId = config.getConfig(KEY_STANDBY_SHORTID);
            s = config.getConfig(KEY_CREATIONTIME);
            if (s != null) {
                this.creationTime = Long.valueOf(s);
            }
            s = config.getConfig(KEY_SITE_STATE);
            if (s != null) {
                state = SiteState.valueOf(config.getConfig(KEY_SITE_STATE));
            }
            String addrs = config.getConfig(KEY_NODESADDR);
            if (addrs != null) {
                int i = 1;
                for (String addr : addrs.split(",")) {
                    hostIPv4AddressMap.put(String.format("node%d", i++), addr);
                }
            }
            
            String addr6s = config.getConfig(KEY_NODESADDR6);
            if (addr6s != null) {
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
        builder.append(vdc);
        builder.append(", name=");
        builder.append(name);
        builder.append(", vip=");
        builder.append(vip);
        builder.append(", description=");
        builder.append(description);
        builder.append(", hostIPv4AddressMap=");
        builder.append(hostIPv4AddressMap);
        builder.append(", hostIPv6AddressMap=");
        builder.append(hostIPv6AddressMap);
        builder.append(", standbyShortId=");
        builder.append(standbyShortId);
        builder.append(", creationTime=");
        builder.append(creationTime);
        builder.append("]");
        return builder.toString();
    }
}