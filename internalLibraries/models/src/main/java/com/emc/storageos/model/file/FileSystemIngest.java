/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * FileSystem Ingest parameters
 * 
 * UnManaged FileSystem are FileSystems, which are present within ViPR Storage Systems, but not managed in ViPR.
 * Use GET /vdc/storage-systems/{id}/unmanaged/filesystems for the list of unmanaged filesystems ids on a Storage System basis.
 * Use GET /vdc/unmanaged/filesystems/bulk for the list of all unManaged filesystems ids.
 * Use POST /vdc/unmanaged/filesystems/bulk for unManaged FileSystem data.
 * FileSystem Ingest provides flexibility in bringing UnManaged FileSystems under ViPR management.
 * User must associate a Project, a Vpool, and a Varray to the file system for the file system to be managed by ViPR.
 * 
 * List of Supported VPools for each UnManagedFileSystem is being exposed using /vdc/unmanaged/filesystems/bulk.
 */
@XmlRootElement(name = "filesystem_ingest")
public class FileSystemIngest {

    private URI vpool;
    private URI varray;
    private URI project;
    private List<URI> unManagedFileSystems;

    public FileSystemIngest() {
    }

    public FileSystemIngest(URI vpool, URI varray, URI project,
            List<URI> unManagedFileSystems) {
        this.vpool = vpool;
        this.varray = varray;
        this.project = project;
        this.unManagedFileSystems = unManagedFileSystems;
    }

    /**
     * URI representing the virtual pool supporting the unmanaged file systems
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    /**
     * URI representing the virtual array supporting the unmanaged file systems
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    /**
     * URI representing the project
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * List of UnManaged FileSystem URIs.
     * Use GET /vdc/storage-systems/{id}/unmanaged/filesystems for the list of unmanaged FileSystem ids on a Storage System basis.
     * Use GET /vdc/unmanaged/filesystems/bulk for the list of all unManaged FileSystem ids.
     * 
     * @valid none
     */
    @XmlElement(name = "unmanaged_filesystem_list", required = true)
    public List<URI> getUnManagedFileSystems() {
        if (unManagedFileSystems == null) {
            unManagedFileSystems = new ArrayList<URI>();
        }
        return unManagedFileSystems;
    }

    public void setUnManagedFileSystems(List<URI> unManagedFileSystems) {
        this.unManagedFileSystems = unManagedFileSystems;
    }

}
