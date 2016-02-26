/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.angularRenderArgs;
import static controllers.Common.backToReferrer;
import static controllers.Common.copyRenderArgsToAngular;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import models.StorageProviderTypes;
import models.StorageSystemTypes;
import models.datatable.StorageProviderDataTable;
import models.datatable.StorageProviderDataTable.StorageProviderInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.jobs.Job;
import play.mvc.With;
import util.DefaultStorageProviderPortMap;
import util.EnumOption;
import util.MessagesUtils;
import util.StorageProviderUtils;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageProviders extends ViprResourceController {

    protected static final String SAVED = "SMISProviders.saved";
    protected static final String DELETED_SUCCESS = "SMISProviders.deleted.success";
    protected static final String DELETED_ERROR = "SMISProviders.deleted.error";
    protected static final String DELETE_NOT_ALLOWED = "SMISProviders.deleted.not.allowed";
    protected static final String UNKNOWN = "SMISProviders.unknown";
    protected static final String DISCOVERY_STARTED = "SMISProviders.introspection";
    private static final int SAVE_WAIT_MILLIS = 300000;

    private static void addReferenceData() {
        renderArgs.put("interfaceTypeOptions", StorageProviderTypes.OPTIONS);
        renderArgs.put("optionsSIO", StorageProviderTypes.optionSIO);
        renderArgs.put("sslDefaultStorageProviderList", Arrays.asList(StorageProviderTypes.SSL_DEFAULT_OPTIONS));
        renderArgs.put("nonSSLStorageSystemList", Arrays.asList(StorageSystemTypes.NON_SSL_OPTIONS));
        renderArgs.put("mdmDefaultStorageProviderList", Arrays.asList(StorageSystemTypes.MDM_DEFAULT_OPTIONS));
        renderArgs.put("mdmonlyProviderList", Arrays.asList(StorageSystemTypes.MDM_ONLY_OPTIONS));
        renderArgs.put("secretKeyProviderList", Arrays.asList(StorageSystemTypes.SECRET_KEY_OPTIONS));
        renderArgs.put("elementManagerStorageProviderList", Arrays.asList(StorageSystemTypes.ELEMENT_MANAGER_OPTIONS));
        List<EnumOption> defaultStorageProviderPortMap = Arrays.asList(EnumOption.options(DefaultStorageProviderPortMap.values()));
        renderArgs.put("defaultStorageProviderPortMap", defaultStorageProviderPortMap);
    }

    public static void list() {
        renderArgs.put("dataTable", new StorageProviderDataTable());
        render();
    }

    public static void listJson() {
        performListJson(StorageProviderUtils.getStorageProviders(), new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(StorageProviderUtils.getStorageProviders(ids), new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        StorageProviderRestRep storageProvider = StorageProviderUtils.getStorageProvider(uri(id));
        if (storageProvider == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        Set<NamedRelatedResourceRep> storageSystems = StorageProviderUtils.getConnectedStorageSystems(uri(id));
        render(storageProvider, storageSystems);
    }

    public static void create() {
        addReferenceData();
        StorageProviderForm smisProvider = new StorageProviderForm();
        // put all "initial create only" defaults here rather than field initializers
        smisProvider.interfaceType = StorageProviderTypes.SMIS;
        smisProvider.portNumber = getDefaultPort(DefaultStorageProviderPortMap.smis_useSSL);
        smisProvider.useSSL = true;
        copyRenderArgsToAngular();
        angularRenderArgs().put("smisProvider", smisProvider);
        render("@edit", smisProvider);
    }

    private static Integer getDefaultPort(DefaultStorageProviderPortMap value) {
        String defaultValue = MessagesUtils.get(DefaultStorageProviderPortMap.class.getSimpleName() + "." + value.name());
        return Integer.valueOf(StringUtils.defaultIfBlank(defaultValue, "0"));
    }

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();
        StorageProviderRestRep provider = StorageProviderUtils.getStorageProvider(uri(id));
        if (provider != null) {
            StorageProviderForm smisProvider = new StorageProviderForm(provider);
            copyRenderArgsToAngular();
            angularRenderArgs().put("smisProvider", smisProvider);
            render(smisProvider);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(StorageProviderForm smisProvider) {
        smisProvider.validate("smisProvider");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        smisProvider.save();
        flash.success(MessagesUtils.get(SAVED, smisProvider.name));
        backToReferrer();
        list();
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        if (StorageProviderUtils.hasStorageSystems(ids)) {
            flash.error(MessagesUtils.get(DELETE_NOT_ALLOWED));
            list();
        }

        List<OperationResult<Void, URI>> results = perform(ids, new DeactivateOperation());
        List<OperationResult<Void, URI>> failed = getFailedResults(results);

        if (failed.isEmpty()) {
            flash.success(MessagesUtils.get(DELETED_SUCCESS));
        }
        else {
            String errorMessage = StringUtils.join(errorMessages(failed), "\n");
            int total = results.size();
            int deleted = total - failed.size();
            flash.error(MessagesUtils.get(DELETED_ERROR, deleted, total, errorMessage));
        }
        list();
    }

    @FlashException("list")
    public static void discover() {
        // API only does not support discovery of a single SMI-S provider
        StorageProviderUtils.discoverAll();
        flash.success(MessagesUtils.get(DISCOVERY_STARTED));
        list();
    }

    // Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here
    @SuppressWarnings("squid:S2068")
    public static class StorageProviderForm {

        public String id;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        @MaxSize(2048)
        
        public String userName;

        @HostNameOrIpAddress
        @Required
        public String ipAddress;

        @Required
        public Integer portNumber;

        @MaxSize(2048)
        public String password = "";

        @MaxSize(2048)
        public String confirmPassword = "";

        public Boolean useSSL;

        public String interfaceType;

        @MaxSize(2048)
        public String secondaryUsername;

        @MaxSize(2048)
        public String secondaryPassword = "";

        @MaxSize(2048)
        public String secondaryPasswordConfirm = "";

        public String elementManagerURL;
        
        public String secretKey;

        public StorageProviderForm() {        	
        }

        public StorageProviderForm(StorageProviderRestRep smisProvider) {
            readFrom(smisProvider);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
        
        public boolean isScaleIOApi() {
            return StorageProviderTypes.isScaleIOApi(interfaceType);
        }

        public boolean isCeph() {
            return StorageProviderTypes.isCeph(interfaceType);
        }

        public void readFrom(StorageProviderRestRep storageProvider) {
            this.id = storageProvider.getId().toString();
            this.name = storageProvider.getName();
            this.ipAddress = storageProvider.getIPAddress();
            this.userName = storageProvider.getUserName();
            this.password = ""; // the platform will never return the real password
            this.portNumber = storageProvider.getPortNumber();
            this.useSSL = storageProvider.getUseSSL();
            this.interfaceType = storageProvider.getInterface();
            this.secondaryUsername = storageProvider.getSecondaryUsername();
            this.secondaryPassword = ""; // the platform will never return the real password
            this.elementManagerURL = storageProvider.getElementManagerURL();
            this.secretKey = ""; // the platform will never return the real key;
            if(isScaleIOApi()) {
            	this.secondaryUsername = this.userName;
            	this.secondaryPassword = this.password;
            	this.secondaryPasswordConfirm = this.confirmPassword;
            }
        }

        public URI save() {
            if (isNew()) {
                return create().getResourceId();
            }
            else {
                return update().getId();
            }
        }

        public StorageProviderRestRep update() {        	
            return StorageProviderUtils.update(uri(id), name, ipAddress, portNumber, userName,
                    password, useSSL, interfaceType, secondaryUsername, secondaryPassword, elementManagerURL, secretKey);
        }

        public Task<StorageProviderRestRep> create() {        	
        	Task<StorageProviderRestRep> task = StorageProviderUtils.create(name, ipAddress, portNumber, userName, password,
                    useSSL, interfaceType, secondaryUsername, secondaryPassword, elementManagerURL, secretKey);
            new SaveWaitJob(getViprClient(), task).now();
            return task;
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
            
            if (isScaleIOApi() ) {
            	Validation.required(fieldName + ".secondaryPassword", this.secondaryPassword);
            	Validation.required(fieldName + ".secondaryPasswordConfirm", this.secondaryPasswordConfirm);
            } else if (isCeph()) {
            	Validation.required(fieldName + ".userName", this.userName);
        	   	Validation.required(fieldName + ".secretKey", this.secretKey);
            } else if (isNew()) {
            	Validation.required(fieldName + ".userName", this.userName);
        	   	Validation.required(fieldName + ".password", this.password);
            	Validation.required(fieldName + ".confirmPassword", this.confirmPassword);
            }
            
            if (!StringUtils.equals(StringUtils.trim(password), StringUtils.trim(confirmPassword))) {
                Validation.addError(fieldName + ".confirmPassword",
                        MessagesUtils.get("smisProvider.confirmPassword.not.match"));
            }

            if (!StringUtils.equals(StringUtils.trim(secondaryPassword), StringUtils.trim(secondaryPasswordConfirm))) {
                Validation.addError(fieldName + ".secondaryPasswordConfirm",
                        MessagesUtils.get("smisProvider.secondaryPassword.confirmPassword.not.match"));
            }
        }
        
    }

    @SuppressWarnings("rawtypes")
    private static class SaveWaitJob extends Job {
        private final ViPRCoreClient client;
        private final Task<StorageProviderRestRep> task;

        public SaveWaitJob(ViPRCoreClient client, Task<StorageProviderRestRep> task) {
            this.client = client;
            this.task = task;
        }

        @Override
        public void doJob() throws Exception {
            try {
                task.waitFor(SAVE_WAIT_MILLIS);
            } catch (Exception e) {
                // Ignore, trying to ensure basic SMI-S discovery completes before kicking off storage system discovery
            }
            client.storageSystems().discoverAll();
        }
    }

    protected static class JsonItemOperation implements ResourceValueOperation<StorageProviderInfo, StorageProviderRestRep> {
        @Override
        public StorageProviderInfo performOperation(StorageProviderRestRep provider) throws Exception {
            return new StorageProviderInfo(provider);
        }
    }

    protected static class DeactivateOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            StorageProviderUtils.deactivate(id);
            return null;
        }
    }
}
