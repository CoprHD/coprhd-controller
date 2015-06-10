/*
 * Copyright 2015 EMC Corporation
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
@XmlRootElement(name = "host_create")
public class HostCreateParam extends HostParam {
    public HostCreateParam() {
        setDiscoverable(true);
    }

    /**
     * The host type.
     * 
     * @valid Windows
     * @valid HPUX
     * @valid Linux
     * @valid Esx
     * @valid SUNVCS
     * @valid Other
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
     * @valid example: hostname
     * @valid example: fqdn.hostname.com
     * @valid example: 10.12.100.200
     */
    @Override
    @XmlElement(name = "host_name", required = true)
    @Endpoint(type = Endpoint.EndpointType.HOST)
    @JsonProperty("host_name")
    public String getHostName() {
        return super.getHostName();
    }
    
    /** 
     * The user label for this host.
     * @valid example: host1
     */    
    @XmlElement(required = true)
    public String getName() {
        return super.getName();
    }
}
