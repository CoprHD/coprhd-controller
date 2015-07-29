/*
 * Copyright (c) 2008-2013 EMC Corporation
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
@XmlRootElement(name = "san_zones_update")
public class SanZoneUpdateParams {

    private List<SanZoneUpdateParam> updateZones;

    public SanZoneUpdateParams() {
    }

    /**
     * A list of updating San Zones. Each zone has a name and a list of zone members.
     * 
     * @valid none
     */
    @XmlElement(name = "san_zone_update")
    public List<SanZoneUpdateParam> getUpdateZones() {
        if (updateZones == null) {
            updateZones = new ArrayList<SanZoneUpdateParam>();
        }
        return updateZones;
    }

    public void setUpdateZones(List<SanZoneUpdateParam> updateZones) {
        this.updateZones = updateZones;
    }

}
