/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for virtual machine paired initiator creation.
 */
@XmlRootElement(name = "paired_initiator_create")
public class PairedInitiatorCreateParam
{

    BaseInitiatorParam firstInitiator;

    BaseInitiatorParam secondInitiator;

    @XmlElement(name = "first_initiator", required = true)
    public BaseInitiatorParam getFirstInitiator() {
        return firstInitiator;
    }

    public void setFirstInitiator(BaseInitiatorParam firstInitiator) {
        this.firstInitiator = firstInitiator;
    }

    @XmlElement(name = "second_initiator", required = true)
    public BaseInitiatorParam getSecondInitiator() {
        return secondInitiator;
    }

    public void setSecondInitiator(BaseInitiatorParam secondInitiator) {
        this.secondInitiator = secondInitiator;
    }

}