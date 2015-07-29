/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk-initiators")
public class InitiatorBulkRep extends BulkRestRep {
    private List<InitiatorRestRep> initiators;

    /**
     * List of initiator objects that exists in ViPR.
     * 
     * @valid none
     */
    @XmlElement(name = "initiators")
    public List<InitiatorRestRep> getInitiators() {
        if (initiators == null) {
            initiators = new ArrayList<InitiatorRestRep>();
        }
        return initiators;
    }

    public void setInitiators(List<InitiatorRestRep> initiators) {
        this.initiators = initiators;
    }

    public InitiatorBulkRep() {
    }

    public InitiatorBulkRep(List<InitiatorRestRep> initiators) {
        this.initiators = initiators;
    }
}
