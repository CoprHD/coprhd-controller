/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.db.client.model.Project;
import com.google.common.base.Function;

public class MapProject implements Function<Project,ProjectRestRep> {
    public static final MapProject instance = new MapProject();

    public static MapProject getInstance() {
        return instance;
    }

    private MapProject() {
    }

    @Override
    public ProjectRestRep apply(Project resource) {
        return DbObjectMapper.map(resource);
    }
}
