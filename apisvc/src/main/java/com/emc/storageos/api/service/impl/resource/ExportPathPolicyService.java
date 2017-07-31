/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapExportPathPolicy;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TagAssignment;
import com.emc.storageos.model.block.export.ExportPathPoliciesBulkRep;
import com.emc.storageos.model.block.export.ExportPathPoliciesList;
import com.emc.storageos.model.block.export.ExportPathPolicy;
import com.emc.storageos.model.block.export.ExportPathPolicyRestRep;
import com.emc.storageos.model.block.export.ExportPathPolicyUpdate;
import com.emc.storageos.model.block.export.StoragePorts;
import com.emc.storageos.model.search.Tags;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.InheritCheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.google.common.collect.Lists;

@Path("/block/export-path-policies")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ExportPathPolicyService extends TaggedResource {

    private static final Logger _log = LoggerFactory.getLogger(BlockVirtualPoolService.class);
    private static final String EXPORT_PATH_POLICY_NAME = "name";

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ExportPathPolicyRestRep createExportPathPolicy(ExportPathPolicy policy) throws DatabaseException {

        // Make path param name and port group flag as mandatory field
        ArgValidator.checkFieldNotEmpty(policy.getName(), EXPORT_PATH_POLICY_NAME);

        // Check for duplicate entry
        checkForDuplicateName(policy.getName(), ExportPathParams.class);

        _log.info("Export Path Policy creation started -- ");

        if (policy.getMaxPaths() != null && !rangeCheck(policy.getMaxPaths(), 1, 65535)) {
            _log.error("Failed to create export path policy due to Max Path not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(policy.getName(), "Max Path not in the range");
        }

        if (policy.getMinPaths() != null && !rangeCheck(policy.getMinPaths(), 1, 65535)) {
            _log.error("Failed to create export path policy due to Min Path not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(policy.getName(), "Min Path not in the range");
        }

        if (policy.getPathsPerInitiator() != null && !rangeCheck(policy.getPathsPerInitiator(), 1, 65535)) {
            _log.error("Failed to create export path policy due to Path Per Initiator not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(policy.getName(), "Path Per Initiator not in the range");
        }
        if (policy.getMaxInitiatorsPerPort() != null) {
            if (!rangeCheck(policy.getMaxInitiatorsPerPort(), 1, 65535)) {
                _log.error("Failed to create export path policy due to Initiators Per Port not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(policy.getName(), "Initiators Per Port not in the range");
            }
        }

        ExportPathParams exportPathParams = prepareExportPathPolicy(policy);

        _dbClient.createObject(exportPathParams);
        _log.info("Export Path Paramters {} created successfully", exportPathParams);

        return map(exportPathParams);

    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ExportPathPolicyRestRep getExportPathPolicy(@PathParam("id") URI id) {
        ExportPathParams pathParams = queryResource(id);
        return map(pathParams);

    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ExportPathPoliciesList getExportPathPolicyList(@DefaultValue("false") @QueryParam("list_all") boolean listAll) {
        ExportPathPoliciesList policiesList = new ExportPathPoliciesList();
        List<ExportPathParams> pathParamsList = null;
        if (listAll) {
            // List all entries including implicitly created
            List<URI> exportPathParams = _dbClient.queryByType(ExportPathParams.class, true);
            for (URI uri : exportPathParams) {
                ExportPathParams pathParams = _dbClient.queryObject(ExportPathParams.class, uri);
                if (pathParams != null && !pathParams.getInactive()) {
                    policiesList.getPathParamsList().add(
                            toNamedRelatedResource(ResourceTypeEnum.EXPORT_PATH_POLICY, pathParams.getId(), pathParams.getLabel()));
                }
            }
        } else {
            // List only explicitly created entries
            pathParamsList = CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, ExportPathParams.class, "explicitlyCreated", Boolean.TRUE.toString());
            for (ExportPathParams pathParams : pathParamsList) {
                if (pathParams != null && !pathParams.getInactive()) {
                    policiesList.getPathParamsList().add(
                            toNamedRelatedResource(ResourceTypeEnum.EXPORT_PATH_POLICY, pathParams.getId(), pathParams.getLabel()));
                }
            }
        }
        return policiesList;
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response updateExportPathPolicy(@PathParam("id") URI id, ExportPathPolicyUpdate param) throws DatabaseException {
        ArgValidator.checkFieldUriType(id, ExportPathParams.class, "id");
        ExportPathParams exportPathParams = updatePathPolicy(param, id);

        _dbClient.updateObject(exportPathParams);
        _log.info("Export Path Paramters {} updated successfully", exportPathParams);

        return Response.ok().build();
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteExportPathPolicy(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ExportPathParams.class, "id");
        ExportPathParams exportPathParams = queryResource(id);
        ArgValidator.checkReference(ExportPathParams.class, id, checkForDelete(exportPathParams));
        _dbClient.markForDeletion(exportPathParams);
        _log.info("Export Path Paramters {} deleted successfully", exportPathParams.getLabel());
        return Response.ok().build();
    }
    
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ExportPathPoliciesBulkRep getBulkResources(BulkIdParam param) {
        return (ExportPathPoliciesBulkRep) super.getBulkResources(param);
    }
    
    @GET
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BulkIdParam getBulkIds() {
        return super.getBulkIds();
    }

    @Override
    public ExportPathPoliciesBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<ExportPathParams> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new ExportPathPoliciesBulkRep(BulkList.wrapping(_dbIterator,
                MapExportPathPolicy.getInstance(_dbClient)));
    }

    @Override
    public ExportPathPoliciesBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<ExportPathParams> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.ExportPathParamsFilter(getUserFromContext(), _permissionsHelper);
        return new ExportPathPoliciesBulkRep(BulkList.wrapping(_dbIterator,
                MapExportPathPolicy.getInstance(_dbClient), filter));
    }

    @Override
    protected ExportPathParams queryResource(URI id) {
        if (id == null) {
            return null;
        }
        ExportPathParams exportPathParams = _permissionsHelper.getObjectById(id, ExportPathParams.class);
        ArgValidator.checkEntityNotNull(exportPathParams, id, isIdEmbeddedInURL(id));
        return exportPathParams;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.EXPORT_PATH_POLICY;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Class<ExportPathParams> getResourceClass() {
        return ExportPathParams.class;
    }

    private boolean rangeCheck(Integer value, Integer min, Integer max) {
        return (value >= min && value <= max) ? true : false;
    }

    private ExportPathPolicyRestRep map(ExportPathParams exportPathParams) {
        ExportPathPolicyRestRep restRep = new ExportPathPolicyRestRep();
        restRep.setId(exportPathParams.getId());
        restRep.setName(exportPathParams.getLabel());
        restRep.setMaxPaths(exportPathParams.getMaxPaths());
        restRep.setMinPaths(exportPathParams.getMinPaths());
        restRep.setMaxInitiatorsPerPort(exportPathParams.getMaxInitiatorsPerPort());
        restRep.setPathsPerInitiator(exportPathParams.getPathsPerInitiator());
        restRep.setDescription(exportPathParams.getDescription());
        StringSet exportPathParamSet = exportPathParams.getStoragePorts();
        List<URI> portUris = Lists.newArrayList();
        for (String uriString : exportPathParamSet) {
            portUris.add(URIUtil.uri(uriString));
        }
        restRep.setStoragePorts(portUris);
        return restRep;
    }

    private ExportPathParams prepareExportPathPolicy(ExportPathPolicy param) {
        ExportPathParams params = new ExportPathParams();
        params.setId(URIUtil.createId(ExportPathParams.class));
        params.setLabel(param.getName());
        params.setDescription(param.getDescription());
        params.setMaxPaths(param.getMaxPaths());
        params.setMinPaths(param.getMinPaths());
        params.setPathsPerInitiator(param.getPathsPerInitiator());
        params.setExplicitlyCreated(true);
        if (param.getMaxInitiatorsPerPort() != null) {
            params.setMaxInitiatorsPerPort(param.getMaxInitiatorsPerPort());
        }
        List<URI> portUris = param.getStoragePorts();
        StringSet exportPathParamSet = new StringSet();
        if (portUris != null) {
            for (URI uri : portUris) {
                exportPathParamSet.add(URIUtil.asString(uri));
            }
            params.setStoragePorts(exportPathParamSet);
        }
        return params;
    }

    private ExportPathParams updatePathPolicy(ExportPathPolicyUpdate param, URI id) {
        ExportPathParams dbparams = queryResource(id);
        dbparams.setExplicitlyCreated(true);

        _log.info("Export Path Policy {} update started", dbparams.getLabel());
        if (param.getName() != null) {
            dbparams.setLabel(param.getName());
        }
        if (param.getDescription() != null) {
            dbparams.setDescription(param.getDescription());
        }

        if (param.getMaxPaths() != null) {
            if (!rangeCheck(param.getMaxPaths(), 1, 65535)) {
                _log.error("Failed to update export path policy due to Max Path not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Max Path not in the range");
            }
            dbparams.setMaxPaths(param.getMaxPaths());
        }
        if (param.getMinPaths() != null) {
            if (!rangeCheck(param.getMinPaths(), 1, 65535)) {
                _log.error("Failed to update export path policy due to Min Path not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Min Path not in the range");
            }
            dbparams.setMinPaths(param.getMinPaths());
        }
        if (param.getPathsPerInitiator() != null) {
            if (!rangeCheck(param.getPathsPerInitiator(), 1, 65535)) {
                _log.error("Failed to update export path policy due to Path Per Initiator not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Path Per Initiator not in the range");
            }
            dbparams.setPathsPerInitiator(param.getPathsPerInitiator());
        }
        if (param.getMaxInitiatorsPerPort() != null) {
            if (!rangeCheck(param.getMaxInitiatorsPerPort(), 1, 65535)) {
                _log.error("Failed to update export path policy due to Initiators Per Port not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Initiators Per Port not in the range");
            }
            dbparams.setMaxInitiatorsPerPort(param.getMaxInitiatorsPerPort());
        }

        // Handle addition and removal of ports
        boolean portsChanged = false;
        StringSet updatedPorts = dbparams.getStoragePorts();
        dbparams.setStoragePorts(updatedPorts);
        if (param.getPortsToAdd() != null) {
            List<String> portsToAdd = URIUtil.asStrings(param.getPortsToAdd());
            for (String portToAdd : portsToAdd) {
                updatedPorts.add(portToAdd);
            }
        }
        if (param.getPortsToRemove() != null) {
            List<String> portsToRemove = URIUtil.asStrings(param.getPortsToRemove());
            for (String portToRemove : portsToRemove) {
                updatedPorts.remove(portToRemove);
            }
        }
        return dbparams;
    }
    /**
     * @brief Assign tags to resource
     *        Assign tags
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR resource
     * @param assignment tag assignments
     * @return No data returned in response body
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tags")
    @InheritCheckPermission(writeAccess = true)
    @Override
    public Tags assignTags(@PathParam("id") URI id, TagAssignment assignment) {
        // Validate that the passed add tags are of are unique to this policy if they are storage systems
        for (String tag : assignment.getAdd()) {
            Type type = null;
            try {
                type = Type.valueOf(tag);
            } catch (IllegalArgumentException ex) {
                // process non array types as regular tags
                if (!tag.equals("default")) {
                    continue;
                }
            }
            ExportPathParams hasTag = BlockStorageScheduler.findDefaultExportPathPolicyForArray(_dbClient, tag);
            if (hasTag != null && hasTag.getId() != id) {
                ScopedLabel scopedLabel = new ScopedLabel(null, tag);
                hasTag.getTag().remove(scopedLabel);
                _dbClient.updateObject(hasTag);
            }
        }
        return super.assignTags(id, assignment);
    }
}
