/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.tenant.*;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.gson.Gson;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;
import models.RoleAssignmentType;
import models.Roles;
import models.datatable.TenantRoleAssignmentDataTable;
import models.datatable.TenantsDataTable;
import models.security.UserInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.Util;
import play.mvc.With;
import util.*;
import util.datatable.DataTablesSupport;

import java.util.*;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;
import static util.RoleAssignmentUtils.createRoleAssignmentEntry;
import static util.RoleAssignmentUtils.getTenantRoleAssignment;
import static util.RoleAssignmentUtils.getTenantRoleAssignments;
import static util.RoleAssignmentUtils.deleteTenantRoleAssignment;
import static util.RoleAssignmentUtils.putTenantRoleAssignmentChanges;

@With(Common.class)
@Restrictions({ @Restrict("ROOT_TENANT_ADMIN"), @Restrict("HOME_TENANT_ADMIN"), @Restrict("TENANT_ADMIN"), @Restrict("SECURITY_ADMIN") })
public class Tenants extends ViprResourceController {
    protected static final String UNKNOWN = "tenants.unknown";

    public static void list() {
        TenantsDataTable dataTable = new TenantsDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<TenantsDataTable.Tenant> tenants = Lists.newArrayList();
        List<TenantOrgRestRep> subtenants;
        UserInfo user = Security.getUserInfo();

        if (Security.isRootTenantAdmin() || Security.isSecurityAdmin()) {
            TenantOrgRestRep rootTenant = TenantUtils.findRootTenant();
            tenants.add(new TenantsDataTable.Tenant(rootTenant, ((Security.isRootTenantAdmin() || Security.isSecurityAdmin()))));
            subtenants = TenantUtils.getSubTenants(rootTenant.getId());
        } else if (Security.isHomeTenantAdmin()) {
            tenants.add(new TenantsDataTable.Tenant(TenantUtils.getUserTenant(), true));
            subtenants = getViprClient().tenants().getByIds(user.getSubTenants());
        } else {
            subtenants = getViprClient().tenants().getByIds(user.getSubTenants());
        }

        for (TenantOrgRestRep tenant : subtenants) {
            boolean admin = Security.isRootTenantAdmin() || user.hasSubTenantRole(tenant.getId().toString(), Security.TENANT_ADMIN)
                    || Security.isSecurityAdmin();
            if (admin) {
                tenants.add(new TenantsDataTable.Tenant(tenant, admin));
            }
        }

        renderJSON(DataTablesSupport.createJSON(tenants, params));
    }

