/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
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
     * 
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
