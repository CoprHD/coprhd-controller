/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for host initiator creation.
 */
@XmlRootElement(name = "initiator_create")
public class InitiatorCreateParam extends BaseInitiatorParam {

    private URI associatedInitiator;

    /**
     * The URI of the associated initiator
     */
    @XmlElement(name = "associated_initiator", required = false)
    public URI getAssociatedInitiator() {
        return associatedInitiator;
    }

    public void setAssociatedInitiator(URI associatedInitiator) {
        this.associatedInitiator = associatedInitiator;
    }

}