    @FlashException("list")
    public static void edit(String id) {
        TenantOrgRestRep viprTenant = TenantUtils.getTenant(id);
        if (viprTenant == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

        QuotaInfo quota = TenantUtils.getQuota(id);
        

        if (viprTenant != null) {
            TenantForm tenant = new TenantForm().from(viprTenant, quota);
            tenant.usermapping = UserMappingForm.loadUserMappingForms(viprTenant.getUserMappings());
            //namespace entries
            List<StringOption> allNamespace = TenantUtils.getUnmappedNamespace();
            allNamespace.add(new StringOption(viprTenant.getNamespace(), viprTenant.getNamespace()));
            renderArgs.put("namespaceOptions", allNamespace);
            render(tenant);
        }
        else {
            flash.error(MessagesUtils.get("tenants.unknown", id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(TenantForm tenant) {
        tenant.validate("tenant");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        if (tenant.isNew()) {
            List<UserMappingParam> tempMappings = UserMappingForm.getAddedMappings(Collections.<UserMappingParam> emptyList(),
                    tenant.usermapping);
            TenantCreateParam createParam = new TenantCreateParam(tenant.name, tempMappings);
            createParam.setDescription(tenant.description);
            if(tenant.enableNamespace){
            	createParam.setNamespace(tenant.namespace);
            }
            tenant.id = stringId(TenantUtils.create(createParam));
            saveTenantQuota(tenant);
        }
        else {
            TenantOrgRestRep currentTenant = TenantUtils.getTenant(tenant.id);
            if (currentTenant != null) {
                UserMappingChanges mappingChanges = null;
                if (Security.isSecurityAdmin()) {
                    mappingChanges = new UserMappingChanges(UserMappingForm.getAddedMappings(currentTenant.getUserMappings(),
                            tenant.usermapping),
                            UserMappingForm.getRemovedMappings(currentTenant.getUserMappings(), tenant.usermapping));
                }

                TenantUpdateParam updateParam = new TenantUpdateParam(tenant.name, mappingChanges);
                updateParam.setDescription(tenant.description);
                if(tenant.enableNamespace){
                	updateParam.setNamespace(tenant.namespace);
                }
                TenantUtils.update(tenant.id, updateParam);
                // only SecurityAdmin and SystemAdmin has the permission to update Quota
                if (Security.isSecurityAdmin() || Security.isSystemAdmin()) {
                    saveTenantQuota(tenant);
                }
            }
        }
        Security.clearUserInfo();

        flash.success(MessagesUtils.get("tenants.saved", tenant.name));
        list();
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN") })
    public static void create() {
        TenantForm tenant = new TenantForm();
        addRenderArgs(tenant);

        render("@edit", tenant);
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SECURITY_ADMIN") })
    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            boolean deleteExecuted = false;
            for (String id : ids) {
                if (TenantUtils.isRootTenant(uri(id))) {
                    flash.error(MessagesUtils.get("tenants.delete.root"));
                }
                else {
                    if (TenantUtils.deactivate(uri(id))) {
                        deleteExecuted = true;
                    }
                }
            }
            if (deleteExecuted == true) {
                Security.clearUserInfo();
                flash.success(MessagesUtils.get("tenants.deleted"));
            }
        }
        list();
    }

    @Util
    private static void addRenderArgs(TenantForm tenant) {
        List<String> domains = Lists.newArrayList();
        for (AuthnProviderRestRep authProvider : AuthnProviderUtils.getAuthnProviders()) {
            if (!authProvider.getDisable()) {
                domains.addAll(authProvider.getDomains());
            }
        }

        Gson g = new Gson();
        renderArgs.put("domainsJson", g.toJson(domains));
        
        List<StringOption> allNamespace = TenantUtils.getUnmappedNamespace();
        renderArgs.put("namespaceOptions", allNamespace);
    }

    @Util
    private static void saveTenantQuota(TenantForm tenant) {
        if (tenant.enableQuota) {
            TenantUtils.enableQuota(tenant.id, tenant.quota);
        }
        else {
            TenantUtils.disableQuota(tenant.id);
        }
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
    public static void listRoles(String id) {
        TenantRoleAssignmentDataTable dataTable = new TenantRoleAssignmentDataTable(uri(id));
        TenantOrgRestRep tenant = TenantUtils.getTenant(id);
        render("@listRoles", dataTable, tenant);
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
    public static void listRolesJson(String id) {
        List<RoleAssignmentEntry> viprRoleAssignments = getTenantRoleAssignments(id);
        List<TenantRoleAssignmentDataTable.RoleInfo> roles = Lists.newArrayList();
        for (RoleAssignmentEntry viprRoleAssignment : viprRoleAssignments) {
            roles.add(new TenantRoleAssignmentDataTable.RoleInfo(viprRoleAssignment, id));
        }
        renderJSON(DataTablesSupport.createJSON(roles, params));
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
    public static void editRole(@Required String id) {

        // Extract info from id
        String name = TenantRoleAssignmentForm.extractNameFromId(id);
        RoleAssignmentType type = TenantRoleAssignmentForm.extractTypeFromId(id);
        String tId = TenantRoleAssignmentForm.extractTenantFromId(id);

        String tenantId = params.get("tenantId");

        RoleAssignmentEntry roleAssignmentEntry = getTenantRoleAssignment(name, type, ResourceUtils.uri(tId));
        if (roleAssignmentEntry != null) {
            addTenantAndRolesToRenderArgs(tenantId);

            Boolean isRootUser = RoleAssignmentUtils.isRootUser(roleAssignmentEntry);

            TenantRoleAssignmentForm roleAssignment = new TenantRoleAssignmentForm();
            roleAssignment.id = id;

            roleAssignment.tenantId = tenantId;
            roleAssignment.readFrom(roleAssignmentEntry);
            render(roleAssignment, isRootUser);
        }
        else {
            flash.error(MessagesUtils.get("roleAssignments.unknown", name));
            listRoles(tenantId);
        }
    }

    @FlashException(keep = true, referrer = { "createRole", "editRole" })
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
    public static void saveRole(TenantRoleAssignmentForm roleAssignment) {
        String tenantId = params.get("tenantId");
        roleAssignment.validate(tenantId, "roleAssignment");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        roleAssignment.save(tenantId);
        flash.success(MessagesUtils.get("roleAssignments.saved", roleAssignment.name));

        if (RoleAssignmentType.USER.equals(roleAssignment.type)
                && Security.getUserInfo().getIdentifier().equalsIgnoreCase(roleAssignment.name)) {
            Security.clearUserInfo();
        }

        listRoles(tenantId);
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
    public static void createRole(@Required String tenantId) {
        addTenantAndRolesToRenderArgs(tenantId);

        TenantRoleAssignmentForm roleAssignment = new TenantRoleAssignmentForm();
        render("@editRole", roleAssignment);
    }

    @Util
    private static void addTenantAndRolesToRenderArgs(String tenantId) {
        renderArgs.put("tenantId", tenantId);
        renderArgs.put("currentTenantName", TenantUtils.getTenant(tenantId).getName());
        renderArgs.put("roles", Roles.options(Roles.TENANT_ROLES));
    }

    /**
     * Removes a number of role assignments from the given tenant, and redisplays the role assignment page.
     * 
     * @param tenantId
     *            the tenant ID.
     * @param ids
     *            the IDs of the roles to remove.
     */
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
    public static void removeRoleAssignments(String tenantId, @As(",") String[] ids) {
        if ((ids == null) || (ids.length == 0)) {
            listRoles(tenantId);
        }

        deleteRoleAssignments(ids);
        listRoles(tenantId);
    }

    @Util
    private static void deleteRoleAssignments(String[] ids) {
        if (ids != null && ids.length > 0) {
            boolean deletedRoleAssignment = false;
            for (String id : ids) {
                String name = TenantRoleAssignmentForm.extractNameFromId(id);
                RoleAssignmentType type = TenantRoleAssignmentForm.extractTypeFromId(id);
                String tenantId = TenantRoleAssignmentForm.extractTenantFromId(id);

                if (RoleAssignmentUtils.isRootUser(type, name)) {
                    flash.put("warningMessage", MessagesUtils.get("roleAssignments.rootNotDeleted"));
                }
                else {
                    deleteTenantRoleAssignment(tenantId, type, name);
                    deletedRoleAssignment = true;
                }
            }
            if (deletedRoleAssignment) {
                flash.success(MessagesUtils.get("roleAssignments.deleted"));
            }
        }
    }

    public static class TenantForm {
        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String description;

        public List<UserMappingForm> usermapping = Lists.newArrayList();

        public Boolean enableQuota = Boolean.FALSE;
        public Long quota;

        public Boolean enableNamespace = Boolean.FALSE;
        public String namespace;

        public TenantForm from(TenantOrgRestRep from, QuotaInfo quota) {
            this.id = from.getId().toString();
            this.name = from.getName();
            this.description = from.getDescription();
            this.namespace = from.getNamespace();
            if (null != from.getNamespace()) {
                this.enableNamespace = Boolean.TRUE;
            }

            this.enableQuota = quota.getEnabled();
            this.quota = quota.getQuotaInGb();

            return this;
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);

            // if the user is not security admin, skip validation for user-mapping and quota below
            // as the fields are disabled in UI, and should be no change for them.
            if (!Security.isSecurityAdmin()) {
                return;
            }

            // Root Tenant is special in that it doesn't need UserMappings
            if (!TenantUtils.isRootTenant(uri(id))) {
                if (usermapping.isEmpty()) {
                    Validation.addError(formName + ".usermapping", Messages.get("tenant.userMappingRequired"));
                }
            }

            for (UserMappingForm userMappingForm : usermapping) {
                try {
                    if (userMappingForm != null) {
                        userMappingForm.validate();
                    }
                } catch (Exception e) {
                    Validation.addError(formName + ".usermapping", e.getMessage());
                }
            }

            if (enableQuota) {
                if (this.quota == null) {
                    Validation.addError(formName + ".quota", MessagesUtils.get("tenant.quota.error.required"));
                }
                else {
                    String quotaParamName = formName + ".quota";
                    String quotaParamString = params.get(quotaParamName);
                    try {
                        int quota = Integer.parseInt(quotaParamString);
                        if (quota <= 0) {
                            Validation.addError(quotaParamName, MessagesUtils.get("tenant.quota.error.mustBeBigger"));
                        }
                    } catch (NumberFormatException e) {
                        Validation.addError(quotaParamName, MessagesUtils.get("tenant.quota.error.invalid"));
                    }
                }
            }
        }
    }

    public static class TenantRoleAssignmentForm {

        private static final String ID_DELIMITER = "~~~~";

        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        public RoleAssignmentType type = RoleAssignmentType.GROUP;

        public String tenantId;

        public Boolean tenantAdmin = Boolean.FALSE;

        public Boolean projectAdmin = Boolean.FALSE;

        public Boolean tenantApprover = Boolean.FALSE;

        public static String createId(String tenantId, String name, RoleAssignmentType type) {
            return tenantId + ID_DELIMITER + name + ID_DELIMITER + type.name();
        }

        public static String extractNameFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 3) {
                    return parts[1];
                }
                else {
                    return id;
                }
            }
            return null;
        }

        public static String extractTenantFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 3) {
                    return parts[0];
                }
            }
            return null;
        }

        public static RoleAssignmentType extractTypeFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 3) {
                    return RoleAssignmentType.valueOf(parts[2]);
                }
            }
            return null;
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String tenantId, String formName) {
            Validation.valid(formName, this);

            if (isNew()) {
                // Ensure we aren't overwriting an existing role assignment with this new one
                RoleAssignmentEntry roleAssignmentEntry = getTenantRoleAssignment(name, type, ResourceUtils.uri(tenantId));
                if (roleAssignmentEntry != null) {
                    Validation.addError(formName + ".name", Messages.get("roleAssignments." + type + ".alreadyExists"));
                }

                boolean atLeastOneChecked = tenantAdmin || projectAdmin || tenantApprover;
                if (atLeastOneChecked == false) {
                    flash.error(Messages.get("roleAssignments.atLeastOneChecked"));
                    Validation.addError(formName, Messages.get("roleAssignments.atLeastOneChecked"));  // here to fail validation
                }
            }

        }

