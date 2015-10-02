/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import javax.xml.bind.annotation.XmlElement;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

public class NamedRelatedBlockConsistencyGroupRep extends NamedRelatedResourceRep {

    private String deviceName;

    public NamedRelatedBlockConsistencyGroupRep() {
    }

    public NamedRelatedBlockConsistencyGroupRep(URI id, RestLinkRep selfLink, String name, String deviceName) {
        super(id, selfLink, name);
        this.deviceName = deviceName;
    }

    /**
     * The device name
     * 
     */
    @XmlElement(name = "device_name")
    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

}
