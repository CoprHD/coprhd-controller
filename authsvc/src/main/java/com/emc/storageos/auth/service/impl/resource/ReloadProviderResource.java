/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl.resource;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.AuthenticationManager;

/**
 * internal resource to tell the authentication manager
 * to reload/refresh all authn providers from the database.
 */
@Path("/internal/reload")
public class ReloadProviderResource {

    @Autowired
    protected AuthenticationManager _authManager;

    @POST
    public Response reload() {
        _authManager.reload();
        return Response.ok().build();
    }
}
