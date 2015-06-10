/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.search;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.AbstractResources;
import static com.emc.vipr.client.core.impl.SearchConstants.*;

public class VolumeSearchBuilder extends ProjectSearchBuilder<VolumeRestRep> {
    public VolumeSearchBuilder(AbstractResources<VolumeRestRep> resources) {
        super(resources);
    }

    public SearchBuilder<VolumeRestRep> byWwn(String wwn) {
        return by(WWN_PARAM, wwn);
    }
}
