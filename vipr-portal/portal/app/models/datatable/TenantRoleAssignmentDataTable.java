/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.net.URI;

import models.RoleAssignmentType;

import org.apache.commons.lang.StringUtils;

import play.mvc.With;
import util.RoleAssignmentUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.auth.RoleAssignmentEntry;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.tenant.Tenants.TenantRoleAssignmentForm;

@With(Common.class)
@Restrictions({@Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN")})
public class TenantRoleAssignmentDataTable extends DataTable {

    public TenantRoleAssignmentDataTable(URI tenantId) {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("type").hidden().setSearchable(false);
        addColumn("localizedType");
        if (Security.isTenantAdmin() || Security.isSecurityAdmin()) {
            addColumn("tenantRoles");
        }
        sortAll();
        setDefaultSortField("name");
    }

    public static class RoleInfo {

        public String id;
        public String name;
        public RoleAssignmentType type;
        public String localizedType;
        public String tenantRoles;
        public String tenantId;

        public RoleInfo(RoleAssignmentEntry roleAssignment, String tenantId) {
            this.tenantId = tenantId;

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
            id = TenantRoleAssignmentForm.createId(tenantId, name, type);
            
            tenantRoles = "";
            if (roleAssignment.getRoles() != null) {
                for (String role : roleAssignment.getRoles()) {
                        tenantRoles = addRole(role, tenantRoles);
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
