/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.infra;

import java.util.Arrays;
import java.util.List;

import models.datatable.DisasterRecoveryDataTable;
import models.datatable.DisasterRecoveryDataTable.StandByInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.DisasterRecoveryUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;


@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class DisasterRecovery extends Controller {
    protected static final String SAVED_SUCCESS = "disasterRecovery.save.success";
    protected static final String SAVED_ERROR = "disasterRecovery.save.error";
    protected static final String DELETED_SUCCESS = "disasterRecovery.delete.success";
    protected static final String DELETED_ERROR = "disasterRecovery.delete.error";
    protected static final String UNKNOWN = "disasterRecovery.unknown";

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

    public static void pause(){
        
    }
    
    public static void stop() {
        
    }
    
    public static void testMode() {
        
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

    public static void create() {
        DisasterRecoveryForm site = new DisasterRecoveryForm();
        edit(site);
    }

   
   
    public static void edit(String id) {
       render();
    }

    private static void edit(DisasterRecoveryForm site) {
        render("@edit", site);
    }


    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(DisasterRecoveryForm disasterRecovery) {
        if (disasterRecovery != null) {
            disasterRecovery.validate("disasterRecovery");
            if (Validation.hasErrors()) {
                Common.handleError();
            }

            SiteAddParam standbySite = new SiteAddParam();
            standbySite.setName(disasterRecovery.name);
            standbySite.setVip(disasterRecovery.VirtualIP);
            standbySite.setUsername(disasterRecovery.userName);
            standbySite.setPassword(disasterRecovery.userPassword);
            
            SiteRestRep result = DisasterRecoveryUtils.addStandby(standbySite);
            flash.success(MessagesUtils.get(SAVED_SUCCESS, result.getName()));
            backToReferrer();
            list();
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        List <String> uuids = Arrays.asList(ids);
        for (String uuid : uuids) {
            if (!DisasterRecoveryUtils.hasStandbySite(uuid)) {
                flash.error(MessagesUtils.get(DELETED_ERROR));
                list();
            }
            
            SiteRestRep result = DisasterRecoveryUtils.deleteStandby(uuid);
            flash.success(MessagesUtils.get(SAVED_SUCCESS, result.getName()));
            backToReferrer();
            list();
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
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isNew()) {
                Validation.required(fieldName + ".name", this.name);
                Validation.required(fieldName + ".userName", this.userName);
                Validation.required(fieldName + ".userPassword", this.userPassword);
                Validation.required(fieldName + ".confirmPassword", this.confirmPassword);
            }

            if (!isMatchingPasswords(userPassword, confirmPassword)) {
                Validation.addError(fieldName + ".confirmPassword",
                        MessagesUtils.get("storageArray.confirmPassword.not.match"));
            }

        }

        private boolean isMatchingPasswords(String password, String confirm) {
            return StringUtils.equals(StringUtils.trimToEmpty(password), StringUtils.trimToEmpty(confirm));
        }

    }
}
