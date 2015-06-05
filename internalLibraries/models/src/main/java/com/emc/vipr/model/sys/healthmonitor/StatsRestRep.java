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
 * Represents node and its services statistics
 */
@XmlRootElement(name = "stats")
public class StatsRestRep {

    private List<NodeStats> nodeStatsList;

    @XmlElementWrapper(name = "node_stats_list")
    @XmlElement(name = "node_stats")
    public List<NodeStats> getNodeStatsList() {
        if (nodeStatsList == null) {
            nodeStatsList = new ArrayList<NodeStats>();
        }
        return nodeStatsList;
    }

    public void setNodeStatsList(List<NodeStats> nodeStatsList) {
        this.nodeStatsList = nodeStatsList;
    }
}
