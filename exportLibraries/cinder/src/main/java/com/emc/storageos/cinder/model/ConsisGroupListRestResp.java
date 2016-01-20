/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "snapshots")
public class ConsisGroupListRestResp {
    private List<ConsistencyGroupDetail> consistencyGroups;

    /**
     * List of snapshots that make up this entry. Used primarily to report to cinder.
     */
    @XmlElement
    public List<ConsistencyGroupDetail> getConsistencyGroups() {
        if (consistencyGroups == null) {
            consistencyGroups = new ArrayList<ConsistencyGroupDetail>();
        }
        return consistencyGroups;
    }

    public void setConsistencyGroups(List<ConsistencyGroupDetail> consistencyGroups) {
        this.consistencyGroups = consistencyGroups;
    }

}
