/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "nfs_acl")
public class NfsACLs implements Serializable {

    private static final long serialVersionUID = -5805098581764691677L;
    private List<NfsACL> nfsACLs;

    @XmlElementWrapper(name = "acl")
    @XmlElement(name = "ace")
    public List<NfsACL> getNfsACLs() {
        return nfsACLs;
    }

    public void setNfsACLs(List<NfsACL> nfsACLs) {
        this.nfsACLs = nfsACLs;
    }

}
