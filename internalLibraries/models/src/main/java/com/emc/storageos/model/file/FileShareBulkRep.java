/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_filesystems")
public class FileShareBulkRep extends BulkRestRep {
    private List<FileShareRestRep> fileShares;

    /**
     * The list of file shares, returned as response to bulk
     * queries.
     * 
     */
    @XmlElement(name = "filesystem")
    public List<FileShareRestRep> getFileShares() {
        if (fileShares == null) {
            fileShares = new ArrayList<FileShareRestRep>();
        }
        return fileShares;
    }

    public void setFileShares(List<FileShareRestRep> fileShares) {
        this.fileShares = fileShares;
    }

    public FileShareBulkRep() {
    }

    public FileShareBulkRep(List<FileShareRestRep> fileShares) {
        this.fileShares = fileShares;
    }
}
