/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.ExecutionWindowMapper.createNewObject;
import static com.emc.sa.api.mapper.ExecutionWindowMapper.map;
import static com.emc.sa.api.mapper.ExecutionWindowMapper.updateObject;
import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.CatalogServiceMapper;
import com.emc.sa.api.mapper.ExecutionWindowFilter;
import com.emc.sa.api.mapper.ExecutionWindowMapper;
import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.ExecutionWindowManager;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.CatalogServiceList;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowBulkRep;
import com.emc.vipr.model.catalog.ExecutionWindowCommonParam;
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam;
import com.emc.vipr.model.catalog.ExecutionWindowList;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowUpdateParam;

@DefaultPermissions(
        read_roles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        write_roles = { Role.TENANT_ADMIN },
        read_acls = { ACL.ANY })
@Path("/catalog/execution-windows")
public class ExecutionWindowService extends CatalogTaggedResourceService {

    private static final String EVENT_SERVICE_TYPE = "catalog-execution-window";

    @Autowired
    private ExecutionWindowManager executionWindowManager;

    @Autowired
    private RecordableEventManager eventManager;

    @Override
    protected ExecutionWindow queryResource(URI id) {
        return getExecutionWindowById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        ExecutionWindow executionWindow = queryResource(id);
        return uri(executionWindow.getTenant());
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.EXECUTION_WINDOW;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get info for catalog category
     * 
     * @param id the URN of a Catalog Category
     * @prereq none
     * @brief Show catalog category
     * @return Catalog Category details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public ExecutionWindowRestRep getExecutionWindow(@PathParam("id") URI id) {
        ExecutionWindow executionWindow = queryResource(id);
        return map(executionWindow);
    }

    /**
     * Creates a new execution window
     * 
     * @param createParam
     *            the parameter to create a new execution window
     * @prereq none
     * @brief Create Execution Window
     * @return none
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    @Path("")
    public ExecutionWindowRestRep createExecutionWindow(ExecutionWindowCreateParam createParam) {

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(createParam.getTenant(), user);

        validateParam(createParam, null, user.getTenantId());

        ExecutionWindow executionWindow = createNewObject(createParam);

        executionWindowManager.createExecutionWindow(executionWindow);

        auditOpSuccess(OperationTypeEnum.CREATE_EXECUTION_WINDOW, executionWindow.auditParameters());

        return map(executionWindow);
    }

    /**
     * Update execution window
     * 
     * @param param Execution Window update parameters
     * @param id the URN the execution window
     * @prereq none
     * @brief Update Execution Window
     * @return No data returned in response body
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public ExecutionWindowRestRep updateExecutionWindow(@PathParam("id") URI id, ExecutionWindowUpdateParam param) {
        ExecutionWindow executionWindow = getExecutionWindowById(id, true);

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(executionWindow.getTenant()), user);

        validateParam(param, executionWindow, user.getTenantId());

        updateObject(executionWindow, param);

        executionWindowManager.updateExecutionWindow(executionWindow);

        auditOpSuccess(OperationTypeEnum.UPDATE_EXECUTION_WINDOW, executionWindow.auditParameters());

        executionWindow = executionWindowManager.getExecutionWindowById(executionWindow.getId());

        return map(executionWindow);
    }

    /**
     * Deactivates the execution window
     * 
     * @param id the URN of an execution window to be deactivated
     * @brief Deactivate Execution Window
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deactivateExecutionWindow(@PathParam("id") URI id) throws DatabaseException {
        ExecutionWindow executionWindow = queryResource(id);
        ArgValidator.checkEntity(executionWindow, id, true);

        executionWindowManager.deleteExecutionWindow(executionWindow);

        auditOpSuccess(OperationTypeEnum.DELETE_EXECUTION_WINDOW, executionWindow.auditParameters());

        return Response.ok().build();
    }

    /**
     * Gets the list of execution windows
     * 
     * @param tenantId the URN of a tenant
     * @brief List Execution Windows
     * @return a list of execution windows
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ExecutionWindowList getExecutionWindows(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId)
            throws DatabaseException {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), getUserFromContext());

        List<ExecutionWindow> executionWindows = executionWindowManager.getExecutionWindows(uri(tenantId));

        ExecutionWindowList list = new ExecutionWindowList();
        for (ExecutionWindow executionWindow : executionWindows) {
            NamedRelatedResourceRep resourceRep = toNamedRelatedResource(ResourceTypeEnum.EXECUTION_WINDOW,
                    executionWindow.getId(), executionWindow.getLabel());
            list.getExecutionWindows().add(resourceRep);
        }

        return list;
    }

    private ExecutionWindow getExecutionWindowById(URI id, boolean checkInactive) {
        ExecutionWindow executionWindow = executionWindowManager.getExecutionWindowById(id);
        ArgValidator.checkEntity(executionWindow, id, isIdEmbeddedInURL(id), checkInactive);
        return executionWindow;
    }

    private void validateParam(ExecutionWindowCommonParam input, ExecutionWindow existing, String tenantId) {

        // Execution Window Name Unique Check
        if (input.getName() != null) {
            String name = input.getName().trim();
            if (StringUtils.isNotBlank(name)) {
                ExecutionWindow existingExecutionWindow = executionWindowManager.getExecutionWindow(name, uri(tenantId));
                // already exists on create
                if (existing == null && existingExecutionWindow != null) {
                    throw APIException.badRequests.executionWindowAlreadyExists(name, tenantId);
                } else if (existing != null && (existingExecutionWindow != null
                        && !existing.getId().equals(existingExecutionWindow.getId()))) {
                    throw APIException.badRequests.executionWindowAlreadyExists(name, tenantId);
                }// already exists on update
            }
        }

        ValidationUtils.validateExecutionWindow(input);

        List<ExecutionWindow> existingWindows =
                executionWindowManager.getExecutionWindows(uri(tenantId));
        ExecutionWindow executionWindow = ExecutionWindowMapper.writeToTempWindow(input);
        ValidationUtils.isOverlapping(executionWindow, existingWindows);

    }

    /**
     * List data for the specified execution windows.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified windows
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
	@POST
	@Path("/bulk")
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Override
	public ExecutionWindowBulkRep getBulkResources(BulkIdParam param) {
	    return (ExecutionWindowBulkRep) super.getBulkResources(param);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Class<ExecutionWindow> getResourceClass() {
	    return ExecutionWindow.class;
	}
	
	@Override
	public ExecutionWindowBulkRep queryBulkResourceReps(List<URI> ids) {
	
	    Iterator<ExecutionWindow> _dbIterator =
	            _dbClient.queryIterativeObjects(getResourceClass(), ids);
	    return new ExecutionWindowBulkRep(BulkList.wrapping(_dbIterator, ExecutionWindowMapper.getInstance()));
	}
	
	@Override
	public ExecutionWindowBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
	
	    Iterator<ExecutionWindow> _dbIterator =
	            _dbClient.queryIterativeObjects(getResourceClass(), ids);
	    BulkList.ResourceFilter filter = new ExecutionWindowFilter(getUserFromContext(), _permissionsHelper);
	    return new ExecutionWindowBulkRep(BulkList.wrapping(_dbIterator, ExecutionWindowMapper.getInstance(), filter));
	}
	 
	/**     
	 * Get service associated with Execution Window 
	 * @param id the URN of an execution window
	 * @prereq none
	 * @brief Get catalog services
	 * @return Catalog Services details
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("/{id}/services")
	public CatalogServiceList getCatalogServices(@PathParam("id") URI id) {
	    List<CatalogService> catalogServices = executionWindowManager.getCatalogServices(id);
	    return CatalogServiceMapper.toCatalogServiceList(catalogServices);
	}   
}
