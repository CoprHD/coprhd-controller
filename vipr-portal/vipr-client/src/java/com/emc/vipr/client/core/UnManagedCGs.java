/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.UnManagedCGList;
import com.emc.storageos.model.block.UnManagedCGRestRep;
import com.emc.storageos.model.block.UnManagedCGsBulkRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Unmanaged CGs resources.
 * <p>
 * Base URL: <tt>/vdc/unmanaged/cgs</tt>
 */
public class UnManagedCGs extends AbstractCoreBulkResources<UnManagedCGRestRep> {
    public UnManagedCGs(ViPRCoreClient parent, RestClient client) {
        super(parent, client, UnManagedCGRestRep.class, PathConstants.UNMANAGED_CGS_URL);
    }

    @Override
    public UnManagedCGs withInactive(boolean inactive) {
        return (UnManagedCGs) super.withInactive(inactive);
    }

    @Override
    public UnManagedCGs withInternal(boolean internal) {
        return (UnManagedCGs) super.withInternal(internal);
    }

    @Override
    protected List<UnManagedCGRestRep> getBulkResources(BulkIdParam input) {
        UnManagedCGsBulkRep response = client.post(UnManagedCGsBulkRep.class, input, getBulkUrl());
        return defaultList(response.getUnManagedCGs());
    }

    /**
     * Gets the list of unmanaged CGs for the given protection system by ID.
     * <p>
     * API Call: <tt>GET /vdc/protection-systems/{protectionSystemId}/unmanaged/cgs</tt>
     * 
     * @param protectionSystemId
     *            the ID of the protection system.
     * @return the list of unmanaged CG references.
     */
    public List<RelatedResourceRep> listByProtectionSystem(URI protectionSystemId) {
        UnManagedCGList response = client.get(UnManagedCGList.class,
                PathConstants.UNMANAGED_CG_BY_PROTECTION_SYSTEM_URL, protectionSystemId);
        return ResourceUtils.defaultList(response.getUnManagedCGs());
    }

    /**
     * Gets the list of unmanaged CGs for the given protection system by ID. This is a convenience method for:
     * <tt>getByRefs(listByProtectionSystem(protectionSystemId))</tt>
     * 
     * @param protectionSystemId
     *            the ID of the protection system.
     * @return the list of unmanaged CGs.
     */
    public List<UnManagedCGRestRep> getByProtectionSystem(URI protectionSystemId) {
        List<RelatedResourceRep> refs = listByProtectionSystem(protectionSystemId);
        return getByRefs(refs, null);
    }

}
