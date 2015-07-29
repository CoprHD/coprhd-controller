/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.UserGroupMapper;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import com.google.common.base.Function;

public class MapUserGroup implements Function<UserGroup, UserGroupRestRep> {
    public static final MapUserGroup instance = new MapUserGroup();

    public static MapUserGroup getInstance() {
        return instance;
    }

    private MapUserGroup() {
    }

    @Override
    public UserGroupRestRep apply(UserGroup resource) {
        return UserGroupMapper.map(resource);
    }
}
