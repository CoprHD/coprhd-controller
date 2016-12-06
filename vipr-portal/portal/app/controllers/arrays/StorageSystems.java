/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.security.Security.isProjectAdmin;
import static controllers.security.Security.isTenantAdmin;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.pools.StoragePoolUpdate;
import com.emc.storageos.model.ports.StoragePortRequestParam;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.VirtualNasParam;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.storageos.model.valid.Endpoint;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.vipr.client.Task;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import controllers.Common;
import controllers.arrays.StorageProviders.StorageProviderForm;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.BlockProtocols;
import models.PoolTypes;
import models.RegistrationStatus;
import models.StorageProviderTypes;
import models.StorageSystemTypes;
import models.datatable.ExportPathParamsDataTable;
import models.datatable.ExportPathParamsDataTable.PortSelectionDataTable;
import models.datatable.ExportPathParamsDataTable.StoragePortsDataTable;
import models.datatable.StoragePoolDataTable;
import models.datatable.StoragePoolDataTable.StoragePoolInfo;
import models.datatable.StoragePortDataTable;
import models.datatable.StoragePortDataTable.StoragePortInfo;
import models.datatable.StorageSystemDataTable;
import models.datatable.StorageSystemDataTable.StorageSystemInfo;
import models.datatable.VirtualNasServerDataTable;
import models.datatable.VirtualNasServerDataTable.VirtualNasServerInfo;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Max;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.BourneUtil;
import util.EnumOption;
import util.MessagesUtils;
import util.StoragePoolUtils;
import util.StoragePortUtils;
import util.StorageSystemUtils;
import util.StringOption;
import util.TenantUtils;
import util.VCenterUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathParametersRestRep;
import com.emc.storageos.model.block.export.ExportPathUpdateParams;
import com.emc.storageos.model.block.export.StoragePorts;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.pools.StoragePoolUpdate;
import com.emc.storageos.model.ports.StoragePortRequestParam;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.VirtualNasParam;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.storageos.model.valid.Endpoint;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.vipr.client.Task;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import controllers.Common;
import controllers.arrays.StorageProviders.StorageProviderForm;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageSystems extends ViprResourceController {
    protected static final String SAVED_ARRAY = "StorageSystems.savedArray";
    protected static final String SAVED_SMIS = "StorageSystems.savedSMIS";
    protected static final String DELETED_SUCCESS = "StorageSystems.deleted.success";
    protected static final String DELETED_ERROR = "StorageSystems.deleted.error";
    protected static final String UNKNOWN = "StorageSystems.unknown";
    protected static final String NAME_NOT_AVAILABLE = "StorageSystems.nameNotAvailable";
    protected static final String SAVED_POOL = "StorageSystems.savedPool";
    protected static final String DEREGISTER_SUCCESS = "PhysicalAssets.deregistration.success";
    protected static final String DEREGISTER_ERROR = "PhysicalAssets.deregistration.error";
    protected static final String REGISTER_SUCCESS = "PhysicalAssets.registration.success";
    protected static final String REGISTER_ERROR = "PhysicalAssets.registration.error";
    protected static final String UNKNOWN_PORT = "storageArrayPort.unknown";
    protected static final String NOT_REGISTERED = "StorageSystems.not.registered";
    protected static final String PARSE_ERROR = "storageArray.parseError";
    protected static final String SECONDARY_DETAIL_MISSING = "storageArray.secondaryHost.secondaryPort.missing";
    protected static final String SECONDARY_USER_MISSING = "storageArray.secondaryName.missing";
    protected static final String SCALEIO = "scaleio";
    protected static final String VMAX = "vmax";
    private static final String EXPECTED_GEO_VERSION_FOR_VNAS_SUPPORT = "2.4";
    private static final String HTTPS = "https";

    private static final String VIPR_START_GUIDE = "VIPR_START_GUIDE";
    private static final String GUIDE_DATA = "GUIDE_DATA";
    private static final String STORAGE_SYSTEMS = "storage_systems";
    private static final String GUIDE_VISIBLE = "guideVisible";
    private static final String GUIDE_COMPLETED_STEP = "completedSteps";
    private static final String SMIS_VMAX = "StorageSystemType.STORAGE_PROVIDER.vmax";
    // key: storage system type, value: storage provider type
    private static Map<String, String> nonNativeSystemProviderMap = new HashMap<String, String>();

    private static void buildSouthBoundTypeMap() {
        nonNativeSystemProviderMap.clear();
        Map<String, StorageSystemTypeRestRep> typeMap = StorageSystemTypes.buildTypeMap();
        for (StorageSystemTypeRestRep type : typeMap.values()) {
            if (type.isNative() || type.getManagedBy() == null) {
                continue;
            }
            nonNativeSystemProviderMap.put(type.getStorageTypeName(),
                    typeMap.get(type.getManagedBy()).getStorageTypeName());
        }
    }

    private static void addReferenceData() {
        renderArgs.put("storageArrayTypeList", StorageSystemTypes.getStorageTypeOptions());
        renderArgs.put("smisStorageSystemTypeList", StorageProviderTypes.getProviderOption());
        renderArgs.put("nonSmisStorageSystemTypeList", StorageSystemTypes.getStorageTypeOptions());
        renderArgs.put("sslDefaultStorageSystemList", StorageProviderTypes.getProvidersWithSSL());
        renderArgs.put("nonSSLStorageSystemList", StorageProviderTypes.getProvidersWithoutSSL());

        List<EnumOption> defaultStorageArrayPortMap = StorageProviderTypes.getStoragePortMap();
        renderArgs.put("defaultStorageArrayPortMap", defaultStorageArrayPortMap);
    }

    private static void addReferenceDataAllFlash() {
        renderArgs.put("storageArrayTypeList", StorageSystemTypes.getAllFlashStorageTypeOptions());
        renderArgs.put("smisStorageSystemTypeList", StorageProviderTypes.getProviderOption());
        renderArgs.put("nonSmisStorageSystemTypeList", StorageSystemTypes.getStorageTypeOptions());
        renderArgs.put("sslDefaultStorageSystemList", StorageProviderTypes.getProvidersWithSSL());
        renderArgs.put("nonSSLStorageSystemList", StorageProviderTypes.getProvidersWithoutSSL());
        List<EnumOption> defaultStorageArrayPortMap = StorageProviderTypes.getStoragePortMap();
        renderArgs.put("defaultStorageArrayPortMap", defaultStorageArrayPortMap);
    }

    public static void list() {
        renderArgs.put("dataTable", new StorageSystemsDataTable());
        render();
    }

    public static class StorageSystemsDataTable extends StorageSystemDataTable {
        public StorageSystemsDataTable() {
            addColumn("actions").setRenderFunction("renderButtonBar");
            sortAllExcept("actions");
        }
    }

    public static void discoveryCheckJson(@As(",") String[] ids) {
        List<String> failedDiscovery = new ArrayList<String>();
        for (String id : ids) {
            StorageSystemRestRep storageSystem = StorageSystemUtils
                    .getStorageSystem(id);
            if (storageSystem == null || storageSystem.getRegistrationStatus().equals("UNREGISTERED")) {
                // ignore for now
                continue;
            }
            if (!storageSystem.getDiscoveryJobStatus().equals("COMPLETE")) {
                failedDiscovery.add(storageSystem.getName());
                continue;
            }
        }
        renderJSON(failedDiscovery);

    }

    public static void listJson() {
        performListJson(StorageSystemUtils.getStorageSystems(),
                new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(StorageSystemUtils.getStorageSystems(ids),
                new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id);
        if (storageSystem == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        StorageProviderRestRep smisProvider = StorageSystemUtils
                .getStorageProvider(storageSystem);
        Map<String, Set<NamedRelatedResourceRep>> connectivityMap = StorageSystemUtils
                .getProtectionConnectivityMap(storageSystem);
        render(storageSystem, smisProvider, connectivityMap);
    }

    public static void create() {
        // Check add is called from guide wizard, yes only AFA
        JsonObject jobject = getCookieAsJson(VIPR_START_GUIDE);
        String isGuideAdd = null;
        if (jobject != null && jobject.get(GUIDE_VISIBLE) != null) {
            isGuideAdd = jobject.get(GUIDE_VISIBLE).getAsString();
        }
        if (isGuideAdd != null && StringUtils.equalsIgnoreCase(isGuideAdd, "true")) {
            addReferenceDataAllFlash();
        } else {
            addReferenceData();
        }
        StorageSystemForm storageArray = new StorageSystemForm();
        // put all "initial create only" defaults here rather than field initializers
        storageArray.type = StorageSystemTypes.VMAX;
        storageArray.useSSL = true;
        storageArray.userName = "";
        storageArray.smisProviderUseSSL = false;
        render("@edit", storageArray);
    }

    public static void createAllFlash() {
        addReferenceDataAllFlash();
        StorageSystemForm storageArray = new StorageSystemForm();
        // put all "initial create only" defaults here rather than field initializers
        storageArray.type = StorageSystemTypes.VMAX;
        storageArray.useSSL = true;
        storageArray.userName = "";
        storageArray.smisProviderUseSSL = true;
        render("@edit", storageArray);
    }

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id);
        if (storageSystem != null) {
            StorageSystemForm storageArray = new StorageSystemForm(
                    storageSystem);
            if (storageArray.type.equals(SCALEIO)) {
                renderArgs.put("storageArrayTypeList",
                        StorageProviderTypes.getProviderOption());
            }
            if (storageArray.type.equals("xtremio")) {
                renderArgs.put("storageArrayTypeList",
                        StorageProviderTypes.getProviderOption());
            }
            if (storageArray.unregistered) {
                flash.put("warning",
                        MessagesUtils.get(NOT_REGISTERED, storageArray.name));
            }
            if (storageArray.type.equals(VMAX)) {
                @SuppressWarnings("unchecked")
                List<StringOption> options = (List<StringOption>) renderArgs.get("storageArrayTypeList");
                options.add(new StringOption(VMAX, MessagesUtils.get(SMIS_VMAX)));
                @SuppressWarnings("unchecked")
                List<StringOption> smisOptions = (List<StringOption>) renderArgs.get("smisStorageSystemTypeList");
                smisOptions.add(new StringOption(VMAX, MessagesUtils.get(SMIS_VMAX)));
            }
            render(storageArray);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    public static void editSmisProvider(String id) {
        Common.setReferrer(Common.reverseRoute(StorageSystems.class, "list"));
        StorageProviders.edit(id);
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(StorageSystemForm storageArray) {
        storageArray.validate("storageArray");

        if (Validation.hasErrors()) {
            Common.handleError();
        }

        Task<?> sp = storageArray.save();
        String message = storageArray.isStorageProviderManaged()
                && StringUtils.isEmpty(storageArray.id) ? MessagesUtils.get(
                        SAVED_SMIS, storageArray.name) : MessagesUtils.get(SAVED_ARRAY, storageArray.name);
        flash.success(message);

        // check if checklist is running on this step
        JsonObject jobject = getCookieAsJson(VIPR_START_GUIDE);
        if (jobject != null && jobject.get(GUIDE_COMPLETED_STEP) != null && jobject.get(GUIDE_VISIBLE) != null) {
            if (jobject.get(GUIDE_COMPLETED_STEP).getAsInt() == 3
                    && jobject.get(GUIDE_VISIBLE).getAsBoolean()) {
                JsonObject dataObject = getCookieAsJson(GUIDE_DATA);

                JsonArray storage_systems = dataObject
                        .getAsJsonArray(STORAGE_SYSTEMS);
                if (storage_systems == null) {
                    storage_systems = new JsonArray();
                }
                JsonObject storage = new JsonObject();
                storage.addProperty("id", sp.getResourceId().toString());
                storage.addProperty("name", storageArray.name);
                storage_systems.add(storage);
                dataObject.add(STORAGE_SYSTEMS, storage_systems);
                saveJsonAsCookie(GUIDE_DATA, dataObject);
                list();
            }
        }

        // TODO: cleanup referrer
        if (StringUtils.isNotEmpty(storageArray.referrerUrl)) {
            redirect(storageArray.referrerUrl);
        }
        list();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        List<StorageSystemRestRep> storageSystems = StorageSystemUtils
                .getStorageSystems(ids);
        performSuccessFail(storageSystems, new DeleteOperation(),
                DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void introspect(@As(",") String[] ids) {
        introspect(uris(ids));
    }

    private static void introspect(List<URI> ids) {
        performSuccess(ids, new DiscoveryOperation(), DISCOVERY_STARTED);
        list();
    }

    public static void deregisterArrays(@As(",") String[] ids) {
        deregisterArrays(uris(ids));
    }

    private static void deregisterArrays(List<URI> ids) {
        performSuccessFail(ids, new DeregisterOperation(), DEREGISTER_SUCCESS,
                DEREGISTER_ERROR);
        list();
    }

    public static void registerArrays(@As(",") String[] ids) {
        registerArrays(uris(ids));
    }

    private static void registerArrays(List<URI> ids) {
        performSuccessFail(ids, new RegisterOperation(), REGISTER_SUCCESS,
                REGISTER_ERROR);
        list();
    }

    //
    // begin Storage Array > Port functionality
    //

    public static void deregisterPorts(@As(",") String[] ids, String arrayId) {
        deregisterPorts(uris(ids), arrayId);
    }

    private static void deregisterPorts(List<URI> ids, String arrayId) {
        performSuccessFail(ids, new DeregisterPortOperation(),
                DEREGISTER_SUCCESS, DEREGISTER_ERROR);
        ports(arrayId);
    }

    public static void registerPorts(@As(",") String[] ids, String arrayId) {
        registerPorts(uris(ids), arrayId);
    }

    private static void registerPorts(List<URI> ids, String arrayId) {
        performSuccessFail(ids, new RegisterPortOperation(arrayId),
                REGISTER_SUCCESS, REGISTER_ERROR);
        ports(arrayId);
    }

    public static void untagAsDRPorts(@As(",") String[] ids, String arrayId) {
        Set<String> removeTags = new HashSet<String>();
        removeTags.add("dr_port");
        for (int i = 0; i < ids.length; i++) {
            BourneUtil.getViprClient().storagePorts().removeTags(uri(ids[i]), removeTags);
        }
        ports(arrayId);
    }

    public static void tagAsDRPorts(@As(",") String[] ids, String arrayId) {
        Set<String> addTags = new HashSet<String>();
        addTags.add("dr_port");
        for (int i = 0; i < ids.length; i++) {
            BourneUtil.getViprClient().storagePorts().addTags(uri(ids[i]), addTags);
        }
        ports(arrayId);
    }

    public static void arrayPortsJson(String id) {
        List<StoragePortInfo> results = Lists.newArrayList();
        List<StoragePortRestRep> storagePorts = StoragePortUtils
                .getStoragePorts(id);
        for (StoragePortRestRep storagePort : storagePorts) {
            results.add(new StoragePortInfo(storagePort));
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void metricDetails(String id) {
        StoragePortRestRep port = StoragePortUtils.getStoragePort(id);
        render(port);
    }

    public static void ports(String id) {
        addReferenceData();
        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id);
        if (storageSystem == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
        StorageArrayPortDataTable dataTable = new StorageArrayPortDataTable(
                storageSystem);

        render("@listPorts", storageSystem, dataTable);
    }

    public static void createPort(String id) {
        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id);
        StringOption[] portTypeOptions = {
                new StringOption("", ""),
                new StringOption("IP", StringOption.getDisplayValue("IP",
                        "storageArrayPort.portTypes")),
                new StringOption("FC", StringOption.getDisplayValue("FC",
                        "storageArrayPort.portTypes")), };
        renderArgs.put("portTypeOptions", Arrays.asList(portTypeOptions));
        render(storageSystem);
    }

    public static void editPort(String id, String portId) {
        StoragePortRestRep storagePort = StoragePortUtils
                .getStoragePort(portId);
        if (storagePort == null) {
            flash.error(MessagesUtils.get(UNKNOWN_PORT, portId));
            ports(id);
        }
        URI storageSystemId = id(storagePort.getStorageDevice());
        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(storageSystemId);
        if (storageSystem == null) {
            flash.error(MessagesUtils.get(UNKNOWN, storageSystemId));
            list();
        }
        if (RegistrationStatus.isUnregistered(storageSystem
                .getRegistrationStatus())) {
            flash.put("warning",
                    MessagesUtils.get(NOT_REGISTERED, storageSystem.getName()));
        }
        StorageArrayPortForm storageArrayPort = new StorageArrayPortForm();
        storageArrayPort.readFrom(storagePort);
        render(storageArrayPort, storagePort, storageSystem);
    }

    @FlashException(keep = true, referrer = { "ports" })
    public static void savePort(StorageArrayPortForm storageArrayPort) {
        storageArrayPort.validate("storageArrayPort");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        StoragePortRestRep port = storageArrayPort.save();
        flash.success(MessagesUtils.get(SAVED_POOL, port.getPortName()));
        ports(stringId(port.getStorageDevice()));
    }

    //
    // begin Storage Array > Pool functionality
    //

    public static void deregisterPools(@As(",") String[] ids, String arrayId) {
        deregisterPools(uris(ids), arrayId);
    }

    private static void deregisterPools(List<URI> ids, String arrayId) {
        performSuccess(ids, new DeregisterPoolOperation(), DEREGISTER_SUCCESS);
        pools(arrayId);
    }

    public static void registerPools(@As(",") String[] ids, String arrayId) {
        registerPools(uris(ids), arrayId);
    }

    private static void registerPools(List<URI> ids, String arrayId) {
        performSuccess(ids, new RegisterPoolOperation(arrayId),
                REGISTER_SUCCESS);
        pools(arrayId);
    }

    public static void arrayPoolsJson(String id) {
        List<StoragePoolInfo> results = Lists.newArrayList();
        List<StoragePoolRestRep> storagePools = StoragePoolUtils
                .getStoragePools(id);
        for (StoragePoolRestRep storagePool : storagePools) {
            if (!DiscoveredDataObject.DiscoveryStatus.NOTVISIBLE.name().equals(
                    storagePool.getDiscoveryStatus())) {
                results.add(new StoragePoolInfo(storagePool));
            }
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void pools(String id) {
        addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id);
        StorageArrayPoolDataTable dataTable = new StorageArrayPoolDataTable();
        if (StorageSystemTypes.isFileStorageSystem(storageSystem
                .getSystemType())) {
            dataTable.configureForFile();
        }
        if (StorageSystemTypes.isECS(storageSystem.getSystemType())) {
            dataTable.configureForECS();
        }
        render("@listPools", storageSystem, dataTable);
    }

    public static void virtualNasServers(String id) {
        addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id);
        VirtualNasServerDataTable dataTable;
        if (isTenantAdmin() || isProjectAdmin()) {
            dataTable = new VirtualNasServerDataTable();
        } else {
            dataTable = new VirtualNasForNonProjectAdminDataTable();
        }
        renderArgs.put("storageId", id);
        renderArgs
                .put("expectedGeoVersion",
                        VCenterUtils
                                .checkCompatibleVDCVersion(EXPECTED_GEO_VERSION_FOR_VNAS_SUPPORT));
        render("@listVirtualNasServers", storageSystem, dataTable);
    }

    public static class VirtualNasForNonProjectAdminDataTable extends
            VirtualNasServerDataTable {
        public VirtualNasForNonProjectAdminDataTable() {
            alterColumn("project").hidden();
        }
    }

    @FlashException(keep = true, referrer = { "virtualNasServers" })
    public static void associateProject(String nasIds, String projectIds,
            String storageId) throws Exception {

        boolean error = false;
        Set<String> vnasServers = new TreeSet<String>();
        String[] projectIdArray = null;
        if (nasIds != null && !nasIds.isEmpty()) {
            String[] nasArray = nasIds.split(",");
            Collections.addAll(vnasServers, nasArray);
        }
        if (projectIds != null && !projectIds.isEmpty()) {
            projectIds = projectIds.trim();
            projectIdArray = projectIds.split(",");
        }
        VirtualNasParam vNasParam = new VirtualNasParam();
        vNasParam.setVnasServers(vnasServers);

        if (projectIdArray != null && projectIdArray.length > 0) {
            for (int i = 0; i < projectIdArray.length; i++) {
                try {
                    getViprClient().virtualNasServers().assignVnasServers(
                            uri(projectIdArray[i].trim()), vNasParam);
                } catch (Exception e) {
                    error = true;
                    continue;
                }
            }

            if (error) {
                String errorMsg = "Some Virtual NAS servers could not be associated with selected project(s). Please check the API logs for more information.";
                throw new Exception(errorMsg);
            }
        }

        virtualNasServers(storageId);
    }

    @FlashException(keep = true, referrer = { "virtualNasServers" })
    public static void dissociateProject(
            @As(",") String[] projectIdsToDissociate, String nasIds,
            String storageId) {

        if (projectIdsToDissociate != null && projectIdsToDissociate.length > 0) {
            for (String projectId : projectIdsToDissociate) {
                Set<String> vNASSet = new HashSet<String>();
                vNASSet.add(nasIds);
                VirtualNasParam vNasParam = new VirtualNasParam();
                vNasParam.setVnasServers(vNASSet);
                getViprClient().virtualNasServers().unassignVnasServers(
                        uri(projectId), vNasParam);
            }
        }

        virtualNasServers(storageId);
    }

    public static void virtualNasServersJson(String storageId) {
        List<VirtualNasServerInfo> results = Lists.newArrayList();
        List<VirtualNASRestRep> vNasServers = getViprClient()
                .virtualNasServers().getByStorageSystem(uri(storageId));
        boolean isProjectAccessible = false;
        if (isTenantAdmin() || isProjectAdmin()) {
            isProjectAccessible = true;
        }
        for (VirtualNASRestRep vNasServer : vNasServers) {
            results.add(new VirtualNasServerInfo(vNasServer,
                    isProjectAccessible));
        }
        renderArgs.put("storageId", storageId);
        renderArgs
                .put("expectedGeoVersion",
                        VCenterUtils
                                .checkCompatibleVDCVersion(EXPECTED_GEO_VERSION_FOR_VNAS_SUPPORT));
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void getProjectsForNas() {
        List<URI> tenants = Lists.newArrayList();
        List<StringOption> allTenants = TenantUtils.getUserSubTenantOptions();
        Iterator<StringOption> tenantsIterator = allTenants.iterator();
        while (tenantsIterator.hasNext()) {
            StringOption tenant = tenantsIterator.next();
            if (tenant == null) {
                continue;
            }
            tenants.add(uri(tenant.id));
        }
        List<StringOption> projectTenantOptions = Lists.newArrayList();
        for (URI tenantId : tenants) {
            String tenantName = getViprClient().tenants().get(tenantId)
                    .getName();
            List<String> projectOptions = Lists.newArrayList();

            List<ProjectRestRep> projects = getViprClient().projects()
                    .getByTenant(tenantId);
            for (ProjectRestRep project : projects) {
                projectOptions.add(project.getId().toString() + "~~~"
                        + project.getName());
            }

            projectTenantOptions.add(new StringOption(
                    projectOptions.toString(), tenantName));
        }

        renderJSON(projectTenantOptions);
    }

    public static void vNasMoreDetails(String id) {
        id = id.substring(0, id.indexOf("~~~"));
        List<URI> ids = Lists.newArrayList();
        ids.add(uri(id));
        List<VirtualNASRestRep> vNasRep = getViprClient().virtualNasServers()
                .getByIds(ids);
        VirtualNASRestRep vNas = new VirtualNASRestRep();
        if (!vNasRep.isEmpty()) {
            vNas = vNasRep.get(0);
        }
        render(vNas);
    }
    
    public static void portGroups(String id) {
        addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        ExportPathParamsDataTable dataTable = new ExportPathParamsDataTable();
        renderArgs.put("dataTable", dataTable);
        renderArgs.put("storageSystemId", id);
        renderArgs.put("storageSystemName", storageSystem.getName());
        PortGroupForm portGroup = new PortGroupForm();
        render("@listPortGroups", storageSystem, dataTable,portGroup);
    }

    public static void portGroupsJson(String storageId) {
        List<ExportPathParamsDataTable.ExportPathParamsModel> results = Lists.newArrayList();
        List<ExportPathParametersRestRep> portGroups = getViprClient().exportPathParameters().getPortGroups();

        for (ExportPathParametersRestRep portGroup : portGroups) {
            results.add(new ExportPathParamsDataTable.ExportPathParamsModel(portGroup.getId(), portGroup.getName(), portGroup
                    .getDescription()));
        }
        renderArgs.put("storageSystemId", storageId);
        renderJSON(DataTablesSupport.createJSON(results, params));
    }
    
   // @FlashException(value = "portGroups", keep = true)
    public static void addPortGroup(String storageSystemId, String name) {
       // ExportPathParamsDataTable dataTable = new ExportPathParamsDataTable();
        //StoragePortsDataTable portDataTable = dataTable.new StoragePortsDataTable();
        //PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();
        renderArgs.put("storageSystemId", storageSystemId);
        //PortGroupForm portGroup = new PortGroupForm();
        
        ExportPathParameters input = new ExportPathParameters();//createExportPathParams(portGroup);
        input.setName(name);
        ExportPathParametersRestRep resp =getViprClient().exportPathParameters().create(input, "true");
        editPortGroup(resp.getId().toString(),storageSystemId);
        //render("@editPortGroup", portGroup, portDataTable,portSelectionDataTable);
    }
    
    //@FlashException(value = "portGroups", keep = true)
    public static void editPortGroup(String id, String storageSystemId) {
        ExportPathParametersRestRep exportPathParametersRestRep = getViprClient().exportPathParameters().get(uri(id));
        renderArgs.put("storageSystemId", storageSystemId);
        renderArgs.put("pathParamId", id);
        ExportPathParamsDataTable dataTable = new ExportPathParamsDataTable();
        StoragePortsDataTable portDataTable = dataTable.new StoragePortsDataTable();
        PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();
        //StorageArrayPortDataTable portDataTable = new StorageArrayPortDataTable(storageSystem);
        if (exportPathParametersRestRep != null) {
            PortGroupForm portGroup = new PortGroupForm().form(exportPathParametersRestRep);
            render(portGroup,portDataTable,portSelectionDataTable);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            portGroups(storageSystemId);
        }

    }
    @FlashException("portGroups")
    public static void deletePortGroup(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().exportPathParameters().delete(uri(id));
            }
            flash.success(MessagesUtils.get("exportPathParameters.deleted"));
        }
        list();
    }
    
    @FlashException(keep = true, referrer = { "create", "editPortGroup" })
    public static void savePortGroup(PortGroupForm portGroup) {
        if (portGroup == null) {
            Logger.error("No port group parameters passed");
            badRequest("No port group parameters passed");
            return;
        }
        portGroup.validate("portGroup");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        portGroup.id = params.get("id");
        if (portGroup.isNew()) {
            
            ExportPathParameters input = createExportPathParams(portGroup);
            getViprClient().exportPathParameters().create(input, "true");
        } else {
            ExportPathParametersRestRep exportPathParametersRestRep = getViprClient().exportPathParameters().get(uri(portGroup.id));
            ExportPathUpdateParams input = updateExportPathParams(portGroup);
            getViprClient().exportPathParameters().update(exportPathParametersRestRep.getId(), input);
        }
        flash.success(MessagesUtils.get("projects.saved", portGroup.name));
        list();
    }
    
    public static ExportPathParameters createExportPathParams( PortGroupForm portGroup ) {
        ExportPathParameters pathParam = new ExportPathParameters();
        pathParam.setName(portGroup.name.trim());
        pathParam.setDescription(portGroup.description.trim());
        pathParam.setMaxPaths(portGroup.maxPaths);
        pathParam.setMinPaths(portGroup.minPaths);
        pathParam.setPathsPerInitiator(portGroup.pathsPerInitiator);
        pathParam.setMaxInitiatorsPerPort(portGroup.maxInitiatorsPerPort);

        pathParam.setStoragePorts(portGroup.storagePorts);
        return pathParam;
    }
    
    public static ExportPathUpdateParams updateExportPathParams( PortGroupForm portGroup ) {
        ExportPathUpdateParams pathParam = new ExportPathUpdateParams();
        pathParam.setName(portGroup.name.trim());
        pathParam.setDescription(portGroup.description.trim());
        pathParam.setMaxPaths(portGroup.maxPaths);
        pathParam.setMinPaths(portGroup.minPaths);
        pathParam.setPathsPerInitiator(portGroup.pathsPerInitiator);
        pathParam.setMaxInitiatorsPerPort(portGroup.maxInitiatorsPerPort);
        return pathParam;
    }
    
    @FlashException(referrer = { "editPortGroup" })
    public static void addPorts(String pathParamId,String storageSystemId, @As(",") String[] ids) {
      ExportPathParametersRestRep exportPathParametersRestRep = getViprClient().exportPathParameters().get(uri(pathParamId));
        List<URI> portsInDb = exportPathParametersRestRep.getStoragePorts();
            List<URI> portsToAdd = Lists.newArrayList();
            for (String value : ids) {
                if (StringUtils.isNotBlank(value)) {
                    if (!portsInDb.contains(uri(value))) {
                        portsToAdd.add(uri(value));
                    }   
                }
            }
            ExportPathUpdateParams pathParam = new ExportPathUpdateParams();
            StoragePorts  storagePorts = new StoragePorts(); 
            if (!portsToAdd.isEmpty()) {
                storagePorts.setStoragePorts(portsToAdd);
                pathParam.setPortsToAdd(storagePorts);
            }
            getViprClient().exportPathParameters().update(exportPathParametersRestRep.getId(), pathParam);
        
        editPortGroup(pathParamId,storageSystemId);
        
    }
    
    @FlashException(referrer = { "editPortGroup" })
    public static void deletePorts(String pathParamId,String storageSystemId, @As(",") String[] ids) {
        
        ExportPathParametersRestRep exportPathParametersRestRep = getViprClient().exportPathParameters().get(uri(pathParamId));
        List<URI> portsInDb = exportPathParametersRestRep.getStoragePorts();
            List<URI> portsToRemove = Lists.newArrayList();
            for (String value : ids) {
                if (StringUtils.isNotBlank(value)) {
                    if (portsInDb.contains(uri(value))) {
                        portsToRemove.add(uri(value));
                    }   
                }
            }
            ExportPathUpdateParams pathParam = new ExportPathUpdateParams();
            StoragePorts  storagePorts = new StoragePorts(); 
            if (!portsToRemove.isEmpty()) {
                storagePorts.setStoragePorts(portsToRemove);
                pathParam.setPortsToRemove(storagePorts);
            }
            getViprClient().exportPathParameters().update(exportPathParametersRestRep.getId(), pathParam);
        editPortGroup(pathParamId,storageSystemId);
    }
    
    public static void storagePortsJson(String pathParamId) {
        List<StoragePortInfo> results = Lists.newArrayList();
        
        if (pathParamId != null) {
            ExportPathParametersRestRep exportPathParametersRestRep = getViprClient().exportPathParameters().get(uri(pathParamId));
            List<URI> storagePortUris = exportPathParametersRestRep.getStoragePorts();
            
            List<StoragePortRestRep> storagePorts = StoragePortUtils
                    .getStoragePorts(storagePortUris);
            for (StoragePortRestRep storagePort : storagePorts) {
                results.add(new StoragePortInfo(storagePort));
            }
            
        } 
        
        renderJSON(DataTablesSupport.createJSON(results, params));
    }
    
    public static void availablePortsJson(String val) {
        List<PortSelectionDataTable.PortSelectionModel> results = Lists.newArrayList();
        String[] data = val.split("~~~");
        String pathParamId = "";
        String systemId = "";
        if(data.length > 1){
            pathParamId = data[0];
            systemId = data[1];
        }
        List<StoragePortRestRep> storagePorts = StoragePortUtils.getStoragePorts(systemId);

        if (pathParamId != null && !"null".equals(pathParamId)) {
            ExportPathParametersRestRep exportPathParametersRestRep = getViprClient().exportPathParameters().get(uri(pathParamId));
            List<URI> storagePortUris = exportPathParametersRestRep.getStoragePorts();
            List<StoragePortRestRep> portsNotForSelection = StoragePortUtils.getStoragePorts(storagePortUris);
            storagePorts.removeAll(portsNotForSelection);

        }
        for (StoragePortRestRep storagePort : storagePorts) {
            ExportPathParamsDataTable dataTable = new ExportPathParamsDataTable();
            PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();
            results.add(portSelectionDataTable.new PortSelectionModel(storagePort));
        }

        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void editPool(String id, String poolId) {
        StoragePoolRestRep storagePool = StoragePoolUtils
                .getStoragePool(poolId);
        StorageSystemRestRep storageSystem = StorageSystemUtils
                .getStorageSystem(id(storagePool.getStorageSystem()));
        if (RegistrationStatus.isUnregistered(storageSystem
                .getRegistrationStatus())) {
            flash.put("warning",
                    MessagesUtils.get(NOT_REGISTERED, storageSystem.getName()));
        }
        StorageArrayPoolForm storageArrayPool = new StorageArrayPoolForm();
        storageArrayPool.readFrom(storagePool);
        render(storageArrayPool, storagePool, storageSystem);
    }

    @FlashException(keep = true, referrer = { "editPool" })
    public static void savePool(StorageArrayPoolForm storageArrayPool) {
        storageArrayPool.validate("storageArrayPool");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        StoragePoolRestRep pool = storageArrayPool.save();
        flash.success(MessagesUtils.get(SAVED_POOL, pool.getPoolName()));
        pools(stringId(pool.getStorageSystem()));
    }

    public static class StorageArrayPoolForm {

        public String storageArrayId;

        public String id;

        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @Min(0)
        @Max(100)
        public Integer maxPoolUtilizationPercentage;

        @Min(0)
        public Integer maxThinPoolSubscriptionPercentage;

        @Min(0)
        public Integer resourceLimit;

        public String poolType;
        public boolean supportsThinProvisioning;

        public StorageArrayPoolForm() {
        }

        public void readFrom(StoragePoolRestRep storagePool) {
            this.storageArrayId = stringId(storagePool.getStorageSystem());
            this.id = stringId(storagePool);
            this.name = storagePool.getPoolName();
            this.maxPoolUtilizationPercentage = storagePool
                    .getMaxPoolUtilizationPercentage();
            if ((storagePool.getMaxResources() != null)
                    && (storagePool.getMaxResources() > -1)) {
                this.resourceLimit = storagePool.getMaxResources();
            }
            this.poolType = storagePool.getPoolServiceType();
            this.supportsThinProvisioning = StoragePoolUtils
                    .supportsThinProvisioning(storagePool);
            if (supportsThinProvisioning) {
                this.maxThinPoolSubscriptionPercentage = storagePool
                        .getMaxThinPoolSubscriptionPercentage();
            }
        }

        public StoragePoolRestRep save() {
            return update();
        }

        private StoragePoolRestRep update() {
            StoragePoolUpdate storagePoolParam = new StoragePoolUpdate();
            storagePoolParam
                    .setMaxPoolUtilizationPercentage(this.maxPoolUtilizationPercentage);
            if (maxThinPoolSubscriptionPercentage != null) {
                storagePoolParam
                        .setMaxThinPoolSubscriptionPercentage(this.maxThinPoolSubscriptionPercentage);
            }

            if ((resourceLimit != null) && (resourceLimit > -1)) {
                storagePoolParam.setMaxResources(this.resourceLimit);
                storagePoolParam.setIsUnlimitedResourcesSet(false);
            } else {
                storagePoolParam.setMaxResources(null);
                storagePoolParam.setIsUnlimitedResourcesSet(true);
            }

            return StoragePoolUtils.update(id, storagePoolParam);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
        }

    }

    public static class StorageArrayPortForm {

        public URI storageArrayId;

        public URI id;

        @Required
        @MinSize(2)
        @MaxSize(128)
        public String name;

        @Required
        public String portType;

        @Required
        public String port;

        public void readFrom(StoragePortRestRep storagePort) {
            storageArrayId = storagePort.getStorageDevice().getId();
            id = storagePort.getId();
            name = storagePort.getPortName();
            portType = storagePort.getTransportType();
            port = storagePort.getPortNetworkId();
        }

        public StoragePortRestRep save() {
            return (id == null) ? create() : update();
        }

        private StoragePortRestRep create() {
            StoragePortRequestParam storagePortParam = new StoragePortRequestParam();
            storagePortParam.setName(name);
            storagePortParam.setTransportType(portType);
            storagePortParam.setPortNetworkId(port);

            return StoragePortUtils.create(storageArrayId, storagePortParam);
        }

        private StoragePortRestRep update() {
            StoragePortUpdate storagePortParam = new StoragePortUpdate();
            storagePortParam.setPortNetworkId(port);

            return StoragePortUtils.update(id, storagePortParam);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (BlockProtocols.isFC(portType)) {
                if (port != null
                        && !EndpointUtility.isValidEndpoint(port,
                                Endpoint.EndpointType.WWN)) {
                    Validation.addError(fieldName + ".port",
                            "storageArrayPort.port.invalidWWN");
                }
            } else {
                boolean valid = EndpointUtility.isValidEndpoint(port,
                        Endpoint.EndpointType.IQN);
                if (!valid) {
                    Validation.addError(fieldName + ".port",
                            "storageArrayPort.port.invalidIQN");
                }
            }
        }
    }

    // Suppressing Sonar violation of Password Hardcoded. Password is not
    // hardcoded here.
    @SuppressWarnings("squid:S2068")
    public static class StorageSystemForm {
        public String id;

        @Required
        public String type;

        @MaxSize(2048)
        @Required
        public String name;

        @Required
        @HostNameOrIpAddress
        public String ipAddress;

        @Required
        public Integer portNumber;

        @MaxSize(2048)
        public String userName;

        @MaxSize(2048)
        public String userPassword;

        @MaxSize(2048)
        public String confirmPassword;

        @MaxSize(2048)
        public String secondaryUsername = "";

        @MaxSize(2048)
        public String secondaryPassword = "";

        @MaxSize(2048)
        public String secondaryPasswordConfirm = "";

        public String elementManagerURL;

        public String secretKey;

        public Boolean useSSL;

        public Integer resourceLimit;
        public String resourceType;
        public boolean unlimitResource;
        public boolean supportsSoftLimit;
        public boolean supportsNotificationLimit;

        //
        // a flag to set if unlimitResource control was previously visible.
        // This is used upon save failure for UI to correctly recover
        // UnlimitResource control
        // state after save failure.
        //
        public boolean unlimitResourceWasVisible;

        //
        // onboard smis provider attributes
        //
        public String smisProviderIpAddress;
        public Integer smisProviderPortNumber;
        public String smisProviderUserName;
        public String smisProviderUserPassword;
        public String smisProviderConfirmPassword;
        public boolean smisProviderUseSSL;

        public String referrerUrl;

        public boolean unregistered;

        @MaxSize(2048)
        public String hyperScaleUsername;

        @MaxSize(2048)
        public String hyperScalePassword = "";

        @MaxSize(2048)
        public String hyperScalePasswordConfirm = "";

        public String hyperScaleHost;

        public String hyperScalePort;

        public URL url;

        public String secondaryURL;

        public StorageSystemForm() {
            this.userPassword = "";
            this.confirmPassword = "";
            this.smisProviderUserPassword = "";
            this.smisProviderConfirmPassword = "";
        }

        public StorageSystemForm(StorageSystemRestRep storageArray) {
            this.id = storageArray.getId().toString();
            this.name = StorageSystemUtils.getName(storageArray);
            this.type = storageArray.getSystemType();
            this.supportsSoftLimit = storageArray.getSupportsSoftLimit();
            this.supportsNotificationLimit = storageArray
                    .getSupportsNotificationLimit();
            // VNX Block uses the same select option as VMAX
            if (StorageSystemTypes.isVnxBlock(type)) {
                this.type = StorageSystemTypes.VMAX;
            }
            this.resourceType = PoolTypes.fromStorageSystemType(storageArray
                    .getSystemType());
            this.userName = storageArray.getUsername();
            this.resourceLimit = storageArray.getMaxResources() != null ? storageArray
                    .getMaxResources() : 0;
            this.unlimitResource = this.unlimitResourceWasVisible = this.resourceLimit != null
                    && this.resourceLimit == -1;
            this.unregistered = RegistrationStatus.isUnregistered(storageArray
                    .getRegistrationStatus());

            if (isStorageProviderManaged()) {
                this.useSSL = storageArray.getSmisUseSSL();
                this.portNumber = storageArray.getSmisPortNumber();
                this.ipAddress = storageArray.getSmisProviderIP();
            } else {
                this.portNumber = storageArray.getPortNumber();
                this.ipAddress = storageArray.getIpAddress();

                this.smisProviderIpAddress = storageArray.getSmisProviderIP();
                this.smisProviderPortNumber = storageArray.getSmisPortNumber();
                this.smisProviderUseSSL = storageArray.getSmisUseSSL();
                this.smisProviderUserName = storageArray.getSmisUserName();
            }

        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public Task<StorageSystemRestRep> update() {
            StorageSystemUpdateRequestParam storageArray = new StorageSystemUpdateRequestParam();
            storageArray.setName(name);

            // if unlimit resources was unchecked, set the given limit
            if (!unlimitResource) {
                storageArray.setMaxResources(resourceLimit);
                storageArray.setIsUnlimitedResourcesSet(false);
                // allow changing back to unlimited
            } else {
                storageArray.setIsUnlimitedResourcesSet(true);
                storageArray.setMaxResources(null);
            }
            if (isVnxFile()) {
                storageArray.setSmisProviderIP(smisProviderIpAddress);
                storageArray.setSmisPortNumber(smisProviderPortNumber);
                storageArray.setSmisUseSSL(smisProviderUseSSL);
                storageArray.setSmisPassword(StringUtils
                        .trimToNull(smisProviderUserPassword));
                storageArray.setSmisUserName(StringUtils
                        .trimToNull(smisProviderUserName));
            }

            if (!isStorageProviderManaged()) {
                storageArray.setIpAddress(ipAddress);
                storageArray.setPortNumber(portNumber);
                storageArray.setPassword(StringUtils.trimToNull(userPassword));
                storageArray.setUserName(StringUtils.trimToNull(userName));
            }

            if (isScaleIOApi()) {
                storageArray.setPassword(secondaryPassword);
            }

            if (isCeph()) {
                storageArray.setPassword(StringUtils.trimToNull(secretKey));
            }

            return StorageSystemUtils.update(id, storageArray);
        }

        public Task<StorageSystemRestRep> create() {
            StorageSystemRequestParam storageArray = new StorageSystemRequestParam();
            storageArray.setName(name);
            storageArray.setSystemType(type);

            storageArray.setPassword(userPassword);
            storageArray.setUserName(userName);
            storageArray.setPortNumber(portNumber);
            storageArray.setIpAddress(ipAddress);
            // storageArray.setRegistrationMode(RegistrationMode.SYSTEM);

            if (isVnxFile()) {
                storageArray.setSmisPassword(smisProviderUserPassword);
                storageArray.setSmisUserName(smisProviderUserName);
                storageArray.setSmisPortNumber(smisProviderPortNumber);
                storageArray.setSmisProviderIP(smisProviderIpAddress);
                storageArray.setSmisUseSSL(smisProviderUseSSL);
            }

            if (isScaleIOApi()) {
                storageArray.setPassword(secondaryPassword);
            }
            return StorageSystemUtils.create(storageArray);
        }

        public Task<StorageProviderRestRep> createStorageProvider() {
            StorageProviderForm storageProviderForm = new StorageProviderForm();
            storageProviderForm.name = this.name;
            storageProviderForm.password = this.userPassword;
            storageProviderForm.userName = this.userName;
            storageProviderForm.useSSL = this.useSSL;
            storageProviderForm.ipAddress = this.ipAddress;
            storageProviderForm.portNumber = this.portNumber;
            storageProviderForm.interfaceType = mapProviderType(this.type);
            storageProviderForm.secondaryUsername = this.secondaryUsername;
            storageProviderForm.secondaryPassword = this.secondaryPassword;
            storageProviderForm.elementManagerURL = this.elementManagerURL;
            storageProviderForm.secretKey = this.secretKey;
            storageProviderForm.secondaryURL = this.secondaryURL;

            return storageProviderForm.create();
        }

        private String mapProviderType(String type) {
            String providerType = StorageProviderTypes.fromStorageArrayType(type);
            if (providerType != null) {
                return providerType;
            }
            return nonNativeSystemProviderMap.get(type);
        }

        public void setSecondaryParameters() {
            if (StringUtils.isNotEmpty(this.hyperScaleUsername)) {
                this.secondaryUsername = this.hyperScaleUsername;
            }
            if (StringUtils.isNotEmpty(this.hyperScalePassword)) {
                this.secondaryPassword = this.hyperScalePassword;
            }
            if (StringUtils.isNotEmpty(this.hyperScalePasswordConfirm)) {
                this.secondaryPasswordConfirm = this.hyperScalePasswordConfirm;
            }
            if (StringUtils.isNotEmpty(this.hyperScaleHost) && StringUtils.isNotEmpty(this.hyperScalePort)) {
                try {
                    url = new URL(HTTPS, this.hyperScaleHost, Integer.parseInt(this.hyperScalePort), "");
                } catch (Exception e) {
                    flash.error(MessagesUtils.get(PARSE_ERROR));
                }
                this.secondaryURL = url.toString();
            }
        }

        public Task<?> save() {
            setSecondaryParameters();
            buildSouthBoundTypeMap();
            if (isNew()) {
                if (isStorageProviderManaged()) {
                    return createStorageProvider();
                } else {
                    return create();
                }
            } else {
                return update();
            }
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isVnxFile()) {
                Validation.required(fieldName + ".smisProviderIpAddress",
                        this.smisProviderIpAddress);
                Validation.required(fieldName + ".smisProviderPortNumber",
                        this.smisProviderPortNumber);
            }

            if (isNew()) {
                if (isCeph()) {
                    Validation.required(fieldName + ".userName", this.userName);
                    Validation.required(fieldName + ".secretKey", this.secretKey);
                } else if (isScaleIOApi()) {
                    Validation.required(fieldName + ".secondaryUsername",
                            this.secondaryUsername);
                    Validation.required(fieldName + ".secondaryPassword",
                            this.secondaryPassword);
                    Validation.required(
                            fieldName + ".secondaryPasswordConfirm",
                            this.secondaryPasswordConfirm);
                } else {
                    Validation.required(fieldName + ".userName", this.userName);
                    Validation.required(fieldName + ".userPassword",
                            this.userPassword);
                    Validation.required(fieldName + ".confirmPassword",
                            this.confirmPassword);

                    if (isVnxFile()) {
                        Validation.required(
                                fieldName + ".smisProviderUserName",
                                this.smisProviderUserName);
                        Validation.required(fieldName
                                + ".smisProviderUserPassword",
                                this.smisProviderUserPassword);
                        Validation.required(fieldName
                                + ".smisProviderConfirmPassword",
                                this.smisProviderConfirmPassword);
                    }

                    if (isScaleIO()
                            && !isMatchingPasswords(secondaryPassword,
                                    secondaryPasswordConfirm)) {
                        Validation
                                .addError(
                                        fieldName + ".secondaryPasswordConfirm",
                                        MessagesUtils
                                                .get("storageArray.secondaryPassword.confirmPassword.not.match"));
                    }
                }
            } else {
                if (!unlimitResource) {
                    Validation.required(fieldName + ".resourceLimit",
                            this.resourceLimit);
                    Validation.min(fieldName + ".resourceLimit",
                            this.resourceLimit, 0);
                }
            }

            if (!isScaleIOApi()
                    && !isMatchingPasswords(userPassword, confirmPassword)) {
                Validation.addError(fieldName + ".confirmPassword",
                        MessagesUtils
                                .get("storageArray.confirmPassword.not.match"));
            }

            if (isVnxFile()) {
                if (!isMatchingPasswords(smisProviderUserPassword,
                        smisProviderConfirmPassword)) {
                    Validation.addError(fieldName
                            + ".smisProviderConfirmPassword", MessagesUtils
                                    .get("storageArray.confirmPassword.not.match"));
                }

                Validation.required(fieldName + ".smisProviderIpAddress",
                        this.smisProviderIpAddress);
                Validation.required(fieldName + ".smisProviderPortNumber",
                        this.smisProviderPortNumber);
            }

            if (isXIV()) {
                if ((StringUtils.isNotEmpty(this.hyperScaleHost) && StringUtils.isNotEmpty(this.hyperScalePort))) {
                    if (StringUtils.isEmpty(hyperScaleUsername)) {
                        Validation.addError(fieldName + ".hyperScaleUsername", MessagesUtils
                                .get(SECONDARY_USER_MISSING));
                    }
                } else if (!(StringUtils.isEmpty(this.hyperScaleHost) && StringUtils.isEmpty(this.hyperScalePort))) {
                    Validation.addError(fieldName + ".hyperScaleHost", MessagesUtils
                            .get(SECONDARY_DETAIL_MISSING));
                    Validation.addError(fieldName + ".hyperScalePort", MessagesUtils
                            .get(SECONDARY_DETAIL_MISSING));
                }
                if (StringUtils.isNotEmpty(this.hyperScaleUsername)) {
                    if ((StringUtils.isEmpty(this.hyperScaleHost) || StringUtils.isEmpty(this.hyperScalePort))) {
                        Validation.addError(fieldName + ".hyperScaleHost", MessagesUtils
                                .get(SECONDARY_DETAIL_MISSING));
                        Validation.addError(fieldName + ".hyperScalePort", MessagesUtils
                                .get(SECONDARY_DETAIL_MISSING));
                    }
                }
            }
        }

        private boolean isMatchingPasswords(String password, String confirm) {
            return StringUtils.equals(StringUtils.trimToEmpty(password),
                    StringUtils.trimToEmpty(confirm));
        }

        private boolean isStorageProviderManaged() {
            return StorageSystemTypes.isStorageProvider(type) || nonNativeSystemProviderMap.containsKey(type);
        }

        private boolean isVnxFile() {
            return StorageSystemTypes.isVnxFile(type);
        }

        private boolean isScaleIO() {
            return StorageSystemTypes.isScaleIO(type);
        }

        private boolean isScaleIOApi() {
            return StorageSystemTypes.isScaleIOApi(type);
        }

        private boolean isXIV() {
            return StorageSystemTypes.isXIV(type);
        }

        private boolean isIsilon() {
            return StorageSystemTypes.isIsilon(type);
        }

        private boolean isCeph() {
            return StorageSystemTypes.isCeph(type);
        }
    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<StorageSystemInfo, StorageSystemRestRep> {
        @Override
        public StorageSystemInfo performOperation(
                StorageSystemRestRep storageSystem) throws Exception {
            return new StorageSystemInfo(storageSystem);
        }
    }

    protected static class DeleteOperation
            implements
            ResourceValueOperation<Task<StorageSystemRestRep>, StorageSystemRestRep> {
        @Override
        public Task<StorageSystemRestRep> performOperation(
                StorageSystemRestRep storageSystem) throws Exception {
            if (RegistrationStatus.isRegistered(storageSystem
                    .getRegistrationStatus())) {
                StorageSystemUtils.deregister(id(storageSystem));
            }
            Task<StorageSystemRestRep> task = StorageSystemUtils
                    .deactivate(id(storageSystem));
            return task;
        }
    }

    protected static class DiscoveryOperation implements
            ResourceIdOperation<Task<StorageSystemRestRep>> {
        @Override
        public Task<StorageSystemRestRep> performOperation(URI id)
                throws Exception {
            Task<StorageSystemRestRep> task = StorageSystemUtils.discover(id);
            return task;
        }
    }

    protected static class DeregisterOperation implements
            ResourceIdOperation<StorageSystemRestRep> {
        @Override
        public StorageSystemRestRep performOperation(URI id) throws Exception {
            return StorageSystemUtils.deregister(id);
        }
    }

    protected static class RegisterOperation implements
            ResourceIdOperation<StorageSystemRestRep> {
        @Override
        public StorageSystemRestRep performOperation(URI id) throws Exception {
            return StorageSystemUtils.register(id);
        }
    }

    protected static class DeregisterPortOperation implements
            ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePortUtils.deregister(id);
            return null;
        }
    }

    protected static class RegisterPortOperation implements
            ResourceIdOperation<Void> {
        private URI arrayId;

        public RegisterPortOperation(String arrayId) {
            this.arrayId = uri(arrayId);
        }

        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePortUtils.register(id, arrayId);
            return null;
        }
    }

    protected static class DeregisterPoolOperation implements
            ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePoolUtils.deregister(id);
            return null;
        }
    }

    protected static class RegisterPoolOperation implements
            ResourceIdOperation<Void> {
        private URI arrayId;

        public RegisterPoolOperation(String arrayId) {
            this.arrayId = uri(arrayId);
        }

        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePoolUtils.deregister(id);
            StoragePoolUtils.register(id, arrayId);
            return null;
        }
    }

    public static class StorageArrayPoolDataTable extends StoragePoolDataTable {
        public StorageArrayPoolDataTable() {
            alterColumn("name").setRenderFunction(
                    "renderStorageArrayPoolEditLink");
            alterColumn("storageSystem").hidden();
        }

        public void configureForFile() {
            alterColumns("driveTypes", "subscribedCapacity").hidden();
        }

        public void configureForECS() {
            alterColumns("registrationStatus", "storageSystem", "volumeTypes",
                    "driveTypes").hidden();
            alterColumn("status").setVisible(true);
        }
    }

    public static class StorageArrayPortDataTable extends StoragePortDataTable {
        public StorageArrayPortDataTable(StorageSystemRestRep storageSystem) {
            alterColumn("name").setRenderFunction(
                    "renderStorageArrayPortEditLink");
            if (StorageSystemTypes.isBlockStorageSystem(storageSystem
                    .getSystemType())) {
                alterColumn("iqn").hidden();
            }
            if (StorageSystemTypes.isECS(storageSystem.getSystemType())) {
                alterColumns("portGroup", "iqn", "alias").hidden();
            }

            if (StorageSystemTypes.isIsilon((storageSystem.getSystemType()))) {
                alterColumns("isDRPort").setVisible(true);
            }
        }
    }
    
    public static class PortGroupForm {
        public String id;
        public String name;
        public String description;
        public Integer maxPaths;
        public Integer pathsPerInitiator;
        public Integer minPaths;
        public List<URI> storagePorts;
        public Integer maxInitiatorsPerPort;

        public PortGroupForm form(ExportPathParametersRestRep restRep){
            this.id = restRep.getId().toString();
            this.name = restRep.getName();
            this.description = restRep.getDescription();
            this.maxPaths = restRep.getMaxPaths();
            this.minPaths = restRep.getMinPaths();
            this.pathsPerInitiator = restRep.getPathsPerInitiator();
            this.maxInitiatorsPerPort = restRep.getMaxInitiatorsPerPort();
            this.storagePorts = restRep.getStoragePorts();
            
            return this;
        }

        public void validate(String formName) {
            Validation.required(formName + ".name", name);
            Validation.required(formName + ".description", description);
            Validation.required(formName + ".storagePorts", storagePorts);
        }
        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }
}
