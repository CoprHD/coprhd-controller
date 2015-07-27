/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import models.ProtectionSystemTypes;
import models.datatable.DataProtectionSystemsDataTable;
import models.datatable.DataProtectionSystemsDataTable.DataProtectionSystemInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.MessagesUtils;
import util.ProtectionSystemUtils;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.protection.ProtectionSystemConnectivityRestRep;
import com.emc.storageos.model.protection.ProtectionSystemRequestParam;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.model.protection.ProtectionSystemUpdateRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;
import controllers.util.ViprResourceController.ResourceIdOperation;
import controllers.util.ViprResourceController.ResourceValueOperation;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class DataProtectionSystems extends ViprResourceController {

    protected static final String SAVED = "DataProtectionSystems.saved";
    protected static final String DELETED_SUCCESS = "DataProtectionSystems.deleted.success";
    protected static final String DELETED_ERROR = "DataProtectionSystems.deleted.error";
    protected static final String UNKNOWN = "DataProtectionSystems.unknown";
    protected static final String MODEL_NAME = "DataProtectionSystem";

    private static void addReferenceData() {
        renderArgs.put("dataProtectionSystemTypeList", Arrays.asList(ProtectionSystemTypes.OPTIONS));
    }

    public static void list() {
        renderArgs.put("dataTable", new DataProtectionSystemsDataTable());
        render();
    }

    public static void listJson() {
        performListJson(ProtectionSystemUtils.getProtectionSystems(), new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(ProtectionSystemUtils.getProtectionSystems(ids), new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        ProtectionSystemRestRep protectionSystem = ProtectionSystemUtils.getProtectionSystem(id);
        if (protectionSystem == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        ProtectionSystemConnectivityRestRep connectivity = ProtectionSystemUtils.getConnectivity(protectionSystem);
        Map<URI, StorageSystemRestRep> storageSystemMap = ProtectionSystemUtils.getStorageSystemMap(connectivity);
        render(protectionSystem, connectivity, storageSystemMap);
    }

    public static void create() {
        addReferenceData();
        DataProtectionSystemForm dataProtectionSystem = new DataProtectionSystemForm();
        // put all "initial create only" defaults here rather than field initializers
        render("@edit", dataProtectionSystem);
    }

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();

        ProtectionSystemRestRep protectionSystem = ProtectionSystemUtils.getProtectionSystem(id);
        if (protectionSystem != null) {
            DataProtectionSystemForm dataProtectionSystem = new DataProtectionSystemForm(protectionSystem);
            render(dataProtectionSystem);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    @FlashException(keep=true, referrer={"create","edit"})
    public static void save(DataProtectionSystemForm dataProtectionSystem) {
        dataProtectionSystem.validate("dataProtectionSystem");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        
        dataProtectionSystem.save();
        String name = dataProtectionSystem.name;
        flash.success(MessagesUtils.get(SAVED, name));
        list();
    }
    
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void introspect(@As(",") String[] ids) {
        discover(uris(ids));
    }

    private static void discover(List<URI> ids) {
        performSuccess(ids, new DiscoverOperation(), DISCOVERY_STARTED);
        list();
    }

    public static class DataProtectionSystemForm {

        public String id;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        @HostNameOrIpAddress
        @Required
        public String ipAddress;

        @Required
        public Integer port;

        public String systemType;

        @MaxSize(2048)
        @Required
        public String userName;

        @MaxSize(2048)
        public String password = "";  //NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here")

        @MaxSize(2048)
        public String passwordConfirm = ""; //NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here")

        public DataProtectionSystemForm() {
        }

        public DataProtectionSystemForm(ProtectionSystemRestRep dataProtectionSystem) {
            this.id = dataProtectionSystem.getId().toString();
            this.name = dataProtectionSystem.getName();
            this.ipAddress = dataProtectionSystem.getIpAddress();
            this.port = dataProtectionSystem.getPortNumber();
            this.systemType = dataProtectionSystem.getSystemType();
            this.userName = dataProtectionSystem.getUsername();
            this.password = ""; //NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here")
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isNew()) {
                Validation.required(fieldName + ".password", this.password);
                Validation.required(fieldName + ".passwordConfirm", this.passwordConfirm);
            }

            if (!StringUtils.equals(StringUtils.trimToEmpty(password), StringUtils.trimToEmpty(passwordConfirm))) {
                Validation.addError(fieldName + ".passwordConfirm",
                        MessagesUtils.get("dataProtectionSystem.confirmPassword.not.match"));
            }
        }

        public Task<ProtectionSystemRestRep> save() {
            if (isNew()) {
                return create();
            }
            else {
                return update();
            }
        }

        private Task<ProtectionSystemRestRep> create() {
            ProtectionSystemRequestParam createParam = new ProtectionSystemRequestParam(this.name, this.systemType,
                    this.ipAddress, this.port, this.userName, StringUtils.trimToNull(password), null);
            return ProtectionSystemUtils.create(createParam);
        }

        private Task<ProtectionSystemRestRep> update() {
            ProtectionSystemUpdateRequestParam updateParam = new ProtectionSystemUpdateRequestParam(this.ipAddress,
                    this.port, this.userName, StringUtils.trimToNull(password));
            return ProtectionSystemUtils.update(id, updateParam);
        }
    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<DataProtectionSystemInfo, ProtectionSystemRestRep> {
        @Override
        public DataProtectionSystemInfo performOperation(ProtectionSystemRestRep protectionSystem) throws Exception {
            return new DataProtectionSystemInfo(protectionSystem);
        }
    }

    protected static class DeleteOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            ProtectionSystemUtils.deactivate(id);
            return null;
        }
    }

    protected static class DiscoverOperation implements ResourceIdOperation<Task<ProtectionSystemRestRep>> {
        @Override
        public Task<ProtectionSystemRestRep> performOperation(URI id) throws Exception {
            Task<ProtectionSystemRestRep> task = ProtectionSystemUtils.discover(id);
            return task;
        }
    }
}
