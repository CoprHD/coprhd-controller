/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class NfsACL implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;
    /*
     * response data attributes.
     */

    private String fSMountPath;
    private String subDir;

    private List<NfsACE> nfsAces;

    @XmlElement(name = "mount_path")
    public String getFSMountPath() {
        return fSMountPath;
    }

    public void setFSMountPath(String fSMountPath) {
        this.fSMountPath = fSMountPath;
    }

    @XmlElement(name = "sub_dir")
    public String getSubDir() {
        return subDir;
    }

    public void setSubDir(String subDir) {
        this.subDir = subDir;
    }

    @XmlElement(name = "ace")
    public List<NfsACE> getNfsAces() {
        return nfsAces;
    }

    public void setNfsAces(List<NfsACE> nfsAces) {
        this.nfsAces = nfsAces;
    }



    public NfsACL(String path, List<NfsACE> nfsAces) {
        this.fSMountPath = path;
        this.nfsAces = nfsAces;
    }

    public NfsACL() {
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACL [");
        if (fSMountPath != null) {
            builder.append("fSMountPath=");
            builder.append(fSMountPath);
            builder.append(", ");
        }
        if (subDir != null) {
            builder.append("subDir=");
            builder.append(subDir);
            builder.append(", ");
        }
        if (nfsAces != null) {
            builder.append("nfsAces=");
            builder.append(nfsAces);
        }
        builder.append("]");
        return builder.toString();
    }

}