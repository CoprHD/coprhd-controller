/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.driver.ibmsvcdriver.api;

public class IBMSVCFabricConnectivity {

    private String remoteWWPN;

    private String remoteNportId;

    private String id;

    private String nodeName;

    private String localWWPN;

    private String localPort;

    private String localNportId;

    private String state;

    private String name;

    private String clusterName;

    private String type;

    public String getRemoteWWPN() {
        return remoteWWPN;
    }

    public void setRemoteWWPN(String remoteWWPN) {
        this.remoteWWPN = remoteWWPN;
    }

    public String getRemoteNportId() {
        return remoteNportId;
    }

    public void setRemoteNportId(String remoteNportId) {
        this.remoteNportId = remoteNportId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getLocalWWPN() {
        return localWWPN;
    }

    public void setLocalWWPN(String localWWPN) {
        this.localWWPN = localWWPN;
    }

    public String getLocalPort() {
        return localPort;
    }

    public void setLocalPort(String localPort) {
        this.localPort = localPort;
    }

    public String getLocalNportId() {
        return localNportId;
    }

    public void setLocalNportId(String localNportId) {
        this.localNportId = localNportId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }



}
