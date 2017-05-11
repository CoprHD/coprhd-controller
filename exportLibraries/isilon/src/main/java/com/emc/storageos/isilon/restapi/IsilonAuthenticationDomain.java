/*
 * Copyright (c) 2017 Dell-EMC
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

/**
 * @author sanjes
 *
 */
public class IsilonAuthenticationDomain {
    private String id;
    private String name;
    private String hostname;
    private String netbios_domain;
    private String primary_domain;
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getHostname() {
        return hostname;
    }
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    public String getNetbios_domain() {
        return netbios_domain;
    }
    public void setNetbios_domain(String netbios_domain) {
        this.netbios_domain = netbios_domain;
    }
    public String getPrimary_domain() {
        return primary_domain;
    }
    public void setPrimary_domain(String primary_domain) {
        this.primary_domain = primary_domain;
    }
    @Override
    public String toString() {
        return "IsilonAuthenticationDomain [name=" + name + ", hostname="
                + hostname + ", netbios_domain=" + netbios_domain + ", primary_domain="
                + primary_domain + "]";
    }

}
