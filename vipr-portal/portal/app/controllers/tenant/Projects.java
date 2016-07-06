/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import static com.emc.vipr.client.core.util.ResourceUtils.*;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import models.ACLs;
import models.RoleAssignmentType;
import models.datatable.ProjectsDataTable;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Util;
import play.mvc.With;
import util.*;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.ProjectUpdateParam;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.security.validator.StorageOSPrincipal;
import com.emc.storageos.security.validator.Validator;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("PROJECT_ADMIN"), @Restrict("TENANT_ADMIN") })
public class Projects extends ViprResourceController {
    protected static final String UNKNOWN = "projects.unknown";

    public static void list() {
        ProjectsDataTable dataTable = new ProjectsDataTable();
        TenantSelector.addRenderArgs();
        render(dataTable);
    }

    @FlashException(value = "list", keep = true)
    public static void listJson() {
        String userId = Security.getUserInfo().getIdentifier();
        List<ProjectRestRep> viprProjects = ProjectUtils.getProjects(Models.currentAdminTenant());
        List<ProjectsDataTable.Project> projects = Lists.newArrayList();
        for (ProjectRestRep viprProject : viprProjects) {
            if (Security.isTenantAdmin()
                    || (Security.isProjectAdmin() && StringUtils.equalsIgnoreCase(userId, viprProject.getOwner()))) {
                projects.add(new ProjectsDataTable.Project(viprProject));
            }
        }
        renderJSON(DataTablesSupport.createJSON(projects, params));
    }

    @FlashException(value = "list", keep = true)
    public static void create() {
        ProjectForm project = new ProjectForm();
        project.tenantId = Models.currentAdminTenant();
        addRenderArgs();
        render("@edit", project);
    }

