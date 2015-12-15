/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "availability")
public class CinderAvailabiltyZone {
    // public absoluteStats absolute = new absoluteStats();
    public String zoneName;
    public CinderAvailabiltyZone.state zoneState = new CinderAvailabiltyZone.state();

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class state {
        public boolean available;
    }
}
