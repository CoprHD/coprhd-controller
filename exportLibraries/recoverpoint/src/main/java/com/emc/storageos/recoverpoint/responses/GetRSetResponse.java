/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import java.io.Serializable;
import java.util.List;

/**
 * Replication set just has a production and a list of scattered targets. No journals.
 * 
 */
@SuppressWarnings("serial")
public class GetRSetResponse implements Serializable {
    private String name;
    private List<GetVolumeResponse> volumes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<GetVolumeResponse> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<GetVolumeResponse> volumes) {
        this.volumes = volumes;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (volumes != null) {
            sb.append("\nReplication Set: " + name);
            for (GetVolumeResponse volume : volumes) {
                sb.append(volume.toString());
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}