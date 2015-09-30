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
     * Payload attributes
     */

    private String fSMountPath;

    private List<NfsACE> nfsAces;

    @XmlElement(name = "mount_path")
    public String getfSMountPath() {
        return fSMountPath;
    }

    public void setfSMountPath(String fSMountPath) {
        this.fSMountPath = fSMountPath;
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
