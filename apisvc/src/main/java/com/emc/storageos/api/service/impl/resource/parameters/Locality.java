/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.parameters;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.google.common.base.Objects;

/**
 * Parameter object for project, virtual array, virtual pool and consistency group.
 */
public class Locality {
    private Project project;
    private VirtualArray virtualArray;
    private VirtualPool virtualPool;
    private BlockConsistencyGroup consistencyGroup;

    public Locality(Project project, VirtualArray virtualArray, VirtualPool virtualPool, BlockConsistencyGroup consistencyGroup) {
        this.project = project;
        this.virtualArray = virtualArray;
        this.virtualPool = virtualPool;
        this.consistencyGroup = consistencyGroup;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public VirtualArray getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(VirtualArray virtualArray) {
        this.virtualArray = virtualArray;
    }

    public VirtualPool getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(VirtualPool virtualPool) {
        this.virtualPool = virtualPool;
    }

    public BlockConsistencyGroup getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(BlockConsistencyGroup consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("project", project)
                .add("virtualArray", virtualArray)
                .add("virtualPool", virtualPool)
                .add("consistencyGroup", consistencyGroup)
                .toString();
    }
}
