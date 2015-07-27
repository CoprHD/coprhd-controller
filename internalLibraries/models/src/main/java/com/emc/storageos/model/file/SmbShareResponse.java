/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "share")
public class SmbShareResponse extends FileSystemShareBase {

    private String mountPoint;
    private String path;
    
    public SmbShareResponse() {
        super();
    }

    public SmbShareResponse(String shareName, String description,
            String maxUsers, String permissionType, String permission,
            String mountPoint, String path) {
        super(shareName, description, maxUsers, permissionType, permission, path);
        this.mountPoint = mountPoint;
        this.path = path;
    }

    /**
     * The SMB mount point of the file system.
     * @valid String path
     */
    @XmlElement(name = "mount_point")
    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    /**
     * The SMB path of the file system or subdirectory
     * @valid String path
     */
    @XmlElement(name = "path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    
}
