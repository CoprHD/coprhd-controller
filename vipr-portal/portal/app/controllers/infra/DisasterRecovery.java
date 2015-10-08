/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.infra;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jobs.vipr.TenantsCall;
import jobs.vipr.VirtualArraysCall;
import models.PoolAssignmentTypes;
import models.PoolTypes;
import models.RegistrationStatus;
import models.StorageProviderTypes;
import models.StorageSystemTypes;
import models.datatable.DisasterRecoveryDataTable;
import models.datatable.DisasterRecoveryDataTable.StandByInfo;
import models.datatable.StoragePoolDataTable;
import models.datatable.StoragePoolDataTable.StoragePoolInfo;
import models.datatable.VirtualPoolDataTable;
import models.datatable.VirtualPoolDataTable.VirtualPoolInfo;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.With;
import util.DisasterRecoveryUtils;
import util.MessagesUtils;
import util.StorageSystemUtils;
import util.TenantUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.arrays.StorageProviders.StorageProviderForm;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class DisasterRecovery extends Controller {
    protected static final String SAVED_SUCCESS = "VirtualPools.save.success";
    protected static final String SAVED_ERROR = "VirtualPools.save.error";
    protected static final String DELETED_SUCCESS = "VirtualPools.delete.success";
    protected static final String DELETED_ERROR = "VirtualPools.delete.error";
    protected static final String UNKNOWN = "VirtualPools.unknown";

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
//        ObjectVirtualPoolForm form = new ObjectVirtualPoolForm();
//        edit(form);
        edit("");
    }

   
   
    public static void edit(String id) {
       render();
    }

//    private static void edit(ObjectVirtualPoolForm vpool) {
//        addStaticOptions();
//        addDynamicOptions(vpool);
//        renderArgs.put("storagePoolsDataTable", createStoragePoolDataTable());
//        render("@edit", vpool);
//    }

//    private static void addStaticOptions() {
//        renderArgs.put("protocolOptions", ObjectProtocols.options(ObjectProtocols.SWIFT, ObjectProtocols.ATMOS, ObjectProtocols.S3));
//        renderArgs.put("systemTypeOptions",
//                StorageSystemTypes.options(
//                        StorageSystemTypes.ECS));
//        renderArgs.put("poolAssignmentOptions",
//                PoolAssignmentTypes.options(PoolAssignmentTypes.AUTOMATIC, PoolAssignmentTypes.MANUAL));
//        renderArgs.put("varrayAttributeNames", VirtualArrayUtils.ATTRIBUTES);
//    }
//
//   
//   
//
//   

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(DisasterRecoveryForm standBy) {
//        if (vpool == null) {
//            list();
//        }
//        if (vpool.objectProtocols != null) {
//            vpool.protocols = Sets.newHashSet(vpool.objectProtocols);
//        }
//        vpool.validate("vpool");
//        if (Validation.hasErrors()) {
//            Common.handleError();
//        }
//        ObjectVirtualPoolRestRep result = vpool.save();
//        flash.success(MessagesUtils.get(SAVED_SUCCESS, result.getName()));
//        backToReferrer();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
      list();
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
        public String VirtualIPv4;

        @Required
        @HostNameOrIpAddress
        public Integer VirtualIPv6;

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
            //this.id = siteaddParam.;
            this.name = siteaddParam.getName();
            this.userName = siteaddParam.getUsername();
            this.VirtualIPv4 = siteaddParam.getVip();
            
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isNew()) {
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
