/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.search;

import static com.emc.vipr.client.core.impl.SearchConstants.CLUSTER_PARAM;
import static com.emc.vipr.client.core.impl.SearchConstants.HOST_PARAM;

import java.net.URI;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.AbstractResources;

public class ExportGroupSearchBuilder extends ProjectSearchBuilder<ExportGroupRestRep> {

	public ExportGroupSearchBuilder(AbstractResources<ExportGroupRestRep> resources) {
        super(resources);
    }
	
	public SearchBuilder<ExportGroupRestRep> byCluster(URI cluster) {
        return by(CLUSTER_PARAM, cluster);
    }
	
	public SearchBuilder<ExportGroupRestRep> byHost(URI host) {
		return by(HOST_PARAM, host);
	}

}
