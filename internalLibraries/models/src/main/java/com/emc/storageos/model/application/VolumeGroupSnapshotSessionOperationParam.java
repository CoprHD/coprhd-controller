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
    // alternative to passing a list of full copy volumes
    private String copySetName;
    // alternative to passing partial flag and list of full copy volumes
    private List<String> subGroups;

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

    @XmlElementWrapper(name = "snapshot_sessions")
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

    /**
     * @return the copySetName
     */
    @XmlElement(name = "copy_set_name", required = false)
    public String getCopySetName() {
        return copySetName;
    }

    /**
     * @param copySetName the copySetName to set
     */
    public void setCopySetName(String copySetName) {
        this.copySetName = copySetName;
    }

    /**
     * @return the subGroups
     */
    @XmlElementWrapper(required = true, name = "subgroups")
    @XmlElement(required = false, name = "subgroup")
    public List<String> getSubGroups() {
        return subGroups;
    }

    /**
     * @param subGroups the subGroups to set
     */
    public void setSubGroups(List<String> subGroups) {
        this.subGroups = subGroups;
    }
}
