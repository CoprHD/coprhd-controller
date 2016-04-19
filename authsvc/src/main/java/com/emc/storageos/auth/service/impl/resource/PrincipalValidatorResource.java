/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl.resource;

import com.emc.storageos.auth.AuthenticationManager;
import com.emc.storageos.auth.AuthenticationManager.ValidationFailureReason;
import com.emc.storageos.model.auth.PrincipalsToValidate;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.resource.UserInfoPage.UserDetails;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * internal resource to validate group and subject principals
 */
@Path("/internal")
public class PrincipalValidatorResource {

    @Autowired
    protected AuthenticationManager _authManager;

    private static final Logger _log = LoggerFactory
            .getLogger(PrincipalValidatorResource.class);

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/principalValidate")
    public Response validatePrincipal(@QueryParam("subject_id") String subjectId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("alt_tenant_id") String altTenantId,
            @QueryParam("group") String groupId) {
        String principal = null;
        ValidationFailureReason[] reason = { ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT };
        if (null != subjectId && null != tenantId) {
            _authManager.validateUser(subjectId, tenantId, altTenantId);
            return Response.ok().build();
        } else if (null != groupId) {
            principal = groupId;
            if (_authManager.isGroupValid(groupId, reason)) {
                return Response.ok().build();
            }
        }

        switch (reason[0]) {
            case LDAP_MANAGER_AUTH_FAILED:
                throw SecurityException.fatals.ldapManagerAuthenticationFailed();
            case LDAP_CONNECTION_FAILED:
                throw SecurityException.fatals.communicationToLDAPResourceFailed();
            case LDAP_CANNOT_SEARCH_GROUP_IN_LDAP_MODE:
                throw APIException.badRequests.
                        authnProviderGroupObjectClassesAndMemberAttributesIsEmpty(groupId);
            default:
            case USER_OR_GROUP_NOT_FOUND_FOR_TENANT:
                throw APIException.badRequests.principalSearchFailed(principal);
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/userDetails")
    public UserDetails getUserDetails(@QueryParam("username") String username) {
        return _authManager.getUserDetails(username);
    }

    @PUT
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/refreshUser")
    public Response refreshUser(@QueryParam("username") String username) {
        _authManager.refreshUser(username);
        return Response.ok().build();
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/principalsValidate")
    public Response validatePrincipals(PrincipalsToValidate principalsToValidate) {
        List<String> invalidPrincipals = new ArrayList<String>();

        if (!CollectionUtils.isEmpty(principalsToValidate.getGroups())) {
            for (String group : principalsToValidate.getGroups()) {
                try {
                    validatePrincipal(null, principalsToValidate.getTenantId(), null,
                            group);
                } catch (APIException e) {
                    invalidPrincipals.add(group + " : " + e.getMessage());
                }
            }
        }

        if (!CollectionUtils.isEmpty(principalsToValidate.getUsers())) {
            for (String user : principalsToValidate.getUsers()) {
                try {
                    validatePrincipal(user, principalsToValidate.getTenantId(), null,
                            null);
                } catch (APIException e) {
                    invalidPrincipals.add(user + " : " + e.getMessage());
                }
            }
        }

        if (!CollectionUtils.isEmpty(principalsToValidate.getAltTenantUsers())) {
            for (String user : principalsToValidate.getAltTenantUsers()) {
                try {
                    validatePrincipal(user, principalsToValidate.getTenantId(),
                            principalsToValidate.getAltTenantId(), null);
                } catch (APIException e) {
                    invalidPrincipals.add(user + " : " + e.getMessage());
                }
            }
        }
        if (CollectionUtils.isEmpty(invalidPrincipals)) {
            return Response.ok().build();
        }

        throw APIException.badRequests.invalidPrincipals(StringUtils.join(invalidPrincipals, ", "));
    }
}
