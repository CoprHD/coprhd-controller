/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a list of the SAN zones returned from the NetworkSystem.
 */
@XmlRootElement(name="san_zones")
public class SanZones {

    private List<SanZone> zones;

    public SanZones() {}
    
    public SanZones(List<SanZone> zones) {
        this.zones = zones;
    }

    /**
     * A list of San Zones. Each zone has a name and a list of zone members.
     * @valid none
     */
    @XmlElement(name="san_zone")
    public List<SanZone> getZones() {
        if (zones == null) {
            zones = new ArrayList<SanZone>();
        }
        return zones;
    }

    public void setZones(List<SanZone> zones) {
        this.zones = zones;
    }
    
}
