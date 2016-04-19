/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Whether or not the file system should be forcibly
 * deleted. If set to yes, file system will be
 * deleted, even if there are valid underlying objects,
 * such as exports, snapshots, etc. This is currently
 * valid only for vnxfiles. Other file systems are
 * always force deleted.
 * 
 */
@XmlRootElement(name = "filesystem_deactivate")
public class FileSystemDeleteParam {
    private static final String DELETE_TYPE = "FULL";

    private boolean forceDelete;
    private String deleteType = DELETE_TYPE;

    public FileSystemDeleteParam() {
    }

    public FileSystemDeleteParam(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    public FileSystemDeleteParam(boolean forceDelete, String deleteType) {
        this.deleteType = deleteType;
        this.forceDelete = forceDelete;
    }

    @XmlElement(name = "forceDelete")
    public boolean getForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    @XmlElement(name = "delete_type")
    public String getDeleteType() {
        return deleteType;
    }

    public void setDeleteType(String deleteType) {
        this.deleteType = deleteType;
    }

}
