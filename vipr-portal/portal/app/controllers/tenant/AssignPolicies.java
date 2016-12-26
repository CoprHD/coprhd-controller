/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.policy.FilePolicyAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyProjectAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyVpoolAssignParam;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;
import models.datatable.ScheculePoliciesDataTable;
import play.Logger;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Util;
import play.mvc.With;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("PROJECT_ADMIN"), @Restrict("TENANT_ADMIN") })
public class AssignPolicies extends ViprResourceController {

    protected static final String UNKNOWN = "schedule.policies.unknown";

    public static void list() {
        ScheculePoliciesDataTable dataTable = new ScheculePoliciesDataTable();
        TenantSelector.addRenderArgs();
        render(dataTable);
    }

    @FlashException(value = "list", keep = true)
    public static void listJson() {
        FilePolicyListRestRep viprSchedulePolicies = getViprClient().fileProtectionPolicies().listFilePolicies();
        List<ScheculePoliciesDataTable.FileProtectionPolicy> scheculePolicies = Lists.newArrayList();
        for (NamedRelatedResourceRep policy : viprSchedulePolicies.getFilePolicies()) {
            scheculePolicies.add(new ScheculePoliciesDataTable.FileProtectionPolicy(
                    getViprClient().fileProtectionPolicies().getFilePolicy(policy.getId())));
        }
        renderJSON(DataTablesSupport.createJSON(scheculePolicies, params));
    }

    @FlashException(value = "list", keep = true)
    public static void create() {
        AssignPolicyForm assignPolicy = new AssignPolicyForm();
        addRenderArgs();
        addProjectArgs();
        render("@edit", assignPolicy);
    }

    @FlashException(value = "list", keep = true)
    public static void assign(String id) {
        FilePolicyRestRep filePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(id));
        if (filePolicyRestRep != null) {
            AssignPolicyForm assignPolicy = new AssignPolicyForm().form(filePolicyRestRep);

            addRenderArgs();
            addProjectArgs();
            render(assignPolicy);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

    }

    @Util
    private static void addRenderArgs() {
        List<StringOption> policyTypeOptions = Lists.newArrayList();
        policyTypeOptions.add(new StringOption("file_snapshot", MessagesUtils.get("schedulePolicy.snapshot")));
        policyTypeOptions.add(new StringOption("file_replication", MessagesUtils.get("schedulePolicy.replication")));
        renderArgs.put("policyTypeOptions", policyTypeOptions);

        List<StringOption> applyPolicyOptions = Lists.newArrayList();
        policyTypeOptions.add(new StringOption(FilePolicyApplyLevel.vpool.name(), MessagesUtils.get("assignPolicy.applyAtVPool")));
        policyTypeOptions.add(new StringOption(FilePolicyApplyLevel.project.name(), MessagesUtils.get("assignPolicy.applyAtProject")));
        policyTypeOptions.add(new StringOption(FilePolicyApplyLevel.file_system.name(), MessagesUtils.get("assignPolicy.applyAtFs")));
        renderArgs.put("applyPolicyOptions", applyPolicyOptions);

    }

    private static List<StringOption> createResourceOptions(Collection<? extends DataObjectRestRep> values) {
        List<StringOption> options = Lists.newArrayList();
        for (DataObjectRestRep value : values) {
            options.add(new StringOption(value.getId().toString(), value.getName()));
        }
        return options;
    }

    private static List<StringOption> getFileVirtualPoolsOptions(URI virtualArray) {
        Collection<FileVirtualPoolRestRep> virtualPools;
        if (virtualArray == null) {
            virtualPools = getViprClient().fileVpools().getAll();
        } else {
            virtualPools = getViprClient().fileVpools().getByVirtualArray(virtualArray);
        }

        return createResourceOptions(virtualPools);
    }

    private static List<StringOption> getFileProjectOptions(URI tenantId) {
        Collection<ProjectRestRep> projects = getViprClient().projects().getByTenant(tenantId);
        return createResourceOptions(projects);
    }

