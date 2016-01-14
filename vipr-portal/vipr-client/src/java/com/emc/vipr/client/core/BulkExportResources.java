/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.export.ITLBulkRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.impl.RestClient;

/**
 * Base class for all resources types that support ITL bulk export operations.
 * 
 * @param <T> the type of resource.
 */
public abstract class BulkExportResources<T extends DataObjectRestRep> extends ProjectResources<T> {

    public BulkExportResources(ViPRCoreClient parent, RestClient client, Class<T> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }

    /**
     * Gets ITL bulk exports for the given ids
     * 
     * @param ids the ids to use in the payload for the bulk export
     * @return itl bulk response
     */
    public ITLBulkRep getBulkExports(BulkIdParam ids) {
        ITLBulkRep result = new ITLBulkRep();
        if (ids != null) {
            BulkIdParam input = new BulkIdParam();
            for (URI id : ids.getIds()) {
                addId(id, input, result);
            }
            if (!input.getIds().isEmpty()) {
                fetchChunk(input, result);
            }
        }
        return result;
    }

    private void addId(URI id, BulkIdParam input, ITLBulkRep results) {
        input.getIds().add(id);
        if (input.getIds().size() >= client.getConfig().getITLBulkSize()) {
            fetchChunk(input, results);
            input.getIds().clear();
        }
    }

    private void fetchChunk(BulkIdParam input, ITLBulkRep results) {
        ITLBulkRep bulkRep = client.post(ITLBulkRep.class, input, baseUrl + "/exports/bulk");
        for (ITLRestRep rep : bulkRep.getExportList()) {
            results.getExportList().add(rep);
        }
    }

}
