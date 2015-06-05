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

package com.emc.storageos.systemservices.impl.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.vipr.model.sys.licensing.License;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;

/**
 * Defines the API for making requests to the log service.
 */
@Path("/license")
public interface LicensingService {
    /**
     * Return the license file as individual xml elements and also includes the full license text.
     * @brief Show the license information
     * @prereq none
     * @throws WebApplicationException
     */
    @GET
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.SECURITY_ADMIN})
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public License getLicense() throws Exception;

    /**
     * Add a license to the system
     * @brief Add a license
     * @param license License text
     * @prereq Cluster state should be STABLE
     * @throws Exception
     */
    @POST
    @CheckPermission(roles = {Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN})
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response postLicense(License license) throws Exception;
}
