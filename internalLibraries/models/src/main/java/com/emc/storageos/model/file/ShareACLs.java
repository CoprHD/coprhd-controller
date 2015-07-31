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

@XmlRootElement(name = "share_acl")
public class ShareACLs implements Serializable {

    private static final long serialVersionUID = -4590555905523347134L;

    private List<ShareACL> shareACLs;

    @XmlElementWrapper(name = "acl")
    @XmlElement(name = "ace")
    public List<ShareACL> getShareACLs() {
        return shareACLs;
    }

    public void setShareACLs(List<ShareACL> shareACLs) {
        this.shareACLs = shareACLs;
    }

}
