/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class VNXeIscsiPortal {
    private String id;
    private String portalGroupTag;
    private String v6PrefixLength;
    private String ipAddress;
    private String ipProtocolVersion;
    private String netmask;
    private VNXeIscsiNode iscsiNode;
    private VNXeBase ethernetPort;
    
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getPortalGroupTag() {
        return portalGroupTag;
    }
    public void setPortalGroupTag(String portalGroupTag) {
        this.portalGroupTag = portalGroupTag;
    }
    public String getV6PrefixLength() {
        return v6PrefixLength;
    }
    public void setV6PrefixLength(String v6PrefixLength) {
        this.v6PrefixLength = v6PrefixLength;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    public String getIpProtocolVersion() {
        return ipProtocolVersion;
    }
    public void setIpProtocolVersion(String ipProtocolVersion) {
        this.ipProtocolVersion = ipProtocolVersion;
    }
    public String getNetmask() {
        return netmask;
    }
    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }
    public VNXeIscsiNode getIscsiNode() {
        return iscsiNode;
    }
    public void setIscsiNode(VNXeIscsiNode iscsiNode) {
        this.iscsiNode = iscsiNode;
    }
    public VNXeBase getEthernetPort() {
        return ethernetPort;
    }
    public void setEthenetPort(VNXeBase ethenetPort) {
        this.ethernetPort = ethenetPort;
    }
    

}
