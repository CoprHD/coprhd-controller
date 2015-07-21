/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.model.block.tier.AutoTieringPolicyRestRep;
import com.google.common.base.Function;

public class MapAutoTierPolicy implements Function<AutoTieringPolicy, AutoTieringPolicyRestRep> {
    public static final MapAutoTierPolicy instance = new MapAutoTierPolicy();

    public static MapAutoTierPolicy getInstance() {
        return instance;
    }

    private MapAutoTierPolicy() {
    }

    @Override
    public AutoTieringPolicyRestRep apply(AutoTieringPolicy resource) {
        return BlockMapper.map(resource);
    }
}
