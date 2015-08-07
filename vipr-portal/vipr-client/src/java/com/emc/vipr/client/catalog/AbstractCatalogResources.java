/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.core.AbstractResources;
import com.emc.vipr.client.impl.RestClient;

public class AbstractCatalogResources<T extends DataObjectRestRep> extends AbstractResources<T> {

    protected final ViPRCatalogClient2 parent;

    public AbstractCatalogResources(ViPRCatalogClient2 parent, RestClient client, Class<T> resourceClass, String baseUrl) {
        super(client, resourceClass, baseUrl);
        this.parent = parent;
    }

}
