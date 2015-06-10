/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public FileVirtualPoolRestRep() {}
            
    public FileVirtualPoolRestRep(FileVirtualPoolProtectionParam protection) {
        super();
        this.protection = protection;
    }

    /**
     * Not currently used
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

