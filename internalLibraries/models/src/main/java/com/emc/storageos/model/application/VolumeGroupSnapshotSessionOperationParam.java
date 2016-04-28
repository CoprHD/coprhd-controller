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

public class VolumeGroupSnapshotSessionOperationParam {
    // By default, consider snapshot session operation for all array replication groups in Application */
    private Boolean partial = Boolean.FALSE;
    private List<URI> snapshotSessions;

    public VolumeGroupSnapshotSessionOperationParam() {
    }

    public VolumeGroupSnapshotSessionOperationParam(Boolean partial, List<URI> snapshotSessions) {
        this.partial = partial;
        this.snapshotSessions = snapshotSessions;
    }

    /**
     * Boolean which indicates whether we need to operate on snapshot session for the entire Application
     * or for set of array replication groups.
     * By default it is set to false, and consider that snapshots to be operated for all array replication groups in an Application.
     * If set to true, sessions list should be provided with session URIs one from each Array replication group.
     * In any case, minimum of one session URI needs to be specified in order to identify the session set.
     */
    @XmlElement(name = "partial", required = false, defaultValue = "false")
    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }

    @XmlElementWrapper(required = true, name = "snapshot_sessions")
    /**
     * List of snapshot session IDs.
     * 
     * If full, snapshot session operation will be performed for the entire Application.
     * A session URI needs to be specified in order to identify the session set.
     *
     * If partial, snaphot session operation need not be performed for the entire Application,
     *  instead operate on snapshot sessions for the specified array replication groups.
     * List can have session URIs one from each Array replication group.
     *
     * Example: list of valid URIs
     */
    @XmlElement(required = true, name = "snapshot_session")
    public List<URI> getSnapshotSessions() {
        if (snapshotSessions == null) {
            snapshotSessions = new ArrayList<URI>();
        }
        return snapshotSessions;
    }

    public void setSnapshotSessions(List<URI> snapshotSessions) {
        this.snapshotSessions = snapshotSessions;
    }
}
