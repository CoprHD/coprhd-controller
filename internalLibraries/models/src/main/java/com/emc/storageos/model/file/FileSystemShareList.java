/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "smb_shares")
public class FileSystemShareList {

    private List<SmbShareResponse> shareList;

    public FileSystemShareList() {
    }

    public FileSystemShareList(List<SmbShareResponse> shareList) {
        this.shareList = shareList;
    }

    /**
     * List of file shares for a file system.
     * 
     */
    @XmlElement(name = "smb_share")
    public List<SmbShareResponse> getShareList() {
        if (shareList == null) {
            shareList = new ArrayList<SmbShareResponse>();
        }
        return shareList;
    }

    public void setShareList(List<SmbShareResponse> shareList) {
        this.shareList = shareList;
    }

}
