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
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapExportPathParams;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathParametersBulkRep;
import com.emc.storageos.model.block.export.ExportPathParametersList;
import com.emc.storageos.model.block.export.ExportPathParametersRestRep;
import com.emc.storageos.model.block.export.ExportPathUpdateParams;
import com.emc.storageos.model.block.export.StoragePorts;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.Lists;

@Path("/block/export-path-parameters")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ExportPathParametersService extends TaggedResource {

    private static final Logger _log = LoggerFactory.getLogger(BlockVirtualPoolService.class);
    private static final String EXPORT_PATH_PARAM_NAME = "name";

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ExportPathParametersRestRep createExportPathParameters(ExportPathParameters param) throws DatabaseException {

        // Make path param name and port group flag as mandatory field
        ArgValidator.checkFieldNotEmpty(param.getName(), EXPORT_PATH_PARAM_NAME);

        // Check for duplicate entry
        checkForDuplicateName(param.getName(), ExportPathParams.class);

        _log.info("Export Path Params creation started -- ");

        if (param.getMaxPaths() != null && !rangeCheck(param.getMaxPaths(), 1, 65535)) {
            _log.error("Failed to create export path params due to Max Path not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Max Path not in the range");
        }

        if (param.getMinPaths() != null && !rangeCheck(param.getMinPaths(), 1, 65535)) {
            _log.error("Failed to create export path params due to Min Path not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Min Path not in the range");
        }

        if (param.getPathsPerInitiator() != null && !rangeCheck(param.getPathsPerInitiator(), 1, 65535)) {
            _log.error("Failed to create export path params due to Path Per Initiator not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Path Per Initiator not in the range");
        }
        if (param.getMaxInitiatorsPerPort() != null) {
            if (!rangeCheck(param.getMaxInitiatorsPerPort(), 1, 65535)) {
                _log.error("Failed to create export path params due to Initiators Per Port not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Initiators Per Port not in the range");
            }
        }

        ExportPathParams exportPathParams = prepareExportPathParams(param);

        _dbClient.createObject(exportPathParams);
        _log.info("Export Path Paramters {} created successfully", exportPathParams);

        return map(exportPathParams);

    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ExportPathParametersRestRep getExportPathParameters(@PathParam("id") URI id) {
        ExportPathParams pathParams = queryResource(id);
        return map(pathParams);

    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ExportPathParametersList getExportPathParametersList() {
        List<URI> exportPathParams = _dbClient.queryByType(ExportPathParams.class, true);
        ExportPathParametersList pathParamsList = new ExportPathParametersList();
        for (URI uri : exportPathParams) {
            ExportPathParams pathParams = _dbClient.queryObject(ExportPathParams.class, uri);
            if (pathParams != null && !pathParams.getInactive()) {
                pathParamsList.getPathParamsList().add(
                        toNamedRelatedResource(ResourceTypeEnum.EXPORT_PATH_PARAMETERS, pathParams.getId(), pathParams.getLabel()));
            }
        }
        return pathParamsList;
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response updateExportPathParameters(@PathParam("id") URI id, ExportPathUpdateParams param) throws DatabaseException {
        ArgValidator.checkFieldUriType(id, ExportPathParams.class, "id");
        ExportPathParams exportPathParams = updateExportPathParams(param, id);

        _dbClient.updateObject(exportPathParams);
        _log.info("Export Path Paramters {} updated successfully", exportPathParams);

        return Response.ok().build();
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteExportPathParameters(@PathParam("id") URI id) {
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
    public ExportPathParametersBulkRep getBulkResources(BulkIdParam param) {
        return (ExportPathParametersBulkRep) super.getBulkResources(param);
    }
    
    @GET
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BulkIdParam getBulkIds() {
        return super.getBulkIds();
    }

    @Override
    public ExportPathParametersBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<ExportPathParams> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new ExportPathParametersBulkRep(BulkList.wrapping(_dbIterator,
                MapExportPathParams.getInstance(_dbClient)));
    }

    @Override
    public ExportPathParametersBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<ExportPathParams> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.ExportPathParamsFilter(getUserFromContext(), _permissionsHelper);
        return new ExportPathParametersBulkRep(BulkList.wrapping(_dbIterator,
                MapExportPathParams.getInstance(_dbClient), filter));
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
        return ResourceTypeEnum.EXPORT_PATH_PARAMETERS;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Class<ExportPathParams> getResourceClass() {
        return ExportPathParams.class;
    }

    private boolean rangeCheck(Integer value, Integer min, Integer max) {
        return (value >= min && value <= max) ? true : false;
    }

    private ExportPathParametersRestRep map(ExportPathParams exportPathParams) {
        ExportPathParametersRestRep restRep = new ExportPathParametersRestRep();
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

    private ExportPathParams prepareExportPathParams(ExportPathParameters param) {
        ExportPathParams params = new ExportPathParams();
        params.setId(URIUtil.createId(ExportPathParams.class));
        params.setLabel(param.getName());
        params.setDescription(param.getDescription());
        params.setMaxPaths(param.getMaxPaths());
        params.setMinPaths(param.getMinPaths());
        params.setPathsPerInitiator(param.getPathsPerInitiator());
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

    private ExportPathParams updateExportPathParams(ExportPathUpdateParams param, URI id) {
        ExportPathParams params = queryResource(id);

        _log.info("Export Path Parameters {} update started", params.getLabel());
        if (param.getName() != null) {
            params.setLabel(param.getName());
        }
        if (param.getDescription() != null) {
            params.setDescription(param.getDescription());
        }

        if (param.getMaxPaths() != null) {
            if (!rangeCheck(param.getMaxPaths(), 1, 65535)) {
                _log.error("Failed to create export path params due to Max Path not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Max Path not in the range");
            }
            params.setMaxPaths(param.getMaxPaths());
        }
        if (param.getMinPaths() != null) {
            if (!rangeCheck(param.getMinPaths(), 1, 65535)) {
                _log.error("Failed to create export path params due to Min Path not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Min Path not in the range");
            }
            params.setMinPaths(param.getMinPaths());
        }
        if (param.getPathsPerInitiator() != null) {
            if (!rangeCheck(param.getPathsPerInitiator(), 1, 65535)) {
                _log.error("Failed to create export path params due to Path Per Initiator not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Path Per Initiator not in the range");
            }
            params.setPathsPerInitiator(param.getPathsPerInitiator());
        }
        if (param.getMaxInitiatorsPerPort() != null) {
            if (!rangeCheck(param.getMaxInitiatorsPerPort(), 1, 65535)) {
                _log.error("Failed to create export path params due to Initiators Per Port not in the range");
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Initiators Per Port not in the range");
            }
            params.setMaxInitiatorsPerPort(param.getMaxInitiatorsPerPort());
        }

        StoragePorts portsToAdd = new StoragePorts();
        StoragePorts portsToRemove = new StoragePorts();
        List<URI> addList = Lists.newArrayList();
        List<URI> removeList = Lists.newArrayList();

        if (param.getPortsToAdd() != null) {
            portsToAdd = param.getPortsToAdd();
        }

        if (param.getPortsToRemove() != null) {
            portsToRemove = param.getPortsToRemove();
        }
        if (portsToAdd.getStoragePorts() != null) {
            addList = portsToAdd.getStoragePorts();
        }
        if (portsToRemove.getStoragePorts() != null) {
            removeList = portsToRemove.getStoragePorts();
        }

        StringSet setToAdd = new StringSet();
        StringSet setToRemove = new StringSet();
        for (URI portToBeAdded : addList) {
            setToAdd.add(portToBeAdded.toString());
        }
        for (URI portToBeRemoved : removeList) {
            setToRemove.add(portToBeRemoved.toString());
        }

        StringSet portsTobeUpdate = params.getStoragePorts();
        portsTobeUpdate.addAll(setToAdd);
        portsTobeUpdate.removeAll(setToRemove);
        
        params.setStoragePorts(portsTobeUpdate);

        return params;
    }

}
