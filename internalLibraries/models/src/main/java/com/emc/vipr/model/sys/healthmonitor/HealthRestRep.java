/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents node and its services health
 */

@XmlRootElement(name = "health")
public class HealthRestRep {

    private List<NodeHealth> nodeHealthList;

    public HealthRestRep() {}
    
    public HealthRestRep(List<NodeHealth> nodeHealthList) {
        this.nodeHealthList = nodeHealthList;
    }

    @XmlElementWrapper(name = "node_health_list")
    @XmlElement(name = "node_health")
    public List<NodeHealth> getNodeHealthList() {
        if (nodeHealthList == null) {
            nodeHealthList = new ArrayList<NodeHealth>();
        }
        return nodeHealthList;
    }

    public void setNodeHealthList(List<NodeHealth> nodeHealthList) {
        this.nodeHealthList = nodeHealthList;
    }
}