        public void save(String tenantId) {
            RoleAssignmentEntry roleAssignmentEntry = getTenantRoleAssignment(name, type, ResourceUtils.uri(tenantId));

            if (Security.isSecurityAdmin() || Security.isTenantAdmin()) {
                List<RoleAssignmentEntry> tenantRolesToAdd = Lists.newArrayList();
                List<RoleAssignmentEntry> tenantRolesToRemove = Lists.newArrayList();
                writeTenantRoleChangesTo(roleAssignmentEntry, tenantRolesToAdd, tenantRolesToRemove);
                if (!tenantRolesToAdd.isEmpty() || !tenantRolesToRemove.isEmpty()) {
                    putTenantRoleAssignmentChanges(tenantId, tenantRolesToAdd, tenantRolesToRemove);
                }
            }
        }

        public void readFrom(RoleAssignmentEntry bourneRoleAssignment) {
            if (StringUtils.isNotBlank(bourneRoleAssignment.getSubjectId())) {
                name = bourneRoleAssignment.getSubjectId();
                type = RoleAssignmentType.USER;
            }
            else {
                name = bourneRoleAssignment.getGroup();
                type = RoleAssignmentType.GROUP;
            }
            id = createId(tenantId, name, type);

            tenantAdmin = isRoleAssigned(bourneRoleAssignment, Security.TENANT_ADMIN);
            projectAdmin = isRoleAssigned(bourneRoleAssignment, Security.PROJECT_ADMIN);
            tenantApprover = isRoleAssigned(bourneRoleAssignment, Security.TENANT_APPROVER);
        }

