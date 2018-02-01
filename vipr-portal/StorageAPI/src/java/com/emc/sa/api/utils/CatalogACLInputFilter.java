/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.net.URI;
import java.util.List;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.authorization.PermissionsHelper.ACLInputFilter;
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

    private final URI tenantId;
    private List<String> groups;
    private List<String> users;

    public CatalogACLInputFilter(URI tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    protected PermissionsKey getPermissionKeyForEntry(ACLEntry entry) throws InternalException {
        PermissionsKey key;
        StorageOSPrincipal principal = new StorageOSPrincipal();
        if (entry.getGroup() != null) {
            String group = entry.getGroup();
            key = new PermissionsKey(PermissionsKey.Type.GROUP, group, this.tenantId);
            principal.setName(group);
            principal.setType(StorageOSPrincipal.Type.Group);
        } else if (entry.getSubjectId() != null) {
            key = new PermissionsKey(PermissionsKey.Type.SID, entry.getSubjectId(), this.tenantId);
            principal.setName(entry.getSubjectId());
            principal.setType(StorageOSPrincipal.Type.User);
        } else {
            throw APIException.badRequests.invalidEntryForCatalogServiceACL();
        }

        return key;
    }

    @Override
    protected void validate() {
        PrincipalsToValidate principalsToValidate = new PrincipalsToValidate();
        principalsToValidate.setGroups(this.groups);
        principalsToValidate.setUsers(this.users);
        principalsToValidate.setTenantId(this.tenantId.toString());
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
