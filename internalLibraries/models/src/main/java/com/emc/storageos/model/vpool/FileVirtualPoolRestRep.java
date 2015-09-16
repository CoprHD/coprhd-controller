/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Virtual pool of File System type.
 * 
 */
@XmlRootElement(name = "file_vpool")
public class FileVirtualPoolRestRep extends VirtualPoolCommonRestRep {

    private FileVirtualPoolProtectionParam protection;
    private Boolean longTermRetention;

    public FileVirtualPoolRestRep() {
    }

    public FileVirtualPoolRestRep(FileVirtualPoolProtectionParam protection) {
        super();
        this.protection = protection;
    }

    /**
     * Not currently used
     * 
     * @valid none
     */
    @XmlElement(name = "protection")
    public FileVirtualPoolProtectionParam getProtection() {
        return protection;
    }

    public void setProtection(FileVirtualPoolProtectionParam protection) {
        this.protection = protection;
    }

    /**
     * Not currently used
     * 
     * @valid none
     */
    @XmlElement(name = "long_term_retention")
    public Boolean getLongTermRetention() {
        return longTermRetention;
    }

    public void setLongTermRetention(Boolean longTermRetention) {
        this.longTermRetention = longTermRetention;
    }

}