    @FlashException(value = "list", keep = true)
    public static void edit(String id) {
        ProjectRestRep viprProject = ProjectUtils.getProject(id);
        if (viprProject == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

        QuotaInfo quota = null;
        if (Security.isTenantAdmin()) {
            quota = ProjectUtils.getQuota(id);
        }

        if (viprProject != null) {
            ProjectForm project = new ProjectForm().from(viprProject, quota);
            project.aclEntries = AclEntryForm.loadAclEntryForms(ProjectUtils.getACLs(id));
            addRenderArgs();
            render(project);
        }
        else {
            flash.error(MessagesUtils.get("projects.unknown", id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(ProjectForm project) {
        if (project == null) {
            Logger.error("No project parameters passed");
            badRequest("No project parameters passed");
            return;
        }

        project.validate("project");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        if (project.isNew()) {
            project.id = stringId(ProjectUtils.create(project.tenantId, new ProjectParam(project.name)));

            saveProjectQuota(project);
            saveProjectACLs(project.id, project.aclEntries);
        }
        else {
            ProjectRestRep currentProject = ProjectUtils.getProject(project.id);
            if (currentProject != null) {

                saveProjectQuota(project);
                saveProjectACLs(project.id, project.aclEntries);

                ProjectUtils.update(project.id, new ProjectUpdateParam(project.name, project.owner));
            }
        }

        flash.success(MessagesUtils.get("projects.saved", project.name));
        response.setCookie("guide_project", project.name);
        if (StringUtils.isNotBlank(project.referrerUrl)) {
            redirect(project.referrerUrl);
        }
        else {
            list();
        }
    }

    @Util
    private static void saveProjectQuota(ProjectForm project) {
        if (Security.isTenantAdmin()) {
            if (project.enableQuota) {
                ProjectUtils.enableQuota(project.id, project.quota);
            }
            else {
                ProjectUtils.disableQuota(project.id);
            }
        }
    }

    @Util
    private static void saveProjectACLs(String projectId, List<AclEntryForm> aclEntries) {
        List<ACLEntry> currentProjectAcls = ProjectUtils.getACLs(projectId);
        ACLAssignmentChanges changes = new ACLAssignmentChanges();
        changes.getAdd().addAll(AclEntryForm.getAddedAcls(currentProjectAcls, aclEntries));
        changes.getRemove().addAll(AclEntryForm.getRemovedAcls(currentProjectAcls, aclEntries));
        try {
            ProjectUtils.updateACLs(projectId, changes);
        } catch (ViPRException e) {
            Logger.error(e, "Failed to update Project ACLs");
            String errorDesc = e.getMessage();
            if (e instanceof ServiceErrorException) {
                errorDesc = ((ServiceErrorException) e).getDetailedMessage();
            }
            flash.error(MessagesUtils.get("projects.updateProjectACLs.failed", errorDesc));
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        String tenantId = null;
        if (ids != null && ids.length > 0) {
            ProjectRestRep project = ProjectUtils.getProject(ids[0]);
            if (project != null) {
                tenantId = project.getTenant().toString();
            }

            for (String id : ids) {
                ProjectUtils.deactivate(uri(id));
            }
            flash.success(MessagesUtils.get("projects.deleted"));
        }
        list();
    }

    @Util
    private static void addRenderArgs() {
        renderArgs.put("acls", ACLs.options(ACLs.ALL, ACLs.BACKUP));

        Set<EnumOption> aclTypes = new TreeSet<EnumOption>();
        aclTypes.addAll(Arrays.asList(EnumOption.options(RoleAssignmentType.values(), "RoleAssignmentType")));
        renderArgs.put("aclTypes", aclTypes);
    }

    public static class ProjectForm {

        public String id;

        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        public String owner;

        public Boolean enableQuota = Boolean.FALSE;

        public Long quota;

        public List<AclEntryForm> aclEntries = Lists.newArrayList();

        public String referrerUrl;

        public ProjectForm from(ProjectRestRep from, QuotaInfo quota) {
            this.id = from.getId().toString();
            this.tenantId = from.getTenant().getId().toString();
            this.name = from.getName();
            this.owner = from.getOwner();

            if (quota != null) {
                this.enableQuota = quota.getEnabled();

                if (isTrue(quota.getEnabled())) {
                    this.quota = quota.getQuotaInGb();
                }
                else {
                    this.quota = null;
                }
            }
            else {
                this.enableQuota = Boolean.FALSE;
                this.quota = null;
            }

            return this;
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);

            boolean ownerChanged = false;
            if (isNew()) {
                ownerChanged = StringUtils.isNotBlank(this.owner);
            }
            else {
                // Require an owner for editing projects
                Validation.required(formName + ".owner", owner);
                ProjectRestRep existingProject = ProjectUtils.getProject(this.id);
                if (existingProject != null) {
                    ownerChanged = StringUtils.equalsIgnoreCase(existingProject.getOwner(), this.owner) == false;
                }
            }

            if (ownerChanged && StringUtils.isNotBlank(owner)) {
                if (LocalUser.isLocalUser(this.owner) && ownerChanged) {
                    Validation.addError(formName + ".owner", MessagesUtils.get("Projects.localUsersNotPermitted"));
                }
                else if (isValidPrincipal(RoleAssignmentType.USER, this.owner) == false) {
                    Validation.addError(formName + ".owner", MessagesUtils.get("project.owner.error.invalid"));
                }
            }

            String fieldName = formName + ".quota";
            if (enableQuota) {
                if (this.quota == null) {
                    Validation.addError(fieldName, MessagesUtils.get("project.quota.error.required"));
                }
                else {
                    String quotaParamName = formName + ".quota";
                    String quotaParamString = params.get(quotaParamName);
                    try {
                        Integer.parseInt(quotaParamString);
                    } catch (NumberFormatException e) {
                        Validation.addError(quotaParamName, MessagesUtils.get("project.quota.error.invalid"));
                    }
                }
            }

            if (Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
                ACLUtils.validateAclEntries(formName + ".aclEntries", this.aclEntries);
            }
        }

        private boolean isValidPrincipal(RoleAssignmentType type, String name) {
            StorageOSPrincipal principal = new StorageOSPrincipal();
            principal.setName(name);
            if (RoleAssignmentType.GROUP.equals(type)) {
                principal.setType(StorageOSPrincipal.Type.Group);
            }
            else if (RoleAssignmentType.USER.equals(type)) {
                principal.setType(StorageOSPrincipal.Type.User);
            }

            String tenant = Models.currentAdminTenant();

            return Validator.isValidPrincipal(principal, URI.create(tenant));
        }

    }

}
