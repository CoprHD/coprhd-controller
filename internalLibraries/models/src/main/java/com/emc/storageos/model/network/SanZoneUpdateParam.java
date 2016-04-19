/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class captures updating zone members and its name
 */
@XmlRootElement(name = "san_zone_update")
public class SanZoneUpdateParam {

    private String name; // original zone name
    private List<String> addMembers;
    private List<String> removeMembers;

    public SanZoneUpdateParam() {
    }

    /**
     * The zone name.
     *  Valid value:
     *      A name starting with an alpha character and consisting of alpha-numberic characters and underscores.
     */
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * A list of Zone members, each consisting of a WWPN address or alias
     * Valid value:
     *      A list of WWPN addresses (for example 10:00:00:00:00:00:00:01)
     */
    @XmlElementWrapper(name = "add")
    @XmlElement(name = "member")
    public List<String> getAddMembers() {
        if (addMembers == null) {
            addMembers = new ArrayList<String>();
        }
        return addMembers;
    }

    public void setAddMembers(List<String> addMembers) {
        this.addMembers = addMembers;
    }

    /**
     * A list of Zone members, each consisting of a WWPN address or alias
     * Valid value:
     *      A list of WWPN addresses (for example 10:00:00:00:00:00:00:01)
     */
    @XmlElementWrapper(name = "remove")
    @XmlElement(name = "member")
    public List<String> getRemoveMembers() {
        if (removeMembers == null) {
            removeMembers = new ArrayList<String>();
        }
        return removeMembers;
    }

    public void setRemoveMembers(List<String> removeMembers) {
        this.removeMembers = removeMembers;
    }

}
