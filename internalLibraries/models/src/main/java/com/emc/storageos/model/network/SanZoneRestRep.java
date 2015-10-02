/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This represents a SAN Zone returned by the Network System.
 * Each zone has a name, and two or more members each consisting of a WWPN address.
 */
@XmlRootElement(name = "san_zone")
public class SanZoneRestRep {

    private String name;
    private List<SanZoneMemberRestRep> members;

    public SanZoneRestRep() {
    }

    public SanZoneRestRep(String name, List<SanZoneMemberRestRep> members) {
        this.name = name;
        this.members = members;
    }

    /**
     * The zone name.
     * 
     */
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElementWrapper(name = "members")
    /** 
     * A list of Zone members, each consisting of a WWPN address. 
     */
    @XmlElement(name = "member")
    public List<SanZoneMemberRestRep> getMembers() {
        if (members == null) {
            members = new ArrayList<SanZoneMemberRestRep>();
        }
        return members;
    }

    public void setMembers(List<SanZoneMemberRestRep> members) {
        this.members = members;
    }

}
