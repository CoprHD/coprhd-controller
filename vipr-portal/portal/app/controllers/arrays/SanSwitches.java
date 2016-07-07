/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import models.NetworkSystemTypes;
import models.RegistrationStatus;
import models.datatable.SanSwitchDataTable;
import models.datatable.SanSwitchDataTable.SanSwitchInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.DefaultSanSwitchPortMap;
import util.EnumOption;
import util.MessagesUtils;
import util.NetworkSystemUtils;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.network.NetworkSystemCreate;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.network.NetworkSystemUpdate;
import com.emc.vipr.client.Task;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class SanSwitches extends ViprResourceController {

    protected static final String SAVED = "SanSwitches.saved";
    protected static final String DELETED_SUCCESS = "SanSwitches.deleted.success";
    protected static final String DELETED_ERROR = "SanSwitches.deleted.error";
    protected static final String UNKNOWN = "SanSwitches.unknown";
    protected static final String MODEL_NAME = "SanSwitches";
    protected static final String DEREGISTER_SUCCESS = "PhysicalAssets.deregistration.success";
    protected static final String DEREGISTER_ERROR = "PhysicalAssets.deregistration.error";
    protected static final String REGISTER_SUCCESS = "PhysicalAssets.registration.success";
    protected static final String REGISTER_ERROR = "PhysicalAssets.registration.error";

    //
    // Add reference data so that they can be reference in html template
    //
    private static void addReferenceData() {
        renderArgs.put("sanSwitchTypeList", NetworkSystemTypes.options(NetworkSystemTypes.VALUES));

        // pass brocade enum into template for port defaault logic
        renderArgs.put("brocadeType", NetworkSystemTypes.BROCADE);

        List<EnumOption> defaultSanSwitchPortMap = Arrays.asList(EnumOption.options(DefaultSanSwitchPortMap.values()));
        renderArgs.put("defaultSanSwitchPortMap", defaultSanSwitchPortMap);

    }

    public static void list() {
        renderArgs.put("dataTable", new SanSwitchDataTable());
        render();
    }

    public static void listJson() {
        performListJson(NetworkSystemUtils.getNetworkSystems(), new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(NetworkSystemUtils.getNetworkSystems(ids), new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        NetworkSystemRestRep networkSystem = NetworkSystemUtils.getNetworkSystem(id);
        render(networkSystem);
    }

    public static void create() {
        SanSwitchForm sanSwitch = new SanSwitchForm();
        // put all "initial create only" defaults here rather than field initializers
        sanSwitch.useSSL = true;
        edit(sanSwitch);
    }

    private static void edit(SanSwitchForm sanSwitch) {
        addReferenceData();
        render("@edit", sanSwitch);
    }

    @FlashException("list")
    public static void edit(String id) {
        NetworkSystemRestRep networkSystem = NetworkSystemUtils.getNetworkSystem(id);
        if (networkSystem == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
        edit(new SanSwitchForm(networkSystem));
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(SanSwitchForm sanSwitch) {
        sanSwitch.validate("sanSwitch");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        sanSwitch.save();
        flash.success(MessagesUtils.get(SAVED, sanSwitch.name));
        response.setCookie("guide_fabric", sanSwitch.name);

        list();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(NetworkSystemUtils.getNetworkSystems(ids), new DeactivateOperation(), DELETED_SUCCESS,
                DELETED_ERROR);
        list();
    }

    public static void introspect(@As(",") String[] ids) {
        discover(uris(ids));
    }

    private static void discover(List<URI> ids) {
        performSuccess(ids, new DiscoveryOperation(), DISCOVERY_STARTED);
        list();
    }

    public static void deregister(@As(",") String[] ids, String arrayId) {
        deregister(uris(ids));
    }

    private static void deregister(List<URI> ids) {
        performSuccessFail(ids, new DeregisterOperation(), DEREGISTER_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void register(@As(",") String[] ids, String arrayId) {
        register(uris(ids));
    }

    private static void register(List<URI> ids) {
        performSuccessFail(ids, new RegisterOperation(), REGISTER_SUCCESS, DELETED_ERROR);
        list();
    }

    public static class SanSwitchForm {

        public String id;

        @Required
        public String deviceType;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        @HostNameOrIpAddress
        @Required
        public String ipAddress;

        @Required
        public Integer portNumber;

        @MaxSize(2048)
        public String userName;

        @MaxSize(2048)
        public String userPassword = ""; // NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

        @MaxSize(2048)
        public String confirmPassword = ""; // NOSONAR
                                            // ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

        public boolean useSSL;

        public Date lastDiscoveryRunTime;

        public SanSwitchForm() {
        }

        public SanSwitchForm(NetworkSystemRestRep networkSystemResponse) {
            readFrom(networkSystemResponse);
        }

        public void readFrom(NetworkSystemRestRep sanSwitch) {

            this.id = stringId(sanSwitch);
            this.name = sanSwitch.getName();
            this.deviceType = sanSwitch.getSystemType();
            if (NetworkSystemTypes.isSmisManaged(sanSwitch.getSystemType())) {
                this.ipAddress = sanSwitch.getSmisProviderIP();
                this.userName = sanSwitch.getSmisUserName();
                this.portNumber = sanSwitch.getSmisPortNumber();
                this.useSSL = sanSwitch.getSmisUseSSL();
            }
            else {
                this.ipAddress = sanSwitch.getIpAddress();
                this.userName = sanSwitch.getUsername();
                this.portNumber = sanSwitch.getPortNumber();
                this.useSSL = false;
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public Task<NetworkSystemRestRep> save() {
            if (isNew()) {
                return create();
            }
            else {
                return update();
            }
        }

        private Task<NetworkSystemRestRep> update() {
            NetworkSystemUpdate sanSwitchParam = new NetworkSystemUpdate();

            sanSwitchParam.setName(this.name);
            // sanSwitchParam.setSystemType(this.deviceType);
            sanSwitchParam.setIpAddress(this.ipAddress);
            sanSwitchParam.setPortNumber(this.portNumber);
            sanSwitchParam.setUserName(StringUtils.trimToNull(this.userName));
            sanSwitchParam.setPassword(StringUtils.trimToNull(this.userPassword));
            sanSwitchParam.setSmisProviderIp(this.ipAddress);
            sanSwitchParam.setSmisPortNumber(this.portNumber);
            sanSwitchParam.setSmisUserName(StringUtils.trimToNull(this.userName));
            sanSwitchParam.setSmisPassword(StringUtils.trimToNull(this.userPassword));
            sanSwitchParam.setSmisUseSsl(this.useSSL);

            return NetworkSystemUtils.update(this.id, sanSwitchParam);
        }

        private Task<NetworkSystemRestRep> create() {
            NetworkSystemCreate sanSwitchParam = new NetworkSystemCreate();
            sanSwitchParam.setName(this.name);
            sanSwitchParam.setUserName(this.userName);
            sanSwitchParam.setPassword(StringUtils.trimToNull(this.userPassword));
            sanSwitchParam.setPortNumber(this.portNumber);
            sanSwitchParam.setIpAddress(this.ipAddress);
            sanSwitchParam.setSystemType(this.deviceType);
            sanSwitchParam.setSmisProviderIp(this.ipAddress);
            sanSwitchParam.setSmisPortNumber(this.portNumber);
            sanSwitchParam.setSmisUserName(this.userName);
            sanSwitchParam.setSmisPassword(StringUtils.trimToNull(this.userPassword));
            sanSwitchParam.setSmisUseSsl(this.useSSL);

            Task<NetworkSystemRestRep> task = NetworkSystemUtils.create(sanSwitchParam);
            this.id = stringId(task.getResource());
            return task;
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isNew()) {
                Validation.required(fieldName + ".userName", this.userName);
                Validation.required(fieldName + ".userPassword", this.userPassword);
                Validation.required(fieldName + ".confirmPassword", this.confirmPassword);
            }
            if (!StringUtils.equals(StringUtils.trimToEmpty(this.userPassword),
                    StringUtils.trimToEmpty(this.confirmPassword))) {
                Validation.addError(fieldName + ".confirmPassword", "error.password.doNotMatch");
            }
            // Validation.ipv4Address(fieldName + ".ipAddress", this.ipAddress);
        }

    }

    protected static class JsonItemOperation implements ResourceValueOperation<SanSwitchInfo, NetworkSystemRestRep> {
        @Override
        public SanSwitchInfo performOperation(NetworkSystemRestRep networkSystem) throws Exception {
            return new SanSwitchInfo(networkSystem);
        }
    }

    protected static class DeactivateOperation implements ResourceValueOperation<Void, NetworkSystemRestRep> {
        @Override
        public Void performOperation(NetworkSystemRestRep networkSystem) throws Exception {
            if (RegistrationStatus.isRegistered(networkSystem.getRegistrationStatus())) {
                NetworkSystemUtils.deregister(networkSystem.getId());
            }
            NetworkSystemUtils.deactivate(networkSystem.getId());
            return null;
        }
    }

    protected static class RegisterOperation implements ResourceIdOperation<NetworkSystemRestRep> {
        @Override
        public NetworkSystemRestRep performOperation(URI id) throws Exception {
            return NetworkSystemUtils.register(id);
        }
    }

    protected static class DeregisterOperation implements ResourceIdOperation<NetworkSystemRestRep> {
        @Override
        public NetworkSystemRestRep performOperation(URI id) throws Exception {
            return NetworkSystemUtils.deregister(id);
        }
    }

    protected static class DiscoveryOperation implements ResourceIdOperation<Task<NetworkSystemRestRep>> {
        @Override
        public Task<NetworkSystemRestRep> performOperation(URI id) throws Exception {
            return NetworkSystemUtils.discover(id);
        }
    }
}
