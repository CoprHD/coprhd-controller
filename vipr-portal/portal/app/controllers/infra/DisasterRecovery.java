/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.infra;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import models.datatable.DisasterRecoveryDataTable;
import models.datatable.DisasterRecoveryDataTable.StandByInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.DisasterRecoveryUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.dr.SiteUpdateParam;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.model.dr.SiteActive;
import com.emc.storageos.model.dr.SiteRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN"), @Restrict("SYSTEM_MONITOR") })
public class DisasterRecovery extends ViprResourceController {
    protected static final String SAVED_SUCCESS = "disasterRecovery.save.success";
    protected static final String PAUSED_SUCCESS = "disasterRecovery.pause.success";
    protected static final String PAUSED_ERROR = "disasterRecovery.pause.error";
    protected static final String SWITCHOVER_SUCCESS = "disasterRecovery.switchover.success";
    protected static final String SWITCHOVER_ERROR = "disasterRecovery.switchover.error";
    protected static final String RESUMED_SUCCESS = "disasterRecovery.resume.success";
    protected static final String SAVED_ERROR = "disasterRecovery.save.error";
    protected static final String DELETED_SUCCESS = "disasterRecovery.delete.success";
    protected static final String DELETED_ERROR = "disasterRecovery.delete.error";
    protected static final String UNKNOWN = "disasterRecovery.unknown";
    protected static final String UPDATE_SUCCESS = "disasterRecovery.update.success";

