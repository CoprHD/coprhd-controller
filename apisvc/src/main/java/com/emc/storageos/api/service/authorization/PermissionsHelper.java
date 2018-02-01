/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.authorization;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.common.VdcUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.validator.StorageOSPrincipal;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * Class derived helper methods for role/acl db access and implements methods converting them for api access
 */
public class PermissionsHelper extends BasePermissionsHelper {
    private int _maxRoleAclEntries = 100;

    public void setMaxRoleAclEntries(int count) {
        _maxRoleAclEntries = count;
    }

    /**
     * Constructor - takes db client
     * 
     * @param dbClient
     */
    public PermissionsHelper(DbClient dbClient) {
        super(dbClient);
    }

    /**
     * Converts StringSetMap of permissions into a list of assignment entries as used by the API
     * 
     * @param roleAssignments
     * @param forZone
     * @return
     */
    public ArrayList<RoleAssignmentEntry> convertToRoleAssignments(StringSetMap roleAssignments, boolean forZone) {
        ArrayList<RoleAssignmentEntry> assignments = new ArrayList<RoleAssignmentEntry>();
        if (roleAssignments != null && !roleAssignments.isEmpty()) {
            for (Map.Entry<String, AbstractChangeTrackingSet<String>> roleAssignment : roleAssignments.entrySet()) {
                PermissionsKey rowKey = new PermissionsKey();
                rowKey.parseFromString(roleAssignment.getKey());
                RoleAssignmentEntry entry = new RoleAssignmentEntry();
                if (rowKey.getType().equals(PermissionsKey.Type.GROUP)) {
                    entry.setGroup(rowKey.getValue());
                } else if (rowKey.getType().equals(PermissionsKey.Type.SID)) {
                    entry.setSubjectId(rowKey.getValue());
                }
                for (String role : roleAssignment.getValue()) {
                    if ((forZone && isRoleZoneLevel(role)) || (!forZone && isRoleTenantLevel(role))) {
                        entry.getRoles().add(role);
                    }
                }
                if (!entry.getRoles().isEmpty()) {
                    assignments.add(entry);
                }
            }
        }
        return assignments;
    }

    /**
     * Abstract class for filtering role entries from input
     */
    public static abstract class RoleInputFilter {
        protected TenantOrg _tenant;

        protected List<String> _groups;
        protected List<String> _users;
        protected List<String> _localUsers;
        protected List<String> _rootRoleAssignments;

        protected RoleInputFilter(TenantOrg tenant) {
            _tenant = tenant;
        }

        protected PermissionsKey getPermissionKeyForEntry(RoleAssignmentEntry entry)
                throws SecurityException {
            PermissionsKey key;
            StorageOSPrincipal principal = new StorageOSPrincipal();
            if (entry.getGroup() != null) {
                entry.setGroup(entry.getGroup().trim());
                key = new PermissionsKey(PermissionsKey.Type.GROUP, entry.getGroup());
                principal.setName(entry.getGroup());
                principal.setType(StorageOSPrincipal.Type.Group);
            } else if (entry.getSubjectId() != null) {
                entry.setSubjectId(entry.getSubjectId().trim());
                key = new PermissionsKey(PermissionsKey.Type.SID, entry.getSubjectId());
                principal.setName(entry.getSubjectId());
                principal.setType(StorageOSPrincipal.Type.User);
            } else {
                throw APIException.badRequests.invalidEntryForRoleAssignmentSubjectIdAndGroupAreNull();
            }

            return key;
        }

        protected void initLists() {
            _users = new ArrayList<String>();
            _groups = new ArrayList<String>();
            _localUsers = new ArrayList<String>();
        }

        protected StringSetMap convertFromRolesNoLocalUsers(
                List<RoleAssignmentEntry> roles) {
            StringSetMap resulStringSetMap = convertFromRoleAssignments(roles);

            if (!CollectionUtils.isEmpty(_localUsers)) {
                throw APIException.badRequests
                        .invalidEntryForRoleAssignmentSubjectIdsCannotBeALocalUsers(StringUtils
                                .join(_localUsers, ", "));
            }
            return resulStringSetMap;
        }

