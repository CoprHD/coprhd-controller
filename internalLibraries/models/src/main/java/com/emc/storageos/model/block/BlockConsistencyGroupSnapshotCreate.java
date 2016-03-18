/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Consistency Group Snapshot creation parameters
 */
@XmlRootElement(name = "consistency_group_snapshot_create")
public class BlockConsistencyGroupSnapshotCreate {

    private String name;
    // field for Application API
    private List<URI> volumes;
    private Boolean createInactive;
    private Boolean readOnly;

    // flag to specify if the copy needs to be taken on HA side of VPLEX Distributed volumes
    // By default, create copy on source back end side
    private Boolean copyOnHighAvailabilitySide = Boolean.FALSE;

    public BlockConsistencyGroupSnapshotCreate() {
    }

    public BlockConsistencyGroupSnapshotCreate(String name,
            Boolean createInactive, Boolean readOnly) {
        this.name = name;
        this.createInactive = createInactive;
        this.readOnly = readOnly;
    }

    public BlockConsistencyGroupSnapshotCreate(String name, List<URI> volumes,
            Boolean createInactive, Boolean readOnly) {
        this(name, createInactive, readOnly);
        this.volumes = volumes;
    }

    /**
     * Snapshot name
     * 
     */
    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElementWrapper(required = false, name = "volumes")
    /**
     * List of Volume IDs.
     * This field is applicable only if volume is part of an application.
     * Snapshots of the replication groups (could be subset or full set of replication groups of an application) that the volumes belong to will be created.
     *
     * Example: list of valid URIs
     */
    @XmlElement(required = false, name = "volume")
    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }

    /**
     * If create_inactive is set to true, then the
     * operation will create the snapshot, but not
     * activate the synchronization between source
     * and target volumes. The activation would have
     * to be done using the block snapshot activate
     * operation.
     * 
     * The default value for the parameter is false.
     * That is, the operation will create and activate
     * the synchronization for the snapshot.
     * 
     */
    @XmlElement(name = "create_inactive", required = false, defaultValue = "false")
    public Boolean getCreateInactive() {
        return createInactive;
    }

    public void setCreateInactive(Boolean createInactive) {
        this.createInactive = createInactive;
    }

    /**
     * If read_only is set to true, then the snapshot will be created
     * as read only, i.e., it will not be possible to write into the snapshot.
     * 
     * The default value is false. That is, the snapshot will be created as writable.
     * 
     */
    @XmlElement(name = "read_only", required = false, defaultValue = "false")
	public Boolean getReadOnly() {
		return readOnly;
	}

	public void setReadOnly(Boolean readOnly) {
		this.readOnly = readOnly;
	}

    /**
     * Flag to specify if the copy needs to be taken on HA side of VPLEX Distributed volumes.
     * By default, it is considered as false which means copy is requested on source backend side.
     */
    @XmlElement(name = "copy_on_high_availability_side", defaultValue = "false")
    public Boolean getCopyOnHighAvailabilitySide() {
        return copyOnHighAvailabilitySide;
    }

    public void setCopyOnHighAvailabilitySide(Boolean copyOnHighAvailabilitySide) {
        this.copyOnHighAvailabilitySide = copyOnHighAvailabilitySide;
    }
}
