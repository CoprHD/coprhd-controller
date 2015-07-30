/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Volume creation parameters
 */
@XmlRootElement(name = "volume_create")
public class VolumeCreate {

    private String name;
    private String size;
    private Integer count;
    private URI vpool;
    private URI varray;
    private URI project;
    private URI consistencyGroup;

    public VolumeCreate() {
    }

    public VolumeCreate(String name, String size, Integer count, URI vpool,
            URI varray, URI project, URI consistencyGroup) {
        this.name = name;
        this.size = size;
        this.count = count;
        this.vpool = vpool;
        this.varray = varray;
        this.project = project;
        this.consistencyGroup = consistencyGroup;
    }

    public VolumeCreate(String name, String size, Integer count, URI vpool,
            URI varray, URI project) {
        super();
        this.name = name;
        this.size = size;
        this.count = count;
        this.vpool = vpool;
        this.varray = varray;
        this.project = project;
    }

    /**
     * This parameter will allow for the creation of a source
     * consistency group. Once the source consistency group is
     * established, the snapshot operations for any volume in
     * the group would apply to all volumes in the group.
     * 
     * @valid example: Currently not supported for VMAX volumes.
     */
    @XmlElement(name = "consistency_group")
    public URI getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(URI consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    /**
     * Number of volumes to be created.
     * 
     * @valid none
     */
    @XmlElement(name = "count")
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * Name with which the volume is to be created.
     * 
     * @valid minimum of 2 characters
     * @valid maximum of 128 characters
     */
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The ViPR project to which the volume will belong.
     * 
     * @valid example: a valid URI of a ViPR project
     */
    @XmlElement(required = true)
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * Size of the volume (in GB) to be created.
     * 
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
     * The virtual array to which the volume will belong.
     * 
     * @valid example: a valid URI of a varray
     */
    @XmlElement(required = true)
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    /**
     * The virtual pool to which the volume will belong.
     * 
     * @valid example: a valid URI of a vpool
     */
    @XmlElement(required = true)
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }
}