    private static void backToReferrer() {
        String referrer = Common.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        }
        else {
            list();
        }
    }

    public static void list() {
        DisasterRecoveryDataTable dataTable = createDisasterRecoveryDataTable();
        render(dataTable);
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void pause(@As(",") String[] ids) {
        List<String> uuids = Arrays.asList(ids);
        for (String uuid : uuids) {
            if (!DisasterRecoveryUtils.hasStandbySite(uuid)) {
                flash.error(MessagesUtils.get(UNKNOWN, uuid));
                list();
            }

        }

        SiteIdListParam param = new SiteIdListParam();
        param.getIds().addAll(uuids);
        DisasterRecoveryUtils.pauseStandby(param);
        flash.success(MessagesUtils.get(PAUSED_SUCCESS));
        list();
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void resume(String id) {
        SiteRestRep result = DisasterRecoveryUtils.getSite(id);
        if (result != null) {
            SiteRestRep siteresume = DisasterRecoveryUtils.resumeStandby(id);
            flash.success(MessagesUtils.get(RESUMED_SUCCESS, siteresume.getName()));
        }
        list();
    }

    public static void test(String id) {

    }

    @FlashException("list")
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void switchover(String id) {
        String standby_name = null;
        String standby_vip = null;
        String active_name = null;

        // Get active site details
        SiteRestRep activesite = DisasterRecoveryUtils.getActiveSite();
        if (activesite == null) {
            flash.error(SWITCHOVER_ERROR, "Can't switchover");
            list();
        }
        else {
            active_name = activesite.getName();
        }

        SiteRestRep result = DisasterRecoveryUtils.getSite(id);
        if (result != null) {
            // Check Switchover or Failover
            SiteActive currentSite = DisasterRecoveryUtils.checkPrimary();
            if (currentSite.getIsActive() == true) {
                DisasterRecoveryUtils.doSwitchover(id);
            }
            else {
                DisasterRecoveryUtils.doFailover(id);
            }
            standby_name = result.getName();
            standby_vip = result.getVip();
        }
        String site_uuid = id;
        result = DisasterRecoveryUtils.getSite(id);
        String site_state = result.getState();
        render(active_name, standby_name, standby_vip, site_uuid, site_state);
    }

    private static DisasterRecoveryDataTable createDisasterRecoveryDataTable() {
        DisasterRecoveryDataTable dataTable = new DisasterRecoveryDataTable();
        return dataTable;
    }

    public static void listJson() {
        List<DisasterRecoveryDataTable.StandByInfo> disasterRecoveries = Lists.newArrayList();
        for (SiteRestRep siteConfig : DisasterRecoveryUtils.getSiteDetails()) {
            disasterRecoveries.add(new StandByInfo(siteConfig));
        }
        renderJSON(DataTablesSupport.createJSON(disasterRecoveries, params));
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void create() {
        DisasterRecoveryForm site = new DisasterRecoveryForm();
        edit(site);
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void edit(String id) {
        SiteRestRep siteRest = DisasterRecoveryUtils.getSite(id);
        if (siteRest != null) {
            DisasterRecoveryForm disasterRecovery = new DisasterRecoveryForm(siteRest);
            edit(disasterRecovery);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    private static void edit(DisasterRecoveryForm disasterRecovery) {
        render("@edit", disasterRecovery);
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void save(DisasterRecoveryForm disasterRecovery) {
        if (disasterRecovery != null) {
            disasterRecovery.validate("disasterRecovery");
            if (Validation.hasErrors()) {
                Common.handleError();
            }
            if (disasterRecovery.isNew()) {
                SiteAddParam standbySite = new SiteAddParam();
                standbySite.setName(disasterRecovery.name);
                standbySite.setVip(disasterRecovery.VirtualIP);
                standbySite.setUsername(disasterRecovery.userName);
                standbySite.setPassword(disasterRecovery.userPassword);
                standbySite.setDescription(disasterRecovery.description);

                SiteRestRep result = DisasterRecoveryUtils.addStandby(standbySite);
                flash.success(MessagesUtils.get(SAVED_SUCCESS, result.getName()));
                list();
            }
            else {
                SiteUpdateParam siteUpdateParam = new SiteUpdateParam();
                siteUpdateParam.setName(disasterRecovery.name);
                siteUpdateParam.setDescription(disasterRecovery.description);
                DisasterRecoveryUtils.updateSite(disasterRecovery.id, siteUpdateParam);
                flash.success(MessagesUtils.get(UPDATE_SUCCESS, disasterRecovery.name));
                list();
            }
        }
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void delete(@As(",") String[] ids) {
        List<String> uuids = Arrays.asList(ids);
        for (String uuid : uuids) {
            if (!DisasterRecoveryUtils.hasStandbySite(uuid)) {
                flash.error(MessagesUtils.get(UNKNOWN, uuid));
                list();
            }

        }

        SiteIdListParam param = new SiteIdListParam();
        param.getIds().addAll(uuids);
        DisasterRecoveryUtils.deleteStandby(param);
        flash.success(MessagesUtils.get(DELETED_SUCCESS));
        list();
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<String> uuids = Arrays.asList(ids);
        itemsJson(uuids);
    }

    public static boolean isPrimarySite() {
        return DisasterRecoveryUtils.isPrimarySite();
    }

    public static void checkFailoverProgress(String uuid) {
        SiteRestRep siteRest = DisasterRecoveryUtils.getSite(uuid);
        renderJSON(siteRest);
    }

    public static void errorDetails(String id) {
        SiteRestRep siteRest = DisasterRecoveryUtils.getSite(id);
        if (siteRest.getState().equals(String.valueOf(SiteState.STANDBY_ERROR))) {
            SiteErrorResponse disasterSiteError = DisasterRecoveryUtils.getSiteError(id);
            render(disasterSiteError);
        }
    }

    private static void itemsJson(List<String> uuids) {
        List<SiteRestRep> standbySites = new ArrayList<SiteRestRep>();
        for (String uuid : uuids) {
            SiteRestRep standbySite = DisasterRecoveryUtils.getSite(uuid);
            standbySites.add(standbySite);
        }
        performItemsJson(standbySites, new JsonItemOperation());
    }

    protected static class JsonItemOperation implements ResourceValueOperation<StandByInfo, SiteRestRep> {
        @Override
        public StandByInfo performOperation(SiteRestRep provider) throws Exception {
            return new StandByInfo(provider);
        }
    }

    // Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.
    @SuppressWarnings("squid:S2068")
    public static class DisasterRecoveryForm {
        public String id;

        @MaxSize(2048)
        @Required
        public String name;

        @Required
        @HostNameOrIpAddress
        public String VirtualIP;

        @MaxSize(2048)
        public String userName;

        @MaxSize(2048)
        public String userPassword;

        @MaxSize(2048)
        public String confirmPassword;

        @MaxSize(2048)
        public String description;

        public DisasterRecoveryForm() {
            this.userPassword = "";
            this.confirmPassword = "";
        }

        public DisasterRecoveryForm(SiteAddParam siteaddParam) {
            this.id = siteaddParam.getId();
            this.name = siteaddParam.getName();
            this.userName = siteaddParam.getUsername();
            this.VirtualIP = siteaddParam.getVip();
            this.description = siteaddParam.getDescription();
        }

        public DisasterRecoveryForm(SiteRestRep siteeditParam) {
            this.id = siteeditParam.getUuid();
            this.name = siteeditParam.getName();
            this.description = siteeditParam.getDescription();
            this.VirtualIP = siteeditParam.getVip();
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldName) {
            if (isNew()) {
                Validation.valid(fieldName, this);
                Validation.required(fieldName + ".name", this.name);
                Validation.required(fieldName + ".description", this.description);
                Validation.required(fieldName + ".VirtualIP", this.VirtualIP);
                Validation.required(fieldName + ".userName", this.userName);
                Validation.required(fieldName + ".userPassword", this.userPassword);
                Validation.required(fieldName + ".confirmPassword", this.confirmPassword);

                if (!isMatchingPasswords(userPassword, confirmPassword)) {
                    Validation.addError(fieldName + ".confirmPassword",
                            MessagesUtils.get("storageArray.confirmPassword.not.match"));
                }
            }
        }

        private boolean isMatchingPasswords(String password, String confirm) {
            return StringUtils.equals(StringUtils.trimToEmpty(password), StringUtils.trimToEmpty(confirm));
        }

    }
}
