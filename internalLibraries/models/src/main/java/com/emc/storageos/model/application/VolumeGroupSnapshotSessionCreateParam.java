/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;

@XmlRootElement(name = "volume_group_snapshot_session_create")
public class VolumeGroupSnapshotSessionCreateParam extends SnapshotSessionCreateParam {
    // By default, consider snapshot session to be created for all array groups in Application
    private Boolean partial = Boolean.FALSE;

    public VolumeGroupSnapshotSessionCreateParam() {
    }

    public VolumeGroupSnapshotSessionCreateParam(String name, SnapshotSessionNewTargetsParam newLinkedTargets,
            Boolean partial, List<URI> volumes) {
        super(name, newLinkedTargets, volumes);
        this.partial = partial;
    }

    /**
     * Boolean which indicates whether we need to create snapshot session for the entire Application or for subset.
     * By default it is set to false, and consider that snapshot to be created for all array replication groups in an Application.
     * If set to true, volumes list should be provided with volumes one from each Array replication group.
     */
    @XmlElement(name = "partial", required = false, defaultValue = "false")
    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }
}
