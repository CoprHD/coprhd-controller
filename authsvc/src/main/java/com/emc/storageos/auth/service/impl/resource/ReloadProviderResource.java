/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
