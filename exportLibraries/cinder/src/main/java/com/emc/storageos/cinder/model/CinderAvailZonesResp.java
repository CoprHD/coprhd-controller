/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "availabilityZoneInfo")
public class CinderAvailZonesResp {
    private List<CinderAvailabiltyZone> availabilityZoneInfo;

    @XmlElementRef
    public List<CinderAvailabiltyZone> getAvailabilityZoneInfo() {
        if (availabilityZoneInfo == null) {
            availabilityZoneInfo = new ArrayList<CinderAvailabiltyZone>();
        }
        return availabilityZoneInfo;
    }

    public void setAvailabilityZoneInfo(List<CinderAvailabiltyZone> zones) {
        this.availabilityZoneInfo = zones;
    }
}
