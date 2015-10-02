/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Consistency Group Snapshot creation parameters
 */
@XmlRootElement(name = "consistency_group_snapshot_create")
public class BlockConsistencyGroupSnapshotCreate {

    private String name;
    private Boolean createInactive;
    private Boolean readOnly;

    public BlockConsistencyGroupSnapshotCreate() {
    }

    public BlockConsistencyGroupSnapshotCreate(String name,
            Boolean createInactive, Boolean readOnly) {
        this.name = name;
        this.createInactive = createInactive;
        this.readOnly = readOnly;
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
}
