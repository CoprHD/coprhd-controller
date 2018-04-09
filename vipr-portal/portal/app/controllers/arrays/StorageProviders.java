/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import com.emc.storageos.model.systems.StorageSystemRestRep;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.angularRenderArgs;
import static controllers.Common.backToReferrer;
import static controllers.Common.copyRenderArgsToAngular;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import play.Logger;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.StorageProviderTypes;
import models.datatable.StorageProviderDataTable;
import models.datatable.StorageProviderDataTable.StorageProviderInfo;
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
import util.StorageSystemUtils;
import util.StringOption;
import util.validation.HostNameOrIpAddress;

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
    private static final String HTTPS = "https";
    private static final String HYPERSCALEPORT = "8443"; //hardcode to be removed in next check-in

    private static final String UNITY = "unity";
    private static final String VMAX = "vmax";
    private static final String XTREMIO = "xtremio";
    private static final String SUFFIX_ALL_FLASH = "F";
    private static final String POWERMAX = "PowerMax";
    private static final String PMAX = "pmax";
    private static final String VIPR_START_GUIDE = "VIPR_START_GUIDE";
    private static final String GUIDE_DATA = "GUIDE_DATA";
    private static final String STORAGE_SYSTEMS = "storage_systems";
    private static final String GUIDE_VISIBLE = "guideVisible";
    private static final String GUIDE_COMPLETED_STEP = "completedSteps";
    private static final String UNREGESTERD = "UNREGISTERED";
    private static final String PROVIDER_TYPE = "StorageProvider";


    private static void addReferenceData() {
        renderArgs.put("interfaceTypeOptions", StorageProviderTypes.getProviderOption());
        renderArgs.put("optionsSIO", StorageProviderTypes.getScaleIoOption());
        renderArgs.put("sslDefaultStorageProviderList", StorageProviderTypes.getProvidersWithSSL());
        renderArgs.put("nonSSLStorageSystemList", StorageProviderTypes.getProvidersWithoutSSL());
        renderArgs.put("mdmDefaultStorageProviderList", StorageProviderTypes.getProvidersWithMDM());
        renderArgs.put("mdmonlyProviderList", StorageProviderTypes.getProvidersWithOnlyMDM());
        renderArgs.put("secretKeyProviderList", StorageProviderTypes.getProvidersWithSecretKey());
        renderArgs.put("elementManagerStorageProviderList", StorageProviderTypes.getProvidersWithEMS());

        List<EnumOption> defaultStorageProviderPortMap = StorageProviderTypes.getStoragePortMap();
        renderArgs.put("defaultStorageProviderPortMap", defaultStorageProviderPortMap);
    }

    private static void addAllFlashReferenceData() {
        renderArgs.put("interfaceTypeOptions", StorageProviderTypes.getAllFlashProviderOption());
        renderArgs.put("optionsSIO", StorageProviderTypes.getScaleIoOption());
        renderArgs.put("sslDefaultStorageProviderList", StorageProviderTypes.getProvidersWithSSL());
        renderArgs.put("nonSSLStorageSystemList", StorageProviderTypes.getProvidersWithoutSSL());
        renderArgs.put("mdmDefaultStorageProviderList", StorageProviderTypes.getProvidersWithMDM());
        renderArgs.put("mdmonlyProviderList", StorageProviderTypes.getProvidersWithOnlyMDM());
        renderArgs.put("secretKeyProviderList", StorageProviderTypes.getProvidersWithSecretKey());
        renderArgs.put("elementManagerStorageProviderList", StorageProviderTypes.getProvidersWithEMS());

        List<EnumOption> defaultStorageProviderPortMap = StorageProviderTypes.getStoragePortMap();
        renderArgs.put("defaultStorageProviderPortMap", defaultStorageProviderPortMap);
    }

    public static void list() {
        renderArgs.put("dataTable", new StorageProviderDataTable());
        render();
    }

    public static void listJson() {
        performListJson(StorageProviderUtils.getStorageProviders(),
                new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(StorageProviderUtils.getStorageProviders(ids),
                new JsonItemOperation());
    }

    public static void discoveryCheckJson(@As(",") String[] ids) {
        List<String> failedDiscovery = new ArrayList<String>();
        for (String id:ids) {
            StorageProviderRestRep storageProvider = StorageProviderUtils
                    .getStorageProvider(uri(id));
            if (storageProvider == null || storageProvider.getRegistrationStatus().equals(UNREGESTERD)) {
                //ignore for now
                continue;
            }
            if (!storageProvider.getScanStatus().equals("COMPLETE")){
                failedDiscovery.add(storageProvider.getName());
                continue;
            }
            Set<NamedRelatedResourceRep> storageSystems = StorageProviderUtils
                    .getConnectedStorageSystems(uri(id));

            boolean ssFound = false;

            for (NamedRelatedResourceRep storageSystem:storageSystems){
                StorageSystemRestRep ss = StorageSystemUtils.getStorageSystem(storageSystem.getId());
                if (ss != null) {
                    if (!ss.getDiscoveryJobStatus().equals("COMPLETE")) {
                        continue;
                    } else {
                        ssFound = true;
                    }
                }
            }

            if (!ssFound){
                failedDiscovery.add(storageProvider.getName());
                continue;
            }
        }
        renderJSON(failedDiscovery);

    }

    public static void getAllFlashStorageSystemsList(@As(",") String[] ids) {
        List<Map<String,String>> storagesystemslist = new ArrayList<Map<String,String>>();
        for (String id:ids) {
            if(id.contains(PROVIDER_TYPE)) {
                StorageProviderRestRep storageProvider = StorageProviderUtils.getStorageProvider(uri(id));
                if (storageProvider == null) {
                    continue;
                }
                Set<NamedRelatedResourceRep> storageSystems = StorageProviderUtils.getConnectedStorageSystems(uri(id));

                for (NamedRelatedResourceRep storageSystem : storageSystems) {
                    StorageSystemRestRep ss = StorageSystemUtils.getStorageSystem(storageSystem.getId());
                    if (ss != null && !ss.getRegistrationStatus().equals(UNREGESTERD)) {
                        Map<String, String> ssMap = new HashMap<String, String>();
                        // Check if storage system is of type UNITY, VMAX or XtremIO
                        if (StringUtils.equals(XTREMIO, ss.getSystemType())) {
                            ssMap.put("id", ss.getId().toString());
                            ssMap.put("name", ss.getName());
                            storagesystemslist.add(ssMap);
                        }
                        if (StringUtils.equals(VMAX, ss.getSystemType())) {
                            String modelType = ss.getModel();
                            if (modelType != null && (modelType.contains(SUFFIX_ALL_FLASH) ||  modelType.contains(POWERMAX) || modelType.contains(PMAX))) {
                                ssMap.put("id", ss.getId().toString());
                                ssMap.put("name", ss.getName());
                                storagesystemslist.add(ssMap);
                            }
                        }
                    }
                }
            } else {
                StorageSystemRestRep ss = StorageSystemUtils.getStorageSystem(id);
                if (ss != null && !ss.getRegistrationStatus().equals(UNREGESTERD)) {
                    Logger.info(ss.getId()+"-----"+ss.getSystemType());
                    Map<String, String> ssMap = new HashMap<String, String>();
                    // Check if storage system is of type UNITY, VMAX or XtremIO
                    if (StringUtils.equals(XTREMIO, ss.getSystemType())) {
                        ssMap.put("id", ss.getId().toString());
                        ssMap.put("name", ss.getName());
                        storagesystemslist.add(ssMap);
                    }
                    if (StringUtils.equals(VMAX, ss.getSystemType()) || StringUtils.equals(UNITY, ss.getSystemType())) {
                        String modelType = ss.getModel();
                        if (modelType != null && (modelType.contains(SUFFIX_ALL_FLASH) ||  modelType.contains(POWERMAX) || modelType.contains(PMAX))) {
                            ssMap.put("id", ss.getId().toString());
                            ssMap.put("name", ss.getName());
                            storagesystemslist.add(ssMap);
                        }
                    }
                }
            }
        }
        renderJSON(storagesystemslist);
    }

    public static void itemDetails(String id) {
        StorageProviderRestRep storageProvider = StorageProviderUtils
                .getStorageProvider(uri(id));
        if (storageProvider == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        Set<NamedRelatedResourceRep> storageSystems = StorageProviderUtils
                .getConnectedStorageSystems(uri(id));
        render(storageProvider, storageSystems);
    }

    public static void create() {
    	// Check add is called from guide wizard, yes only AFA
       	JsonObject jobject = getCookieAsJson(VIPR_START_GUIDE);
       	String isGuideAdd = null;
       	if (jobject != null && jobject.get(GUIDE_VISIBLE) != null) {
       		isGuideAdd = jobject.get(GUIDE_VISIBLE).getAsString();
       	}

       	if( isGuideAdd != null && StringUtils.equalsIgnoreCase(isGuideAdd, "true")) {
       		addAllFlashReferenceData();
       	}
       	else {
       		addReferenceData();
       	}
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
        String defaultValue = MessagesUtils
                .get(DefaultStorageProviderPortMap.class.getSimpleName() + "."
                        + value.name());
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
        } else {
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

        URI providerUri = smisProvider.save();
        flash.success(MessagesUtils.get(SAVED, smisProvider.name));

        //check if checklist is running on this step
        JsonObject jobject = getCookieAsJson(VIPR_START_GUIDE);
        if(jobject != null && jobject.get(GUIDE_COMPLETED_STEP) != null && jobject.get(GUIDE_VISIBLE) != null) {
			if (jobject.get(GUIDE_COMPLETED_STEP).getAsInt() == 3
					&& jobject.get(GUIDE_VISIBLE).getAsBoolean()) {
				JsonObject dataObject = getCookieAsJson(GUIDE_DATA);

				JsonArray storage_systems = dataObject.getAsJsonArray(STORAGE_SYSTEMS);
				if (storage_systems == null) {
					storage_systems = new JsonArray();
				}
				boolean addToCookie = true;
                for(Object storageObject: storage_systems) {
                	JsonObject storagearray = (JsonObject)storageObject;
                	if(storagearray.get("id") != null) {
                		String arrayId = storagearray.get("id").getAsString();
                		if(StringUtils.equals(arrayId, providerUri.toString())) {
                			addToCookie = false; //update case, don't add in cookie
                			break;
                		}
                	}
                }
				if (addToCookie) {
					JsonObject storage = new JsonObject();
					storage.addProperty("id", providerUri.toString());
					storage.addProperty("name", smisProvider.name);
					storage_systems.add(storage);
					dataObject.add(STORAGE_SYSTEMS, storage_systems);
					saveJsonAsCookie(GUIDE_DATA, dataObject);
				}
				list();
			}
        }

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

        List<OperationResult<Void, URI>> results = perform(ids,
                new DeactivateOperation());
        List<OperationResult<Void, URI>> failed = getFailedResults(results);

        if (failed.isEmpty()) {
            flash.success(MessagesUtils.get(DELETED_SUCCESS));
        } else {
            String errorMessage = StringUtils.join(errorMessages(failed), "\n");
            int total = results.size();
            int deleted = total - failed.size();
            flash.error(MessagesUtils.get(DELETED_ERROR, deleted, total,
                    errorMessage));
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
    // Suppressing sonar violation for need of accessor methods. Accessor methods are not needed and we use public variables
    @SuppressWarnings("squid:S2068 , ClassVariableVisibilityCheck")
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

        public String secondaryURL;

        
        public String secretKey;
        @MaxSize(2048)
        public String hyperScaleUser;

        @MaxSize(2048)
        public String hyperScalePassword = "";

        @MaxSize(2048)
        public String hyperScaleConfPasswd = "";

        public String hyperScaleHost;

        public String hyperScalePort;
        
        public URL url;

        public StorageProviderForm() {        	
        }

        public StorageProviderForm(StorageProviderRestRep smisProvider) {
            readFrom(smisProvider);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
        
        private boolean isXIV() {
            return StorageProviderTypes.isXIV(interfaceType);
        }
        
        public boolean isScaleIOApi() {
            return StorageProviderTypes.isScaleIOApi(interfaceType);
        }

        public boolean isCeph() {
            return StorageProviderTypes.isCeph(interfaceType);
        }

        public void setXIVParameters() {
            if (StringUtils.isNotEmpty(this.hyperScaleUser)) {
                this.secondaryUsername = this.hyperScaleUser;
            }
            if (StringUtils.isNotEmpty(this.hyperScalePassword)) {
                this.secondaryPassword = this.hyperScalePassword;
            }
            if (StringUtils.isNotEmpty(this.hyperScaleConfPasswd)) {
                this.secondaryPasswordConfirm = this.hyperScaleConfPasswd;
            }
            if (StringUtils.isNotEmpty(this.hyperScaleHost) && StringUtils.isNotEmpty(this.hyperScalePort)) {
                try {
                    url = new URL(HTTPS, this.hyperScaleHost, Integer.parseInt(this.hyperScalePort), "");
                } catch (Exception e) {
                    flash.error("Unable to parse Hyper Scale Manager URL");
                }
                this.secondaryURL = url.toString();
            } else if ((StringUtils.isNotEmpty(this.hyperScaleHost) || StringUtils.isNotEmpty(this.hyperScalePort))){
                flash.error("Secondary Host or Port is Missing");
                edit(id);
            }
        }

        public void readFrom(StorageProviderRestRep storageProvider) {
            this.id = storageProvider.getId().toString();
            this.name = storageProvider.getName();
            this.ipAddress = storageProvider.getIPAddress();
            this.userName = storageProvider.getUserName();
            this.password = ""; // the platform will never return the real
                                // password
            this.portNumber = storageProvider.getPortNumber();
            this.useSSL = storageProvider.getUseSSL();
            this.interfaceType = storageProvider.getInterface();
            this.secondaryUsername = storageProvider.getSecondaryUsername();
            this.secondaryPassword = ""; // the platform will never return the real password
            this.secondaryURL = storageProvider.getSecondaryURL();
            this.elementManagerURL = storageProvider.getElementManagerURL();
            this.secretKey = ""; // the platform will never return the real key
            this.hyperScaleUser = storageProvider.getSecondaryUsername();
            this.hyperScalePassword = ""; // the platform will never return the real password
            if(!StringUtils.isEmpty(secondaryURL)) {
                try {
                    url = new URL(this.secondaryURL);
                } catch(Exception e) {
                    flash.error("Unable to parse Hyper Scale Manager URL");
                }
                if(null!=url) {
                    this.hyperScaleHost = url.getHost();
                    this.hyperScalePort = Integer.toString(url.getPort());
                }
            }
            if (isScaleIOApi()) {
                this.secondaryUsername = this.userName;
                this.secondaryPassword = this.password;
                this.secondaryPasswordConfirm = this.confirmPassword;
            }
            setXIVParameters();

        }

        public URI save() {
            setXIVParameters();
            if (isNew()) {
                return create().getResourceId();
            } else {
                return update().getId();
            }
        }

        public StorageProviderRestRep update() {
            return StorageProviderUtils.update(uri(id), name, ipAddress,
                    portNumber, userName, password, useSSL, interfaceType,
                    secondaryUsername, secondaryPassword, elementManagerURL, secondaryURL, secretKey);
        }

        public Task<StorageProviderRestRep> create() {
            Task<StorageProviderRestRep> task = StorageProviderUtils.create(
                    name, ipAddress, portNumber, userName, password, useSSL,
                    interfaceType, secondaryUsername, secondaryPassword,
                    elementManagerURL, secondaryURL, secretKey);
            new SaveWaitJob(getViprClient(), task).now();
            return task;
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
            
            if (isScaleIOApi() ) {
                Validation.required(fieldName + ".secondaryPassword",
                        this.secondaryPassword);
                Validation.required(fieldName + ".secondaryPasswordConfirm",
                        this.secondaryPasswordConfirm);
            } else if (isCeph()) {
            	Validation.required(fieldName + ".userName", this.userName);
        	   	Validation.required(fieldName + ".secretKey", this.secretKey);
            } else if (isNew()) {
            	Validation.required(fieldName + ".userName", this.userName);
        	   	Validation.required(fieldName + ".password", this.password);
                Validation.required(fieldName + ".password", this.password);
                Validation.required(fieldName + ".confirmPassword",
                        this.confirmPassword);
            } else if (isXIV()) {
                if ((StringUtils.isNotEmpty(this.hyperScaleHost) || StringUtils.isNotEmpty(this.hyperScalePort))) {
                    Validation.addError(fieldName + ".hyperScaleHost","Either Secondary Host or Port details is missing");
                    Validation.addError(fieldName + ".hyperScalePort","Either Secondary Host or Port details is missing");
                }
            }
            
            if (!StringUtils.equals(StringUtils.trim(password), StringUtils.trim(confirmPassword))) {
                Validation.addError(fieldName + ".confirmPassword",
                        MessagesUtils
                                .get("smisProvider.confirmPassword.not.match"));
            }
            

            if (!StringUtils.equals(StringUtils.trim(secondaryPassword),
                    StringUtils.trim(secondaryPasswordConfirm))) {
                Validation
                        .addError(
                                fieldName + ".secondaryPasswordConfirm",
                                MessagesUtils
                                        .get("smisProvider.secondaryPassword.confirmPassword.not.match"));
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static class SaveWaitJob extends Job {
        private final ViPRCoreClient client;
        private final Task<StorageProviderRestRep> task;

        public SaveWaitJob(ViPRCoreClient client,
                Task<StorageProviderRestRep> task) {
            this.client = client;
            this.task = task;
        }

        @Override
        public void doJob() throws Exception {
            try {
                task.waitFor(SAVE_WAIT_MILLIS);
            } catch (Exception e) {
                // Ignore, trying to ensure basic SMI-S discovery completes
                // before kicking off storage system discovery
            }
            client.storageSystems().discoverAll();
        }
    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<StorageProviderInfo, StorageProviderRestRep> {
        @Override
        public StorageProviderInfo performOperation(
                StorageProviderRestRep provider) throws Exception {
            return new StorageProviderInfo(provider);
        }
    }

    protected static class DeactivateOperation implements
            ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            StorageProviderUtils.deactivate(id);
            return null;
        }
    }

}
