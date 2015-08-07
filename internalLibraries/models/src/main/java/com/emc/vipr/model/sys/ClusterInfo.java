/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RestLinkRep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@XmlRootElement(name = "cluster_info")
public class ClusterInfo {

    public final static String CLUSTER_URI = "/upgrade/cluster-state";

    private String currentState;
    private Map<String, NodeState> controlNodes;
    private Map<String, NodeState> extraNodes;
    private NodeState targetState;
    private List<String> newVersions;
    private List<String> removableVersions;
    private RestLinkRep selfLink;

    public ClusterInfo() {
    }

    public ClusterInfo(String currentState,
            Map<String, NodeState> controlNodes,
            Map<String, NodeState> extraNodes, NodeState targetState,
            List<String> newVersions, List<String> removableVersions,
            RestLinkRep selfLink) {
        this.currentState = currentState;
        this.controlNodes = controlNodes;
        this.extraNodes = extraNodes;
        this.targetState = targetState;
        this.newVersions = newVersions;
        this.removableVersions = removableVersions;
        this.selfLink = selfLink;
    }

    @XmlElement(name = "cluster_state")
    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String state) {
        this.currentState = state;
    }

    @XmlElementWrapper(name = "control_nodes")
    public Map<String, NodeState> getControlNodes() {
        return controlNodes;
    }

    public void setControlNodes(Map<String, NodeState> controlNodes) {
        this.controlNodes = controlNodes;
    }

    @XmlElementWrapper(name = "extra_nodes")
    public Map<String, NodeState> getExtraNodes() {
        return extraNodes;
    }

    public void setExtraNodes(Map<String, NodeState> extraNodes) {
        this.extraNodes = extraNodes;
    }

    @XmlElement(name = "target_state")
    public NodeState getTargetState() {
        return targetState;
    }

    public void setTargetState(NodeState targetState) {
        this.targetState = targetState;
    }

    @XmlElementWrapper(name = "new_versions")
    @XmlElement(name = "new_version")
    public List<String> getNewVersions() {
        return newVersions;
    }

    public void setNewVersions(List<String> newVersions) {
        this.newVersions = newVersions;
    }

    @XmlElementWrapper(name = "removable_versions")
    @XmlElement(name = "removable_version")
    public List<String> getRemovableVersions() {
        return removableVersions;
    }

    public void setRemovableVersions(List<String> removableVersions) {
        this.removableVersions = removableVersions;
    }

    @XmlElement(name = "link")
    public RestLinkRep getSelfLink() {
        return selfLink;
    }

    public void setSelfLink(RestLinkRep selfLink) {
        this.selfLink = selfLink;
    }

    public static enum ClusterState {
        UNKNOWN,
        STABLE,
        SYNCING,
        UPGRADING,
        UPGRADING_PREP_DB,
        UPGRADING_CONVERT_DB,
        UPGRADING_FAILED,
        DEGRADED,
        UPDATING,
        POWERINGOFF,
        INITIALIZING,
    }

    public static class NodeState {

        private ArrayList<String> available;
        private String current;
        private String configVersion;

        public NodeState() {
        }

        public NodeState(ArrayList<String> available, String current,
                String configVersion) {
            super();
            this.available = available;
            this.current = current;
            this.configVersion = configVersion;
        }

        @XmlElementWrapper(name = "available_versions")
        @XmlElement(name = "available_version")
        public ArrayList<String> getAvailable() {
            if (available == null) {
                available = new ArrayList<String>();
            }
            return available;
        }

        public void setAvailable(ArrayList<String> available) {
            this.available = available;
        }

        @XmlElement(name = "current_version")
        public String getCurrent() {
            return current;
        }

        public void setCurrent(String current) {
            this.current = current;
        }

        @XmlElement(name = "config_version")
        public String getConfigVersion() {
            return configVersion;
        }

        public void setConfigVersion(String configVersion) {
            this.configVersion = configVersion;
        }

    }
}
