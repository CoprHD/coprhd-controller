/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.util.List;
import java.util.Map;

import models.datatable.ScheculePoliciesDataTable;

import org.apache.commons.lang.StringUtils;

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
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.file.ScheduleSnapshotExpireParam;
import com.emc.storageos.model.schedulepolicy.PolicyParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("PROJECT_ADMIN"), @Restrict("TENANT_ADMIN") })
public class SchedulePolicies extends ViprResourceController {

    protected static final String UNKNOWN = "schedule.policies.unknown";

    public static void list() {
        ScheculePoliciesDataTable dataTable = new ScheculePoliciesDataTable();
        TenantSelector.addRenderArgs();
        render(dataTable);
    }

    @FlashException(value = "list", keep = true)
    public static void listJson() {
        String userId = Security.getUserInfo().getIdentifier();
        List<SchedulePolicyRestRep> viprSchedulePolicies = getViprClient().schedulePolicies().getByTenant(uri(Models.currentAdminTenant()));
        List<ScheculePoliciesDataTable.ScheculePolicy> scheculePolicies = Lists.newArrayList();
        for (SchedulePolicyRestRep viprSchedulePolicy : viprSchedulePolicies) {
            if (Security.isTenantAdmin()
                    || Security.isProjectAdmin()) {
                scheculePolicies.add(new ScheculePoliciesDataTable.ScheculePolicy(viprSchedulePolicy));
            }
        }
        renderJSON(DataTablesSupport.createJSON(scheculePolicies, params));
    }

    @FlashException(value = "list", keep = true)
    public static void create() {
        SchedulePolicyForm schedulePolicy = new SchedulePolicyForm();
        schedulePolicy.tenantId = Models.currentAdminTenant();
        addRenderArgs();
        addDateTimeRenderArgs();
        render("@edit", schedulePolicy);
    }

