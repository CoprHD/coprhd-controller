/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import com.emc.vipr.model.catalog.ApprovalInfo;
import com.emc.vipr.model.catalog.Reference;
import com.emc.vipr.client.ViPRCatalogClient;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.sun.jersey.api.client.GenericType;

import javax.ws.rs.core.UriBuilder;

import java.util.List;

import static com.emc.vipr.client.catalog.impl.ApiListUtils.getApiList;
import static com.emc.vipr.client.catalog.impl.PathConstants.*;

/**
 * 
 * @deprecated Replaced by
 * @see Approvals2
 */
@Deprecated
public class Approvals extends AbstractResources<ApprovalInfo> {
    public Approvals(ViPRCatalogClient parent, RestClient client) {
        super(parent, client, ApprovalInfo.class, APPROVALS_URL);
    }

    /**
     * Lists approval requests.
     * <p>
     * API Call: GET /api/approvals
     * 
     * @return References to approval requests.
     */
    @Deprecated
    public List<Reference> list() {
        return doList();
    }

    /**
     * Retrieves all approval requests. Convenience method to list approval requests and retrieve all.
     * <p>
     * API Call: GET /api/approvals
     * 
     * @see #list()
     * @see #getByRefs(java.util.Collection)
     * @return All approval requests
     */
    @Deprecated
    public List<ApprovalInfo> getAll() {
        return doGetAll();
    }

    /**
     * Lists all pending approval requests.
     * <p>
     * API Call: GET /api/approvals/pending
     * 
     * @return Pending approval request references.
     */
    @Deprecated
    public List<Reference> listPending() {
        List<Reference> apiList = getApiList(client, new GenericType<List<Reference>>() {
        }, APPROVALS_PENDING_URL);
        return apiList;
    }

    /**
     * Retrieves all pending approval requests. Convenience method to list and retrieve all pending approval requests.
     * <p>
     * API Call: GET /api/approvals/pending
     * 
     * @return All pending approval requests.
     */
    @Deprecated
    public List<ApprovalInfo> getPending() {
        return getByRefs(listPending());
    }

    /**
     * Approves an approval request.
     * <p>
     * API Call: POST /api/approvals/{id}/approve
     * 
     * @param id Approval request ID to approve.
     * @param message Approval message.
     * @return Approval request information.
     */
    @Deprecated
    public ApprovalInfo approve(String id, String message) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.APPROVE_URL);
        if (message != null) {
            uriBuilder.replaceQueryParam("message", message);
        }
        return client.postURI(ApprovalInfo.class, uriBuilder.build(id));
    }

    /**
     * Rejects an approval request.
     * <p>
     * API Call: POST /api/approvals/{id}/reject
     * 
     * @param id Approval request ID to reject.
     * @param message Rejection message.
     * @return Approval request information.
     */
    @Deprecated
    public ApprovalInfo reject(String id, String message) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.REJECT_URL);
        if (message != null) {
            uriBuilder.replaceQueryParam("message", message);
        }
        return client.postURI(ApprovalInfo.class, uriBuilder.build(id));
    }
}
