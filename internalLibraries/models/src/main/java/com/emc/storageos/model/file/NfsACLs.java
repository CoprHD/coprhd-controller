/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "nfs_acls")
public class NfsACLs implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;

    private List<NfsACE> nfsAces;

    public NfsACLs() {
    }

    @XmlElementWrapper(name = "acl")
    @XmlElement(name = "ace")
    public List<NfsACE> getNfsAces() {
        return nfsAces;
    }

    public void setNfsAces(List<NfsACE> nfsAces) {
        this.nfsAces = nfsAces;
    }

    public void addACE(NfsACE ace) {
        if (this.nfsAces == null) {
            this.nfsAces = new ArrayList<NfsACE>();
        }
        this.nfsAces.add(ace);
    }

    @Override
    public String toString() {
        return "NfsACL [" + nfsAces + "]";
    }

}
