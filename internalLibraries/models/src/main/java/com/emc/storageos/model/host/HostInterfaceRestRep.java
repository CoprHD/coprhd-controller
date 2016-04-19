/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * Super class of host interfaces.
 * 
 * Please refer IpInterfaceRestRep
 * Please refer com.emc.storageos.model.host.InitiatorRestRep
 * @author elalih
 * 
 */
public abstract class HostInterfaceRestRep extends DataObjectRestRep {
    private RelatedResourceRep host;
    private String protocol;
    private String registrationStatus;

    public HostInterfaceRestRep() {
    }

    public HostInterfaceRestRep(RelatedResourceRep host, String protocol) {
        this.host = host;
        this.protocol = protocol;
    }

    /**
     * The host where the interface belongs.
     * 
     */
    @XmlElement(name = "host")
    public RelatedResourceRep getHost() {
        return host;
    }

    public void setHost(RelatedResourceRep host) {
        this.host = host;
    }

    /**
     * The host interface protocol.
     * 
     */
    @XmlElement(name = "protocol")
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The host interface registration status.
     * Only registered hosts can be used for provisioning operations.
     * Valid values:
     *   UNREGISTERED
     *   REGISTERED
     */
    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }
}