        protected StringSetMap convertFromRoleAssignments(
                List<RoleAssignmentEntry> assignments) {
            initLists();
            StringSetMap rolesMap = new StringSetMap();
            if (!CollectionUtils.isEmpty(assignments)) {
                for (RoleAssignmentEntry roleAssignment : assignments) {
                    PermissionsKey key = getPermissionKeyForEntry(roleAssignment);
                    if (key.getType().equals(PermissionsKey.Type.SID)
                            && Validator.isUserLocal(key.getValue())) {
                        _localUsers.add(key.getValue());
                        if (key.getValue().equalsIgnoreCase("root")) {
                            _rootRoleAssignments = roleAssignment.getRoles();
                        }
                    }
                    if (CollectionUtils.isEmpty(roleAssignment.getRoles())) {
                        throw APIException.badRequests.noRoleSpecifiedInAssignmentEntry();
                    }
                    for (String role : roleAssignment.getRoles()) {
                        if (!isValidRole(role)) {
                            throw APIException.badRequests
                                    .invalidEntryForRoleAssignment(role);
                        }
                        rolesMap.put(key.toString(), role.toUpperCase());
                    }
                    addPrincipalToList(key, roleAssignment);
                }
            }
            return rolesMap;
        }

        protected abstract boolean isValidRole(String ace);

        protected abstract void validatePrincipals();

        protected abstract void addPrincipalToList(PermissionsKey key,
                RoleAssignmentEntry roleAssignment);

        public abstract StringSetMap convertFromRolesRemove(
                List<RoleAssignmentEntry> remove);

        public abstract StringSetMap convertFromRolesAdd(List<RoleAssignmentEntry> add,
                boolean validate);
    }

    /**
     * Update role assignments on a given tenant
     * 
     * @param tenant TenantOrg object
     * @param changes RoleAssignmentChanges
     * @param filter RoleInputFilter for the roles allowed
     */
    public void updateRoleAssignments(TenantOrg tenant, RoleAssignmentChanges changes,
            RoleInputFilter filter) {

        // in GEO scenario, root shouldn't be assigned with any tenant roles
        if (!CollectionUtils.isEmpty(changes.getAdd()) && !VdcUtil.isLocalVdcSingleSite()) {
            StringSetMap roleAssignments =
                    filter.convertFromRolesAdd(changes.getAdd(), false);

            for (Map.Entry<String, AbstractChangeTrackingSet<String>> roleAssignment : roleAssignments.entrySet()) {
                if (roleAssignment.getKey().equalsIgnoreCase("root")) {
                    throw APIException.badRequests.invalidRoleAssignments(
                            "root can't be assigned with tenant roles in GEO scenario"
                            );
                }
            }
        }

        updateRoleAssignments(tenant.getRoleAssignments(), changes, filter);
    }

    /**
     * Update role assignments on a given virtual data center
     * 
     * @param vdc VirtualDataCenter object
     * @param changes RoleAssignmentChanges
     * @param filter RoleInputFilter for the roles allowed
     */
    public void updateRoleAssignments(VirtualDataCenter vdc, RoleAssignmentChanges changes,
            RoleInputFilter filter) {
        updateRoleAssignments(vdc.getRoleAssignments(), changes, filter);
    }

    private void updateRoleAssignments(StringSetMap originalRoleAssignments, RoleAssignmentChanges changes,
            RoleInputFilter filter) {
        if (!CollectionUtils.isEmpty(changes.getRemove())) {
            StringSetMap rolesRemoved =
                    filter.convertFromRolesRemove(changes.getRemove());
            for (Map.Entry<String, AbstractChangeTrackingSet<String>> roleAssignment : rolesRemoved.entrySet()) {
                String rowKey = roleAssignment.getKey();
                for (String role : roleAssignment.getValue()) {
                    originalRoleAssignments.remove(rowKey, role);
                }
            }
        }

        if (!CollectionUtils.isEmpty(changes.getAdd())) {
            StringSetMap roleAssignments =
                    filter.convertFromRolesAdd(changes.getAdd(), false);
            int current = 0;
            if (originalRoleAssignments != null) {
                current = originalRoleAssignments.size();
            }
            if (current + roleAssignments.size() > _maxRoleAclEntries) {
                throw APIException.badRequests.exceedingRoleAssignmentLimit(
                        _maxRoleAclEntries, current + roleAssignments.entrySet().size());
            }

            roleAssignments = filter.convertFromRolesAdd(changes.getAdd(), true);

            for (Map.Entry<String, AbstractChangeTrackingSet<String>> roleAssignment : roleAssignments.entrySet()) {
                String rowKey = roleAssignment.getKey();
                for (String role : roleAssignment.getValue()) {
                    originalRoleAssignments.put(rowKey, role);
                }
            }
        }
    }

