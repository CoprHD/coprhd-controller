/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file system snapshot,
 * provided by the user during snapshot creation.
 *
 */
@XmlRootElement(name = "filesystem_snapshot_create")
public class FileSystemSnapshotParam {
    
    private String label;

    public FileSystemSnapshotParam() {}
    
    public FileSystemSnapshotParam(String label) {
        this.label = label;
    }

    /**
     * User provided name/label for the snapshot.
     * @valid none
     */
    @XmlElement(required = true, name = "name")
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
    
}
