/*
 * Copyright 2015 EMC Corporation
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
    private List<String> logNames;
    private Integer severity;
    private Integer expirInMin;
    private String scope;

    public SetLogLevelParam() {}
    
    public SetLogLevelParam(List<String> nodeIds, List<String> logNames,
            Integer severity, Integer expirInMin, String scope) {
        this.nodeIds = nodeIds;
        this.logNames = logNames;
        this.severity = severity;
        this.expirInMin = expirInMin;
        this.scope = scope;
    }
    /**
    *  Optional, A list of node ids to be updated. 
    *  All the nodes in the cluster will be updated by default
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
     * Following values are valid:
     *  @valid 0 (FATAL)
     *  @valid 1 (EMERG)
     *  @valid 2 (ALERT)
     *  @valid 3 (CRIT)
     *  @valid 4 (ERROR)
     *  @valid 5 (WARN)
     *  @valid 6 (NOTICE)
     *  @valid 7 (INFO)
     *  @valid 8 (DEBUG)
     *  @valid 9 (TRACE)
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
     * Optional, log level scope
     * @valid SCOPE_DEFAULT
     * @valid SCOPE_DEPENDENCY
     */
    @XmlElement(name = "scope", required = false)
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    } 
}
