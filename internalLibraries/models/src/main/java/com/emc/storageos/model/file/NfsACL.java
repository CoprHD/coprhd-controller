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

    @XmlElement(name = "subDir")
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACL [");
        if (fSMountPath != null) {
            builder.append("mount_path=");
            builder.append(fSMountPath);
            builder.append("subDir=");
            builder.append(subDir);
            builder.append(", ");
        }

        builder.append("]");
        return builder.toString();
    }

    public NfsACL(String path, List<NfsACE> nfsAces) {
        this.fSMountPath = path;
        this.nfsAces = nfsAces;

    }

    public NfsACL() {
        // TODO Auto-generated constructor stub
    }

}
