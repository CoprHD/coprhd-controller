/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.rdfgroup.RDFGroupList;
import com.emc.storageos.model.rdfgroup.RDFGroupRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * SRDF Group Resources
 * <p>
 * Base URL: <tt>/block/virtualpool/{id}/rdf-groups</tt>
 * 
 * Note: RDF Groups can also be returned by the NB API via:
 */
public class RDFGroups extends AbstractCoreResources<RDFGroupRestRep> {

    public RDFGroups(ViPRCoreClient parent, RestClient client) {
        super(parent, client, RDFGroupRestRep.class, "");
    }

    @Override
    public RDFGroups withInactive(boolean inactive) {
        return (RDFGroups) super.withInactive(inactive);
    }

    @Override
    public RDFGroups withInternal(boolean internal) {
        return (RDFGroups) super.withInternal(internal);
    }

    /**
     * Lists all RDF Groups for a virtual pool
     * <p>
     * API Call: <tt>GET /block/virtual-pool/{id}/rdf-groups</tt>
     * 
     * @return the list of replication group references.
     */
    public List<RDFGroupRestRep> listByVpool(URI virtualPoolId) {
        RDFGroupList response = client.get(RDFGroupList.class, PathConstants.VPOOL_RDF_GROUPS_URL, virtualPoolId);
        return ResourceUtils.defaultList(response.getRdfGroups());
    }

    /**
     * Lists all RDF Groups for a storage system
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{id}/rdf-groups</tt>
     * 
     * @return the list of storage system references.
     */
    public Collection<? extends RDFGroupRestRep> listByStorageSystem(URI storageSystemId) {
        RDFGroupList response = client.get(RDFGroupList.class, PathConstants.SS_RDF_GROUPS_URL, storageSystemId);
        return ResourceUtils.defaultList(response.getRdfGroups());
    }
}