    /**
     * Abstract class for filtering acl entries from input
     */
    public static abstract class ACLInputFilter {

        protected abstract PermissionsKey getPermissionKeyForEntry(ACLEntry entry)
                throws InternalException;

        protected abstract void validate();

        protected abstract boolean isValidACL(String ace);

        /**
         * Converts a list of ACLEntries as used by the API into StringSetMap saved to
         * Project
         * 
         * @param entries
         * @param validatePrincipals
         * @return
         */
        public StringSetMap convertFromACLEntries(List<ACLEntry> entries,
                boolean validatePrincipals) {
            StringSetMap assignments = new StringSetMap();
            initLists();
            if (entries != null && !entries.isEmpty()) {
                for (ACLEntry entry : entries) {
                    PermissionsKey key = getPermissionKeyForEntry(entry);

                    ArgValidator.checkFieldNotEmpty(entry.getAces(), "privilege");
                    int countACLType = 0;
                    if (entry.getTenant() != null) {
                        countACLType++;
                    }
                    if (entry.getGroup() != null) {
                        countACLType++;
                    }
                    if (entry.getSubjectId() != null) {
                        countACLType++;
                    }
                    if (countACLType == 0) {
                        throw APIException.badRequests.invalidACLTypeEmptyNotAllowed();
                    } else if (countACLType > 1) {
                        throw APIException.badRequests.invalidACLTypeMultipleNotAllowed();
                    }

                    for (String ace : entry.getAces()) {
                        // owner is not settable through acls, we save it as acl for
                        // indexing
                        if (!isValidACL(ace)) {
                            throw APIException.badRequests.invalidACL(ace);
                        }

                        assignments.put(key.toString(), ace.toUpperCase());
                    }
                    addPrincipalToList(key);
                }
            }

            if (validatePrincipals) {
                validate();
            }

            return assignments;
        }

        protected abstract void addPrincipalToList(PermissionsKey key);

        protected abstract void initLists();
    }

    /**
     * Filter class for validating input args for usage ACLs
     */
    public static class UsageACLFilter extends PermissionsHelper.ACLInputFilter {
        private final BasePermissionsHelper _helper;
        private final String _specifier;

        public UsageACLFilter(BasePermissionsHelper helper, String specifier) {
            _helper = helper;
            _specifier = specifier;
        }

        public UsageACLFilter(BasePermissionsHelper helper) {
            this(helper, null);
        }

        @Override
        public PermissionsKey getPermissionKeyForEntry(ACLEntry entry)
                throws DatabaseException {
            if (entry.getTenant() == null) {
                throw APIException.badRequests.invalidEntryACLEntryMissingTenant();
            }

            TenantOrg tenant = _helper.getObjectById(URI.create(entry.getTenant()), TenantOrg.class);
            if (tenant == null) {
                throw APIException.badRequests.invalidEntryForUsageACL(entry.getTenant());
            }

            PermissionsKey key;
            if (_specifier == null) {
                key = new PermissionsKey(PermissionsKey.Type.TENANT, entry.getTenant());
            } else {
                key = new PermissionsKey(PermissionsKey.Type.TENANT, entry.getTenant(), _specifier);
            }
            return key;
        }

        @Override
        public boolean isValidACL(String ace) {
            return (isUsageACL(ace));
        }

        @Override
        protected void validate() {
            // Usage ACLs don't pertain to users or group. Nothing to validate here.
            return;
        }

        @Override
        protected void addPrincipalToList(PermissionsKey key) {
            // Don't need to add anything here since no validation is done
        }

        @Override
        protected void initLists() {
            // No lists to initialize
        }
    }

