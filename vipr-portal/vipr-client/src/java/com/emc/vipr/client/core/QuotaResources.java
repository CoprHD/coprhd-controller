/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;

import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;

/**
 * Interface for resources that support quotas.
 */
public interface QuotaResources {
    /**
     * Gets the quota information for a given resource by ID.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/quota
     * 
     * @param id
     *        the resource ID.
     * @return the quota information.
     */
    public QuotaInfo getQuota(URI id);

    /**
     * Updates the quota information for a given resource by ID.
     * <p>
     * API Call: PUT <tt><i>baseUrl</i>/{id}/quota
     * 
     * @param id
     *        the resource ID.
     * @param quota
     *        the update to the quota.
     * @return the updated quota information after applying the update.
     */
    public QuotaInfo updateQuota(URI id, QuotaUpdateParam quota);
}
