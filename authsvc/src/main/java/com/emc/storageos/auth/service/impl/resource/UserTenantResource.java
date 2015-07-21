/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl.resource;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.validator.MarshallUtil;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.AuthenticationManager;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.resource.UserInfoPage.UserTenant;
import com.emc.storageos.security.resource.UserInfoPage.UserTenantList;

/**
 * Internal resource to query a user's tenancy
 */
@Path("/internal/userTenant")
public class UserTenantResource {

    private static final Logger _log = LoggerFactory.getLogger (UserTenantResource.class);
    @Autowired
    protected AuthenticationManager _authManager;
    
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Response getUserTenant(@QueryParam("username") String username,
                                  @QueryParam("tenantURI") String tenantURI,
                                  @QueryParam("usermappings") String strUserMappings)
    {
        if( username==null || username.isEmpty() ) {
            Response.status(Status.BAD_REQUEST).entity("Query parameter username is required").build();
        }

        Map<URI, UserMapping> userTenants = null;
        if (StringUtils.isEmpty(tenantURI)) {
            userTenants = _authManager.getUserTenants(username);
        } else {
            List<UserMapping> userMappings = null;

            if (!StringUtils.isEmpty(strUserMappings)) {
                userMappings = MarshallUtil.convertStringToUserMappingList(strUserMappings);
                _log.debug("usermapping parameter after convert: " + userMappings);
            }

            userTenants = _authManager.peekUserTenants(username, URI.create(tenantURI), userMappings);
        }
        if( null != userTenants ) {
            UserTenantList userTenantList = new UserTenantList();
            userTenantList._userTenantList = new ArrayList<UserTenant>();
            for(Entry<URI, UserMapping> userTenantEntry :userTenants.entrySet() ) {
                UserTenant userTenant = new UserTenant();
                userTenant._id = userTenantEntry.getKey();
                userTenant._userMapping = userTenantEntry.getValue();                
                userTenantList._userTenantList.add(userTenant);
            }
            return Response.ok(userTenantList).build();
        }
        return Response.status(Status.BAD_REQUEST).entity(String.format("Invalid username %1s", username)).build();
    }
}
