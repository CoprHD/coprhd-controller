/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import models.RoleAssignmentType;
import models.Roles;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.google.common.collect.Lists;

import controllers.security.Security;

public class RoleAssignmentUtils {
    public static final String ROOT_USERNAME = "root";

    public static RoleAssignmentEntry createRoleAssignmentEntry(RoleAssignmentType type, String name, String role) {
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        if (RoleAssignmentType.USER.equals(type)) {
            roleAssignmentEntry.setSubjectId(name);
        }
        else if (RoleAssignmentType.GROUP.equals(type)) {
            roleAssignmentEntry.setGroup(name);
        }
        roleAssignmentEntry.getRoles().add(role);
        
        return roleAssignmentEntry;
    }

    public static List<RoleAssignmentEntry> getVDCRoleAssignments() {
    	List<RoleAssignmentEntry> allRollAssignments = Lists.newArrayList();
    	if (Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
            for (RoleAssignmentEntry vdcRoleAssignment : getViprClient().vdc().getRoleAssignments()) {
                boolean found = false;
                for (RoleAssignmentEntry roleAssignment : allRollAssignments) {
                    if (isSameRoleAssignmentEntry(roleAssignment, vdcRoleAssignment)) {
                        roleAssignment.getRoles().addAll(vdcRoleAssignment.getRoles());
                        found = true;
                        break;
                    }
                }
                if (found == false) {
                    allRollAssignments.add(vdcRoleAssignment);
                }
            }
        }
    	addRootUserIfRequired(allRollAssignments);        
        return allRollAssignments;
    }
    
    public static List<RoleAssignmentEntry> getTenantRoleAssignments(URI tenantId) {
    	List<RoleAssignmentEntry> allRollAssignments = Lists.newArrayList();

        if (Security.isTenantAdmin() || Security.isSecurityAdmin()) {
            List<RoleAssignmentEntry> tenantRoleAssignments = getViprClient().tenants().getRoleAssignments(tenantId);
            allRollAssignments.addAll(tenantRoleAssignments);
        }        
        return allRollAssignments;
    }
    
    public static List<RoleAssignmentEntry> getTenantRoleAssignments(String tenantId) {
        return getTenantRoleAssignments(uri(tenantId));
    }
    
    private static void addRootUserIfRequired(List<RoleAssignmentEntry> roleAssignmentEntries) {

        RoleAssignmentEntry rootRoleAssignmentEntry = null;
        for (RoleAssignmentEntry roleAssignmentEntry : roleAssignmentEntries) {
            if (StringUtils.equalsIgnoreCase(ROOT_USERNAME, roleAssignmentEntry.getSubjectId())) {
                rootRoleAssignmentEntry = roleAssignmentEntry;
                break;
            }
        }

        if (rootRoleAssignmentEntry == null) {
            rootRoleAssignmentEntry = new RoleAssignmentEntry();
            rootRoleAssignmentEntry.setSubjectId(ROOT_USERNAME);
            roleAssignmentEntries.add(rootRoleAssignmentEntry);
        }

        rootRoleAssignmentEntry.getRoles().add(Security.SYSTEM_ADMIN);
        rootRoleAssignmentEntry.getRoles().add(Security.SYSTEM_MONITOR);
        rootRoleAssignmentEntry.getRoles().add(Security.SYSTEM_AUDITOR);
        rootRoleAssignmentEntry.getRoles().add(Security.SECURITY_ADMIN);
    }
    
    private static boolean isSameRoleAssignmentEntry(RoleAssignmentEntry left, RoleAssignmentEntry right) {
        if (StringUtils.isNotBlank(left.getSubjectId()) && StringUtils.equals(left.getSubjectId(), right.getSubjectId())) {
            return true;
        }
        else if (StringUtils.isNotBlank(left.getGroup()) && StringUtils.equals(left.getGroup(), right.getGroup())) {
            return true;
        }
        return false;
    }

