/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.io.Serializable;

public class BaseZoneInfo implements Serializable {

    String name;
    String instanceID;
    Boolean active = false;
    Boolean existingZone = false;

    public BaseZoneInfo(String name) {
        this.name = name;
    }
    
    public String getInstanceID() {
		return instanceID;
	}
	public void setInstanceID(String instanceID) {
		this.instanceID = instanceID;
	}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active != null && active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }   

    public Boolean getExistingZone() {
        return existingZone;
    }

    public void setExistingZone(Boolean existingZone) {
        this.existingZone = existingZone;
    }
}