    @FlashException(value = "list", keep = true)
    public static void edit(String id) {
        SchedulePolicyRestRep schedulePolicyRestRep = getViprClient().schedulePolicies().get(uri(id));
        if (schedulePolicyRestRep != null) {
            SchedulePolicyForm schedulePolicy = new SchedulePolicyForm().form(schedulePolicyRestRep);
            renderArgs.put("filterDialog.startTime_time", "10:10");
            addRenderArgs();
            addDateTimeRenderArgs();
            
            render(schedulePolicy);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

    }

    @Util
    private static void addRenderArgs() {
        List<StringOption> policyTypeOptions = Lists.newArrayList();
        policyTypeOptions.add(new StringOption("snapshot", MessagesUtils.get("schedulePolicy.snapshot")));
        renderArgs.put("policyTypeOptions", policyTypeOptions);

    }

    private static void addDateTimeRenderArgs() {
        final String LAST_DAY_OF_MONTH = "L";
        // Days of the Week
        Map<String, String> daysOfWeek = Maps.newLinkedHashMap();
        for (int i = 1; i <= 7; i++) {
            String num = String.valueOf(i);
            daysOfWeek.put(num, MessagesUtils.get("datetime.daysOfWeek." + num));
        }
        renderArgs.put("daysOfWeek", daysOfWeek);

        // Days of the Month
        Map<String, String> daysOfMonth = Maps.newLinkedHashMap();
        for (int i = 1; i <= 31; i++) {
            String num = String.valueOf(i);
            daysOfMonth.put(num, num);
        }
        daysOfMonth.put(LAST_DAY_OF_MONTH, MessagesUtils.get("datetime.dayOfMonth.L"));
        renderArgs.put("daysOfMonth", daysOfMonth);

        List<StringOption> expirationTypeOptions = Lists.newArrayList();
        expirationTypeOptions.add(new StringOption("hours", MessagesUtils.get("schedulePolicy.hours")));
        expirationTypeOptions.add(new StringOption("days", MessagesUtils.get("schedulePolicy.days")));
        expirationTypeOptions.add(new StringOption("weeks", MessagesUtils.get("schedulePolicy.weeks")));
        expirationTypeOptions.add(new StringOption("months", MessagesUtils.get("schedulePolicy.months")));
        expirationTypeOptions.add(new StringOption("years", MessagesUtils.get("schedulePolicy.years")));
        renderArgs.put("expirationTypeOptions", expirationTypeOptions);
        
        String[] hoursOptions = new String[24];
        for (int i = 0; i < 24; i++) {
            String num = "";
            if( i<10 ){
                num = "0"+String.valueOf(i);
            }else{
                num = String.valueOf(i);
            }
            hoursOptions[i] = num;
        }
        String[] minutesOptions = new String[60];
        for (int i = 0; i < 60; i++) {
            String num = "";
            if( i<10 ){
                num = "0"+String.valueOf(i);
            }else{
                num = String.valueOf(i);
            }
            minutesOptions[i] = num;
        }
       
        renderArgs.put("hours", StringOption.options(hoursOptions));
        renderArgs.put("minutes", StringOption.options(minutesOptions));

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
        if (schedulePolicy.isNew()) {
            schedulePolicy.tenantId = Models.currentAdminTenant();
            PolicyParam policyParam = updatePolicyParam(schedulePolicy);
            getViprClient().schedulePolicies().create(uri(schedulePolicy.tenantId), policyParam);
        }else{
            
        }
        flash.success(MessagesUtils.get("projects.saved", schedulePolicy.policyName));
        if (StringUtils.isNotBlank(schedulePolicy.referrerUrl)) {
            redirect(schedulePolicy.referrerUrl);
        }
        else {
            list();
        }

    }
    
    private static PolicyParam updatePolicyParam(SchedulePolicyForm schedulePolicy){
        PolicyParam param = new PolicyParam();
        param.setPolicyName(schedulePolicy.policyName);
        param.setPolicyType(schedulePolicy.policyType);
        
        SchedulePolicyParam scheduleParam = new SchedulePolicyParam();
        scheduleParam.setScheduleTime(schedulePolicy.scheduleHour + ":" +schedulePolicy.scheduleMin);
        scheduleParam.setScheduleFrequency(schedulePolicy.frequency);
        scheduleParam.setScheduleRepeat(schedulePolicy.repeat);
        
        
        if(schedulePolicy.frequency!=null && "weeks".equals(schedulePolicy.frequency)){
            if (schedulePolicy.scheduleDayOfWeek != null){
                scheduleParam.setScheduleDayOfWeek(schedulePolicy.scheduleDayOfWeek); 
            }
           
        } else if(schedulePolicy.frequency!=null && "months".equals(schedulePolicy.frequency)){
            scheduleParam.setScheduleDayOfMonth(schedulePolicy.scheduleDayOfMonth);
        }
        param.setPolicySchedule(scheduleParam);
        
        ScheduleSnapshotExpireParam expireParam = new ScheduleSnapshotExpireParam();
       
        
        if(schedulePolicy.expiration != null && !"NEVER".equals(schedulePolicy.expiration)){
            expireParam.setExpireType(schedulePolicy.expireType);
            expireParam.setExpireValue(schedulePolicy.expireValue);
        }
        param.setSnapshotExpire(expireParam);
        return param;

    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().schedulePolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("schedulepolicies.deleted"));
        }
        list();
    }

    public static class SchedulePolicyForm {

        public String id;

        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        // Schedule policy name
        public String policyName;
        // Type of the policy
        public String policyType;

        // File Policy schedule type - daily, weekly, monthly.
        public String frequency;

        // Policy execution repeats on
        public int repeat;

        // Time when policy run
        public String scheduleTime;

        // week day when policy run
        public String scheduleDayOfWeek;

        // Day of the month
        public int scheduleDayOfMonth;

        // Schedule Snapshot expire type e.g hours, days, weeks, months
        public String expireType;

        // Schedule Snapshot expire after
        public int expireValue;
        
        public String expiration;
        public String referrerUrl;
        
        public String scheduleHour;
        public String scheduleMin;

        public SchedulePolicyForm form(SchedulePolicyRestRep restRep) {

            this.id = restRep.getPolicyId().toString();
            this.tenantId = restRep.getTenant().getId().toString();
            this.policyType = restRep.getPolicyType();
            this.policyName = restRep.getPolicyName();
            this.frequency = restRep.getScheduleFrequency();
            this.scheduleTime = restRep.getScheduleTime();
            

            if (restRep.getScheduleDayOfMonth() != null) {
                this.scheduleDayOfMonth = restRep.getScheduleDayOfMonth().intValue();
            }

            if (restRep.getScheduleDayOfWeek() != null) {
                this.scheduleDayOfWeek = restRep.getScheduleDayOfWeek();
            }
            
            this.expireType = restRep.getSnapshotExpireType();
            
            if (restRep.getSnapshotExpireTime() != null) {
                this.expireValue = restRep.getSnapshotExpireTime().intValue();
            }
            if (restRep.getScheduleRepeat() != null) {
                this.repeat =restRep.getScheduleRepeat().intValue();
            }
            String[] hoursMin = this.scheduleTime.split(":");
            if(hoursMin.length > 2){
                this.scheduleHour = hoursMin[0];
                this.scheduleMin = hoursMin[1];
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
