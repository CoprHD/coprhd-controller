package com.emc.vipr.model.sys.recovery;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.lang.reflect.Array;
import java.util.ArrayList;

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
@XmlRootElement(name="recovery_precheck_status")
public class RecoveryPrecheckStatus {

    @XmlType(name="recoveryPrecheckStatus_type")
    public enum Status {
        ALL_GOOD,
        VAPP_IN_DR_OR_GEO,
        NODE_UNREACHABLE,
        CORRUPTED_NODE_COUNT_MORE_THAN_QUORUM,
        CORRUPTED_NODE_FOR_OTHER_REASON,
        RECOVERY_NEEDED
    }

    private Status status ;
    private ArrayList<String> unavailables ;
    private ArrayList<String> recoverables;
    /*public RecoveryPrecheckStatus (boolean status) {
        this.status = status;
    }*/

    @XmlElement(name="status")
    public  Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
    /*dbsvc available node list*/
    @XmlElementWrapper(name="unavailable_nodes")
    @XmlElement(name="unavailable_node")
    public ArrayList<String> getUnavailables() {
        if (unavailables == null) {
            unavailables = new ArrayList<String>();
        }
        return unavailables;
    }
    @XmlElementWrapper(name="recoverable_nodes")
    @XmlElement(name="recoverable_node")
    public ArrayList<String> getRecoverables() {
        if (recoverables == null) {
            recoverables = new ArrayList<>();
        }
        return recoverables;
    }

    public void setRecoverables (ArrayList<String> nodes) {
        this.recoverables = nodes;
    }

    public void setUnavailables (ArrayList<String> nodes) {
        this.unavailables = nodes;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("status:");
        sb.append(getStatus());
        return sb.toString();
    }
}
