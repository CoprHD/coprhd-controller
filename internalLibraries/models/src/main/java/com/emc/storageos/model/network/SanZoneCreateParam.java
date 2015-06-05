/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="san_zones_create")
public class SanZoneCreateParam extends SanZones {
    //TODO - Should I change the element name to san_zone_create??
}
