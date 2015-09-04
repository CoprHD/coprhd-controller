/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.objectmodel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConsistencyGroupUID;

/**
 * A RecoverPoint Consistency Group object. Child of an RPSystem.
 * 
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement
public class RPConsistencyGroup {
    private String _name;
    private ConsistencyGroupUID _cgUID;
    private ClusterUID _ClusterUID;
    private Map<ClusterUID, Set<String>> _siteToArrayIDsMap = new HashMap<ClusterUID, Set<String>>();

    public ClusterUID getClusterUID() {
        return _ClusterUID;
    }

    public void setClusterUID(ClusterUID ClusterUID) {
        this._ClusterUID = ClusterUID;
    }

    private Set<RPCopy> copies;

    @XmlElement
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
    }

    @XmlElement
    public ConsistencyGroupUID getCGUID() {
        return _cgUID;
    }

    public void setCGUID(ConsistencyGroupUID cGUID) {
        _cgUID = cGUID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RPConsistencyGroup that = (RPConsistencyGroup) o;

        if (_cgUID == null || that.getCGUID() == null)
            return false; // null != everything (including null)
        return _cgUID.getId() == that.getCGUID().getId();
    }

    @Override
    public int hashCode() {
        if (_cgUID != null)
            // TODO: Danger, loss of precision.
            return (int) (_cgUID.getId() != 0L ? _cgUID.getId() : 0);
        return super.hashCode();
    }

    public void cloneMe(RPConsistencyGroup clone) {
        _name = clone._name;
        _cgUID = clone._cgUID;
        _ClusterUID = clone._ClusterUID;
    }

    public void setCopies(Set<RPCopy> copies) {
        this.copies = copies;
    }

    public Set<RPCopy> getCopies() {
        return copies;
    }

    public void setSiteToArrayIDsMap(Map<ClusterUID, Set<String>> siteToArrayIDsMap) {
        this._siteToArrayIDsMap = siteToArrayIDsMap;
    }

    public Map<ClusterUID, Set<String>> getSiteToArrayIDsMap() {
        return _siteToArrayIDsMap;
    }
}
