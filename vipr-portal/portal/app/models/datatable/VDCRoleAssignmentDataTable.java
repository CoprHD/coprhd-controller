/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.RoleAssignmentType;

import org.apache.commons.lang.StringUtils;

import util.RoleAssignmentUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.auth.RoleAssignmentEntry;

import controllers.auth.VDCRoleAssignments.VDCRoleAssignmentForm;
import controllers.security.Security;

public class VDCRoleAssignmentDataTable extends DataTable {

    public VDCRoleAssignmentDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("type").hidden().setSearchable(false);
        addColumn("localizedType");
        if (Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
            addColumn("vdcRoles");
        }
        sortAll();
        setDefaultSortField("name");
    }

    public static class RoleInfo {

        public String id;
        public String name;
        public RoleAssignmentType type;
        public String localizedType;
        public String vdcRoles;

        public RoleInfo(RoleAssignmentEntry roleAssignment) {
            if (StringUtils.isNotBlank(roleAssignment.getSubjectId())) {
                name = roleAssignment.getSubjectId();
                type = RoleAssignmentType.USER;
                localizedType = RoleAssignmentType.USER.getDisplayName();
            }
            else {
                name = roleAssignment.getGroup();
                type = RoleAssignmentType.GROUP;
                localizedType = RoleAssignmentType.GROUP.getDisplayName();
            }
            id = VDCRoleAssignmentForm.createId(name, type);

            vdcRoles = "";
            if (roleAssignment.getRoles() != null) {
                for (String role : roleAssignment.getRoles()) {
                    vdcRoles = addRole(role, vdcRoles);
                }
            }
        }

        private String addRole(String role, String value) {
            if (value.length() > 0) {
                value += ", ";
            }
            value += RoleAssignmentUtils.getRoleDisplayName(role);
            return value;
        }
    }
}
