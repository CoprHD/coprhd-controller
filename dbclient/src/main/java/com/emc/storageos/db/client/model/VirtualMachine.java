/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * A compute virtual machine to which a volume or file system can be exported.
 * 
 */
@Cf("VM")
public class VirtualMachine extends Host {

    @Name("vmName")
    @AlternateId("AltIdIndex")
    public String getVmName() {
        return super.getHostName();
    }

    public void setVmName(String vm) {
        super.setHostName(vm);
        setChanged("vmName");
    }

}