/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IvrZoneset extends BaseZoneInfo {
    private static final Logger _log = LoggerFactory.getLogger(IvrZoneset.class);

    private List<IvrZone> zones;

    public IvrZoneset(String name) {
        super(name);
    }

    public void setZones(List<IvrZone> zones) {
        this.zones = zones;
    }

    public List<IvrZone> getZones() {
        if (zones == null) {
            zones = new ArrayList<IvrZone>();
        }
        return zones;
    }

    public void print() {
        _log.info("zoneset: " + this.name + " " + (this.active ? "active" : "inactive"));
        for (IvrZone zone : zones) {
            zone.print();
        }
    }

    /**
     * Verify if the zone member is a member of the ivr zoneset
     * 
     * @param zoneMember
     * @return
     */
    public boolean contains(IvrZone zone) {

        boolean contained = false;
        for (IvrZone ivrZone : getZones()) {
            if (ivrZone.equals(zone)) {
                contained = true;
                break;
            }
        }

        return contained;
    }

}
