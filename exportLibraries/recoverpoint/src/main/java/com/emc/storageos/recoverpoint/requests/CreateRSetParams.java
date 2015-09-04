/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.util.List;

/**
 * Replication set just has a production and a list of scattered targets. No journals.
 *
 */
@SuppressWarnings("serial")
public class CreateRSetParams implements Serializable {
    private String name;
    private List<CreateVolumeParams> volumes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CreateVolumeParams> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<CreateVolumeParams> volumes) {
        this.volumes = volumes;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (volumes != null) {
            sb.append("\nVolumes: " + name);
            for (CreateVolumeParams volume : volumes) {
                sb.append(volume.toString());
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}