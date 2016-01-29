/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to File VirtualPool creation
 */
@XmlRootElement(name = "file_vpool_create")
public class FileVirtualPoolParam extends VirtualPoolCommonParam {

    private FileVirtualPoolProtectionParam protection;

    public FileVirtualPoolParam() {
    }

    public FileVirtualPoolParam(FileVirtualPoolProtectionParam protection) {
        super();
        this.protection = protection;
    }

    /**
     * The protection settings for the virtual pool.
     * 
     */
    @XmlElement(name = "protection")
    public FileVirtualPoolProtectionParam getProtection() {
        return protection;
    }

    public void setProtection(FileVirtualPoolProtectionParam protection) {
        this.protection = protection;
    }

}