    /**
     * Update the acls from request
     * 
     * @param obj Object on which acls need to be updated
     * @param changes ACLAssignmentChanges from the request
     * @param filter ACLInputFilter for this object
     */
    public void updateACLs(DataObjectWithACLs obj, ACLAssignmentChanges changes,
                           ACLInputFilter filter) {
        if (changes.getRemove() != null && !changes.getRemove().isEmpty()) {
            StringSetMap acls = filter.convertFromACLEntries(changes.getRemove(), false);
            for (Map.Entry<String, AbstractChangeTrackingSet<String>> aclAssignment : acls.entrySet()) {
                String rowKey = aclAssignment.getKey();
                for (String ace : aclAssignment.getValue()) {
                    obj.removeAcl(rowKey, ace);
                }
            }
        }
        if (changes.getAdd() != null && !changes.getAdd().isEmpty()) {
            // call this once, just to get the number of acls
            StringSetMap acls = filter.convertFromACLEntries(changes.getAdd(), false);
            int current = 0;
            if (obj.getAcls() != null) {
                current = obj.getAcls().size();
            }
            if (current + acls.size() > _maxRoleAclEntries) {
                throw APIException.badRequests.exceedingRoleAssignmentLimit(_maxRoleAclEntries, current + acls.size());
            }

            // now that we're sure the total number of acls is less than the
            // max, we can validate the users.
            acls = filter.convertFromACLEntries(changes.getAdd(), true);

            for (Map.Entry<String, AbstractChangeTrackingSet<String>> aclAssignment : acls.entrySet()) {
                String rowKey = aclAssignment.getKey();
                for (String ace : aclAssignment.getValue()) {
                    obj.addAcl(rowKey, ace);
                }
            }
        }
    }

    public void checkTenantHasAccessToVirtualArray(URI tenantId,
            VirtualArray varray) {
        if (!tenantHasUsageACL(tenantId, varray)) {
            throw APIException.forbidden.tenantCannotAccessVirtualArray(varray.getLabel());
        }
    }

    public void checkTenantHasAccessToVirtualPool(URI tenantId, VirtualPool vpool) {
        if (!tenantHasUsageACL(tenantId, vpool)) {
            throw APIException.forbidden.tenantCannotAccessVirtualPool(vpool.getLabel());
        }
    }

    public void checkTenantHasAccessToComputeVirtualPool(URI tenantId, ComputeVirtualPool vcpool) {
        if (!tenantHasUsageACL(tenantId, vcpool)) {
            throw APIException.forbidden.tenantCannotAccessComputeVirtualPool(vcpool.getLabel());
        }
    }

    /**
     * Update the acls for the DiscoveredComputedSystems objects (vCenter)
     * from request.
     *
     * @param obj Object on which acls need to be updated
     * @param changes ACLAssignmentChanges from the request
     * @param filter ACLInputFilter for this object
     */
    public void updateACLs(DiscoveredComputeSystemWithAcls obj, ACLAssignmentChanges changes,
                           ACLInputFilter filter) {
        if (changes.getRemove() != null && !changes.getRemove().isEmpty()) {
            StringSetMap acls = filter.convertFromACLEntries(changes.getRemove(), false);
            for (Map.Entry<String, AbstractChangeTrackingSet<String>> aclAssignment : acls.entrySet()) {
                String rowKey = aclAssignment.getKey();
                obj.removeAcl(rowKey);
            }
        }
        if (changes.getAdd() != null && !changes.getAdd().isEmpty()) {
            // call this once, just to get the number of acls
            StringSetMap acls = filter.convertFromACLEntries(changes.getAdd(), false);
            int current = 0;
            if (obj.getAcls() != null) {
                current = obj.getAcls().size();
            }
            if (current + acls.size() > _maxRoleAclEntries) {
                throw APIException.badRequests.exceedingRoleAssignmentLimit(_maxRoleAclEntries, current + acls.size());
            }

            // now that we're sure the total number of acls is less than the
            // max, we can validate the users.
            acls = filter.convertFromACLEntries(changes.getAdd(), true);

            for (Map.Entry<String, AbstractChangeTrackingSet<String>> aclAssignment : acls.entrySet()) {
                String rowKey = aclAssignment.getKey();
                for (String ace : aclAssignment.getValue()) {
                    obj.addAcl(rowKey, ace);
                }
            }
        }
    }
}
