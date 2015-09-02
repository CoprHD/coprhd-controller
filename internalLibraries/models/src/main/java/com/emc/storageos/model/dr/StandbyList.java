/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "standbys")
public class StandbyList {

    private List<NamedRelatedResourceRep> standbys;

    public StandbyList() {
    }

    public StandbyList(List<NamedRelatedResourceRep> standbys) {
        this.standbys = standbys;
    }

    @XmlElement(name = "standby")
    public List<NamedRelatedResourceRep> getStandbys() {
        if (standbys == null) {
            standbys = new ArrayList<NamedRelatedResourceRep>();
        }
        return standbys;
    }

    public void setStandbys(List<NamedRelatedResourceRep> standbys) {
        this.standbys = standbys;
    }
}
