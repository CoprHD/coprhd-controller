/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;

import models.datatable.VCenterDataTable;
import models.datatable.VCenterDataTable.VCenterInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.MessagesUtils;
import util.VCenterUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterParam;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class VCenters extends ViprResourceController {

    protected static final String SAVED = "VCenters.saved";
    protected static final String DELETED = "VCenters.deleted";
    protected static final String UNKNOWN = "VCenters.unknown";
    protected static final String MODEL_NAME = "VCenter";
    protected static final String DETACH_STORAGE = "VCenters.detachStorage";

    public static void list() {
        renderArgs.put("dataTable", new VCenterDataTable());
        TenantSelector.addRenderArgs();
        render();
    }

    public static void listJson() {
        List<VcenterRestRep> vcenters = VCenterUtils.getVCenters(Models.currentAdminTenant());
        List<VCenterInfo> vcenterInfos = Lists.newArrayList();
        for (VcenterRestRep vcenter : vcenters) {
            vcenterInfos.add(new VCenterInfo(vcenter));
        }
        renderJSON(DataTablesSupport.createJSON(vcenterInfos, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<VCenterInfo> results = Lists.newArrayList();
        if ((ids != null) && (ids.length > 0)) {
            for (String id : ids) {
                if (StringUtils.isBlank(id)) {
                    continue;
                }
                VcenterRestRep vcenter = VCenterUtils.getVCenter(uri(id));
                if (vcenter != null) {
                    results.add(new VCenterInfo(vcenter));
                }
            }
        }
        renderJSON(results);
    }

    public static void itemDetails(String id) {
        VcenterRestRep vcenter = VCenterUtils.getVCenter(uri(id));
        List<VcenterDataCenterRestRep> dataCenters = VCenterUtils.getDataCentersInVCenter(vcenter);
        render(vcenter, dataCenters);
    }

    public static void create() {
        VCenterForm vCenter = new VCenterForm();
        vCenter.tenantId = Models.currentAdminTenant();
        render("@edit", vCenter);
    }

    public static void edit(String id) {
        VcenterRestRep dbVCenter = VCenterUtils.getVCenter(uri(id));
        if (dbVCenter != null) {
            VCenterForm vCenter = new VCenterForm(dbVCenter);
            render(vCenter, dbVCenter);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    private static void edit(VCenterForm vcenter) {
        // Remove password/confirm from params before flashing
        params.remove("vCenter.password");
        params.remove("vCenter.passwordConfirm");

        params.flash();
        Validation.keep();
        if (vcenter.isNew()) {
            create();
        }
        else {
            edit(vcenter.id);
        }
    }

    public static void save(VCenterForm vCenter) {
        vCenter.validate("vCenter");
        if (Validation.hasErrors()) {
            edit(vCenter);
        }
        else {
            Boolean validateConnectionParam = params.get("validateConnection", Boolean.class);
            boolean validateConnection = validateConnectionParam != null ? validateConnectionParam.booleanValue() : false;

            vCenter.save(validateConnection);

            flash.success(MessagesUtils.get(SAVED, vCenter.name));
            list();
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids, boolean detachStorage) {
        for (URI id : ResourceUtils.uris(ids)) {
            VCenterUtils.deactivateVCenter(id, detachStorage);
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
            VCenterUtils.detachStorage(id);
        }
        flash.success(MessagesUtils.get(DETACH_STORAGE));
        list();
    }

    protected static class DiscoveryOperation implements ResourceIdOperation<Task<VcenterRestRep>> {
        @Override
        public Task<VcenterRestRep> performOperation(URI id) throws Exception {
            return VCenterUtils.discover(id);
        }
    }

    public static class VCenterForm {

        public static final int DEFAULT_PORT = 443;

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
        @MaxSize(1024)
        public String username;

        @MaxSize(1024)
        public String password;

        public String passwordConfirm;

        @Required
        @Min(1)
        public Integer port = DEFAULT_PORT;

        public VCenterForm() {
        }

        public VCenterForm(VcenterRestRep vCenter) {
            this();
            doReadFrom(vCenter);
        }

        public void doReadFrom(VcenterRestRep vCenter) {
            this.id = vCenter.getId().toString();
            this.name = vCenter.getName();
            this.hostname = vCenter.getIpAddress();
            this.username = vCenter.getUsername();
            this.port = vCenter.getPortNumber();
        }

        public void doWriteTo(VcenterCreateParam vcenterCreateParam) {
            doWriteToBase(vcenterCreateParam);
            vcenterCreateParam.setIpAddress(this.hostname);
        }

        public void doWriteTo(VcenterUpdateParam vcenterUpdateParam) {
            doWriteToBase(vcenterUpdateParam);
            vcenterUpdateParam.setIpAddress(this.hostname);
        }

        public void doWriteToBase(VcenterParam vCenter) {
            vCenter.setName(this.name);
            vCenter.setUserName(this.username);
            if (StringUtils.isNotBlank(this.password)) {
                vCenter.setPassword(this.password);
            }
            vCenter.setPortNumber(this.port);
        }

        public void doValidation(String formName) {
            if (this.isNew()) {
                Validation.required(String.format("%s.password", formName), this.password);
            }

            boolean hasPassword = StringUtils.isNotBlank(password) || StringUtils.isNotBlank(passwordConfirm);
            boolean passwordMatches = StringUtils.equals(password, passwordConfirm);
            if (hasPassword && !passwordMatches) {
                Validation.addError(String.format("%s.passwordConfirm", formName), "error.password.doNotMatch");
            }
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            doValidation(formName);
        }

        public void save(boolean validateConnection) {
            if (isNew()) {
                try {
                    createVCenter(validateConnection);
                } catch (Exception e) {
                    flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                    Common.handleError();
                }

            }
            else {
                try {
                    updateVCenter(validateConnection);
                } catch (Exception e) {
                    flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                    Common.handleError();
                }
            }
        }

        protected Task<VcenterRestRep> createVCenter(boolean validateConnection) {
            VcenterCreateParam vcenterCreateParam = new VcenterCreateParam();
            doWriteTo(vcenterCreateParam);
            return VCenterUtils.createVCenter(uri(tenantId), vcenterCreateParam, validateConnection);
        }

        protected Task<VcenterRestRep> updateVCenter(boolean validateConnection) {
            VcenterUpdateParam vcenterUpdateParam = new VcenterUpdateParam();
            doWriteTo(vcenterUpdateParam);
            return VCenterUtils.updateVCenter(uri(id), vcenterUpdateParam, validateConnection);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }
}
