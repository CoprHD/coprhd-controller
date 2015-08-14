/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * SDS attributes
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleIOSDS {
    private String id;
    private List<IP> ipList;
    private String protectionDomainId;
    private String name;
    private String sdsState;
    private String port;
    
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public List<IP> getIpList() {
        return ipList;
    }
    public void setIpList(List<IP> ipList) {
        this.ipList = ipList;
    }
    public String getProtectionDomainId() {
        return protectionDomainId;
    }
    public void setProtectionDomainId(String protectionDomainId) {
        this.protectionDomainId = protectionDomainId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getSdsState() {
        return sdsState;
    }
    public void setSdsState(String sdsState) {
        this.sdsState = sdsState;
    }
    public String getPort() {
        return port;
    }
    public void setPort(String port) {
        this.port = port;
    }
    
    public class IP {
        private String role;
        private String ip;
        public String getRole() {
            return role;
        }
        public void setRole(String role) {
            this.role = role;
        }
        public String getIp() {
            return ip;
        }
        public void setIp(String ip) {
            this.ip = ip;
        }
        
        
    }
    
}
