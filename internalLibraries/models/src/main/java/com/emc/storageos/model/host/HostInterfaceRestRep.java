/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * Super class of host interfaces.
 * 
 * @see IpInterfaceRestRep
 * @see com.emc.storageos.model.host.InitiatorRestRep
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
     * @valid none
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
     * @valid none
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
     * 
     * @valid UNREGISTERED
     * @valid REGISTERED
     */
    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }
}
