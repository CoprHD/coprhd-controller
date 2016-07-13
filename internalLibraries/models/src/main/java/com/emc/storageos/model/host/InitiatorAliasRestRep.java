/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * REST Response representing an Initiator Alias.
 */
@XmlRootElement(name = "alias")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class InitiatorAliasRestRep {
    private String serialNumber;
    private String initiatorAlias;

    public InitiatorAliasRestRep() {
    }

    public InitiatorAliasRestRep(String serialNumber, String initiatorAlias) {
        this.serialNumber = serialNumber;
        this.initiatorAlias = initiatorAlias;
    }

    /**
     * The serial number of the storage system.
     * 
     * @return The serial number.
     */
    @XmlElement(name = "serial_number")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /**
     * The corresponding initiator alias for the initiator.
     * 
     * @return The alias.
     */
    @XmlElement(name = "initiator_alias")
    public String getInitiatorAlias() {
        return initiatorAlias;
    }

    public void setInitiatorAlias(String initiatorAlias) {
        this.initiatorAlias = initiatorAlias;
    }

}
