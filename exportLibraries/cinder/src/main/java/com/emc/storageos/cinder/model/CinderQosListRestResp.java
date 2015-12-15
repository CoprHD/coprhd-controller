/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "qos_specs")
public class CinderQosListRestResp {
    private List<CinderQos> qos_specs;

    /**
     * List of snapshots that make up this entry. Used primarily to report to cinder.
     */

    @XmlElementRef
    public List<CinderQos> getQos_specs() {
        if (qos_specs == null) {
            qos_specs = new ArrayList<CinderQos>();
        }
        return qos_specs;
    }

    public void setQos_specs(List<CinderQos> lstqos) {
        this.qos_specs = lstqos;
    }

}
