/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.security;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

import models.deadbolt.Role;
import models.deadbolt.RoleHolder;
import util.TenantUtils;

import com.emc.storageos.model.user.SubTenantRoles;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.security.Security;

/**
 * Wrapper around the Bourne UserInfo object which exposes Deadbolt Roles.
 * 
 * @author Chris Dail
 */
public class UserInfo implements RoleHolder, Serializable {
    private static final long serialVersionUID = 1L;
    private String commonName;
    private String distinguishedName;
    private String tenant;
    private String tenantName;
    private List<Role> roles = Lists.newArrayList();
    private Map<String, List<Role>> subTenantRoles = Maps.newHashMap();
    private List<URI> subTenants = Lists.newArrayList();

    public UserInfo(com.emc.storageos.model.user.UserInfo userInfo) {
        this.commonName = userInfo.getCommonName();
        this.distinguishedName = userInfo.getDistinguishedName();
        this.tenant = userInfo.getTenant();
        this.setTenantName(userInfo.getTenantName());
        List<Role> vdcRoles = convertToRoles(userInfo.getVdcRoles());
        List<Role> homeTenantRoles = convertToRoles(userInfo.getHomeTenantRoles());
        roles.addAll(vdcRoles);
        roles.addAll(homeTenantRoles);

        for (SubTenantRoles subTenant : userInfo.getSubTenantRoles()) {
            this.subTenantRoles.put(subTenant.getTenant(), convertToRoles(subTenant.getRoles()));
            this.subTenants.add(ResourceUtils.uri(subTenant.getTenant()));
        }
        addPortalOnlyTenantRolesIfRequired();
    }

    @Override
    public List<? extends Role> getRoles() {
        return roles;
    }

    public Map<String, List<Role>> getSubTenantRoles() {
        return subTenantRoles;
    }

    /**
     * Gets a globally unique identifier to the user. For now this is the 'distinguishedName' which contains the name
     * and domain suffix so it is globally unique.
     * 
     * @return User identifier
     */
    public String getIdentifier() {
        return this.distinguishedName;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public String getTenant() {
        return tenant;
    }

    public List<URI> getSubTenants() {
        return Lists.newArrayList(subTenants);
    }

    public boolean hasSubTenantRole(String tenantId, String roleName) {
        List<Role> roles = subTenantRoles.get(tenantId);
        return roles != null && roles.contains(new StringRole(roleName));
    }

    public boolean containsTenant(String tenant) {
        return getTenant().equals(tenant) ||
                getSubTenants().contains(ResourceUtils.uri(tenant));
    }

    private List<Role> convertToRoles(List<String> stringRoles) {
        List<Role> roles = Lists.newArrayList();

        if (stringRoles != null) {
            for (String role : stringRoles) {
                roles.add(new StringRole(role));
            }
        }

        return roles;
    }

    private void addPortalOnlyTenantRolesIfRequired() {
        if (roles.contains(new StringRole(Security.TENANT_ADMIN))) {
            roles.add(new StringRole(Security.HOME_TENANT_ADMIN));
            if (TenantUtils.isRootTenant(ResourceUtils.uri(getTenant()))) {
                roles.add(new StringRole(Security.ROOT_TENANT_ADMIN));
            }
        } else {
            for (List<Role> tenantRoles : subTenantRoles.values()) {
                if (tenantRoles.contains(new StringRole(Security.TENANT_ADMIN))) {
                    roles.add(new StringRole(Security.TENANT_ADMIN));
                    return;
                }
            }
        }
    }

	public String getTenantName() {
		return tenantName;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}
}
