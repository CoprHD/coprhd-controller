/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.ApprovalMapper.map;
import static com.emc.sa.api.mapper.ApprovalMapper.updateObject;
import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.ApprovalFilter;
import com.emc.sa.api.mapper.ApprovalMapper;
import com.emc.sa.catalog.ApprovalManager;
import com.emc.sa.catalog.OrderManager;
import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.ApprovalStatus;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.ApprovalBulkRep;
import com.emc.vipr.model.catalog.ApprovalCommonParam;
import com.emc.vipr.model.catalog.ApprovalList;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.ApprovalUpdateParam;
import com.google.common.collect.Lists;

@DefaultPermissions(
        readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/catalog/approvals")
public class ApprovalService extends CatalogTaggedResourceService {

    private static final String EVENT_SERVICE_TYPE = "catalog-approval";

    @Autowired
    private ApprovalManager approvalManager;

    @Autowired
    private OrderManager orderManager;

    @Autowired
    private RecordableEventManager eventManager;

    @Override
    protected ApprovalRequest queryResource(URI id) {
        return getApprovalById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        ApprovalRequest approval = queryResource(id);
        return uri(approval.getTenant());
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.APPROVAL;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ApprovalResRepFilter(user, permissionsHelper);
    }

    /**
     * parameter: 'orderId' The id of the order to search for approvals
     * parameter: 'approvalStatus' The status for the approval.
     * parameter: 'tenantId' The id of the tenant (if not the current tenant)
     * 
     * @return Return a list of matching approvals or an empty list if no match was found.
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {

        StorageOSUser user = getUserFromContext();
        String tenantId = user.getTenantId();
        if (parameters.containsKey(SearchConstants.TENANT_ID_PARAM)) {
            tenantId = parameters.get(SearchConstants.TENANT_ID_PARAM).get(0);
        }
        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        if (!parameters.containsKey(SearchConstants.ORDER_ID_PARAM) && !parameters.containsKey(SearchConstants.APPROVAL_STATUS_PARAM)) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(),
                    SearchConstants.ORDER_ID_PARAM + " or " + SearchConstants.APPROVAL_STATUS_PARAM);
        }

        if (parameters.containsKey(SearchConstants.ORDER_ID_PARAM) && parameters.containsKey(SearchConstants.APPROVAL_STATUS_PARAM)) {
            throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(getResourceClass().getName(),
                    SearchConstants.ORDER_ID_PARAM, SearchConstants.APPROVAL_STATUS_PARAM);
        }

        List<ApprovalRequest> approvals = Lists.newArrayList();
        if (parameters.containsKey(SearchConstants.ORDER_ID_PARAM)) {
            String orderId = parameters.get(SearchConstants.ORDER_ID_PARAM).get(0);
            ArgValidator.checkFieldNotEmpty(orderId, SearchConstants.ORDER_ID_PARAM);
            approvals = approvalManager.findApprovalsByOrderId(uri(orderId));
        }
        else if (parameters.containsKey(SearchConstants.APPROVAL_STATUS_PARAM)) {
            String approvalStatus = parameters.get(SearchConstants.APPROVAL_STATUS_PARAM).get(0);
            ArgValidator.checkFieldNotEmpty(approvalStatus, SearchConstants.APPROVAL_STATUS_PARAM);
            approvals = approvalManager.findApprovalsByStatus(uri(tenantId), ApprovalStatus.valueOf(approvalStatus));
        }

        ResRepFilter<SearchResultResourceRep> resRepFilter =
                (ResRepFilter<SearchResultResourceRep>) getPermissionFilter(getUserFromContext(), _permissionsHelper);

        List<SearchResultResourceRep> searchResultResourceReps = Lists.newArrayList();
        for (ApprovalRequest approval : approvals) {
            RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), approval.getId()));
            SearchResultResourceRep searchResultResourceRep = new SearchResultResourceRep();
            searchResultResourceRep.setId(approval.getId());
            searchResultResourceRep.setLink(selfLink);
            if (authorized || resRepFilter.isAccessible(searchResultResourceRep)) {
                searchResultResourceReps.add(searchResultResourceRep);
            }
        }

        SearchResults result = new SearchResults();
        result.setResource(searchResultResourceReps);
        return result;
    }

    /**
     * Get info for approval
     * 
     * @param id the URN of an approval
     * @prereq none
     * @brief Show approval
     * @return Approval details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public ApprovalRestRep getApproval(@PathParam("id") URI id) {
        ApprovalRequest approval = queryResource(id);
        return map(approval);
    }

    /**
     * Update approval
     * 
     * @param param Approval update parameters
     * @param id the URN the approval
     * @prereq none
     * @brief Update Approval
     * @return No data returned in response body
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_APPROVER })
    
    public ApprovalRestRep updateApproval(@PathParam("id") URI id, ApprovalUpdateParam param) {
        ApprovalRequest approval = getApprovalById(id, true);

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(approval.getTenant()), user);
        
        Order order = orderManager.getOrderById(approval.getOrderId());
        if (order.getSubmittedByUserId().equals(user.getUserName()) && 
                param.getApprovalStatus().equals(ApprovalStatus.APPROVED.toString())) {
            throw APIException.badRequests.updateApprovalBySameUser(
                    user.getUserName());
        }

        validateParam(param, approval);

        updateObject(approval, param);

        approvalManager.updateApproval(approval, user);

        if (approval.getOrderId() != null) {
            if (order != null) {
                orderManager.processOrder(order);
            }
        }

        auditOpSuccess(OperationTypeEnum.UPDATE_APPROVAL, approval.auditParameters());

        approval = approvalManager.getApprovalById(approval.getId());

        return map(approval);
    }

    /**
     * Gets the list of approvals
     * 
     * @param tenantId the URN of a tenant
     * @brief List Approvals
     * @return a list of approvals
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ApprovalList getApprovals(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId)
            throws DatabaseException {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), getUserFromContext());

        List<ApprovalRequest> approvals = approvalManager.getApprovals(uri(tenantId));

        ApprovalList approvalList = new ApprovalList();
        for (ApprovalRequest approval : approvals) {
            NamedRelatedResourceRep approvalRestRep = toNamedRelatedResource(ResourceTypeEnum.APPROVAL,
                    approval.getId(), approval.getLabel());
            approvalList.getApprovals().add(approvalRestRep);
        }

        return approvalList;
    }

    private ApprovalRequest getApprovalById(URI id, boolean checkInactive) {
        ApprovalRequest approval = approvalManager.getApprovalById(id);
        ArgValidator.checkEntity(approval, id, isIdEmbeddedInURL(id), checkInactive);
        return approval;
    }

    private void validateParam(ApprovalCommonParam input, ApprovalRequest existingApproval) {
        // Are we approving or rejecting?
        if (StringUtils.equalsIgnoreCase(input.getApprovalStatus(), ApprovalStatus.PENDING.name()) == false) {
            // Is existing approval not in pending state
            if (existingApproval.pending() == false) {
                throw APIException.badRequests.updatingCompletedApproval();
            }
        }
    }

    public static class ApprovalResRepFilter<E extends RelatedResourceRep> extends ResRepFilter<E>
    {
        public ApprovalResRepFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            ApprovalRequest obj = _permissionsHelper.getObjectById(id, ApprovalRequest.class);
            if (obj == null) {
                return false;
            }
            if (obj.getTenant().toString().equals(_user.getTenantId())) {
                return true;
            }
            ret = isTenantAccessible(uri(obj.getTenant()));
            return ret;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ApprovalRequest> getResourceClass() {
        return ApprovalRequest.class;
    }

    @Override
    public ApprovalBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ApprovalRequest> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ApprovalBulkRep(BulkList.wrapping(_dbIterator, ApprovalMapper.getInstance()));
    }

    @Override
    public ApprovalBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<ApprovalRequest> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new ApprovalFilter(getUserFromContext(), _permissionsHelper);
        return new ApprovalBulkRep(BulkList.wrapping(_dbIterator, ApprovalMapper.getInstance(), filter));
    }

}
