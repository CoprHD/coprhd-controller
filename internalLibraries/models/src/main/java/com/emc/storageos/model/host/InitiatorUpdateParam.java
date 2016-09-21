/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for host initiator creation.
 */
@XmlRootElement(name = "initiator_update")
public class InitiatorUpdateParam extends BaseInitiatorParam {

    private Boolean disassociatePairedInitiator;

    @XmlElement(name = "disassociate_paired_initiator")
    public Boolean getDiscoverable() {
        return disassociatePairedInitiator;
    }

    public void setDiscoverable(Boolean disassociatePairedInitiator) {
        this.disassociatePairedInitiator = disassociatePairedInitiator;
    }
}
