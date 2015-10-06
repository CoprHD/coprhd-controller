package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name="qos_specs")
public class CinderQosListRestResp {
    private List<CinderQos> qos_specs;

    /**
     * List of snapshots that make up this entry.  Used primarily to report to cinder.  
     */

    @XmlElementRef
    public List<CinderQos> getQos_specs() {
        if (qos_specs== null) {
        	qos_specs = new ArrayList<CinderQos>();
        }
        return qos_specs;
    }

    public void setQos_specs(List<CinderQos> lstqos) {
        this.qos_specs= lstqos;
    }
       
}

