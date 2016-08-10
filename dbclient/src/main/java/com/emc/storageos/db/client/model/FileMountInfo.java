/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Base data object for file system mount and snapshot mount
 */
@Cf("FileMountInfo")
public class FileMountInfo extends DataObject {

    private URI hostId;
    private URI fileSystemId;
    private String mountPath;
    private String subDirectory;
    private String securityType;
    private String fsType;

    @RelationIndex(cf = "RelationIndex", type = Host.class)
    @Name("hostId")
    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
        setChanged("hostId");
    }

    @RelationIndex(cf = "RelationIndex", type = FileShare.class)
    @Name("fileSystemId")
    public URI getFsId() {
        return fileSystemId;
    }

    public void setFsId(URI fsId) {
        this.fileSystemId = fsId;
        setChanged("fileSystemId");
    }

    @Name("mountPath")
    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
        setChanged("mountPath");
    }

    @Name("subDirectory")
    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
        setChanged("subDirectory");
    }

    @Name("securityType")
    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
        setChanged("securityType");
    }

    @Name("fsType")
    public String getFsType() {
        return fsType;
    }

    public void setFsType(String fsType) {
        this.fsType = fsType;
        setChanged("fsType");
    }

}
