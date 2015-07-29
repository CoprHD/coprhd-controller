/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.utils;

/**
 * Wrapper class used only by VPlexDeviceController. This object encapsulates
 * the relationship between VPlex cluster and CG name. Also keeps track of
 * whether or not the CG is distributed.
 */
public class ClusterConsistencyGroupWrapper {
    String clusterName;
    boolean distributed;
    String cgName;

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public boolean isDistributed() {
        return distributed;
    }

    public void setDistributed(boolean distributed) {
        this.distributed = distributed;
    }

    public String getCgName() {
        return cgName;
    }

    public void setCgName(String cgName) {
        this.cgName = cgName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((cgName == null) ? 0 : cgName.hashCode());
        result = prime * result
                + ((clusterName == null) ? 0 : clusterName.hashCode());
        result = prime * result + (distributed ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ClusterConsistencyGroupWrapper other = (ClusterConsistencyGroupWrapper) obj;
        if (cgName == null) {
            if (other.cgName != null) {
                return false;
            }
        } else if (!cgName.equals(other.cgName)) {
            return false;
        }
        if (clusterName == null) {
            if (other.clusterName != null) {
                return false;
            }
        } else if (!clusterName.equals(other.clusterName)) {
            return false;
        }
        if (distributed != other.distributed) {
            return false;
        }
        return true;
    }

}
