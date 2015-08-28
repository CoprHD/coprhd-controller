/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DataObjectRestRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class FileObjectRestRep extends DataObjectRestRep {
    private String mountPath;

    @XmlElement(name = "mount_path")
    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    // TODO: These need to be re-implemented. These are internal structures and
    // Do not get properly converted to XML/JSON anyway.

    // @XmlElement(name = "fs_exports")
    // public FSExportMap getFsExports() {
    // return getData().getFsExports();
    // }
    // public void setFsExports(FSExportMap fsExportMap) {
    // getData().setFsExports(fsExportMap);
    // }
    //
    // @XmlElement(name = "smb_file_shares")
    // public SMBShareMap getSMBFileShares() {
    // return getData().getSMBFileShares();
    // }
    //
    // public void setSMBFileShares(SMBShareMap smbShareMap) {
    // getData().setSMBFileShares(smbShareMap);
    // }
}
