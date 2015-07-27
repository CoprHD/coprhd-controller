/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.TaskResourceRep;

public class TaskStateFilter extends DefaultResourceFilter<TaskResourceRep> {
    private String state;

    public TaskStateFilter(String status) {
        this.state = status;
    }

    @Override
    public boolean accept(TaskResourceRep item) {
        return item.getState().equals(state);
    }
}
