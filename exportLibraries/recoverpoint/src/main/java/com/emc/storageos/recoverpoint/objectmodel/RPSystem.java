/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.objectmodel;

import java.io.Serializable;
import java.util.Set;

import javax.xml.bind.annotation.*;

/**
 * Representation of a RecoverPoint System
 * 
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement
public class RPSystem implements Serializable {

    private static final long serialVersionUID = -555268916024617685L;
    private String _name;
    private Set<RPSite> _sites;

    @XmlElement
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
    }

    @XmlElement
    public Set<RPSite> getSites() {
        return _sites;
    }

    public void setSites(Set<RPSite> siteList) {
        _sites = siteList;
    }
}
