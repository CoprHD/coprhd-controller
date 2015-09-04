/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * This class holds the context for multiple fiber channel zoning operations that are in flight.
 * 
 * @author Watson
 *
 */
public class NetworkFCContext implements Serializable {

    boolean addingZones = true;
    ArrayList<NetworkFCZoneInfo> _zoneInfos = new ArrayList<NetworkFCZoneInfo>();

    public ArrayList<NetworkFCZoneInfo> getZoneInfos() {
        return _zoneInfos;
    }

    public void setZoneInfos(ArrayList<NetworkFCZoneInfo> zoneInfos) {
        this._zoneInfos = zoneInfos;
    }

    public boolean isAddingZones() {
        return addingZones;
    }

    public void setAddingZones(boolean addingZones) {
        this.addingZones = addingZones;
    }
}
