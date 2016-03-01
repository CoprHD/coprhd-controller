/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * List of file systems. Used by file system ingest method to
 * build the list of unmanaged file systems that are now under
 * ViPR management.
 * 
 */
@XmlRootElement(name = "filesystems")
public class NamedFileSystemList {

    private List<NamedRelatedResourceRep> filesystems;

    public NamedFileSystemList() {
    }

    public NamedFileSystemList(List<NamedRelatedResourceRep> filesystems) {
        this.filesystems = filesystems;
    }

    /**
     * List of file systems.
     * 
     */
    @XmlElement(name = "filesystem")
    public List<NamedRelatedResourceRep> getFilesystems() {
        if (filesystems == null) {
            filesystems = new ArrayList<NamedRelatedResourceRep>();
        }
        return filesystems;
    }

    public void setFilesystems(List<NamedRelatedResourceRep> filesystems) {
        this.filesystems = filesystems;
    }

}
