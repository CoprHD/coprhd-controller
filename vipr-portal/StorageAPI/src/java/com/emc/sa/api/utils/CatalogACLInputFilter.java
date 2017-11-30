/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.util.List;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.authorization.PermissionsHelper.ACLInputFilter;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.auth.PrincipalsToValidate;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.validator.StorageOSPrincipal;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.collect.Lists;

public class CatalogACLInputFilter extends ACLInputFilter {

    private final TenantOrg _tenant;
    private List<String> groups;
    private List<String> users;

    public CatalogACLInputFilter(TenantOrg _tenant) {
        this._tenant = _tenant;
    }

    @Override
    protected PermissionsKey getPermissionKeyForEntry(ACLEntry entry) throws InternalException {
        PermissionsKey key;
        StorageOSPrincipal principal = new StorageOSPrincipal();
        if (entry.getGroup() != null) {
            String group = entry.getGroup();
            key = new PermissionsKey(PermissionsKey.Type.GROUP, group, _tenant.getId());
            principal.setName(group);
            principal.setType(StorageOSPrincipal.Type.Group);
        } else if (entry.getSubjectId() != null) {
            key = new PermissionsKey(PermissionsKey.Type.SID, entry.getSubjectId(), _tenant.getId());
            principal.setName(entry.getSubjectId());
            principal.setType(StorageOSPrincipal.Type.User);
        } else {
            throw APIException.badRequests.invalidEntryForCatalogServiceACL();
        }

        return key;
    }

    @Override
    protected void validate() {
    	// Validate if given UserGroup belongs to the logged-in tenant UserGroups
    	validateTenantUserGroup(this.groups, _tenant);
    	
        PrincipalsToValidate principalsToValidate = new PrincipalsToValidate();
        principalsToValidate.setGroups(this.groups);
        principalsToValidate.setUsers(this.users);
        principalsToValidate.setTenantId(_tenant.getId().toString());
        StringBuilder error = new StringBuilder();
        if (!Validator.validatePrincipals(principalsToValidate, error)) {
            throw APIException.badRequests.invalidRoleAssignments(error.toString());
        }
    }

    @Override
    protected boolean isValidACL(String ace) {
        return (PermissionsHelper.isUsageACL(ace) && !ace.equalsIgnoreCase(ACL.OWN.toString()));
    }

    @Override
    protected void addPrincipalToList(PermissionsKey key) {
        switch (key.getType()) {
            case GROUP:
                groups.add(key.getValue());
                break;
            case SID:
                users.add(key.getValue());
                break;
            case TENANT:
            default:
                break;
        }
    }

    @Override
    protected void initLists() {
        this.groups = Lists.newArrayList();
        this.users = Lists.newArrayList();
    }

}
