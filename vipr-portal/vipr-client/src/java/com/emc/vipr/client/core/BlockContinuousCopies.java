/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.block.BlockMirrorBulkRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class BlockContinuousCopies extends ProjectResources<BlockMirrorRestRep> {

	public BlockContinuousCopies(ViPRCoreClient parent, RestClient client) {
		super(parent, client, BlockMirrorRestRep.class, PathConstants.BLOCK_CONTINUOUS_COPIES_URL);
	}

	@Override
	protected List getBulkResources(BulkIdParam input) {
		BlockMirrorBulkRep response = client.post(BlockMirrorBulkRep.class, input, getBulkUrl());
	     return defaultList(response.getBlockMirrors());
	}

}
