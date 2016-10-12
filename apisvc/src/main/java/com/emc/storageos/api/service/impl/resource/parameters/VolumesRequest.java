/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.parameters;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;

import java.util.Collections;
import java.util.Set;

/**
 * Parameter object for a volume creation request.
 */
public class VolumesRequest {
    private Locality locality;
    private int count;
    private Long size;
    private String baseLabel;
    private Set<Volume> preCreated;

    public VolumesRequest(Locality locality, int count, Long size, String baseLabel) {
        this.locality = locality;
        this.count = count;
        this.size = size;
        this.baseLabel = baseLabel;
    }

    public Locality getLocality() {
        return locality;
    }

    public void setLocality(Locality locality) {
        this.locality = locality;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getBaseLabel() {
        return baseLabel;
    }

    public void setBaseLabel(String baseLabel) {
        this.baseLabel = baseLabel;
    }

    public Set<Volume> getPreCreated() {
        if (preCreated == null) {
            return Collections.emptySet();
        }
        return preCreated;
    }

    public void setPreCreated(Set<Volume> preCreated) {
        this.preCreated = preCreated;
    }

    /*
     * Delegate methods for Locality.
     */

    public Project getProject() {
        return locality.getProject();
    }

    public VirtualArray getVirtualArray() {
        return locality.getVirtualArray();
    }

    public VirtualPool getVirtualPool() {
        return locality.getVirtualPool();
    }

    public BlockConsistencyGroup getConsistencyGroup() {
        return locality.getConsistencyGroup();
    }
}
