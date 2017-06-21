/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.RDFGroupBulkRep;
import com.emc.storageos.model.block.RDFGroupRestRep;
import com.emc.storageos.model.rdfgroup.RDFGroupList;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block Volumes resources.
 * <p>
 * Base URL: <tt>/block/volumes</tt>
 */
public class RDFGroups extends BulkExportResources<RDFGroupRestRep> {

    public RDFGroups(ViPRCoreClient parent, RestClient client) {
        super(parent, client, RDFGroupRestRep.class, PathConstants.RDF_GROUPS_URL);
    }

    @Override
    public RDFGroups withInactive(boolean inactive) {
        return (RDFGroups) super.withInactive(inactive);
    }

    @Override
    public RDFGroups withInternal(boolean internal) {
        return (RDFGroups) super.withInternal(internal);
    }

    @Override
    protected List<RDFGroupRestRep> getBulkResources(BulkIdParam input) {
        RDFGroupBulkRep response = client.post(RDFGroupBulkRep.class, input, getBulkUrl());
        return defaultList(response.getRDFGroups());
    }

    /**
     * Lists all storage systems.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems</tt>
     * 
     * @return the list of storage system references.
     */
    public List<NamedRelatedResourceRep> list() {
        RDFGroupList response = client.get(RDFGroupList.class, baseUrl);
        return ResourceUtils.defaultList(response.getRdfGroups());
    }

}
