/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;

@XmlRootElement(name = "volume_group_snapshot_create")
public class VolumeGroupSnapshotCreateParam extends BlockConsistencyGroupSnapshotCreate {
    // By default, consider snapshot to be created for all array groups in Application
    private Boolean partial = Boolean.FALSE;
    // alternative to partial flag and list of snapshot objects
    private List<String> subGroups;
    
    public VolumeGroupSnapshotCreateParam() {
    }

    public VolumeGroupSnapshotCreateParam(String name, Boolean createInactive, Boolean readOnly,
            Boolean partial, List<URI> volumes) {
        super(name, volumes, createInactive, readOnly);
        this.partial = partial;
    }

    /**
     * Boolean which indicates whether we need to take snapshot for the entire Application or for subset.
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
