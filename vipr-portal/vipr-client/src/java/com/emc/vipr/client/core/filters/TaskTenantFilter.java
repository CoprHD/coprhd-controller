/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.TaskResourceRep;

import java.net.URI;

/**
 */
public class TaskTenantFilter extends DefaultResourceFilter<TaskResourceRep> {
    private URI tenantId;

    public TaskTenantFilter(String status) {
        // this.state = status;
    }

    @Override
    public boolean accept(TaskResourceRep item) {
        // return item.getState().equals(state);
        return false;
    }
}
