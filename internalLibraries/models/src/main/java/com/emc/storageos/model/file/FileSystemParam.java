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
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

import com.emc.storageos.model.valid.Length;

/**
 * Attributes associated with a file system, specified
 * during file system creation.
 *
 */
@XmlRootElement(name = "filesystem_create")
public class FileSystemParam {

    private String label;
    private String size;
    private URI vpool;
    private URI varray;
    private String fsId;

    public FileSystemParam() {}
    
    public FileSystemParam(String label, String size, URI vpool, URI varray, String fsId) {
        this.label = label;
        this.size = size;
        this.vpool = vpool;
        this.varray = varray;
        this.fsId = fsId;
    }

    /** 
     * User provided name or label assigned to the 
     * file system.
     */
    @XmlElement(required = true,name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** 
     * Total capacity of the file system in Bytes.
     * @valid none
     */
    @XmlElement(required = true)
    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    /** 
     * URI representing the virtual pool supporting the file system.
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
     * URI representing the virtual array containing the file system.
     * @valid none
     */
    @XmlElement(name = "varray", required = true)
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    /**
     * User provided id for the file system
     * @valid none
     */
    @XmlElement(name="fs_id", required = false)
    public String getFsId() {
        return fsId;
    }

    public void setFsId(String fsId) {
        this.fsId = fsId;
    }
    
}
