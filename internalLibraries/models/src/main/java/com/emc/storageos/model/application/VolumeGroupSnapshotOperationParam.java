/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "volume_group_snapshot_operation")
public class VolumeGroupSnapshotOperationParam {
    // By default, consider snapshot operation for all array replication groups in Application */
    private Boolean partial = Boolean.FALSE;
    private List<URI> snapshots;

    public VolumeGroupSnapshotOperationParam() {
    }

    public VolumeGroupSnapshotOperationParam(Boolean partial, List<URI> snapshots) {
        this.partial = partial;
        this.snapshots = snapshots;
    }

    /**
     * Boolean which indicates whether we need to operate on snapshot for the entire Application or for set of array replication groups.
     * By default it is set to false, and consider that snapshots to be operated for all array replication groups in an Application.
     * If set to true, volumes list should be provided with snapshot URIs one from each Array replication group.
     * In any case, minimum of one snapshot URI needs to be specified in order to identify the clone set.
     */
    @XmlElement(name = "partial", required = false, defaultValue = "false")
    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }

    @XmlElementWrapper(required = true, name = "snapshots")
    /**
     * List of snapshot IDs.
     *
     * If full, snapshot operation will be operated for the entire Application. A snapshot URI needs to be specified in order to identify the snapshot set.
     *
     * If partial, snapshot operation need not be operated for the entire Application, instead operate on snapshots for the specified array replication groups.
     * List can have snapshot URIs one from each Array replication group.
     *
     * Example: list of valid URIs
     */
    @XmlElement(required = true, name = "snapshot")
    public List<URI> getSnapshots() {
        if (snapshots == null) {
            snapshots = new ArrayList<URI>();
        }
        return snapshots;
    }

    public void setSnapshots(List<URI> snapshots) {
        this.snapshots = snapshots;
    }
}
