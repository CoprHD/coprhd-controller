/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
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
    private int softLimit;
    private int softGrace;
    private int notificationLimit;

    public FileSystemParam() {
    }

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
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Total capacity of the file system in Bytes.
     * 
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
     * 
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
     * 
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
     * 
     */
    @XmlElement(name = "fs_id", required = false)
    public String getFsId() {
        return fsId;
    }

    public void setFsId(String fsId) {
        this.fsId = fsId;
    }

    @XmlElement(name="soft_limit", required=false)
    public int getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(int softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name="soft_grace", required=false)
    public int getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(int softGrace) {
        this.softGrace = softGrace;
    }
    @XmlElement(name="notification_limit", required=false)
    public int getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(int notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

}
