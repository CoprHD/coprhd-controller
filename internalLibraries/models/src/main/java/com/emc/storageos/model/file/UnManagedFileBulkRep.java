/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response to a bulk query for the list of unmanaged file systems.
 * 
 */
@XmlRootElement(name = "bulk-unmanaged-filesystems")
public class UnManagedFileBulkRep extends BulkRestRep {
    private List<UnManagedFileSystemRestRep> unManagedFileSystems;

    /**
     * List of unmanaged file systems. UnManaged FileSystems are FileSystems that
     * are present within ViPR, but are not under ViPR management. ViPR
     * provides an ingest capability that enables users to bring the unmanaged
     * file systems under ViPR management.
     * 
     */
    @XmlElement(name = "unmanaged-filesystems")
    public List<UnManagedFileSystemRestRep> getUnManagedFileSystems() {
        if (unManagedFileSystems == null) {
            unManagedFileSystems = new ArrayList<UnManagedFileSystemRestRep>();
        }
        return unManagedFileSystems;
    }

    public void setUnManagedFileSystems(List<UnManagedFileSystemRestRep> unManagedFileSystems) {
        this.unManagedFileSystems = unManagedFileSystems;
    }

    public UnManagedFileBulkRep() {
    }

    public UnManagedFileBulkRep(List<UnManagedFileSystemRestRep> unManagedFileSystems) {
        this.unManagedFileSystems = unManagedFileSystems;
    }
}
