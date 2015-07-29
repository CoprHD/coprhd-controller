/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
