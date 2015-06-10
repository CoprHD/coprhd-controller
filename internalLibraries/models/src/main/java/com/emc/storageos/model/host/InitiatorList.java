/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for getting a list of host initiators
 */
@XmlRootElement(name = "initiators")
public class InitiatorList {
    private List<NamedRelatedResourceRep> initiators;
    
    public InitiatorList() {}
    
    public InitiatorList(List<NamedRelatedResourceRep> initiators) {
        this.initiators = initiators;
    }

    /**
     * List of host initiators that exists in ViPR.
     * @valid none
     */
    @XmlElement(name = "initiator")
    public List<NamedRelatedResourceRep> getInitiators() {
        if (initiators == null) {
            initiators = new ArrayList<NamedRelatedResourceRep>();
        }
        return initiators;
    }

    public void setInitiators(List<NamedRelatedResourceRep> initiators) {
        this.initiators = initiators;
    }
}
