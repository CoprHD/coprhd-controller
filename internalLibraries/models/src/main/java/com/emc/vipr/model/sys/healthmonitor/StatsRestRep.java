/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
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
