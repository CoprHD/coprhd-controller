/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.pathparam.ExportPathCreateParam;
import com.emc.storageos.model.block.pathparam.ExportPathParmsRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * @author hariks
 *
 */
@Path("/block/export-path-parameters")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ExportPathParametersService extends TaggedResource {

    private static final Logger _log = LoggerFactory.getLogger(BlockVirtualPoolService.class);
    private static final String EXPORT_PATH_PARAM_NAME = "name";

    /**
     * @param param
     * @return
     * @throws DatabaseException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ExportPathParmsRestRep createExportPathParameters(ExportPathCreateParam param) throws DatabaseException {

        // Make path param name as mandatory field
        ArgValidator.checkFieldNotEmpty(param.getName(), EXPORT_PATH_PARAM_NAME);
        // Check for duplicate entry
        checkForDuplicateName(param.getName(), ExportPathParams.class);

        _log.info("Export Path Params creation started -- ");

        if (!rangeCheck(param.getMaxPaths(), 1, 65535)) {
            _log.error("Failed to create export path params due to Max Path not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Max Path not in the range");
        }

        if (!rangeCheck(param.getMinPaths(), 1, 65535)) {
            _log.error("Failed to create export path params due to Min Path not in the range");
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getName(), "Min Path not in the range");
        }

        if (!rangeCheck(param.getPathsPerInitiator(), 1, 65535)) {
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

        auditOp(OperationTypeEnum.EXPORT_PATH_PARAMS_CREATE, true, null, exportPathParams.getName(),
                exportPathParams.getMaxPaths().toString(), exportPathParams.getMinPaths().toString(),
                exportPathParams.getPathsPerInitiator().toString(), exportPathParams.getMaxInitiatorsPerPort().toString());

        return map(exportPathParams);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ExportPathParmsRestRep getExportPathParameters(@PathParam("id") URI id) {
        ExportPathParams pathParams = queryResource(id);
        return map(pathParams);

    }

    @Override
    protected ExportPathParams queryResource(URI id) {
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

    private ExportPathParams prepareExportPathParams(ExportPathCreateParam param) {
        ExportPathParams params = new ExportPathParams();
        params.setId(URIUtil.createId(ExportPathParams.class));
        params.setName(param.getName());
        params.setMaxPaths(param.getMaxPaths());
        params.setMinPaths(param.getMinPaths());
        params.setPathsPerInitiator(param.getPathsPerInitiator());
        if (param.getMaxInitiatorsPerPort() != null) {
            params.setMaxInitiatorsPerPort(param.getMaxInitiatorsPerPort());
        }

        return params;
    }

    private boolean rangeCheck(Integer value, Integer min, Integer max) {
        return (value >= min && value <= max) ? true : false;
    }

    private ExportPathParmsRestRep map(ExportPathParams exportPathParams) {
        ExportPathParmsRestRep restRep = new ExportPathParmsRestRep();
        restRep.setId(exportPathParams.getId());
        restRep.setName(exportPathParams.getName());
        restRep.setMaxPaths(exportPathParams.getMaxPaths());
        restRep.setMinPaths(exportPathParams.getMinPaths());
        restRep.setMaxInitiatorsPerPort(exportPathParams.getMaxInitiatorsPerPort());
        restRep.setPathsPerInitiator(exportPathParams.getPathsPerInitiator());
        return restRep;
    }

}
