/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This is a list of the SAN zones returned from the NetworkSystem.
 */
@XmlRootElement(name = "san_zones")
public class SanZonesRestRep {
    private List<SanZoneRestRep> zones;

    /**
     * A list of San Zones. Each zone has a name and a list of zone members.
     * 
     */
    @XmlElement(name = "san_zone")
    public List<SanZoneRestRep> getZones() {
        if (zones == null) {
            zones = new ArrayList<SanZoneRestRep>();
        }
        return zones;
    }

    public void setZones(List<SanZoneRestRep> zones) {
        this.zones = zones;
    }

}
