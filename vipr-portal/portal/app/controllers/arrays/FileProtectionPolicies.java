/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.file.policy.FilePolicyAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyCreateParam;
import com.emc.storageos.model.file.policy.FilePolicyCreateResp;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyParam;
import com.emc.storageos.model.file.policy.FilePolicyProjectAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.ReplicationSettingsRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.SnapshotSettingsRestRep;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FilePolicyUnAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.model.file.policy.FilePolicyVpoolAssignParam;
import com.emc.storageos.model.file.policy.FileReplicationPolicyParam;
import com.emc.storageos.model.file.policy.FileReplicationTopologyParam;
import com.emc.storageos.model.file.policy.FileReplicationTopologyRestRep;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.vipr.client.core.ACLResources;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;
import jobs.vipr.TenantsCall;
import models.datatable.ScheculePoliciesDataTable;
import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Util;
import play.mvc.With;
import util.MessagesUtils;
import util.StringOption;
import util.TenantUtils;
import util.VirtualPoolUtils;
import util.builders.ACLUpdateBuilder;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("PROJECT_ADMIN"), @Restrict("TENANT_ADMIN"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class FileProtectionPolicies extends ViprResourceController {

    protected static final String UNKNOWN = "schedule.policies.unknown";

    public static void list() {
        ScheculePoliciesDataTable dataTable = new ScheculePoliciesDataTable();
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
    
    
    
    public static void details(String id) {        
        FilePolicyRestRep filePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(id));        
        if (filePolicyRestRep == null) {
            renderJSON("");
        }     
        renderJSON(filePolicyRestRep);
    }
    

    @FlashException(value = "list", keep = true)
    public static void create() {
        SchedulePolicyForm schedulePolicy = new SchedulePolicyForm();
        schedulePolicy.tenantId = Models.currentAdminTenant();
        addRenderArgs();
        addDateTimeRenderArgs();
        addTenantOptionsArgs();
        addRenderApplyPolicysAt();
        addNumWorkerThreadsArgs();
        render("@edit", schedulePolicy);
    }

    @FlashException(value = "list", keep = true)
    public static void edit(String id) {
        FilePolicyRestRep filePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(id));
        if (filePolicyRestRep != null) {
            SchedulePolicyForm schedulePolicy = new SchedulePolicyForm().form(filePolicyRestRep);

            addRenderArgs();
            addDateTimeRenderArgs();
            addTenantOptionsArgs();
            addRenderApplyPolicysAt();
            addNumWorkerThreadsArgs();
            render(schedulePolicy);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

    }

    @FlashException(value = "list", keep = true)
    public static void assign(String ids) {

        FilePolicyRestRep filePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(ids));

        if (filePolicyRestRep != null) {
            AssignPolicyForm assignPolicy = new AssignPolicyForm().form(filePolicyRestRep);
            addRenderApplyPolicysAt();
            addProjectArgs(ids);
            addvPoolArgs(ids);
            render(assignPolicy);

        } else {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            list();
        }

    }
    
    
    @FlashException(value = "list", keep = true)
    public static void unassign(String ids) {

        FilePolicyRestRep filePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(ids));

        if (filePolicyRestRep != null) {
            AssignPolicyForm assignPolicy = new AssignPolicyForm().form(filePolicyRestRep);
            addRenderApplyPolicysAt();
            addProjectArgs(ids);
            addvPoolArgs(ids);
            render(assignPolicy);

        } else {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            list();
        }

    }

    @Util
    private static void addRenderArgs() {
        List<StringOption> policyTypeOptions = Lists.newArrayList();
        policyTypeOptions.add(new StringOption("file_snapshot", MessagesUtils.get("schedulePolicy.snapshot")));
        policyTypeOptions.add(new StringOption("file_replication", MessagesUtils.get("schedulePolicy.replication")));
        renderArgs.put("policyTypeOptions", policyTypeOptions);

        List<StringOption> replicationTypeOptions = Lists.newArrayList();
        replicationTypeOptions.add(new StringOption("REMOTE", MessagesUtils.get("schedulePolicy.replicationRemote")));
        replicationTypeOptions.add(new StringOption("LOCAL", MessagesUtils.get("schedulePolicy.replicationLocal")));
        renderArgs.put("replicationTypeOptions", replicationTypeOptions);

        List<StringOption> replicationCopyTypeOptions = Lists.newArrayList();
        replicationCopyTypeOptions.add(new StringOption("ASYNC", MessagesUtils.get("schedulePolicy.replicationAsync")));
        renderArgs.put("replicationCopyTypeOptions", replicationCopyTypeOptions);

        List<StringOption> policyPriorityOptions = Lists.newArrayList();
        policyPriorityOptions.add(new StringOption("HIGH", MessagesUtils.get("schedulePolicy.priorityHigh")));
        policyPriorityOptions.add(new StringOption("LOW", MessagesUtils.get("schedulePolicy.priorityLow")));
        renderArgs.put("policyPriorityOptions", policyPriorityOptions);

    }

    private static void addDateTimeRenderArgs() {
        // Days of the Week
        Map<String, String> daysOfWeek = Maps.newLinkedHashMap();
        for (int i = 1; i <= 7; i++) {
            String num = String.valueOf(i);
            daysOfWeek.put(MessagesUtils.get("datetime.daysOfWeek." + num).toLowerCase(), MessagesUtils.get("datetime.daysOfWeek." + num));
        }
        renderArgs.put("daysOfWeek", daysOfWeek);

        // Days of the Month
        Map<String, String> daysOfMonth = Maps.newLinkedHashMap();
        for (int i = 1; i <= 31; i++) {
            String num = String.valueOf(i);
            daysOfMonth.put(num, num);
        }

        renderArgs.put("daysOfMonth", daysOfMonth);

        List<StringOption> expirationTypeOptions = Lists.newArrayList();
        expirationTypeOptions.add(new StringOption("hours", MessagesUtils.get("schedulePolicy.hours")));
        expirationTypeOptions.add(new StringOption("days", MessagesUtils.get("schedulePolicy.days")));
        expirationTypeOptions.add(new StringOption("weeks", MessagesUtils.get("schedulePolicy.weeks")));
        expirationTypeOptions.add(new StringOption("months", MessagesUtils.get("schedulePolicy.months")));

        renderArgs.put("expirationTypeOptions", expirationTypeOptions);

        String[] hoursOptions = new String[24];
        for (int i = 0; i < 24; i++) {
            String num = "";
            if (i < 10) {
                num = "0" + String.valueOf(i);
            } else {
                num = String.valueOf(i);
            }
            hoursOptions[i] = num;
        }
        String[] minutesOptions = new String[60];
        for (int i = 0; i < 60; i++) {
            String num = "";
            if (i < 10) {
                num = "0" + String.valueOf(i);
            } else {
                num = String.valueOf(i);
            }
            minutesOptions[i] = num;
        }

        renderArgs.put("hours", StringOption.options(hoursOptions));
        renderArgs.put("minutes", StringOption.options(minutesOptions));

    }

    private static void addNumWorkerThreadsArgs() {
        // Number of worker threads!!
        Map<String, String> numWorkerThreads = Maps.newLinkedHashMap();
        for (int i = 3; i <= 10; i++) {
            String num = String.valueOf(i);
            numWorkerThreads.put(num, num);
        }
        renderArgs.put("numWorkerThreadsOptions", numWorkerThreads);
    }

    private static void addTenantOptionsArgs() {

        if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
            addDataObjectOptions("tenantOptions", new TenantsCall().asPromise());
        }
    }

    private static void addRenderApplyPolicysAt() {

        List<StringOption> applyPolicyAtOptions = Lists.newArrayList();
        applyPolicyAtOptions.add(new StringOption(FilePolicyApplyLevel.vpool.name(), MessagesUtils.get("assignPolicy.applyAtVPool")));
        applyPolicyAtOptions.add(new StringOption(FilePolicyApplyLevel.project.name(), MessagesUtils.get("assignPolicy.applyAtProject")));
        applyPolicyAtOptions.add(new StringOption(FilePolicyApplyLevel.file_system.name(), MessagesUtils.get("assignPolicy.applyAtFs")));
        renderArgs.put("applyPolicyOptions", applyPolicyAtOptions);

    }

    private static List<StringOption> createResourceOptions(Collection<? extends DataObjectRestRep> values) {
        List<StringOption> options = Lists.newArrayList();
        for (DataObjectRestRep value : values) {
            options.add(new StringOption(value.getId().toString(), value.getName()));
        }
        return options;
    }

    private static List<StringOption> getFileVirtualPoolsOptions(URI virtualArray, String id) {
        Collection<FileVirtualPoolRestRep> virtualPools;
        if (virtualArray == null) {
            virtualPools = getViprClient().fileVpools().getAll();
        } else {
            virtualPools = getViprClient().fileVpools().getByVirtualArray(virtualArray);
        }
        // Filter the vpools based on policy type!!!
        Collection<FileVirtualPoolRestRep> vPools = Lists.newArrayList();
        FilePolicyRestRep policy = getViprClient().fileProtectionPolicies().get(uri(id));
        for (FileVirtualPoolRestRep vpool : virtualPools) {
            if (FilePolicyType.file_snapshot.name().equalsIgnoreCase(policy.getType()) &&
                    vpool.getProtection() != null && vpool.getProtection().getScheduleSnapshots()) {
                vPools.add(vpool);
            } else if (FilePolicyType.file_replication.name().equalsIgnoreCase(policy.getType()) &&
                    vpool.getProtection() != null && vpool.getProtection().getReplicationSupported()) {
                vPools.add(vpool);
            }
        }

        return createResourceOptions(vPools);
    }
    
    
    /**
     * Get list the pool which is already assigned to a protection policy.
     * @param id
     * @return
     */
    private static List<StringOption> getAssignedResourceOptions(String id,FilePolicyApplyLevel type) {

        List<StringOption> options = Lists.newArrayList();
        // Filter the vpools based on policy type!!!
        Collection<FileVirtualPoolRestRep> vPools = Lists.newArrayList();
        FilePolicyRestRep policy = getViprClient().fileProtectionPolicies().get(uri(id));
        if (type.name().equalsIgnoreCase(policy.getAppliedAt())) {
            List<NamedRelatedResourceRep> existingResource = policy.getAssignedResources();

            for (NamedRelatedResourceRep value : existingResource) {

                options.add(new StringOption(value.getId().toString(), value.getName())); 
            }
        }
        return options;
    }

    private static List<StringOption> getFileProjectOptions(URI tenantId) {
        Collection<ProjectRestRep> projects = getViprClient().projects().getByTenant(tenantId);
        return createResourceOptions(projects);
    }

    private static void addProjectArgs(String id) {
        renderArgs.put("projectVpoolOptions", getFileVirtualPoolsOptions(null, id));
        renderArgs.put("projectOptions", getFileProjectOptions(uri(Models.currentAdminTenant())));
    }

    private static void addvPoolArgs(String id) {
        renderArgs.put("vPoolOptions", getFileVirtualPoolsOptions(null, id));
        renderArgs.put("virtualArrayOptions", getVarrays());
    }
    
    
    
    private static void addAssignedProjectArgs(String id) {
        renderArgs.put("projectVpoolOptions", getFileVirtualPoolsOptions(null, id));
        renderArgs.put("projectOptions", getFileProjectOptions(uri(Models.currentAdminTenant())));
    }

    private static void addAssignedVPoolArgs(String id) {
        renderArgs.put("vPoolOptions", getFileVirtualPoolsOptions(null, id));
        renderArgs.put("virtualArrayOptions", getVarrays());
    }
    
    
    
    

    private static List<StringOption> getVarrays() {

        List<StringOption> varrayList = Lists.newArrayList();
        List<NamedRelatedResourceRep> allVarrays = getViprClient().varrays().list();

        for (NamedRelatedResourceRep varray : allVarrays) {
            varrayList.add(new StringOption(varray.getId().toString(), varray.getName()));
        }
        return varrayList;
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(SchedulePolicyForm schedulePolicy) {
        if (schedulePolicy == null) {
            Logger.error("No policy parameters passed");
            badRequest("No policy parameters passed");
            return;
        }
        schedulePolicy.validate("schedulePolicy");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        schedulePolicy.id = params.get("id");
        URI policyId = null;
        if (schedulePolicy.isNew()) {
            schedulePolicy.tenantId = Models.currentAdminTenant();
            FilePolicyCreateParam policyParam = new FilePolicyCreateParam();
            updatePolicyParam(schedulePolicy, policyParam, null);
            policyParam.setPolicyType(schedulePolicy.policyType);
            policyParam.setPolicyDescription(schedulePolicy.description);
            FilePolicyCreateResp createdPolicy = getViprClient().fileProtectionPolicies().create(policyParam);
            policyId = createdPolicy.getId();
        } else {
            FilePolicyRestRep schedulePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(schedulePolicy.id));
            FilePolicyUpdateParam input = new FilePolicyUpdateParam();
            updatePolicyParam(schedulePolicy, input, schedulePolicyRestRep.getType());
            getViprClient().fileProtectionPolicies().update(schedulePolicyRestRep.getId(), input);
            policyId = schedulePolicyRestRep.getId();
        }
        // Update the ACLs
        com.emc.vipr.client.core.FileProtectionPolicies filePolicies = getViprClient().fileProtectionPolicies();
        schedulePolicy.saveTenantACLs(filePolicies, policyId);
        flash.success(MessagesUtils.get("schedulepolicies.saved", schedulePolicy.policyName));
        if (StringUtils.isNotBlank(schedulePolicy.referrerUrl)) {
            redirect(schedulePolicy.referrerUrl);
        } else {
            list();
        }

    }

    @FlashException(keep = true, referrer = { "assign" })
    public static void saveAssignPolicy(AssignPolicyForm assignPolicy) {

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
        FilePolicyUnAssignParam unAssignPolicyParam = new FilePolicyUnAssignParam();
        if (updateUnAssignPolicyParam(assignPolicy, unAssignPolicyParam)) {
            getViprClient().fileProtectionPolicies().unassignPolicy(uri(assignPolicy.id), unAssignPolicyParam);
            flash.success(MessagesUtils.get("unAssignPolicy.request.submit", assignPolicy.policyName));
        }else{
            FilePolicyAssignParam assignPolicyParam = new FilePolicyAssignParam();
            updateAssignPolicyParam(assignPolicy, assignPolicyParam);
            getViprClient().fileProtectionPolicies().assignPolicy(uri(assignPolicy.id), assignPolicyParam);
            flash.success(MessagesUtils.get("assignPolicy.request.submit", assignPolicy.policyName));
        }
       
        if (StringUtils.isNotBlank(assignPolicy.referrerUrl)) {
            redirect(assignPolicy.referrerUrl);
        } else {
            list();
        }

    }
    
    private static Set<FileReplicationTopologyParam>  getFileReplicationTopologyParamSet(List<FileReplicationTopology> replicationTopologies){
        
        Set<FileReplicationTopologyParam>  topologyParamSet = new HashSet<FileReplicationTopologyParam>();
        
        for (FileReplicationTopology replicationTopology : replicationTopologies) {
            
            FileReplicationTopologyParam  param= new FileReplicationTopologyParam();
            param.setSourceVArray((uri(replicationTopology.sourceVArray)));
            Set<URI> targetVArrays = new HashSet<URI>();
            targetVArrays.add((uri(replicationTopology.targetVArray)));
            param.setTargetVArrays(targetVArrays);          
            topologyParamSet.add(param);
        }
        return topologyParamSet;
    }

    private static FilePolicyParam updatePolicyParam(SchedulePolicyForm schedulePolicy, FilePolicyParam param, String policyType) {
        param.setPolicyName(schedulePolicy.policyName);
        if (schedulePolicy.appliedAt != null) {
            param.setApplyAt(schedulePolicy.appliedAt);
        }
        
        if(schedulePolicy.description != null && !schedulePolicy.description.isEmpty()){
            param.setPolicyDescription(schedulePolicy.description);           
        }

        if (policyType == null) {
            policyType = schedulePolicy.policyType;
        }
        FilePolicyScheduleParams scheduleParam = new FilePolicyScheduleParams();
        if (policyType.equalsIgnoreCase("file_snapshot") || policyType.equalsIgnoreCase("file_replication")) {
            scheduleParam.setScheduleTime(schedulePolicy.scheduleHour + ":" + schedulePolicy.scheduleMin);
            scheduleParam.setScheduleFrequency(schedulePolicy.frequency);
            scheduleParam.setScheduleRepeat(schedulePolicy.repeat);

            if (schedulePolicy.frequency != null && "weeks".equals(schedulePolicy.frequency)) {
                if (schedulePolicy.scheduleDayOfWeek != null) {
                    scheduleParam.setScheduleDayOfWeek(schedulePolicy.scheduleDayOfWeek);
                }

            } else if (schedulePolicy.frequency != null && "months".equals(schedulePolicy.frequency)) {
                scheduleParam.setScheduleDayOfMonth(schedulePolicy.scheduleDayOfMonth);
            }
        }

        if (policyType.equalsIgnoreCase("file_snapshot")) {
            FileSnapshotPolicyParam snapshotParam = new FileSnapshotPolicyParam();
            snapshotParam.setSnapshotNamePattern(schedulePolicy.snapshotNamePattern);
            snapshotParam.setPolicySchedule(scheduleParam);
            FileSnapshotPolicyExpireParam snapExpireParam = new FileSnapshotPolicyExpireParam();
            if (schedulePolicy.expiration != null && !"NEVER".equals(schedulePolicy.expiration)) {
                snapExpireParam.setExpireType(schedulePolicy.expireType);
                snapExpireParam.setExpireValue(schedulePolicy.expireValue);
                snapshotParam.setSnapshotExpireParams(snapExpireParam);
                param.setSnapshotPolicyPrams(snapshotParam);
            }
            if ("NEVER".equals(schedulePolicy.expiration)) {
                snapExpireParam.setExpireType("never");
                snapshotParam.setSnapshotExpireParams(snapExpireParam);
                param.setSnapshotPolicyPrams(snapshotParam);
            }
        } else if (policyType.equalsIgnoreCase("file_replication")) {
            FileReplicationPolicyParam replicationPolicyParams = new FileReplicationPolicyParam();
            replicationPolicyParams.setReplicationCopyMode(schedulePolicy.replicationCopyType);
            replicationPolicyParams.setReplicationType(schedulePolicy.replicationType);
            replicationPolicyParams.setPolicySchedule(scheduleParam);
            param.setPriority(schedulePolicy.priority);
            param.setNumWorkerThreads(schedulePolicy.numWorkerThreads);
            param.setReplicationPolicyParams(replicationPolicyParams);
        }

        return param;

    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().fileProtectionPolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("schedulepolicies.deleted"));
        }
        list();
    }

    // Suppressing sonar violation for need of accessor methods. Accessor methods are not needed and we use public variables
    @SuppressWarnings("ClassVariableVisibilityCheck")
    public static class SchedulePolicyForm {

        public String id;

        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        // Schedule policy name
        public String policyName;
               
        // Schedule policy description
        public String description;
              
        // Type of the policy
        public String policyType;

        // File Policy schedule type - daily, weekly, monthly.
        public String frequency = "days";

        // Policy execution repeats on
        public Long repeat = 1L;

        // Time when policy run
        public String scheduleTime;

        // week day when policy run
        public String scheduleDayOfWeek;

        // Day of the month
        public Long scheduleDayOfMonth;

        public String snapshotNamePattern = "Snapshot_%Y-%m-%d-_%H-%M";

        // Schedule Snapshot expire type e.g hours, days, weeks, months and never
        public String expireType;

        // Schedule Snapshot expire after
        public int expireValue = 2;

        public String expiration = "EXPIRE_TIME";
        public String referrerUrl;

        public String scheduleHour;
        public String scheduleMin;

        public String appliedAt;

        public boolean enableTenants;

        public String tenants;

        // Replication policy specific fields
        // Replication type local / remote
        public String replicationType;
        // Replication copy type - sync / async / demi-sync
        public String replicationCopyType;
        // Replication policy priority low /high
        public String priority;

        public int numWorkerThreads = 3;

        public SchedulePolicyForm form(FilePolicyRestRep restRep) {

            this.id = restRep.getId().toString();
            // this.tenantId = restRep.getTenant().getId().toString();
            this.policyType = restRep.getType();
            this.policyName = restRep.getName();
            this.frequency = restRep.getSchedule().getFrequency();
            
            if (restRep.getDescription() != null && !restRep.getDescription().isEmpty()) {
               this.description= restRep.getDescription();
            }
            if (restRep.getSchedule().getDayOfMonth() != null) {
                this.scheduleDayOfMonth = restRep.getSchedule().getDayOfMonth();
            }

            if (restRep.getSchedule().getDayOfWeek() != null) {
                this.scheduleDayOfWeek = restRep.getSchedule().getDayOfWeek();
            }
            if (restRep.getSnapshotSettings() != null) {
                SnapshotSettingsRestRep snapshotSettings = restRep.getSnapshotSettings();

                if (snapshotSettings.getSnapshotNamePattern() != null) {
                    this.snapshotNamePattern = snapshotSettings.getSnapshotNamePattern();
                }
                if (snapshotSettings.getExpiryType() != null) {
                    this.expireType = snapshotSettings.getExpiryType();
                }
                if (snapshotSettings.getExpiryTime() != null) {
                    this.expireValue = snapshotSettings.getExpiryTime().intValue();
                }
            }

            if (restRep.getSchedule() != null) {
                this.repeat = restRep.getSchedule().getRepeat();
            }

            if (restRep.getSchedule() != null && restRep.getSchedule().getTime() != null) {
                this.scheduleTime = restRep.getSchedule().getTime();
                String[] hoursMin = this.scheduleTime.split(":");
                if (hoursMin.length > 1) {
                    this.scheduleHour = hoursMin[0];
                    String[] minWithStrings = hoursMin[1].split(" ");
                    if (minWithStrings.length > 0) {
                        this.scheduleMin = minWithStrings[0];
                    }

                }
            }

            if (this.expireType == null || "never".equals(this.expireType)) {
                this.expiration = "NEVER";
            } else {
                this.expiration = "EXPIRE_TIME";
            }

            if (restRep.getPriority() != null) {
                this.priority = restRep.getPriority();
            }

            if (restRep.getNumWorkerThreads() != null && restRep.getNumWorkerThreads() > 0) {
                this.numWorkerThreads = restRep.getNumWorkerThreads().intValue();
            }

            if (restRep.getAppliedAt() != null) {
                this.appliedAt = restRep.getAppliedAt();
            }
            // Update replication fileds
            if (restRep.getReplicationSettings() != null) {
                ReplicationSettingsRestRep replSetting = restRep.getReplicationSettings();
                if (replSetting.getMode() != null) {
                    this.replicationCopyType = replSetting.getMode();
                }
                if (replSetting.getType() != null) {
                    this.replicationType = replSetting.getType();
                }
            }

            // Get the ACLs
            com.emc.vipr.client.core.FileProtectionPolicies fileProtectionPolicies = getViprClient().fileProtectionPolicies();
            loadTenantACLs(fileProtectionPolicies);
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

        /**
         * Loads the tenant ACL information from the provided ACLResources.
         * 
         * @param resources
         *            the resources from which to load the ACLs.
         */
        protected void loadTenantACLs(ACLResources resources) {
            this.tenants = "";

            URI policyId = ResourceUtils.uri(id);
            if (policyId != null) {
                for (ACLEntry acl : resources.getACLs(policyId)) {
                    if (StringUtils.isNotBlank(acl.getTenant())) {
                        this.tenants = acl.getTenant();
                        break;
                    }
                }
            }
            this.enableTenants = !tenants.isEmpty();
        }

        /**
         * Saves the tenant ACL information using the provided ACLResources.
         * 
         * @param resources
         *            the resources on which to save the tenant ACLs.
         */
        protected void saveTenantACLs(ACLResources resources, URI policyId) {
            // Only allow a user than can read all tenants and update ACLs do this
            if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
                if (policyId != null) {
                    Set<String> tenantIds = Sets.newHashSet();
                    if (isTrue(enableTenants) && (tenants != null)) {
                        tenantIds.add(tenants);
                    }
                    ACLUpdateBuilder builder = new ACLUpdateBuilder(resources.getACLs(policyId));
                    builder.setTenants(tenantIds);
                    resources.updateACLs(policyId, builder.getACLUpdate());
                }
            }
        }

    }

    private static Boolean updateAssignPolicyParam(AssignPolicyForm assignPolicy, FilePolicyAssignParam param) {

        Boolean policyAssignment = false;
        FilePolicyRestRep existingPolicy = getViprClient().fileProtectionPolicies().get(URI.create(assignPolicy.id));

        param.setApplyOnTargetSite(assignPolicy.applyOnTargetSite);

        // Get source and target varrays
        if (FilePolicyType.file_replication.name().equalsIgnoreCase(existingPolicy.getType())) {

            List<FileReplicationTopology> replicationTopologies =null;
            if (assignPolicy.topologiesString != null && !assignPolicy.topologiesString.isEmpty()) {
                GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();
                replicationTopologies = Arrays
                        .asList((gson.fromJson(assignPolicy.topologiesString, FileReplicationTopology[].class)));
            }

            Set<FileReplicationTopologyParam> toploSet = getFileReplicationTopologyParamSet(replicationTopologies);
            param.setFileReplicationtopologies(toploSet);
        }

        if (FilePolicyApplyLevel.project.name().equalsIgnoreCase(existingPolicy.getAppliedAt())) {

            FilePolicyProjectAssignParam projectAssignParams = new FilePolicyProjectAssignParam();
            projectAssignParams.setVpool(uri(assignPolicy.vpool));

            List<String> existingProjects = stringRefIds(existingPolicy.getAssignedResources());
            List<String> projects = Lists.newArrayList();
            if (assignPolicy.projects != null) {
                projects = assignPolicy.projects;
            }

            Set<String> add = Sets.newHashSet(CollectionUtils.subtract(projects, existingProjects));

            // Assign new projects
            Set<URI> assignProjects = new HashSet<URI>();
            if (!add.isEmpty()) {
                for (String project : add) {
                    assignProjects.add(uri(project));
                }
                policyAssignment = true;
            }
            projectAssignParams.setAssigntoProjects(assignProjects);
            param.setProjectAssignParams(projectAssignParams);

        } else if (FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(existingPolicy.getAppliedAt())) {
            FilePolicyVpoolAssignParam vpoolAssignParams = new FilePolicyVpoolAssignParam();

            List<String> existingvPools = stringRefIds(existingPolicy.getAssignedResources());
            List<String> vPools = Lists.newArrayList();

            if (assignPolicy.virtualPools != null) {
                vPools = assignPolicy.virtualPools;
            }

            Set<String> add = Sets.newHashSet(CollectionUtils.subtract(vPools, existingvPools));

            Set<URI> assignVpools = new HashSet<URI>();
            if (!add.isEmpty()) {
                for (String vpool : add) {
                    assignVpools.add(uri(vpool));
                }
                policyAssignment = true;
            }
            vpoolAssignParams.setAssigntoVpools(assignVpools);
            param.setVpoolAssignParams(vpoolAssignParams);
        }

        return policyAssignment;

    }

    private static Boolean updateUnAssignPolicyParam(AssignPolicyForm assignPolicy, FilePolicyUnAssignParam param) {

        Boolean policyUnAssignment = false;
        FilePolicyRestRep existingPolicy = getViprClient().fileProtectionPolicies().get(URI.create(assignPolicy.id));

        if (FilePolicyApplyLevel.project.name().equalsIgnoreCase(existingPolicy.getAppliedAt())) {

            List<String> existingProjects = stringRefIds(existingPolicy.getAssignedResources());
            List<String> projects = Lists.newArrayList();
            if (assignPolicy.projects != null) {
                projects = assignPolicy.projects;
            }

            Set<String> remove = Sets.newHashSet(CollectionUtils.subtract(existingProjects, projects));

            // removed projects
            Set<URI> unAssingRes = new HashSet<URI>();
            if (!remove.isEmpty()) {
                for (String project : remove) {
                    unAssingRes.add(uri(project));
                }
                policyUnAssignment = true;
            }
            param.setUnassignfrom(unAssingRes);

        } else if (FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(existingPolicy.getAppliedAt())) {

            List<String> existingvPools = stringRefIds(existingPolicy.getAssignedResources());
            List<String> vPools = Lists.newArrayList();

            if (assignPolicy.virtualPools != null) {
                vPools = assignPolicy.virtualPools;
            }

            Set<String> remove = Sets.newHashSet(CollectionUtils.subtract(existingvPools, vPools));

            // removed vpools
            Set<URI> unAssingRes = new HashSet<URI>();
            if (!remove.isEmpty()) {
                for (String vpool : remove) {
                    unAssingRes.add(uri(vpool));
                }
                policyUnAssignment = true;
            }
            param.setUnassignfrom(unAssingRes);
        }

        return policyUnAssignment;

    }

    // Suppressing sonar violation for need of accessor methods. Accessor methods are not needed and we use public variables
    @SuppressWarnings("ClassVariableVisibilityCheck")
    public static class AssignPolicyForm {

        public String id;

        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        // Schedule policy name
        public String policyName;
        // Type of the policy
        public String policyType;

        public String vpool;

        public List<String> projects;

        public List<String> virtualPools;

        public boolean applyOnTargetSite;
        
        //if true then form is used for assignmnet
        public boolean operationAssign;

        public String appliedAt;

        public List<FileReplicationTopology> topologies;
        public String topologiesString;

        public String referrerUrl;


        public AssignPolicyForm form(FilePolicyRestRep restRep) {

            this.id = restRep.getId().toString();
            // this.tenantId = restRep.getTenant().getId().toString();
            this.policyType = restRep.getType();
            this.policyName = restRep.getName();
            if(restRep.getAssignedResources() == null) {
                // if it does not have already assigned  resource
                this.operationAssign =true;
                
            }

            this.appliedAt = restRep.getAppliedAt();
            this.applyOnTargetSite = false;
            if (restRep.getApplyOnTargetSite() != null) {
                this.applyOnTargetSite = restRep.getApplyOnTargetSite();
            }
            if (restRep.getReplicationSettings() != null) {  List<FileReplicationTopologyRestRep> topologyRestReps = restRep.getReplicationSettings().getReplicationTopologies();
            if (topologyRestReps != null && !topologyRestReps.isEmpty()) {


                List<FileReplicationTopology> replicationTopologies = new ArrayList<FileReplicationTopology>();

                for (FileReplicationTopologyRestRep repTopology : topologyRestReps) {
                    FileReplicationTopology fileTopology = new FileReplicationTopology();
                    if (repTopology.getSourceVArray() != null) {
                        fileTopology.sourceVArray = repTopology.getSourceVArray().toString();
                    }
                    if (repTopology.getTargetVArrays() != null && !repTopology.getTargetVArrays().isEmpty()) {
                        fileTopology.targetVArray = repTopology.getTargetVArrays().iterator().next().toString();
                    }
                    replicationTopologies.add(fileTopology);
                }
                if (!replicationTopologies.isEmpty()) {
                    this.topologies =replicationTopologies;
                }
            }

            }
            // Load project applicable fields
            if (FilePolicyApplyLevel.project.name().equalsIgnoreCase(restRep.getAppliedAt())) {
                this.vpool = ResourceUtils.stringId(restRep.getVpool());
                this.projects = ResourceUtils.stringRefIds(restRep.getAssignedResources());
            } else if (FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(restRep.getAppliedAt())) {
                this.virtualPools = ResourceUtils.stringRefIds(restRep.getAssignedResources());
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
