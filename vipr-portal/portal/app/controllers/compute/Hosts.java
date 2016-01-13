/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.HostTypes;
import models.datatable.HostDataTable;
import models.datatable.HostDataTable.HostInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.With;
import util.ClusterUtils;
import util.HostUtils;
import util.MessagesUtils;
import util.StringOption;
import util.VCenterUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class Hosts extends ViprResourceController {

    protected static final String SAVED = "Hosts.saved";
    protected static final String DELETED = "Hosts.deleted";
    protected static final String UNKNOWN = "Hosts.unknown";
    protected static final String MODEL_NAME = "Host";
    protected static final String DETACH_STORAGE = "Hosts.detachStorage";

    public static final String CONNECTION_FAILED_MSG = "host.validation.connection.failed";

    public static void list() {
        TenantSelector.addRenderArgs();
        renderArgs.put("dataTable", new HostDataTable());
        render();
    }

    public static void listJson() {
        String tenantId = Models.currentAdminTenant();

        List<HostRestRep> hosts = HostUtils.getHosts(tenantId);
        Map<URI, String> clusterMap = ResourceUtils.mapNames(getViprClient().clusters().listByTenant(uri(tenantId)));
        Set<URI> dataCenterIds = HostUtils.getDataCenterIds(hosts);
        Map<URI, VcenterDataCenterRestRep> vcenterDataCenters = HostUtils.getVcenterDataCenters(dataCenterIds);
        List<HostInfo> hostInfos = Lists.newArrayList();
        for (HostRestRep host : hosts) {
            hostInfos.add(new HostInfo(host, clusterMap, vcenterDataCenters));
        }
        renderJSON(DataTablesSupport.createJSON(hostInfos, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        Map<URI, String> clusterMap = ResourceUtils.mapNames(getViprClient().clusters().listByTenant(uri(Models.currentAdminTenant())));
        List<HostInfo> results = Lists.newArrayList();
        if ((ids != null) && (ids.length > 0)) {
            for (String id : ids) {
                if (StringUtils.isBlank(id)) {
                    continue;
                }
                HostRestRep host = HostUtils.getHost(uri(id));
                if (host != null) {
                    Set<URI> dataCenterIds = HostUtils.getDataCenterIds(Lists.newArrayList(host));
                    Map<URI, VcenterDataCenterRestRep> vcenterDataCenters = HostUtils.getVcenterDataCenters(dataCenterIds);
                    results.add(new HostInfo(host, clusterMap, vcenterDataCenters));
                }
            }
        }
        renderJSON(results);
    }

    public static void itemDetails(String id) {
        HostRestRep host = HostUtils.getHost(uri(id));
        ClusterRestRep cluster = ClusterUtils.getCluster(ResourceUtils.id(host.getCluster()));
        VcenterDataCenterRestRep dataCenter = VCenterUtils.getDataCenter(ResourceUtils.id(host.getvCenterDataCenter()));
        VcenterRestRep vcenter = VCenterUtils.getVCenter(dataCenter);
        render(host, cluster, dataCenter, vcenter);
    }

    private static void addReferenceData() {
        renderArgs.put("types", StringOption.options(HostTypes.STANDARD_CREATION_TYPES, HostTypes.OPTION_PREFIX, false));
        renderArgs.put("clusters", ClusterUtils.getClusterOptions(Models.currentTenant()));

        List<ProjectRestRep> projects = getViprClient().projects().getByTenant(ResourceUtils.uri(Models.currentTenant()));
        renderArgs.put("projects", projects);
    }

    public static void create() {
        addReferenceData();
        HostForm host = new HostForm();
        host.tenantId = Models.currentAdminTenant();
        render("@edit", host);
    }

    @FlashException("list")
    public static void edit(String id) {
        HostRestRep dbHost = HostUtils.getHost(uri(id));
        if (dbHost != null) {
            addReferenceData();
            HostForm host = new HostForm(dbHost);

            List<InitiatorRestRep> initiators = HostUtils.getInitiators(dbHost.getId());
            List<IpInterfaceRestRep> ipInterfaces = HostUtils.getIpInterfaces(dbHost.getId());

            render(host, dbHost, initiators, ipInterfaces);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    private static void edit(HostForm host) {
        // Clear password/confirm before flashing params
        params.remove("host.password");
        params.remove("host.passwordConfirm");
        Common.handleError();
    }

    public static void save(HostForm host) {
        host.validate("host");
        if (Validation.hasErrors()) {
            edit(host);
        }

        try {
            Boolean validateConnectionParam = params.get("validateConnection", Boolean.class);
            boolean validateConnection = validateConnectionParam != null ? validateConnectionParam.booleanValue() : false;

            host.save(validateConnection);
            flash.success(MessagesUtils.get(SAVED, host.name));
            list();
        } catch (Exception e) {
            flashException(e);
            edit(host);
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids, boolean detachStorage) {
        for (URI id : ResourceUtils.uris(ids)) {
            HostUtils.deactivate(id, detachStorage);
        }
        flash.success(MessagesUtils.get(DELETED));
        list();
    }

    public static void introspect(@As(",") String[] ids) {
        introspect(uris(ids));
    }

    private static void introspect(List<URI> ids) {
        performSuccess(ids, new DiscoveryOperation(), DISCOVERY_STARTED);
        list();
    }

    @FlashException("list")
    public static void detachStorage(@As(",") String[] ids) {
        for (URI id : ResourceUtils.uris(ids)) {
            HostUtils.detachStorage(id);
        }
        flash.success(MessagesUtils.get(DETACH_STORAGE));
        list();
    }

    protected static class DiscoveryOperation implements ResourceIdOperation<Task<HostRestRep>> {
        @Override
        public Task<HostRestRep> performOperation(URI id) throws Exception {
            return HostUtils.discover(id);
        }
    }

    public static class HostForm {
        public String id;
        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @HostNameOrIpAddress
        public String hostname;

        @Required
        @Min(1)
        public Integer port;

        @Required
        public String type;

        @MaxSize(1024)
        public String username;

        @MaxSize(1024)
        public String password;

        public String passwordConfirm;

        public boolean discoverable = true;

        public boolean useHttps;

        public HostForm() {
        }

        public HostForm(HostRestRep host) {
            this();
            doReadFrom(host);
        }

        protected void doReadFrom(HostRestRep host) {
            this.id = host.getId().toString();
            this.name = host.getName();
            this.hostname = host.getHostName();
            this.type = host.getType();

            if (!isManualHost()) {
                this.username = host.getUsername();
                if (host.getPortNumber() != null && host.getPortNumber() > -1) {
                    this.port = host.getPortNumber();
                }
            }

            this.discoverable = host.getDiscoverable() == null ? true : host.getDiscoverable();
            this.useHttps = host.getUseSsl() == null ? true : host.getUseSsl();
        }

        protected void doWriteTo(HostCreateParam hostCreateParam) {
            doWriteToHostParam(hostCreateParam);
            hostCreateParam.setType(this.type.toString());
            hostCreateParam.setHostName(this.hostname);
            hostCreateParam.setCluster(ResourceUtils.NULL_URI);
        }

        protected void doWriteTo(HostUpdateParam hostUpdateParam) {
            doWriteToHostParam(hostUpdateParam);
            hostUpdateParam.setType(this.type.toString());
            hostUpdateParam.setHostName(this.hostname);
        }

        protected void doWriteToHostParam(HostParam hostParam) {
            hostParam.setName(this.name);
            hostParam.setTenant(uri(tenantId));
            if (isManualHost()) {
                hostParam.setUserName(Messages.get("Hosts.defaultUsername"));
                hostParam.setPassword(Messages.get("Hosts.defaultPassword"));
                hostParam.setUseSsl(false);
                hostParam.setDiscoverable(false);
            }
            else {
                if (StringUtils.isNotBlank(this.username)) {
                    hostParam.setUserName(this.username);
                }
                if (StringUtils.isNotBlank(this.password)) {
                    hostParam.setPassword(this.password);
                }
                hostParam.setPortNumber(this.port);
                hostParam.setUseSsl(this.useHttps);
                hostParam.setDiscoverable(this.discoverable);
            }
        }

        private boolean isManualHost() {
            return HostTypes.isOther(this.type) || HostTypes.isSUNVCS(this.type);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            doValidation(formName);
        }

        protected void doValidation(String formName) {
            if (isManualHost()) {
                validateManualHost(formName);
            }
            else {
                validateStandardHost(formName);
            }
        }

        protected void validateStandardHost(String formName) {
            if (discoverable) {
                Validation.required(String.format("%s.username", formName), this.username);
                if (this.isNew()) {
                    Validation.required(String.format("%s.password", formName), this.password);
                }

                boolean hasPassword = StringUtils.isNotBlank(password) || StringUtils.isNotBlank(passwordConfirm);
                boolean passwordMatches = StringUtils.equals(password, passwordConfirm);
                if (hasPassword && !passwordMatches) {
                    Validation.addError(String.format("%s.passwordConfirm", formName), "error.password.doNotMatch");
                }
            }
        }

        /**
         * Clears all other validation error, except for the specified fields.
         * 
         * @param formName
         *            the form name.
         * @param fieldsToKeep
         *            the fields to keep.
         */
        protected void clearOtherErrors(String formName, String... fieldsToKeep) {
            Set<play.data.validation.Error> errors = Sets.newHashSet();
            for (String name : fieldsToKeep) {
                play.data.validation.Error error = Validation.error(String.format("%s.%s", formName, name));
                if (error != null) {
                    errors.add(error);
                }
            }
            Validation.clear();
            for (play.data.validation.Error error : errors) {
                Validation.addError(error.getKey(), error.message());
            }
        }

        protected void validateManualHost(String formName) {
            clearOtherErrors(formName, "name", "hostname");
        }

        public void save(boolean validateConnection) {
            if (isNew()) {
                createHost(validateConnection);
            }
            else {
                updateHost(validateConnection);
            }
        }

        protected Task<HostRestRep> createHost(boolean validateConnection) {
            HostCreateParam hostCreateParam = new HostCreateParam();
            doWriteTo(hostCreateParam);
            return HostUtils.createHost(hostCreateParam, validateConnection);
        }

        protected Task<HostRestRep> updateHost(boolean validateConnection) {
            HostUpdateParam hostUpdateParam = new HostUpdateParam();
            doWriteTo(hostUpdateParam);
            return HostUtils.updateHost(uri(id), hostUpdateParam, validateConnection);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }
}