    public static RoleAssignmentEntry getTenantRoleAssignment(String name, RoleAssignmentType type, URI tenantId) {
        if (StringUtils.isBlank(name) || type == null) {
            return null;
        }
        
        List<RoleAssignmentEntry> bourneRoleAssignments = getTenantRoleAssignments(tenantId);
        for (RoleAssignmentEntry bourneRoleAssignment : bourneRoleAssignments) {
            if (bourneRoleAssignment != null) {
                if (RoleAssignmentType.USER.equals(type) && name.equals(bourneRoleAssignment.getSubjectId())) {
                    return bourneRoleAssignment;
                }
                else if (RoleAssignmentType.GROUP.equals(type) && name.equals(bourneRoleAssignment.getGroup())) {
                    return bourneRoleAssignment;
                }
            }
        }        
        return null;
    }
    
    public static RoleAssignmentEntry getVDCRoleAssignment(String name, RoleAssignmentType type) {
        if (StringUtils.isBlank(name) || type == null) {
            return null;
        }
        
        List<RoleAssignmentEntry> bourneRoleAssignments = getVDCRoleAssignments();
        for (RoleAssignmentEntry bourneRoleAssignment : bourneRoleAssignments) {
            if (bourneRoleAssignment != null) {
                if (RoleAssignmentType.USER.equals(type) && name.equals(bourneRoleAssignment.getSubjectId())) {
                    return bourneRoleAssignment;
                }
                else if (RoleAssignmentType.GROUP.equals(type) && name.equals(bourneRoleAssignment.getGroup())) {
                    return bourneRoleAssignment;
                }
            }
        }        
        return null;
    }
    
    public static void putTenantRoleAssignmentChanges(String tenantId, List<RoleAssignmentEntry> add, List<RoleAssignmentEntry> remove) {
        getViprClient().tenants().updateRoleAssignments(uri(tenantId), new RoleAssignmentChanges(add, remove));
    }
    
    public static void putVdcRoleAssignmentChanges(List<RoleAssignmentEntry> add, List<RoleAssignmentEntry> remove) {
        
        // disallow removing root vdc roles
        for (RoleAssignmentEntry removeRoleAssignmentEntry : Lists.newArrayList(remove)) {
            if (isRootUser(removeRoleAssignmentEntry)) {
                remove.remove(removeRoleAssignmentEntry);
            }
        }

        getViprClient().vdc().updateRoleAssignments(new RoleAssignmentChanges(add, remove));
    }
    
    public static void deleteTenantRoleAssignment(String tenantId, RoleAssignmentType type, String name) {
        
        // disallow deleting root user
        if (isRootUser(type, name)) {
            return;
        }

        if (Security.isSecurityAdmin() || Security.isTenantAdmin()) {
            List<RoleAssignmentEntry> tenantRoles = Lists.newArrayList();
            for (String tenantRole : getTenantRoles()) {
                tenantRoles.add(createRoleAssignmentEntry(type, name, tenantRole));
            }
            putTenantRoleAssignmentChanges(tenantId, new ArrayList<RoleAssignmentEntry>(), tenantRoles);
        }
    }

    public static void deleteVDCRoleAssignment(RoleAssignmentType type, String name) {
        
        // disallow deleting root user
        if (isRootUser(type, name)) {
            return;
        }

        if (Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
            List<RoleAssignmentEntry> vdcRoles = Lists.newArrayList();
            for (String vdcRole : getVdcRoles()) {
                vdcRoles.add(createRoleAssignmentEntry(type, name, vdcRole));
            }
            putVdcRoleAssignmentChanges(new ArrayList<RoleAssignmentEntry>(), vdcRoles);
        }
    }
    
    public static boolean isRootUser(RoleAssignmentType type, String name) {
        return RoleAssignmentType.USER.equals(type) && StringUtils.equalsIgnoreCase(ROOT_USERNAME, name);
    }
    
    public static boolean isRootUser(RoleAssignmentEntry roleAssignmentEntry) {
       return roleAssignmentEntry != null && StringUtils.equalsIgnoreCase(ROOT_USERNAME, roleAssignmentEntry.getSubjectId()); 
    }

    public static List<String> getTenantRoles() {
        return Lists.newArrayList(Roles.TENANT_ROLES);
    }

    public static List<String> getVdcRoles() {
        return Lists.newArrayList(Roles.VDC_ROLES);
    }

    public static boolean isTenantRole(String role) {
        return Roles.isTenantRole(role);
    }

    public static boolean isVdcRole(String role) {
        return Roles.isVdcRole(role);
    }

    public static String getRoleDisplayName(String role) {
        return Roles.getDisplayValue(role);
    }
}
