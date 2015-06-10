/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.search;

import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.client.core.AbstractResources;
import static com.emc.vipr.client.core.impl.SearchConstants.*;

public class ClusterSearchBuilder extends ProjectSearchBuilder<ClusterRestRep> {
    public ClusterSearchBuilder(AbstractResources<ClusterRestRep> resources) {
        super(resources);
    }

    public SearchBuilder<ClusterRestRep> byName(String name) {
        return by(NAME_PARAM, name);
    }
}
