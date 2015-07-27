/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
   instance of CISCO_Zoneset {
   Caption = null;
   Description = null;
   InstanceID = "3176_1_0";
   ConnectivityStatus = null;
   ElementName = "UIM_ZONESET_A_3176";
   Active = false;
};
 */

public class Zoneset extends BaseZoneInfo {
    private static final Logger _log = LoggerFactory.getLogger(Zoneset.class);

    List<Zone> zones;            
    String description;         // description of the Zoneset

    /**
     * marked transient because it cannot be serialized 
     */
    transient Object cimObjectPath;		// The CIM path to this object

    public Zoneset(String name) {
        super(name);
    }    
    
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public Object getCimObjectPath() {
		return cimObjectPath;
	}
	public void setCimObjectPath(Object cimObjectPath) {
		this.cimObjectPath = cimObjectPath;
	}

    public void setZones(List<Zone> zones) {
		this.zones = zones;
	}
    
	public List<Zone> getZones() {
	    if ( zones == null) {
	        zones = new ArrayList<Zone>();
	    }
	    return zones; 
	}
    
    public void print() {
        _log.info("zoneset: " + this.name + " " + (this.active ? "active" : "inactive"));
        for (Zone zone : getZones()) zone.print();
    }
}
