/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.impl.RestClient;

public class AbstractCoreResources<T extends DataObjectRestRep> extends AbstractResources<T> {

    protected final ViPRCoreClient parent;

    public AbstractCoreResources(ViPRCoreClient parent, RestClient client, Class<T> resourceClass, String baseUrl) {
        super(client, resourceClass, baseUrl);
        this.parent = parent;
    }

}