        private boolean isRoleAssigned(RoleAssignmentEntry roleAssignParamEntry, String findRole) {
            if (roleAssignParamEntry.getRoles() != null && findRole != null) {
                return roleAssignParamEntry.getRoles().contains(findRole);
            }
            return false;
        }

        private boolean hasRoleChanged(RoleAssignmentEntry roleAssignParamEntry, String findRole) {
            if (roleAssignParamEntry != null) {
                boolean value = isRoleAssigned(roleAssignParamEntry, findRole);
                if (Roles.isTenantAdmin(findRole)) {
                    return tenantAdmin != value;
                }
                else if (Roles.isProjectAdmin(findRole)) {
                    return projectAdmin != value;
                }
                else if (Roles.isTenantApprover(findRole)) {
                    return tenantApprover != value;
                }
            }
            return true;
        }

        public void writeTenantRoleChangesTo(RoleAssignmentEntry roleAssignmentEntry, List<RoleAssignmentEntry> add,
                List<RoleAssignmentEntry> remove) {
            RoleAssignmentEntry rae = createRoleAssignmentEntry(type, name, Security.TENANT_ADMIN);
            if (hasRoleChanged(roleAssignmentEntry, Security.TENANT_ADMIN)) {
                if (tenantAdmin != null && tenantAdmin == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }

            rae = createRoleAssignmentEntry(type, name, Security.PROJECT_ADMIN);
            if (hasRoleChanged(roleAssignmentEntry, Security.PROJECT_ADMIN)) {
                if (projectAdmin != null && projectAdmin == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }

            rae = createRoleAssignmentEntry(type, name, Security.TENANT_APPROVER);
            if (hasRoleChanged(roleAssignmentEntry, Security.TENANT_APPROVER)) {
                if (tenantApprover != null && tenantApprover == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }
        }

    }
}