    private static void addProjectArgs() {
        renderArgs.put("projectVpoolOptions", getFileVirtualPoolsOptions(null));
        renderArgs.put("projectOptions", getFileProjectOptions(uri(Models.currentAdminTenant())));
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void edit(AssignPolicyForm assignPolicy) {
        if (assignPolicy == null) {
            Logger.error("No assign policy parameters passed");
            badRequest("No assign policy parameters passed");
            return;
        }
        assignPolicy.validate("assignPolicy");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        assignPolicy.id = params.get("id");
        FilePolicyAssignParam assignPolicyParam = updateAssignPolicyParam(assignPolicy);
        getViprClient().fileProtectionPolicies().assignPolicy(uri(assignPolicy.id), assignPolicyParam);

        flash.success(MessagesUtils.get("assignPolicy.saved", assignPolicy.policyName));
        if (StringUtils.isNotBlank(assignPolicy.referrerUrl)) {
            redirect(assignPolicy.referrerUrl);
        } else {
            list();
        }

    }

    private static FilePolicyAssignParam updateAssignPolicyParam(AssignPolicyForm assignPolicy) {
        FilePolicyAssignParam param = new FilePolicyAssignParam();
        param.setApplyAt(assignPolicy.appliedAt);

        // FilePolicyRestRep existingPolicy = getViprClient().fileProtectionPolicies().get(URI.create(assignPolicy.id));

        if (FilePolicyApplyLevel.project.name().equalsIgnoreCase(assignPolicy.appliedAt)) {

            FilePolicyProjectAssignParam projectAssignParams = new FilePolicyProjectAssignParam();
            projectAssignParams.setVpool(uri(assignPolicy.vpool));

            Set<URI> assignProjects = new HashSet<URI>();
            for (String project : assignPolicy.projects) {
                assignProjects.add(uri(project));
            }
            projectAssignParams.setAssigntoProjects(assignProjects);

            if (assignProjects == null || assignProjects.isEmpty()) {
                projectAssignParams.setAssigntoAll(true);
            } else {
                projectAssignParams.setAssigntoAll(false);
            }

        } else if (FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(assignPolicy.appliedAt)) {
            FilePolicyVpoolAssignParam vpoolAssignParams = new FilePolicyVpoolAssignParam();

            Set<URI> assignVpools = new HashSet<URI>();
            for (String vpool : assignPolicy.vPools) {
                assignVpools.add(uri(vpool));
            }
            vpoolAssignParams.setAssigntoVpools(assignVpools);

            if (assignVpools == null || assignVpools.isEmpty()) {
                vpoolAssignParams.setAssigntoAll(true);
            } else {
                vpoolAssignParams.setAssigntoAll(false);
            }
        }

        return param;

    }

    public static class AssignPolicyForm {

        public String id;

        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        // Schedule policy name
        public String policyName;
        // Type of the policy
        // public String policyType;

        public String appliedAt;

        public String vpool;

        public boolean applyToAllProjects;

        public List<String> projects;

        public boolean applyToAllVpools;

        public List<String> vPools;

        public boolean applyOnTargetSite;

        public FileReplicationTopology[] replicationTopology = {};

        public String referrerUrl;

        public AssignPolicyForm form(FilePolicyRestRep restRep) {

            this.id = restRep.getId().toString();
            // this.tenantId = restRep.getTenant().getId().toString();
            // this.policyType = restRep.getType();
            this.policyName = restRep.getName();

            this.appliedAt = restRep.getAppliedAt();

            // Load project applicable fields
            if (FilePolicyApplyLevel.project.name().equalsIgnoreCase(this.appliedAt)) {

                this.vpool = ResourceUtils.stringId(restRep.getVpool());
                this.projects = ResourceUtils.stringRefIds(restRep.getAssignedResources());
                if (this.projects == null || this.projects.isEmpty()) {
                    this.applyToAllProjects = true;
                }
            } else if (FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(this.appliedAt)) {

                this.vpool = restRep.getVpool().getName();
                for (NamedRelatedResourceRep vpool : restRep.getAssignedResources()) {
                    this.vPools.add(vpool.getName());
                }
                if (this.vPools == null || this.vPools.isEmpty()) {
                    this.applyToAllVpools = true;
                }
            }
            return this;
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            Validation.required(formName + ".policyName", policyName);

            if (policyName == null || policyName.isEmpty()) {
                Validation.addError(formName + ".policyName", MessagesUtils.get("schedulePolicy.policyName.error.required"));
            }
        }

    }

}
