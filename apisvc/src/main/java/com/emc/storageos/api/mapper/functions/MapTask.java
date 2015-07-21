/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.model.TaskResourceRep;
import com.google.common.base.Function;

public class MapTask implements Function<Task,TaskResourceRep> {
    public static final MapTask instance = new MapTask();

    public static MapTask getInstance() {
        return instance;
    }

    private MapTask() {
    }

    @Override
    public TaskResourceRep apply(Task task) {
        return TaskMapper.toTask(task);
    }
}
