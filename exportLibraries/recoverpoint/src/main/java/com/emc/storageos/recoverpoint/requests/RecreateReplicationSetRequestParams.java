/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.ConsistencyGroupUID;
import com.emc.fapiclient.ws.DeviceUID;
import com.emc.fapiclient.ws.GlobalCopyUID;

/**
 * Parameters necessary to create a replication set. The member variables must be serializable, however most of the
 * information it carries is used for FAPI methods and are not serializable. Therefore the object carries the primitive
 * objects contained within the FAPI objects, and get/set methods do the translation. This allows us to be able to
 * serialize this object and use it across Workflow and other transports.
 *
 */
@SuppressWarnings("serial")
public class RecreateReplicationSetRequestParams implements Serializable {
    // Name of the CG Group
    private String cgName;
    // Name of the Replication Set
    private String name;
    // CG id
    private long cgID;

    // Volumes that make up the replication set
    private List<CreateRSetVolumeParams> volumes;

    public static class CreateRSetVolumeParams implements Serializable {
        // makes up DeviceUID
        private long deviceID;

        // makes up ConsistencyGroupCopyUID
        private int copyUID;
        private long cgID;
        private long siteID;

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("\ndeviceID: " + deviceID);
            sb.append("\ngroupID: " + copyUID);
            sb.append("\ncgID: " + cgID);
            sb.append("\nsiteID: " + siteID);
            return sb.toString();
        }

        public DeviceUID getDeviceUID() {
            DeviceUID deviceUID = new DeviceUID();
            deviceUID.setId(deviceID);
            return deviceUID;
        }

        public ConsistencyGroupCopyUID getConsistencyGroupCopyUID() {
            ConsistencyGroupCopyUID cgCopyUID = new ConsistencyGroupCopyUID();
            cgCopyUID.setGlobalCopyUID(new GlobalCopyUID());
            cgCopyUID.getGlobalCopyUID().setCopyUID(copyUID);
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(cgID);
            cgCopyUID.setGroupUID(cgUID);
            ClusterUID clusterUID = new ClusterUID();
            clusterUID.setId(siteID);
            cgCopyUID.getGlobalCopyUID().setClusterUID(clusterUID);
            return cgCopyUID;
        }

        public void setDeviceUID(DeviceUID deviceUID) {
            deviceID = deviceUID.getId();
        }

        public void setConsistencyGroupCopyUID(ConsistencyGroupCopyUID cgCopyUID) {
            copyUID = cgCopyUID.getGlobalCopyUID().getCopyUID();
            cgID = cgCopyUID.getGroupUID().getId();
            siteID = cgCopyUID.getGlobalCopyUID().getClusterUID().getId();
        }
    }

    public ConsistencyGroupUID getConsistencyGroupUID() {
        ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
        cgUID.setId(cgID);
        return cgUID;
    }

    public void setConsistencyGroupUID(ConsistencyGroupUID cgUID) {
        cgID = cgUID.getId();
    }

    public String getCgName() {
        return cgName;
    }

    public void setCgName(String cgName) {
        this.cgName = cgName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCgID() {
        return cgID;
    }

    public void setCgID(long cgID) {
        this.cgID = cgID;
    }

    public List<CreateRSetVolumeParams> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<CreateRSetVolumeParams>();
        }
        return volumes;
    }

    public void setVolumes(List<CreateRSetVolumeParams> volumes) {
        this.volumes = volumes;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("name: " + name);
        sb.append("\ncgName: " + cgName);
        if (volumes != null) {
            for (CreateRSetVolumeParams volume : volumes) {
                sb.append(volume);
            }
        }
        return sb.toString();
    }
}
