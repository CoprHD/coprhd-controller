/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import com.emc.vipr.model.sys.diagutil.DiagutilParam;
import com.emc.vipr.model.sys.diagutil.UploadParam;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.OutputStream;
import java.util.List;

/**
 * Defines the API for making request to diagnostics data collection service.
 */
@Path("/diagutil/")
public class DataCollectionService {
    @Autowired
    private CoordinatorClient coordinatorClient;
    private static final String key = "diagutilStatus";

    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response collectDiagnosticData(@QueryParam("options") List<String> options, DiagutilParam diagutilParam) {

        return Response.status(Response.Status.ACCEPTED).build();
    }

    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    public Response getDiagutilData() {
        OutputStream os = null;

        return Response.ok(os).build();
    }

    @GET
    @Path("/status")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public DiagutilInfo getDiagutilStatus() {
        DiagutilInfo diagutilInfo = new DiagutilInfo();
        Configuration config = coordinatorClient.queryConfiguration(key, Constants.GLOBAL_ID);
        if (config != null) {

        }
        return diagutilInfo;
    }

    @POST
    @Path("/upload")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response uploadDiagutilData(UploadParam uploadParam) {
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @DELETE
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteDiagutilJob() {

        return Response.ok().build();
    }

}
