/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.backToReferrer;
import static controllers.Common.flashException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import models.ComputeImageServerListTypes;
import models.ComputeSystemTypes;
import models.RegistrationStatus;
import models.SearchScopes;
import models.VlanListTypes;
import models.datatable.ComputeImageServersDataTable.ComputeImageServerInfo;
import models.datatable.ComputeSystemElementDataTable;
import models.datatable.ComputeSystemElementDataTable.ComputeElementInfo;
import models.datatable.ComputeSystemsDataTable;
import models.datatable.ComputeSystemsDataTable.ComputeSystemsInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.ComputeImageServerUtils;
import util.ComputeSystemUtils;
import util.DefaultComputeSystemPortMap;
import util.EnumOption;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddressCheck;

import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeSystemCreate;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.compute.ComputeSystemUpdate;
import com.emc.vipr.client.Task;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ComputeSystems extends ViprResourceController {

    protected static final String SAVED = "ComputeSystems.saved";
    protected static final String UNKNOWN = "ComputeSystems.unknown";
    protected static final String MODEL_NAME = "ComputeSystems";
    protected static final String DELETED_SUCCESS = "ComputeSystems.deleted.success";
    protected static final String DELETED_ERROR = "ComputeSystems.deleted.error";
    protected static final String AVAILABLE = "AVAILABLE";

    //
    // Add reference data so that they can be reference in html template
    //

    private static void addReferenceData() {
        renderArgs.put("computeSystemTypeList",
                ComputeSystemTypes.options(ComputeSystemTypes.VALUES));
        renderArgs.put("searchScopeTypeList", SearchScopes.options(
                SearchScopes.ONELEVEL, SearchScopes.SUBTREE));
        List<EnumOption> defaultComputeSystemPortMap = Arrays.asList(EnumOption.options(DefaultComputeSystemPortMap.values()));
        renderArgs.put("defaultComputeSystemPortMap", defaultComputeSystemPortMap);

    }

    public static void list() {
        renderArgs.put("dataTable", new ComputeSystemsDataTable());
        render();
    }

    public static void listElements(String computeSystemId) {
        ComputeSystemRestRep computeSystem = ComputeSystemUtils
                .getComputeSystem(computeSystemId);
        ComputeSystemElementDataTable dataTable = new ComputeSystemElementDataTable();
        render("@listElements", computeSystem, dataTable);
    }

    public static void listJson() {
        performListJson(ComputeSystemUtils.getComputeSystems(),
                new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(ComputeSystemUtils.getComputeSystems(ids),
                new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        ComputeSystemRestRep computeSystem = ComputeSystemUtils
                .getComputeSystem(id);
        if (computeSystem == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        render(computeSystem);
    }

    public static void elementDetails(String id) {
        ComputeElementRestRep computeElement = ComputeSystemUtils
                .getComputeElement(id);
        if (computeElement == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        render(computeElement);
    }

    public static void create() {
        addReferenceData();
        ComputeSystemForm computeSystems = new ComputeSystemForm();
        // put all "initial create only" defaults here rather than field
        // initializers
        List<ComputeImageServerRestRep> computeImageServersList = ComputeImageServerUtils.getComputeImageServers();
        if (computeImageServersList != null) {
            List<StringOption> computeImageServerOptions = new ArrayList<StringOption>();
            List<String> computeImageServersArrayList = new ArrayList<String>();
            computeImageServerOptions.add(ComputeImageServerListTypes.option(ComputeImageServerListTypes.NO_COMPUTE_IMAGE_SERVER_NONE));
            for (ComputeImageServerRestRep cisrr : computeImageServersList) {
                if (cisrr.getComputeImageServerStatus().equalsIgnoreCase(AVAILABLE)) {
                    computeImageServersArrayList.add(cisrr.getName());
                }
            }
            for (String imageServerId : computeImageServersArrayList) {
                computeImageServerOptions.add(ComputeImageServerListTypes.option(imageServerId));
            }
            renderArgs.put("availableComputeImageServersList", computeImageServerOptions);
        }
        computeSystems.portNumber = getDefaultPort(DefaultComputeSystemPortMap.port443);
        computeSystems.useSSL = true;
        render("@edit", computeSystems);
    }

    public static void edit(String id) {
        try {
            addReferenceData();

            ComputeSystemRestRep computeSystem = ComputeSystemUtils
                    .getComputeSystem(id);
            if (computeSystem != null) {
                if (computeSystem.getVlans() != null) {
                    List<StringOption> vlanOptions = new ArrayList<StringOption>();
                    vlanOptions.add(VlanListTypes.option(VlanListTypes.NO_OSINSTALL_NONE));
                    List<String> vlanList = new ArrayList<String>(Arrays.asList(computeSystem.getVlans().split(",")));
                    for (String vlan : vlanList) {
                        vlanOptions.add(VlanListTypes.option(vlan));
                    }
                    renderArgs.put("computeSystemVlanList", vlanOptions);
                }

                List<StringOption> computeImageServerOptions = new ArrayList<StringOption>();
                computeImageServerOptions.add(ComputeImageServerListTypes
                        .option(ComputeImageServerListTypes.NO_COMPUTE_IMAGE_SERVER_NONE));
                List<ComputeImageServerRestRep> computeImageServersList = ComputeImageServerUtils.getComputeImageServers();
                if (computeImageServersList != null) {
                    List<String> computeImageServersArrayList = new ArrayList<String>();
                    for (ComputeImageServerRestRep cisrr : computeImageServersList) {
                        if (cisrr.getComputeImageServerStatus().equalsIgnoreCase(AVAILABLE)) {
                            computeImageServersArrayList.add(cisrr.getName());
                        }
                    }
                    for (String imageServerId : computeImageServersArrayList) {
                        computeImageServerOptions.add(ComputeImageServerListTypes.option(imageServerId));
                    }
                    renderArgs.put("availableComputeImageServersList", computeImageServerOptions);
                }
                ComputeSystemForm computeSystems = new ComputeSystemForm(computeSystem);
                render("@edit", computeSystems);
            } else {
                flash.error(MessagesUtils.get(UNKNOWN, id));
                list();
            }
        } catch (Exception e) {
            flashException(e);
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(ComputeSystemForm computeSystems) {
        computeSystems.validate("computeSystems");

        if (Validation.hasErrors()) {
            handleError(computeSystems);
        }

        computeSystems.save();
        String name = computeSystems.name;
        flash.success(MessagesUtils.get(SAVED, name));
        backToReferrer();
        list();
    }

    private static Integer getDefaultPort(DefaultComputeSystemPortMap value) {
        String defaultValue = MessagesUtils
                .get(DefaultComputeSystemPortMap.class.getSimpleName() + "."
                        + value.name());
        return Integer.valueOf(StringUtils.defaultIfBlank(defaultValue, "0"));
    }

    private static void handleError(ComputeSystemForm computeSystems) {
        // Clear password/confirm before flashing params
        params.remove("computeSystems.password");
        params.remove("computeSystems.passwordConfirm");
        params.flash();
        Validation.keep();
        if (computeSystems.isNew()) {
            create();
        }
        else {
            edit(computeSystems.id);
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    @FlashException("list")
    public static void introspect(@As(",") String[] ids) {
        introspect(uris(ids));
    }

    private static void introspect(List<URI> ids) {
        performSuccess(ids, new DiscoveryOperation(), DISCOVERY_STARTED);
        list();
    }

    @FlashException("list")
    public static void deregisterComputes(@As(",") String[] ids) {
        deregisterComputes(uris(ids));
    }

    private static void deregisterComputes(List<URI> ids) {
        performSuccessFail(ids, new DeregisterOperation(), DEREGISTER_SUCCESS,
                DEREGISTER_ERROR);
        list();
    }

    @FlashException("list")
    public static void registerComputes(@As(",") String[] ids) {
        registerComputes(uris(ids));
    }

    private static void registerComputes(List<URI> ids) {
        performSuccessFail(ids, new RegisterOperation(), REGISTER_SUCCESS,
                REGISTER_ERROR);
        list();
    }

    @FlashException(referrer = { "listElements" })
    public static void deregisterElements(@As(",") String[] ids,
            String computeSystemId) {
        deregisterElements(uris(ids), computeSystemId);
    }

    private static void deregisterElements(List<URI> ids, String computeSystemId) {
        performSuccessFail(ids, new DeregisterElementOperation(),
                DEREGISTER_SUCCESS, DEREGISTER_ERROR);
        listElements(computeSystemId);
    }

    @FlashException(referrer = { "listElements" })
    public static void registerElements(@As(",") String[] ids,
            String computeSystemId) {
        registerElements(uris(ids), computeSystemId);
    }

    private static void registerElements(List<URI> ids, String computeSystemId) {
        performSuccessFail(ids, new RegisterElementOperation(computeSystemId),
                REGISTER_SUCCESS, REGISTER_ERROR);
        listElements(computeSystemId);
    }

    public static void elements(String id) {
        addReferenceData();

        ComputeSystemRestRep computeSystem = ComputeSystemUtils
                .getComputeSystem(id);
        ComputeSystemElementDataTable dataTable = new ComputeSystemElementDataTable();
        render("@listElements", computeSystem, dataTable);
    }

    public static void computeElementsJson(String id) {
        List<ComputeElementInfo> results = Lists.newArrayList();
        List<ComputeElementRestRep> computeElements = ComputeSystemUtils
                .getComputeElements(id);
        String computeSystemName = ComputeSystemUtils.getComputeSystem(id)
                .getName();
        for (ComputeElementRestRep computeElement : computeElements) {
            results.add(new ComputeElementInfo(computeElement,
                    computeSystemName));
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void computeImageServersJson() {
        List<ComputeImageServerInfo> results = Lists.newArrayList();
        List<ComputeImageServerRestRep> computeImageServers = ComputeImageServerUtils.getComputeImageServers();
        for (ComputeImageServerRestRep computeImageServerRR : computeImageServers) {
            ComputeImageServerInfo computeImageServer = new ComputeImageServerInfo(computeImageServerRR);
            results.add(computeImageServer);
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static class ComputeSystemForm {

        public String id;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        @MaxSize(2048)
        public String ipAddress;

        public Boolean useSSL;

        @Required
        public Integer portNumber;

        public String systemType;

        public String osInstallNetwork;

        @MaxSize(2048)
        @Required
        public String userName;

        @MaxSize(2048)
        public String password = "";// NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

        @MaxSize(2048)
        public String confirmPassword = "";// NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

        public String vlanList;

        public List<String> computeImageServerOptions = Lists.newArrayList();;

        public String computeImageServer;

        public ComputeSystemForm() {
        }

        public boolean unregistered;

        public ComputeSystemForm(ComputeSystemRestRep computeSystem) {
            this.id = computeSystem.getId().toString();
            this.name = computeSystem.getName();
            this.ipAddress = computeSystem.getIpAddress();
            this.systemType = computeSystem.getSystemType();
            this.useSSL = computeSystem.getUseSSL();
            this.portNumber = computeSystem.getPortNumber();
            this.osInstallNetwork = computeSystem.getOsInstallNetwork();
            this.vlanList = computeSystem.getOsInstallNetwork();
            this.userName = computeSystem.getUsername();
            this.password = ""; // the platform will never return the real password //NOSONAR
                                // ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")
            this.unregistered = RegistrationStatus.isUnregistered(computeSystem.getRegistrationStatus());
            if (computeSystem.getComputeImageServer().equalsIgnoreCase("null")) {
                this.computeImageServer = ComputeImageServerListTypes.NO_COMPUTE_IMAGE_SERVER_NONE;
            } else {
                ComputeImageServerRestRep cisrr = ComputeImageServerUtils.getComputeImageServer(computeSystem.getComputeImageServer());
                this.computeImageServer = cisrr.getName();
            }

        }

        public boolean isNew() {
            return StringUtils.isBlank(this.id);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isNew()) {
                Validation.required(fieldName + ".ipAddress", this.ipAddress);
                Validation.required(fieldName + ".password", this.password);
                Validation.required(fieldName + ".confirmPassword",
                        this.confirmPassword);
                if (!HostNameOrIpAddressCheck.isValidIp(ipAddress)) {
                    Validation.addError(fieldName + ".ipAddress",
                            MessagesUtils.get("computeSystem.invalid.ipAddress"));
                }
            }

            if (!StringUtils.equals(StringUtils.trim(password),
                    StringUtils.trim(confirmPassword))) {
                Validation
                        .addError(fieldName + ".confirmPassword", MessagesUtils
                                .get("computeSystem.confirmPassword.not.match"));
            }

            if (!StringUtils.isEmpty(osInstallNetwork) && StringUtils.isNumeric(osInstallNetwork)) {
                try {
                    int osInstall = Integer.parseInt(osInstallNetwork);
                    if (osInstall < 1 || osInstall > 4093) {
                        Validation.addError(fieldName + ".osInstallNetwork",
                                MessagesUtils.get("computeSystem.invalid.osInstallNetwork"));
                    }
                } catch (NumberFormatException e) {
                    Validation.addError(fieldName + ".osInstallNetwork",
                            MessagesUtils.get("computeSystem.invalid.osInstallNetwork"));
                }

            } else if (!StringUtils.isEmpty(osInstallNetwork)) {
                Validation.addError(fieldName + ".osInstallNetwork",
                        MessagesUtils.get("computeSystem.invalid.osInstallNetwork"));
            }

        }

        public Task<ComputeSystemRestRep> save() {
            if (isNew()) {
                return create();
            } else {
                return update();
            }
        }

        private Task<ComputeSystemRestRep> create() {
            ComputeSystemCreate createParam = new ComputeSystemCreate();
            createParam.setName(this.name);
            createParam.setIpAddress(this.ipAddress);
            createParam.setPassword(this.password);
            createParam.setPortNumber(this.portNumber);
            createParam.setSystemType(this.systemType);
            createParam.setUserName(this.userName);
            createParam.setUseSSL(this.useSSL);
            if (!this.osInstallNetwork.isEmpty()) {
                createParam.setOsInstallNetwork(this.osInstallNetwork);
            }
            if (this.computeImageServer != null) {
                if (this.computeImageServer.equalsIgnoreCase(ComputeImageServerListTypes.NO_COMPUTE_IMAGE_SERVER_NONE)) {
                    URI computeImageServerUrl = null;
                    try {
                        computeImageServerUrl = new URI("");
                    } catch (URISyntaxException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    createParam.setComputeImageServer(computeImageServerUrl);
                } else {
                    ComputeImageServerRestRep cisrr = ComputeImageServerUtils.getComputeImageServerByName(this.computeImageServer);
                    createParam.setComputeImageServer(cisrr.getId());
                }
            }
            return ComputeSystemUtils.create(createParam);
        }

        private Task<ComputeSystemRestRep> update() {
            ComputeSystemUpdate updateParam = new ComputeSystemUpdate();
            ComputeSystemRestRep origCS = ComputeSystemUtils.getComputeSystem(this.id);
            updateParam.setName(this.name);
            // password is optional on update - but setting "" to update param gives an error
            if (this.password != null && this.password.length() > 0) {
                updateParam.setPassword(this.password);
            }
            updateParam.setPortNumber(this.portNumber);
            if (!this.osInstallNetwork.isEmpty()) {
                updateParam.setOsInstallNetwork(this.osInstallNetwork);
            }
            else if (origCS.getOsInstallNetwork() != null) {
                updateParam.setOsInstallNetwork(" ");
            }
            if (this.vlanList != null && !this.vlanList.isEmpty()) {
                if (vlanList.equalsIgnoreCase(VlanListTypes.NO_OSINSTALL_NONE)) {
                    updateParam.setOsInstallNetwork(" ");
                }
                else {
                    updateParam.setOsInstallNetwork(this.vlanList);
                }
            }
            if (this.computeImageServer != null) {
                if (this.computeImageServer.equalsIgnoreCase(ComputeImageServerListTypes.NO_COMPUTE_IMAGE_SERVER_NONE)) {
                    URI computeImageServerUrl = null;
                    try {
                        computeImageServerUrl = new URI("");
                    } catch (URISyntaxException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    updateParam.setComputeImageServer(computeImageServerUrl);
                } else {
                    ComputeImageServerRestRep cisrr = ComputeImageServerUtils.getComputeImageServerByName(this.computeImageServer);
                    updateParam.setComputeImageServer(cisrr.getId());
                }
            }
            updateParam.setUserName(this.userName);
            updateParam.setUseSSL(this.useSSL);
            return ComputeSystemUtils.update(id, updateParam);
        }
    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<ComputeSystemsInfo, ComputeSystemRestRep> {
        @Override
        public ComputeSystemsInfo performOperation(
                ComputeSystemRestRep computeSystem) throws Exception {
            return new ComputeSystemsInfo(computeSystem);
        }
    }

    protected static class DeleteOperation implements ResourceIdOperation<Task<ComputeSystemRestRep>> {
        @Override
        public Task<ComputeSystemRestRep> performOperation(URI id) throws Exception {
            Task<ComputeSystemRestRep> task = ComputeSystemUtils.deactivate(id);
            return task;
        }
    }

    protected static class DiscoverOperation implements
            ResourceIdOperation<Task<ComputeSystemRestRep>> {
        @Override
        public Task<ComputeSystemRestRep> performOperation(URI id)
                throws Exception {
            Task<ComputeSystemRestRep> task = ComputeSystemUtils.discover(id);
            return task;
        }
    }

    protected static class DiscoveryOperation implements
            ResourceIdOperation<Task<ComputeSystemRestRep>> {
        @Override
        public Task<ComputeSystemRestRep> performOperation(URI id)
                throws Exception {
            Task<ComputeSystemRestRep> task = ComputeSystemUtils.discover(id);
            return task;
        }
    }

    protected static class DeregisterOperation implements
            ResourceIdOperation<ComputeSystemRestRep> {
        @Override
        public ComputeSystemRestRep performOperation(URI id) throws Exception {
            return ComputeSystemUtils.deregister(id);
        }
    }

    protected static class RegisterOperation implements
            ResourceIdOperation<ComputeSystemRestRep> {
        @Override
        public ComputeSystemRestRep performOperation(URI id) throws Exception {
            return ComputeSystemUtils.register(id);
        }
    }

    protected static class RediscoverElementOperation implements
            ResourceIdOperation<ComputeElementRestRep> {
        @Override
        public ComputeElementRestRep performOperation(URI id) throws Exception {
            return ComputeSystemUtils.rediscoverElement(id);
        }
    }

    protected static class DeregisterElementOperation implements
            ResourceIdOperation<ComputeElementRestRep> {
        @Override
        public ComputeElementRestRep performOperation(URI id) throws Exception {
            return ComputeSystemUtils.deregisterElement(id);
        }
    }

    protected static class RegisterElementOperation implements
            ResourceIdOperation<ComputeElementRestRep> {
        private URI computeSystemId;

        public RegisterElementOperation(String computeSystemId) {
            this.computeSystemId = uri(computeSystemId);
        }

        @Override
        public ComputeElementRestRep performOperation(URI id) throws Exception {
            return ComputeSystemUtils.registerElement(id);
        }
    }

}
