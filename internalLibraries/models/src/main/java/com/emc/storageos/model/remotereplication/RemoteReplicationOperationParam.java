/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "remote_replication_operation_param")
public class RemoteReplicationOperationParam {

    public enum OperationContext {
        RR_SET,
        RR_GROUP,
        RR_PAIR,
        RR_GROUP_CG,
        RR_SET_CG
    }

    private String operationContext;

    private List<URI> ids;

    private String newReplicationMode;

    public RemoteReplicationOperationParam() {
    }

    public RemoteReplicationOperationParam(String operationContext, List<URI> ids) {
        this.ids = ids;
        this.operationContext = operationContext;
    }

    public RemoteReplicationOperationParam(String operationContext, List<URI> ids, String newReplicationMode) {
        this.operationContext = operationContext;
        this.ids = ids;
        this.newReplicationMode = newReplicationMode;
    }

    @XmlElement(name = "operation_context")
    public String getOperationContext() {
        return operationContext;
    }

    public void setOperationContext(String operationContext) {
        this.operationContext = operationContext;
    }

    @XmlElementWrapper
    /**
     * The list of remote replication pairs URIs
     *
     */
    @XmlElement(name = "id")
    public List<URI> getIds() {
        if (ids == null) {
            ids = new ArrayList<URI>();
        }
        return ids;
    }

    public void setIds(List<URI> ids) {
        this.ids = ids;
    }

    @XmlElement(name = "replication_mode")
    public String getNewReplicationMode() {
        return newReplicationMode;
    }

    public void setNewReplicationMode(String newReplicationMode) {
        this.newReplicationMode = newReplicationMode;
    }
}
