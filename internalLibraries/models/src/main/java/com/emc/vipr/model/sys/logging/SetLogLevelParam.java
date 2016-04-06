/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.logging;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "log_level_set")
public class SetLogLevelParam {

    private List<String> nodeIds;
    private List<String> nodeNames;
    private List<String> logNames;
    private Integer severity;
    private Integer expirInMin;
    private String scope;

    public SetLogLevelParam() {
    }

    public SetLogLevelParam(List<String> nodeIds, List<String> logNames,
            Integer severity, Integer expirInMin, String scope) {
        this.nodeIds = nodeIds;
        this.logNames = logNames;
        this.severity = severity;
        this.expirInMin = expirInMin;
        this.scope = scope;
    }

    /**
     * Optional, A list of node ids to be updated.
     * All the nodes in the cluster will be updated by default
     */
    @XmlElement(required = false, name = "node_id")
    public List<String> getNodeIds() {
        if (nodeIds == null) {
            nodeIds = new ArrayList<String>();
        }
        return nodeIds;
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds;
    }

    /**
     *  Optional, A list of node names to be updated.
     *  All the nodes in the cluster will be updated by default
     */
    @XmlElement(required = false, name = "node_name")
    public List<String> getNodeNames(){
        if (nodeNames == null) {
            nodeNames = new ArrayList<String>();
        }
        return nodeNames;
    }

    public void setNodeNames(List<String> nodeNames){
        this.nodeNames=nodeNames;
    }

    /**
     *  Optional, A list of service names to be updated with new log level. 
     *  All the services will be updated by default
     */
    @XmlElement(required = false, name = "log_name")
    public List<String> getLogNames() {
        if (logNames == null) {
            logNames = new ArrayList<String>();
        }
        return logNames;
    }

    public void setLogNames(List<String> logNames) {
        this.logNames = logNames;
    }

    /**
     * Required, An int indicating the new log level.
     * Valid values:
     *  FATAL (0)
     *  EMERG(1)
     *  ALERT(2)
     *  CRIT(3)
     *  ERROR(4)
     *  WARN(5)
     *  NOTICE(6)
     *  INFO(7)
     *  DEBUG(8)
     *  TRACE(9)
     *
     */
    @XmlElement(required = true)
    public Integer getSeverity() {
        return severity;
    }

    public void setSeverity(Integer severity) {
        this.severity = severity;
    }

    /**
     * Optional, Expiration time in minutes
     */
    @XmlElement(name = "expir_in_min", required = false)
    public Integer getExpirInMin() {
        return expirInMin;
    }

    public void setExpirInMin(Integer expirInMin) {
        this.expirInMin = expirInMin;
   }

    /**
     * Optional, log level scope.
     * Valid values:
     * 	SCOPE_DEFAULT
     * 	SCOPE_DEPENDENCY
     * 
     */
    @XmlElement(name = "scope", required = false)
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
