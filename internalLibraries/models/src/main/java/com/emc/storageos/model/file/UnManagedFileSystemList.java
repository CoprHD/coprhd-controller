/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * List of unmanaged file systems. UnManaged FileSystem are FileSystems that
 * are present within ViPR, but are not under ViPR management.
 * 
 */
@XmlRootElement(name = "unmanaged_filesystems")
public class UnManagedFileSystemList {

    private List<RelatedResourceRep> unManagedFileSystem;

    private List<NamedRelatedResourceRep> namedUnManagedFileSystem;

    public UnManagedFileSystemList() {
    }

    public UnManagedFileSystemList(List<RelatedResourceRep> unManagedFileSystem) {
        this.unManagedFileSystem = unManagedFileSystem;
    }

    /**
     * List of unmanaged file systems.
     * 
     */
    @XmlElement(name = "unmanaged_filesystem")
    public List<RelatedResourceRep> getUnManagedFileSystem() {
        if (unManagedFileSystem == null) {
            unManagedFileSystem = new ArrayList<RelatedResourceRep>();
        }
        return unManagedFileSystem;
    }

    public void setUnManagedFileSystem(List<RelatedResourceRep> unManagedFileSystem) {
        this.unManagedFileSystem = unManagedFileSystem;
    }

    /**
     * The list of unmanaged FileSystems with name which are available in a storage system.
     * Used primarily to ingest volumes into ViPR.
     * 
     */
    @XmlElement(name = "named_unmanaged_filesystem")
    public List<NamedRelatedResourceRep> getNamedUnManagedFileSystem() {
        if (namedUnManagedFileSystem == null) {
            namedUnManagedFileSystem = new ArrayList<NamedRelatedResourceRep>();
        }
        return namedUnManagedFileSystem;
    }

    public void setNamedUnManagedFileSystem(List<NamedRelatedResourceRep> namedUnManagedFileSystem) {
        this.namedUnManagedFileSystem = namedUnManagedFileSystem;
    }

}
