/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
