/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.resource;

import java.net.URI;
import java.security.Principal;
import java.util.*;
import java.util.Map.Entry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.db.common.VdcUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.user.SubTenantRoles;
import com.emc.storageos.model.user.UserInfo;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.svcs.errorhandling.resources.APIException;


/**
 * user information resource
 */
@Path("/user")
public class UserInfoPage {

    @Context
    SecurityContext sc;

    @Autowired
    protected BasePermissionsHelper _permissionsHelper = null;

    @XmlRootElement(name="tenants")
    public static class UserTenantList {
        /**
         * List of tenancies to which the user maps
         * @valid none.
         */
        @XmlElement(name="tenant")
        public List<UserTenant> _userTenantList;
    }

    public static class UserTenant {
        /**
         * Tenant id corresponding to the user's Tenant
         * @valid none
         */
        @XmlElement(name="id")
        public URI _id;

        /**
         * Tenant mapping that resulted in the user being mapped
         * to this Tenant.
         * @valid none
         */
        @XmlElement(name="user_mapping")
        public UserMapping _userMapping;
    }

    @XmlRootElement(name = "user_details")
    public static class UserDetails {
        private String username;
        private String tenant;

        private List<String> userGroupList;

        public UserDetails() {
            userGroupList = new ArrayList<String>();
        }

        /**
         * @return the username
         */
        @XmlElement(name = "username")
        public String getUsername() {
            return username;
        }

        /**
         * @param username the username to set
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * @return the userGroupList - List of groups to which the user maps
         */
        @XmlElementWrapper(name = "groups")
        @XmlElement(name = "group")
        public List<String> getUserGroupList() {
            return userGroupList;
        }

        /**
         * @param userGroupList the userGroupList to set
         */
        public void setUserGroupList(List<String> userGroupList) {
            this.userGroupList = userGroupList;
        }

        /**
         * @return the tenant
         */
        @XmlElement(name = "tenant")
        public String getTenant() {
            return tenant;
        }

        /**
         * @param tenant
         *            the tenant to set
         */
        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

    /**
     * This call returns the list of tenants that the user maps to including the details of the mappings.
     * It also returns a list of the virtual data center roles and tenant roles assigned to this user.
     * @brief Show my Tenant and assigned roles
     * @prereq none
     * @return List of tenants user mappings,VDC role and tenant role of the user.
     */
    @GET
    @Path("/whoami")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UserInfo getMyInfo() {
        Principal principal = sc.getUserPrincipal();
        if (!(principal instanceof StorageOSUser)) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        StorageOSUser user = (StorageOSUser) principal;
        UserInfo info = new UserInfo();
        info.setCommonName(user.getName());
        // To Do - fix Distinguished name - for now setting it to name
        info.setDistinguishedName(user.getName());
        info.setTenant(user.getTenantId());
        info.setTenantName(_permissionsHelper.getTenantNameByID(user.getTenantId()));
        info.setVdcRoles(new ArrayList<String>());
        info.setHomeTenantRoles(new ArrayList<String>());
        info.setSubTenantRoles(new ArrayList<SubTenantRoles>());

        // special check: root in geo scenario
        boolean isLocalVdcSingleSite = VdcUtil.isLocalVdcSingleSite();
        boolean isRootInGeo = user.getName().equalsIgnoreCase("root") && (!isLocalVdcSingleSite);

        // add Vdc Roles
        if (user.getRoles() != null) {
            for (String role: user.getRoles()) {

                // geo scenario, return RESTRICTED_*_ADMIN for root, instead of *_ADMIN
                if (isRootInGeo) {
                    if (role.equalsIgnoreCase(Role.SYSTEM_ADMIN.toString())) {
                        role = Role.RESTRICTED_SYSTEM_ADMIN.toString();
                    }

                    if (role.equalsIgnoreCase(Role.SECURITY_ADMIN.toString())) {
                        role = Role.RESTRICTED_SECURITY_ADMIN.toString();
                    }
                }

                info.getVdcRoles().add(role);
            }
        }


        // geo scenario, skip adding tenant roles for root
        if (isRootInGeo) {
            return info;
        }


        try {
            Set<String> tenantRoles = _permissionsHelper.getTenantRolesForUser(user,
                    URI.create(user.getTenantId()), false);
            if (tenantRoles != null) {
                for (String role : tenantRoles) {
                    info.getHomeTenantRoles().add(role);
                }
            }

            Map<String, Collection<String>> subTenantRoles = _permissionsHelper
                    .getSubtenantRolesForUser(user);
            if (subTenantRoles != null) {
                for (Entry<String, Collection<String>> entry : subTenantRoles.entrySet()) {
                    SubTenantRoles subRoles = new SubTenantRoles();
                    subRoles.setTenant(entry.getKey());
                    subRoles.setTenantName(_permissionsHelper.getTenantNameByID(entry.getKey()));
                    subRoles.setRoles(new ArrayList<String>(entry.getValue()));
                    info.getSubTenantRoles().add(subRoles);
                }
            }

        } catch (DatabaseException ex) {
            throw SecurityException.fatals.failedReadingTenantRoles(ex);
        }

        return info;
    }

    /**
     * Evaluates the tenancies that this user maps to based on the mappings defined in the Tenants in the system.
     * @brief Get the tenancies to which a user maps given the current mappings
     * @prereq none
     * @param username required The user name for which to retrieve the tenant list.
     * @return User tenant list
     */
    @GET
    @Path("/tenant")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UserTenantList getUserTenantList(@QueryParam("username") String username) {
        Principal principal = sc.getUserPrincipal();
        if (!(principal instanceof StorageOSUser) || 
                !((StorageOSUser)principal).getRoles().contains(Role.SECURITY_ADMIN.toString()) ) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        if(username == null || username.isEmpty()) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("username");
        }
        return Validator.getUserTenants(username);
    }
}
