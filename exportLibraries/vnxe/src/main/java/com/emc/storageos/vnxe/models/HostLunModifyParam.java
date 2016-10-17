/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

/**
 * Class to modify host Lun HLU
 *
 */
public class HostLunModifyParam {
    private VNXeBase hostLUN;
    private Integer hlu;

    public VNXeBase getHostLUN() {
        return hostLUN;
    }

    public void setHostLun(VNXeBase hostLUN) {
        this.hostLUN = hostLUN;
    }

    public Integer getHlu() {
        return hlu;
    }

    public void setHlu(Integer hlu) {
        this.hlu = hlu;
    }

}
