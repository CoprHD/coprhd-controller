/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Param class to modify host luns HLU
 *
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ModifyHostLUNsParam extends ParamBase{
    private List<HostLunModifyParam> hostLunModifyList;

    public List<HostLunModifyParam> getHostLunModifyList() {
        return hostLunModifyList;
    }

    public void setHostLunModifyList(List<HostLunModifyParam> hostLunModifyList) {
        this.hostLunModifyList = hostLunModifyList;
    }

}
