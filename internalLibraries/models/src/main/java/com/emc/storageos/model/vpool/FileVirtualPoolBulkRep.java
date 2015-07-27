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

import com.emc.storageos.model.BulkRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * List of all file system virtual pools, returned as a response
 * to a REST request.
 *
 */
@XmlRootElement(name = "bulk_file_vpools")
public class FileVirtualPoolBulkRep extends BulkRestRep {
    private List<FileVirtualPoolRestRep> virtualPools;

    /** 
     * List of all virtual pools of File System type.
     * @valid none
     */
    @XmlElement(name = "file_vpool")
    @JsonProperty("file_vpool")
    public List<FileVirtualPoolRestRep> getVirtualPools() {
        if (virtualPools == null) {
            virtualPools = new ArrayList<FileVirtualPoolRestRep>();
        }
        return virtualPools;
    }

    public void setVirtualPools(List<FileVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }

    public FileVirtualPoolBulkRep() {
    }

    public FileVirtualPoolBulkRep(List<FileVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }
}
