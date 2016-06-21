/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Endpoint;

/**
 * Request POST parameter for host creation.
 */
@XmlRootElement(name = "control_station_create")
public class ControlStationCreateParam extends ControlStationParam {
    public ControlStationCreateParam() {
        setDiscoverable(true);
    }

    /**
     * The host type.
     * Valid values:
     * HMC
     * Other
     */
    @Override
    @XmlElement(required = true)
    public String getType() {
        return super.getType();
    }

    /**
     * The short or fully qualified host name or IP address of the host
     * management interface.
     * 
     */
    @Override
    @XmlElement(name = "control_station_name", required = true)
    @Endpoint(type = Endpoint.EndpointType.HOST)
    @JsonProperty("control_station_name")
    public String getControlStationName() {
        return super.getControlStationName();
    }

    /**
     * The user label for this host.
     * 
     */
    @XmlElement(required = true)
    public String getName() {
        return super.getName();
    }
}
