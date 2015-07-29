/*
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

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class NfsShareModifyForShareParam extends ParamBase {
    private String description;
    private int defaultAccessLevel;
    private List<VNXeBase> noAccessHosts;
    private List<VNXeBase> readOnlyHosts;
    private List<VNXeBase> readWriteHosts;
    private List<VNXeBase> rootAccessHosts;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDefaultAccessLevel() {
        return defaultAccessLevel;
    }

    public void setDefaultAccessLevel(int defaultAccessLevel) {
        this.defaultAccessLevel = defaultAccessLevel;
    }

    public List<VNXeBase> getNoAccessHosts() {
        return noAccessHosts;
    }

    public void setNoAccessHosts(List<VNXeBase> noAccessHosts) {
        this.noAccessHosts = noAccessHosts;
    }

    public List<VNXeBase> getReadOnlyHosts() {
        return readOnlyHosts;
    }

    public void setReadOnlyHosts(List<VNXeBase> readOnlyHosts) {
        this.readOnlyHosts = readOnlyHosts;
    }

    public List<VNXeBase> getReadWriteHosts() {
        return readWriteHosts;
    }

    public void setReadWriteHosts(List<VNXeBase> readWriteHosts) {
        this.readWriteHosts = readWriteHosts;
    }

    public List<VNXeBase> getRootAccessHosts() {
        return rootAccessHosts;
    }

    public void setRootAccessHosts(List<VNXeBase> rootAccessHosts) {
        this.rootAccessHosts = rootAccessHosts;
    }

}
