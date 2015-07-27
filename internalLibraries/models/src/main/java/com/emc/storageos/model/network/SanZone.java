/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * This represents a SAN Zone returned by the Network System.
 * Each zone has a name, and two or more members each consisting of a WWPN address.
 */
@XmlRootElement(name="san_zone")
public class SanZone {

    private String name;
    private List<String> members;

    public SanZone() {}
            
    public SanZone(String name, List<String> members) {
        this.name = name;
        this.members = members;
    }

    /** 
     * The zone name. 
     * @valid A name starting with an alpha character and consisting of alpha-numberic characters and underscores.
     */
    @XmlElement(required=true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElementWrapper(name = "members")
    /** 
     * A list of Zone members, each consisting of a WWPN address. 
     * @valid A list of WWPN addresses (for example 10:00:00:00:00:00:00:01)
     */
    @XmlElement(name = "member")
    public List<String> getMembers() {
        if (members == null) {
            members = new ArrayList<String>();
        }
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

}
