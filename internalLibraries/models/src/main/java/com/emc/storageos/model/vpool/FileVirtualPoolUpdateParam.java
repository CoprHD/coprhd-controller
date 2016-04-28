/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to File VirtualPool update.
 */
@XmlRootElement(name = "file_vpool_update")
public class FileVirtualPoolUpdateParam extends VirtualPoolUpdateParam {

    private FileVirtualPoolProtectionUpdateParam protection;
    private Boolean retention;

    public FileVirtualPoolUpdateParam() {
    }

    public FileVirtualPoolUpdateParam(FileVirtualPoolProtectionUpdateParam protection) {
        super();
        this.protection = protection;
    }

    /**
     * The new protection settings for the virtual pool.
     * 
     */
    @XmlElement(name = "protection")
    public FileVirtualPoolProtectionUpdateParam getProtection() {
        return protection;
    }

    public void setProtection(FileVirtualPoolProtectionUpdateParam protection) {
        this.protection = protection;
    }

    @XmlElement(name = "long_term_retention")
    public Boolean getLongTermRetention() {
        return retention;
    }

    public void setLongTermRetention(Boolean retention) {
        this.retention = retention;
    }

}
