package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "snapshots")
public class ConsisGroupListRestResp {
    private List<ConsistencyGroupDetail> consistencyGroups;

    /**
     * List of snapshots that make up this entry.  Used primarily to report to cinder.  
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

